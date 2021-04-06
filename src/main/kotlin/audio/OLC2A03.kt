package audio

import audio.timer.NoiseTimer
import audio.timer.SquareTimer
import audio.timer.TriangleTimer
import bus.Bus
import util.TVType

@ExperimentalUnsignedTypes
class OLC2A03(val tvType: TVType, val sampleRate: Int, val soundFiltering: Boolean) {

    companion object {
        private val NTD_LOOKUP = IntArray(203) { ((163.67 / (24329.0 / it + 100.0)) * 49151.0).toInt() }
        private val SQUARE_LOOKUP = IntArray(203) { ((95.52 / (8128.0 / it + 100.0)) * 49151.0).toInt() }
        private val DUTY_LOOKUP = arrayOf(
            intArrayOf(0, 1, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, 1, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, 1, 1, 1, 0, 0, 0),
            intArrayOf(1, 0, 0, 1, 1, 1, 1, 1)
        )
    }

    var bus: Bus? = null
    val beeper: Beeper

    var apuClocksAsPpu = 0L
    var clocksAfterSample = 0L

    private val cyclesPerSample: Double
    private val timers = arrayOf(
        SquareTimer(8, 2),
        SquareTimer(8, 2),
        TriangleTimer(),
        NoiseTimer()
    )

    init {
        beeper = Beeper(sampleRate, tvType)
        cyclesPerSample = tvType.getAudioCyclesPerSample(sampleRate.toDouble())
    }

    fun pause() {
        beeper.pause()
    }

    fun resume() {
        beeper.resume()
    }

    fun destroy() {
        beeper.destroy()
    }

    /**
     * CPU bus communication
     */
    fun cpuWrite(address: UShort, data: UByte) {
    }

    /**
     * CPU bus communication
     */
    fun cpuRead(address: UShort, readOnly: Boolean = false): UByte {
        return 0u
    }

    fun clockTo(ppuClocks: Long) {
        if (soundFiltering) {

        }
    }

    private fun clockToUsingFiltering(ppuClocks: Long) {
        while (apuClocksAsPpu < ppuClocks) {
            clocksAfterSample++

        }
    }

}