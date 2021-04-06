package audio.timer

interface Timer {

    var period: Int

    fun setDuty(duty: Int)

    fun setDuty(duty: IntArray)

    fun reset()

    fun clock()

    fun clock(cycles: Int)

    fun getValue(): Int

}