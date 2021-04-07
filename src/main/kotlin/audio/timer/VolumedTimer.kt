package audio.timer

interface VolumedTimer {

    var volume: Int
    var envelopeConstantVolume : Boolean

    fun refreshVolume()

}