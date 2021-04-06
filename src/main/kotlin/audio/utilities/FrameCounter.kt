package audio.utilities

import util.TVType

class FrameCounter(private val tvType: TVType) {

    var value = 7456

    fun clock() {
        value--
        if (value <= 0) {
            value = tvType.audioFrameCounterReload

        }
    }

}