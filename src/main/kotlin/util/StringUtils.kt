package util

@ExperimentalUnsignedTypes
infix fun UByte.toHex(minSize: Int): String {
    var value = this.toString(16)
    while (value.length < minSize) value = "0${value}"
    return value;
}

@ExperimentalUnsignedTypes
infix fun UShort.toHex(minSize: Int): String {
    var value = this.toString(16)
    while (value.length < minSize) value = "0${value}"
    return value;
}

@ExperimentalUnsignedTypes
infix fun UInt.toHex(minSize: Int): String {
    var value = this.toString(16)
    while (value.length < minSize) value = "0${value}"
    return value;
}