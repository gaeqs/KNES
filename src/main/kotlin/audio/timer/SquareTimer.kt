package audio.timer

import kotlin.math.max

class SquareTimer(cycleLength: Int, private val periodAdd: Int = 0) : Timer {

    override var period: Int = 0

    private var position = 0
    private var values = IntArray(cycleLength)
    private var divider = 0

    init {
        setDuty(cycleLength / 2)
    }

    override fun setDuty(duty: Int) {
        repeat(values.size) { values[it] = if (it < duty) 1 else 0 }
    }

    override fun setDuty(duty: IntArray) {
        values = duty
    }

    override fun reset() {
        position = 0
    }

    override fun getValue(): Int {
        return values[position]
    }

    override fun clock() {
        if (period + periodAdd <= 0) return
        divider++
        refreshPosition()
    }

    override fun clock(cycles: Int) {
        if (period < 8) return
        divider += cycles
        refreshPosition()
    }

    private fun refreshPosition() {
        val periods = max((divider + period + periodAdd) / (period + periodAdd), 0)
        position = (position + periods) % values.size
        divider -= (period + periodAdd) * periods
    }
}