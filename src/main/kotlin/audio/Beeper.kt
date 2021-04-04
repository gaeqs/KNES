package audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.ceil

class Beeper {

    companion object {
        val SAMPLE_RATE = 44100.0f
    }

    val line: SourceDataLine
    var onSample: (Double) -> Unit = {}

    private val buffer: ByteArray
    private var amount = 0

    init {
        buffer = ByteArray(ceil(SAMPLE_RATE ).toInt())
        val af = AudioFormat(SAMPLE_RATE, 8, 1, true, true)
        line = AudioSystem.getSourceDataLine(af)
        line.open(af, SAMPLE_RATE.toInt())
        line.start()
    }

    fun sendSample(sample: Double) {
        onSample(sample)
        val s =  (sample * 127).toInt().toByte()

        buffer[amount++] = s
    }

    fun flushFrameSamples() {
        line.write(buffer, 0, amount)
        amount = 0
    }

}