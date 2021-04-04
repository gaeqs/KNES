package audio

import util.concatenate
import util.shr

@ExperimentalUnsignedTypes
class OLC2A03(val beeper: Beeper) {

    companion object {
        val LENGTH_TABLE = ubyteArrayOf(
            10u, 254u, 20u, 2u, 40u, 4u, 80u, 6u,
            160u, 8u, 60u, 10u, 14u, 12u, 26u, 14u,
            12u, 16u, 24u, 18u, 48u, 20u, 96u, 22u,
            192u, 24u, 72u, 26u, 16u, 28u, 32u, 30u
        )
        val TIME_PER_NES_CLOCK = 1.0 / 5369318.0 // PPU FREQUENCY
        val TIME_PER_SAMPLE = 1.0 / Beeper.SAMPLE_RATE // PPU FREQUENCY
    }

    val channel1 = SquareChannel()
    val channel2 = SquareChannel()

    private var counter = 0
    private var globalTime = 0.0
    private var audioTime = 0.0

    /**
     * CPU bus communication
     */
    fun cpuWrite(address: UShort, data: UByte) {
        when (address.toUInt()) {
            0x4000u -> {
                when ((data and 0xC0u shr 6).toUInt()) {
                    0x0u -> {
                        channel1.sequencer.sequenceSnapshot = 0b00000001u
                        channel1.oscillatorPulse.dutyCycle = 0.125
                    }
                    0x1u -> {
                        channel1.sequencer.sequenceSnapshot = 0b00000011u
                        channel1.oscillatorPulse.dutyCycle = 0.250
                    }
                    0x2u -> {
                        channel1.sequencer.sequenceSnapshot = 0b00001111u
                        channel1.oscillatorPulse.dutyCycle = 0.500
                    }
                    0x3u -> {
                        channel1.sequencer.sequenceSnapshot = 0b11111100u
                        channel1.oscillatorPulse.dutyCycle = 0.750
                    }
                }
                channel1.halt = data and 0x20u > 0u
                channel1.envelope.volume = (data and 0x0Fu).toUShort()
                channel1.envelope.disabled = data and 0x10u > 0u
            }
            0x4001u -> {
                channel1.sweeper.enabled = data and 0x80u > 0u
                channel1.sweeper.period = data and 0x70u shr 4
                channel1.sweeper.down = data and 0x08u > 0u
                channel1.sweeper.shift = data and 0x07u
                channel1.sweeper.reload = true
            }
            0x4002u -> {
                channel1.sequencer.reload = channel1.sequencer.reload and 0xFF00u or data.toUShort()
            }
            0x4003u -> {
                channel1.sequencer.reload = data concatenate channel1.sequencer.reload.toUByte()
                channel1.sequencer.timer = channel1.sequencer.reload
                channel1.sequencer.sequence = channel1.sequencer.sequenceSnapshot
                channel1.lengthCounter.counter = LENGTH_TABLE[(data and 0xF8u shr 3).toInt()]
                channel1.envelope.start = true
            }
            0x4004u -> {
                when ((data and 0xC0u shr 6).toUInt()) {
                    0x0u -> {
                        channel2.sequencer.sequenceSnapshot = 0b00000001u
                        channel2.oscillatorPulse.dutyCycle = 0.125
                    }
                    0x1u -> {
                        channel2.sequencer.sequenceSnapshot = 0b00000011u
                        channel2.oscillatorPulse.dutyCycle = 0.250
                    }
                    0x2u -> {
                        channel2.sequencer.sequenceSnapshot = 0b00001111u
                        channel2.oscillatorPulse.dutyCycle = 0.500
                    }
                    0x3u -> {
                        channel2.sequencer.sequenceSnapshot = 0b11111100u
                        channel2.oscillatorPulse.dutyCycle = 0.750
                    }
                }
                channel2.halt = data and 0x20u > 0u
                channel2.envelope.volume = (data and 0x0Fu).toUShort()
                channel2.envelope.disabled = data and 0x10u > 0u
            }
            0x4005u -> {
                channel2.sweeper.enabled = data and 0x80u > 0u
                channel2.sweeper.period = data and 0x70u shr 4
                channel2.sweeper.down = data and 0x08u > 0u
                channel2.sweeper.shift = data and 0x07u
                channel2.sweeper.reload = true
            }
            0x4006u -> {
                channel2.sequencer.reload = channel2.sequencer.reload and 0xFF00u or data.toUShort()
            }
            0x4007u -> {
                channel2.sequencer.reload = data concatenate channel2.sequencer.reload.toUByte()
                channel2.sequencer.timer = channel2.sequencer.reload
                channel2.sequencer.sequence = channel2.sequencer.sequenceSnapshot
                channel2.lengthCounter.counter = LENGTH_TABLE[(data and 0xF8u shr 3).toInt()]
                channel2.envelope.start = true
            }
            0x4008u -> {
            }
            0x400Cu -> {
            }
            0x400Eu -> {
            }
            0x4015u -> {
                channel1.enabled = data and 0x1u > 0u
                channel2.enabled = data and 0x2u > 0u
            }
            0x400Fu -> {
                channel1.envelope.start = true
                channel2.envelope.start = true
            }
        }
    }

    /**
     * CPU bus communication
     */
    fun cpuRead(address: UShort, readOnly: Boolean = false): UByte {
        if (address.toUInt() == 0x4015u) {
            var data: UByte = if (channel1.lengthCounter.counter > 0u) 1u else 0u
            data = data or (if (channel2.lengthCounter.counter > 0u) 2u else 0u)
            return data
        }
        return 0x40u
    }

    fun clock() {
        globalTime += (3.0 / 1789773.0)
        counter++

        var quarterFrameClock = false
        var halfFrameClock = false

        when (counter) {
            3729, 11186 -> quarterFrameClock = true
            7457 -> {
                quarterFrameClock = true
                halfFrameClock = true
            }
            14916 -> {
                quarterFrameClock = true
                halfFrameClock = true
                counter = 0
            }
        }

        channel1.clock(quarterFrameClock, halfFrameClock, globalTime)
        channel2.clock(quarterFrameClock, halfFrameClock, globalTime)
    }

    fun onFrame() {
        beeper.flushFrameSamples()
    }

    fun checkAndSendSample() {
        channel1.trackSweep()
        audioTime += TIME_PER_NES_CLOCK
        while (audioTime >= TIME_PER_SAMPLE) {
            audioTime -= TIME_PER_SAMPLE
            beeper.sendSample(getOutputSample())
        }
    }

    fun reset() {

    }

    fun getOutputSample(): Double {
        return ((1.0 * channel1.output) - 0.8) * 0.1 +
                ((1.0 * channel2.output) - 0.8) * 0.1
    }
}