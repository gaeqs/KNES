package mapper

import util.Mirror
import util.isZero

@ExperimentalUnsignedTypes
class Mapper002(prgBanks: UByte, chrBanks: UByte) : Mapper(prgBanks, chrBanks) {

    override var mirror = Mirror.HARDWARE
    override val irqState = false

    private var selectedPRGBankLow: UByte = 0u
    private var selectedPRGBankHigh: UByte = prgBanks.dec()

    override fun cpuMapRead(address: UShort): Triple<Boolean, UInt, UByte> {
        if (address in 0x8000u..0xBFFFu) {
            return Triple(true, selectedPRGBankLow * 0x4000u + (address and 0x3FFFu), 0u)
        }
        if (address in 0xC000u..0xFFFFu) {
            return Triple(true, selectedPRGBankHigh * 0x4000u + (address and 0x3FFFu), 0u)
        }

        return Triple(false, 0u, 0u)
    }

    override fun cpuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt> {
        if (address in 0x8000u..0xFFFFu) {
            selectedPRGBankLow = data and 0x0Fu
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
        if (address in 0x0000u..0x1FFFu) {
            if (chrBanks.isZero()) {
                // RAM MODE
                return Pair(true, address.toUInt())
            }
        }

        return Pair(false, 0u)
    }

    override fun reset() {
        selectedPRGBankLow = 0u
        selectedPRGBankHigh = prgBanks.dec()
    }

    override fun clearIrq() {
    }

    override fun scanline() {
    }
}