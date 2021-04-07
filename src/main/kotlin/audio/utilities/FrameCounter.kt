package audio.utilities

import util.TVType

class FrameCounter(private val tvType: TVType, private val run: () -> Unit) {

    var value = 7456
    var mode = 4
    var frame = 0

    fun clock() {
        value--
        if (value <= 0) {
            value = tvType.audioFrameCounterReload
            run()
        }
    }

}