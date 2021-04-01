package mapper

@ExperimentalUnsignedTypes
class Mapper000(prgBanks: UByte, chrBanks: UByte) : Mapper(prgBanks, chrBanks) {

    override fun cpuMapRead(address: UShort): Pair<Boolean, UInt> {
        if (address in 0x8000u..0xFFFFu) {
            return Pair(true, (address and (if (prgBanks > 1u) 0x7FFFu else 0x3FFFu).toUShort()).toUInt())
        }

        return Pair(false, 0u)
    }

    override fun cpuMapWrite(address: UShort): Pair<Boolean, UInt> {
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

    override fun ppuMapWrite(address: UShort): Pair<Boolean, UInt> {
        return Pair(false, 0u)
    }
}