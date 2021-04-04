package util

@ExperimentalUnsignedTypes
fun UByte.isZero(): Boolean {
    return this.toUInt() == 0u;
}

@ExperimentalUnsignedTypes
fun UShort.isZero(): Boolean {
    return this.toUInt() == 0u;
}

@ExperimentalUnsignedTypes
fun UByte.flip(): UByte {
    var aux = this
    aux = (aux and 0xF0u shr 4) or (aux and 0x0Fu shl 4)
    aux = (aux and 0xCCu shr 2) or (aux and 0x33u shl 2)
    aux = (aux and 0xAAu shr 1) or (aux and 0x55u shl 1)
    return aux
}

@ExperimentalUnsignedTypes
infix fun UByte.shl(other: Number): UByte {
    return (this.toUInt() shl other.toInt()).toUByte()
}

@ExperimentalUnsignedTypes
infix fun UByte.shr(other: Number): UByte {
    return (this.toUInt() shr other.toInt()).toUByte()
}

@ExperimentalUnsignedTypes
infix fun UByte.rol(other: Number): UByte {
    val i = toUInt()
    return ((i shl other) or (i shr (8 - other.toInt()))).toUByte()
}

@ExperimentalUnsignedTypes
infix fun UByte.ror(other: Number): UByte {
    val i = toUInt()
    return ((i shr other) or (i shl (8 - other.toInt()))).toUByte()
}

@ExperimentalUnsignedTypes
infix fun UShort.shl(other: Number): UShort {
    return (this.toUInt() shl other.toInt()).toUShort()
}

@ExperimentalUnsignedTypes
infix fun UShort.shr(other: Number): UShort {
    return (this.toUInt() shr other.toInt()).toUShort()
}

@ExperimentalUnsignedTypes
infix fun UShort.rol(other: Number): UShort {
    val i = toUInt()
    return ((i shl other) or (i shr (16 - other.toInt()))).toUShort()
}

@ExperimentalUnsignedTypes
infix fun UShort.ror(other: Number): UShort {
    val i = toUInt()
    return ((i shr other) or (i shl (16 - other.toInt()))).toUShort()
}

@ExperimentalUnsignedTypes
infix fun UInt.shl(other: Number): UInt {
    return this shl other.toInt()
}

@ExperimentalUnsignedTypes
infix fun UInt.shr(other: Number): UInt {
    return this shr other.toInt()
}

@ExperimentalUnsignedTypes
infix fun UInt.rol(other: Number): UInt {
    val i = toUInt()
    return ((i shl other) or (i shr (32 - other.toInt())))
}

@ExperimentalUnsignedTypes
infix fun UInt.ror(other: Number): UInt {
    val i = toUInt()
    return ((i shr other) or (i shl (32 - other.toInt())))
}

@ExperimentalUnsignedTypes
infix fun UByte.concatenate(other: UByte): UShort {
    return (this.toUShort() shl 8) or other.toUShort()
}

@ExperimentalUnsignedTypes
infix fun Boolean.concatenate(other: Boolean): UByte {
    return when {
        !this && !other -> 0u
        !this && other -> 1u
        this && !other -> 2u
        else -> 3u
    }
}