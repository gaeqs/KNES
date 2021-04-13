package mapper

import util.Mirror
import util.isZero
import util.shr
import util.toHex

@ExperimentalUnsignedTypes
class Mapper004(prgBanks: UByte, chrBanks: UByte) : Mapper(prgBanks, chrBanks) {

    override var mirror = Mirror.HORIZONTAL
    override var irqState = false

    private var targetRegister: UByte = 0u

    private var prgBankMode = false
    private var chrInversion = false
    private val register = UIntArray(8)
    private val chrBank = UIntArray(8)
    private val prgBank = UIntArray(4)

    private var irqEnabled = false
    private var irqUpdate = false
    private var irqCounter: UShort = 0u
    private var irqReload: UShort = 0u

    private val staticVRAM = UByteArray(0x2000)

    override fun cpuMapRead(address: UShort): Triple<Boolean, UInt, UByte> {
        return when (address) {
            in 0x6000u..0x7FFFu -> {
                Triple(true, 0xFFFFFFFFu, staticVRAM[address.toInt() and 0x1FFF])
            }
            in 0x8000u..0x9FFFu -> {
                Triple(true, prgBank[0] + (address and 0x1FFFu), 0u)
            }
            in 0xA000u..0xBFFFu -> {
                Triple(true, prgBank[1] + (address and 0x1FFFu), 0u)
            }
            in 0xC000u..0xDFFFu -> {
                Triple(true, prgBank[2] + (address and 0x1FFFu), 0u)
            }
            in 0xE000u..0xFFFFu -> {
                Triple(true, prgBank[3] + (address and 0x1FFFu), 0u)
            }
            else -> Triple(false, 0u, 0u)
        }

    }

    override fun cpuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt> {
        return when (address) {
            in 0x6000u..0x7FFFu -> {
                staticVRAM[address.toInt() and 0x1FFF] = data
                Pair(true, 0xFFFFFFFFu)
            }
            in 0x8000u..0x9FFFu -> {
                if ((address and 0x0001u).isZero()) {
                    targetRegister = data and 0x07u
                    prgBankMode = data and 0x40u > 0u
                    chrInversion = data and 0x80u > 0u
                } else {
                    register[targetRegister.toInt()] = data.toUInt()
                }

                if (chrInversion) {
                    chrBank[0] = register[2] * 0x0400u
                    chrBank[1] = register[3] * 0x0400u
                    chrBank[2] = register[4] * 0x0400u
                    chrBank[3] = register[5] * 0x0400u
                    chrBank[4] = (register[0] and 0xFEu) * 0x400u
                    chrBank[5] = (register[0] and 0xFEu) * 0x400u + 0x400u
                    chrBank[6] = (register[1] and 0xFEu) * 0x400u
                    chrBank[7] = (register[1] and 0xFEu) * 0x400u + 0x400u
                } else {
                    chrBank[0] = (register[0] and 0xFEu) * 0x400u
                    chrBank[1] = (register[0] and 0xFEu) * 0x400u + 0x400u
                    chrBank[2] = (register[1] and 0xFEu) * 0x400u
                    chrBank[3] = (register[1] and 0xFEu) * 0x400u + 0x400u
                    chrBank[4] = register[2] * 0x0400u
                    chrBank[5] = register[3] * 0x0400u
                    chrBank[6] = register[4] * 0x0400u
                    chrBank[7] = register[5] * 0x0400u
                }

                if (prgBankMode) {
                    prgBank[2] = (register[6] and 0x3Fu) * 0x2000u
                    prgBank[0] = (prgBanks * 2u - 2u) * 0x2000u
                } else {
                    prgBank[0] = (register[6] and 0x3Fu) * 0x2000u
                    prgBank[2] = (prgBanks * 2u - 2u) * 0x2000u
                }

                prgBank[1] = (register[7] and 0x3Fu) * 0x2000u
                prgBank[3] = (prgBanks * 2u - 1u) * 0x2000u

                Pair(false, 0u)
            }
            in 0xA000u..0xBFFFu -> {
                if ((address and 0x0001u).isZero()) {
                    mirror = if (data and 0x01u > 0u) Mirror.HORIZONTAL else Mirror.VERTICAL
                }
                // TODO PRG RAM PROTECT
                Pair(false, 0u)
            }
            in 0xC000u..0xDFFFu -> {
                if ((address and 0x0001u).isZero()) {
                    irqReload = data.toUShort()
                } else {
                    irqCounter = 0u
                }
                Pair(false, 0u)
            }
            in 0xE000u..0xFFFFu -> {
                if ((address and 0x0001u).isZero()) {
                    irqEnabled = false
                    irqState = false
                } else {
                    irqEnabled = true
                }
                Pair(false, 0u)
            }
            else -> Pair(false, 0u)
        }
    }

    override fun ppuMapRead(address: UShort): Pair<Boolean, UInt> {
        if (address in 0x0000u..0x1FFFu) {
            val index = address shr 10 and 0x7u
            return Pair(true, chrBank[index.toInt()] + (address and 0x03FFu))
        }
        return Pair(false, 0u)
    }

    override fun ppuMapWrite(address: UShort, data: UByte): Pair<Boolean, UInt> {
        return Pair(false, 0u)
    }

    override fun reset() {
        targetRegister = 0u
        prgBankMode = false
        chrInversion = false
        mirror = Mirror.HORIZONTAL

        irqState = false
        irqEnabled = false
        irqUpdate = false
        irqCounter = 0u
        irqReload = 0u

        chrBank.fill(0u)
        register.fill(0u)

        prgBank[0] = 0x0000u
        prgBank[1] = 0x2000u
        prgBank[2] = (prgBanks * 2u - 2u) * 0x2000u
        prgBank[3] = (prgBanks * 2u - 1u) * 0x2000u
    }

    override fun clearIrq() {
        irqState = false
    }

    override fun scanline() {
        if (irqCounter.isZero()) {
            irqCounter = irqReload
        } else {
            irqCounter--
        }

        if (irqCounter.isZero() && irqEnabled) {
            irqState = true
        }
    }
}