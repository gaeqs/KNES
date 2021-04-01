package ppu

@ExperimentalUnsignedTypes
data class PPUSprite(var y: UByte, var id: UByte, var attribute: UByte, var x: UByte) {

    fun fill(value: UByte) {
        y = value
        id = value
        attribute = value
        x = value
    }

    fun moveFrom(sprite : PPUSprite) {
        y = sprite.y
        id = sprite.id
        attribute = sprite.attribute
        x = sprite.x
    }

    operator fun get(i: Int): UByte {
        return when (i) {
            0 -> y
            1 -> id
            2 -> attribute
            3 -> x
            else -> throw IllegalArgumentException()
        }
    }

    operator fun set(i: Int, value: UByte) {
        when (i) {
            0 -> y = value
            1 -> id = value
            2 -> attribute = value
            3 -> x = value
            else -> throw IllegalArgumentException()
        }
    }


}