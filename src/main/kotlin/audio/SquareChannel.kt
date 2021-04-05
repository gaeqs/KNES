package audio

import util.ror

@ExperimentalUnsignedTypes
class SquareChannel {

    companion object {
        private val SQUARE_LOOKUP = IntArray(31) { ((95.52 / (8128.0 / it + 100.0)) * 49151.0).toInt() }
        private var accumulator = 0
        private var dcKiller = 0

        fun getSquareSample(channel1: SquareChannel, channel2: SquareChannel): Int {
            val value = SQUARE_LOOKUP[channel1.getVolume() * channel1.sample
                    + channel2.getVolume() * channel2.sample]
            return lowPass(highPass(value))
        }

        private fun lowPass(sample: Int): Int {
            accumulator += (sample - accumulator) / 2
            return accumulator
        }

        private fun highPass(sample: Int): Int {
            val snapshot = sample - dcKiller
            dcKiller += snapshot shr 8
            dcKiller += if (snapshot > 0) 1 else -1
            return snapshot
        }

    }

    var enabled = false
    var halt = false
    var sample = 0

    val sequencer = Sequencer()
    val oscillatorPulse = OscillatorPulse()
    val envelope = Envelope()
    val lengthCounter = LengthCounter()
    val sweeper = Sweeper()

    fun clock(quarterFrameClock: Boolean, halfFrameClock: Boolean, globalTime: Double) {
        if (!enabled) {
            sample = 0
            return
        }
        if (quarterFrameClock) {
            envelope.clock(halt)
        }

        if (halfFrameClock) {
            lengthCounter.clock(enabled, halt)
            sequencer.reload = sweeper.clock(sequencer.reload, 0u)
        }

        sequencer.clock(enabled) { it ror 1 }
        oscillatorPulse.frequency = 1789773.0 / (16.0 * (sequencer.reload.toDouble() + 1.0))
        oscillatorPulse.amplitude = (envelope.output.toDouble() - 1.0) / 16.0
        sample = if (sequencer.output) 1 else 0 //oscillatorPulse.sample(globalTime)
//
//        if (lengthCounter.counter > 0u && sequencer.timer >= 8u && !sweeper.mute && envelope.output > 2u) {
//            output += (sample - output) * 0.5
//        } else {
//            output = 0.0
//        }
    }

    fun getVolume(): Int {
        return if (lengthCounter.counter <= 0u || sweeper.mute) 0
        else envelope.output.toInt()
    }

    fun trackSweep() {
        sweeper.track(sequencer.reload)
    }

}