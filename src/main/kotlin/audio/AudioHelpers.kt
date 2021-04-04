package audio

import util.fastSin
import util.isZero
import util.shr
import kotlin.math.PI

@ExperimentalUnsignedTypes
class Sequencer {

    var sequence = 0u
    var sequenceSnapshot = 0u
    var timer: UShort = 0u
    var reload: UShort = 0u
    var output: Boolean = false

    fun clock(enabled: Boolean, function: (UInt) -> UInt): Boolean {
        if (enabled) {
            if ((timer--).isZero()) {
                timer = reload
                sequence = function(sequence)
                output = sequence and 0x1u > 0u
            }
        }
        return output
    }
}

@ExperimentalUnsignedTypes
class LengthCounter {

    var counter: UByte = 0u

    fun clock(enabled: Boolean, halt: Boolean): UByte {
        if (!enabled) counter = 0u
        else if (counter > 0u && !halt) counter--
        return counter
    }
}

@ExperimentalUnsignedTypes
class Envelope {

    var start = false
    var disabled = false
    var dividerCount: UShort = 0u
    var volume: UShort = 0u
    var decayCount: UShort = 0u
    var output: UShort = 0u

    fun clock(loop: Boolean) {
        if (!start) {
            if (dividerCount.isZero()) {
                dividerCount = volume
                if (decayCount.isZero()) {
                    if (loop) decayCount = 15u
                } else {
                    decayCount--
                }
            } else {
                dividerCount--
            }
        } else {
            start = false
            decayCount = 15u
            dividerCount = volume
        }

        output = if (disabled) volume else decayCount
    }
}

class OscillatorPulse {

    var frequency = 0.0
    var dutyCycle = 0.0
    var amplitude = 1.0
    var harmonics = 20.0

    fun sample(t: Double): Double {
        val p = dutyCycle * 2.0 * PI
        var a = 0.0
        var b = 0.0
        var n = 1.0

        while (n < harmonics) {
            val c = n * frequency * 2.0 * PI * t
            a += -fastSin(c) / n
            b += -fastSin(c - p * n) / n

            n++
        }

        return (a - b) * 2.0 * amplitude / PI
    }

}

@ExperimentalUnsignedTypes
class Sweeper {

    var enabled = false
    var down = false
    var reload = false
    var mute = false
    var shift: UByte = 0u
    var timer: UByte = 0u
    var period: UByte = 0u
    var change: UShort = 0u

    fun track(target: UShort) {
        if (enabled) {
            change = target shr shift.toInt()
            mute = (target < 8u) || (target > 0x4FFu)
        }
    }

    fun clock(target: UShort, channel: UByte): UShort {
        var changedTarget = target
        if (enabled && !mute && shift > 0u && timer.isZero()) {
            if (target >= 8u && change < 0x07FFu) {
                changedTarget = if (down) {
                    (target - change + channel).toUShort()
                } else {
                    (target + change).toUShort()
                }
            }
        }

        if (enabled) {
            if (timer.isZero() || reload) {
                timer = period
                reload = false
            } else timer--
            mute = (changedTarget < 8u) || (changedTarget > 0x7FFu)
        }

        return changedTarget
    }

}