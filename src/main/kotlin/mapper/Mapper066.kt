package mapper

import util.Mirror
import util.isZero
import util.shr

@ExperimentalUnsignedTypes
class Mapper066(prgBanks: UByte, chrBanks: UByte) : Mapper(prgBanks, chrBanks) {

    override var mirror = Mirror.HARDWARE
    override val irqState = false

    private var selectedPRGBank: UByte = 0u
    private var selectedCHRBank: UByte = 0u

    override fun cpuMapRead(address: UShort): Triple<Boolean, UInt, UByte> {
        if (address in 0x8000u..0xFFFFu) {
            return Triple(true, selectedPRGBank * 0x8000u + (address and 0x7FFFu), 0u)
        }

        return Triple(false, 0u, 0u)
    }

    override fun cpuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt> {
        if (address in 0x8000u..0xFFFFu) {
            selectedCHRBank = data and 0x03u
            selectedPRGBank = data and 0x30u shr 4
        }

        return Pair(false, 0u)
    }

    override fun ppuMapRead(address: UShort): Pair<Boolean, UInt> {
        if (address in 0x0000u..0x1FFFu) {
            return Pair(true, selectedCHRBank * 0x2000u + address.toUInt())
        }

        return Pair(false, 0u)
    }

    override fun ppuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt> {
        return Pair(false, 0u)
    }

    override fun reset() {
        selectedCHRBank = 0u
        selectedPRGBank = 0u
    }

    override fun clearIrq() {
    }

    override fun scanline() {
    }
}