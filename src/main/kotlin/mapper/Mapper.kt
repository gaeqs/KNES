package mapper

@ExperimentalUnsignedTypes
abstract class Mapper(val prgBanks: UByte, val chrBanks: UByte) {

    abstract fun cpuMapRead(address: UShort): Pair<Boolean, UInt>
    abstract fun cpuMapWrite(address: UShort): Pair<Boolean, UInt>
    abstract fun ppuMapRead(address: UShort): Pair<Boolean, UInt>
    abstract fun ppuMapWrite(address: UShort): Pair<Boolean, UInt>

}