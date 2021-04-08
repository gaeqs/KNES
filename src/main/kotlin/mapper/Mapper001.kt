package mapper

import util.Mirror
import util.isZero
import util.shl
import util.shr

@ExperimentalUnsignedTypes
class Mapper001(prgBanks: UByte, chrBanks: UByte) : Mapper(prgBanks, chrBanks) {

    override var mirror = Mirror.HORIZONTAL
    override val irqState = false

    private var selectedCHRBank4Low: UByte = 0u
    private var selectedCHRBank4High: UByte = 0u
    private var selectedCHRBank8: UByte = 0u

    private var selectedPRGBank16Low: UByte = 0u
    private var selectedPRGBank16High: UByte = prgBanks.dec()
    private var selectedPRGBank32: UByte = 0u

    private var loadRegister: UByte = 0u
    private var loadRegisterCount: UByte = 0u
    private var controlRegister: UByte = 0x1Cu

    private val staticVRAM = UByteArray(0x2000)

    override fun cpuMapRead(address: UShort): Triple<Boolean, UInt, UByte> {
        if (address in 0x6000u..0x7FFFu) {
            return Triple(true, 0xFFFFFFFFu, staticVRAM[address.toInt() and 0x1FFF])
        }

        if (address in 0x8000u..0xFFFFu) {
            return if (controlRegister and 0b01000u > 0u) {
                // 16KB Mode
                if (address in 0x8000u..0xBFFFu)
                    Triple(true, selectedPRGBank16Low * 0x4000u + (address and 0x3FFFu), 0u)
                else Triple(true, selectedPRGBank16High * 0x4000u + (address and 0x3FFFu), 0u)
            } else {
                // 32KB Mode
                Triple(true, selectedPRGBank32 * 0x8000u + (address and 0x7FFFu), 0u)
            }
        }

        return Triple(false, 0u, 0u)
    }

    override fun cpuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt> {
        if (address in 0x6000u..0x7FFFu) {
            staticVRAM[address.toInt() and 0x1FFF] = data
            return Pair(true, 0xFFFFFFFFu)
        }

        if (address in 0x8000u..0xFFFFu) {
            if (data and 0x80u > 0u) {
                loadRegister = 0u
                loadRegisterCount = 0u
                controlRegister = controlRegister or 0x0Cu
            } else {
                loadRegister = loadRegister shr 1
                loadRegister = loadRegister or (data and 0x01u shl 4)
                loadRegisterCount++
                if (loadRegisterCount.toInt() == 5) {
                    val targetRegister = (address shr 13) and 0x03u
                    when (targetRegister.toUInt()) {
                        0u -> {
                            controlRegister = loadRegister and 0x1Fu
                            mirror = when (controlRegister.toUInt() and 0x03u) {
                                0u -> Mirror.ONESCREEN_LO
                                1u -> Mirror.ONESCREEN_HI
                                2u -> Mirror.VERTICAL
                                else -> Mirror.HORIZONTAL
                            }
                        }
                        1u -> {
                            if (controlRegister and 0b10000u > 0u) {
                                // 4k CHR Bank
                                selectedCHRBank4Low = loadRegister and 0x1Fu
                            } else {
                                // 8K CHR Bank
                                selectedCHRBank8 = loadRegister and 0x1Eu
                            }
                        }
                        2u -> {
                            if (controlRegister and 0b10000u > 0u) {
                                selectedCHRBank4High = loadRegister and 0x1Fu
                            }
                        }
                        3u -> {
                            when ((controlRegister shr 2 and 0x03u).toUInt()) {
                                0u, 1u -> selectedPRGBank32 = loadRegister and 0x0Eu shr 1
                                2u -> {
                                    selectedPRGBank16Low = 0u
                                    selectedPRGBank16High = loadRegister and 0x0Fu
                                }
                                3u -> {
                                    selectedPRGBank16Low = loadRegister and 0x0Fu
                                    selectedPRGBank16High = prgBanks.dec()
                                }
                            }
                        }
                    }

                    loadRegister = 0u
                    loadRegisterCount = 0u
                }
            }
        }

        return Pair(false, 0u)
    }

    override fun ppuMapRead(address: UShort): Pair<Boolean, UInt> {
        if (address in 0x0000u..0x1FFFu) {
            if (chrBanks.isZero()) {
                return Pair(true, address.toUInt())
            }


            return if (controlRegister and 0b10000u > 0u) {
                // 4KB CHR mode
                when (address) {
                    in 0x0000u..0x0FFFu -> Pair(true, selectedCHRBank4Low * 0x1000u + (address and 0x0FFFu))
                    else -> Pair(true, selectedCHRBank4High * 0x1000u + (address and 0x0FFFu))
                }
            } else {
                // 8K CHR mode
                Pair(true, selectedCHRBank8 * 0x2000u + (address and 0x1FFFu))
            }
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
        controlRegister = 0x1Cu
        loadRegister = 0u
        loadRegisterCount = 0u

        selectedCHRBank4Low = 0u
        selectedCHRBank4High = 0u
        selectedCHRBank8 = 0u

        selectedPRGBank32 = 0u
        selectedPRGBank16Low = 0u
        selectedPRGBank16High = prgBanks.dec()
    }

    override fun clearIrq() {
    }

    override fun scanline() {
    }
}