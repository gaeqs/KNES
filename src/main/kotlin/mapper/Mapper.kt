package mapper

import util.Mirror

@ExperimentalUnsignedTypes
abstract class Mapper(val prgBanks: UByte, val chrBanks: UByte) {

    abstract var mirror: Mirror
        protected set
    abstract val irqState: Boolean

    abstract fun cpuMapRead(address: UShort): Triple<Boolean, UInt, UByte>
    abstract fun cpuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt>
    abstract fun ppuMapRead(address: UShort): Pair<Boolean, UInt>
    abstract fun ppuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt>

    abstract fun reset()
    abstract fun clearIrq()
    abstract fun scanline()
}