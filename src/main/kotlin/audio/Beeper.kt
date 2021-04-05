package audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.ceil

class Beeper {

    companion object {
        const val SAMPLE_RATE = 44100.0f
    }

    val line: SourceDataLine
    var onSample: (Double) -> Unit = {}

    private val buffer: ByteArray
    private var amount = 0

    init {
        buffer = ByteArray(ceil(SAMPLE_RATE * 2).toInt())
        val af = AudioFormat(SAMPLE_RATE * 2, 16, 2, true, false)
        line = AudioSystem.getSourceDataLine(af)
        line.open(af, SAMPLE_RATE.toInt() * 4 * 2 * 2)
        line.start()
    }

    fun sendSample(sample: Int) {
        val s = (sample * 0.8).toInt()

        onSample(s.toDouble())

        buffer[amount++] = (s and 0xFF).toByte()
        buffer[amount++] = (s shr 8).toByte()
        buffer[amount++] = (s and 0xFF).toByte()
        buffer[amount++] = (s shr 8).toByte()
    }

    fun flushFrameSamples() {
        if(line.available() >= amount) {
            line.write(buffer, 0, amount)
            amount = 0
        }
    }

}