package audio.timer

import util.BIT0
import kotlin.math.max

class NoiseTimer : Timer {

    override var period: Int = 1

    private var position = 0
    private var values = generateValues(1, 1)
    private var divider = 0
    private var previousDuty = 1

    override fun setDuty(duty: Int) {
        if (duty != previousDuty) {
            values = generateValues(duty, values[position])
            position = 0
        }
        previousDuty = duty
    }

    override fun setDuty(duty: IntArray) {
        throw UnsupportedOperationException()
    }

    override fun reset() {
        position = 0
    }

    override fun getValue(): Int {
        return values[position] and 1
    }

    override fun clock() {
        divider++
        refreshPosition()
    }

    override fun clock(cycles: Int) {
        divider += cycles
        refreshPosition()
    }

    private fun refreshPosition() {
        val periods = max((divider + period) / period, 0)
        position = (position + periods) % values.size
        divider -= period * periods
    }
}

private fun generateValues(from: Int, startSeed: Int): IntArray {
    var seed = startSeed
    val array = IntArray(if (from == 1) 32767 else 93)
    repeat(array.size) {
        seed = (seed shr 1 or if ((seed and (1 shl from) != 0) xor (seed and BIT0 != 0)) 16384 else 0)
        array[it] = seed
    }
    return array
}