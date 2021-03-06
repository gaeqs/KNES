package mapper

import util.Mirror
import util.isZero

@ExperimentalUnsignedTypes
class Mapper000(prgBanks: UByte, chrBanks: UByte) : Mapper(prgBanks, chrBanks) {
    override var mirror = Mirror.HARDWARE
    override val irqState = false

    override fun cpuMapRead(address: UShort):Triple<Boolean, UInt, UByte> {
        if (address in 0x8000u..0xFFFFu) {
            return Triple(true, (address and (if (prgBanks > 1u) 0x7FFFu else 0x3FFFu).toUShort()).toUInt(), 0u)
        }

        return Triple(false, 0u, 0u)
    }

    override fun cpuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt> {
        if (address in 0x8000u..0xFFFFu) {
            return Pair(true, (address and (if (prgBanks > 1u) 0x7FFFu else 0x3FFFu).toUShort()).toUInt())
        }

        return Pair(false, 0u)
    }

    override fun ppuMapRead(address: UShort): Pair<Boolean, UInt> {
        if (address in 0x0000u..0x1FFFu) {
            return Pair(true, address.toUInt())
        }

        return Pair(false, 0u)
    }

    override fun ppuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt> {
        if(address in 0x0000u..0x1FFFu) {
            if(chrBanks.isZero()) {
                // RAM MODE
                return Pair(true, address.toUInt())
            }
        }

        return Pair(false, 0u)
    }

    override fun reset() {
    }

    override fun clearIrq() {
    }

    override fun scanline() {
    }
}