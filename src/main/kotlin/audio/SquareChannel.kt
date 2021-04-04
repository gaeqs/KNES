package audio

import util.ror

@ExperimentalUnsignedTypes
class SquareChannel {

    var enabled = false
    var halt = false
    var sample = 0.0
    var output = 0.0

    val sequencer = Sequencer()
    val oscillatorPulse = OscillatorPulse()
    val envelope = Envelope()
    val lengthCounter = LengthCounter()
    val sweeper = Sweeper()

    fun clock(quarterFrameClock: Boolean, halfFrameClock: Boolean, globalTime: Double) {
        if(!enabled) {
            output = 0.0
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
        sample = oscillatorPulse.sample(globalTime)

        if (lengthCounter.counter > 0u && sequencer.timer >= 8u && !sweeper.mute && envelope.output > 2u) {
            output += (sample - output) * 0.5
        } else {
            output = 0.0
        }
    }

    fun trackSweep () {
        sweeper.track(sequencer.reload)
    }

}