package bus

import cpu.OLC6502
import ppu.OLC2C02
import util.concatenate
import util.isZero
import util.shl

@ExperimentalUnsignedTypes
class Bus(val cpu: OLC6502, val ppu: OLC2C02) {

    var clockCounter: Int = 0
        private set

    var cartridge: Cartridge? = null
        set(value) {
            field = value
            ppu.cartridge = value
        }

    val controllers = ubyteArrayOf(0u, 0u)
    private val controllersSnapshot = ubyteArrayOf(0u, 0u)

    var dmaPage: UByte = 0u
    var dmaAddress: UByte = 0u
    var dmaData: UByte = 0u
    var dmaTransfer = false
    var dmaWaitForSync = true

    init {
        cpu.bus = this
    }

    private val cpuRAM = UByteArray(2048)

    fun cpuWrite(address: UShort, data: UByte) {
        if (cartridge?.cpuWrite(address, data) == true) return
        when (address) {
            in 0x0000u..0x1FFFu -> cpuRAM[address.toInt() and 0x07FF] = data
            in 0x2000u..0x3FFFu -> ppu.cpuWrite(address and 0x0007u, data)
            (0x4014u).toUShort() -> {
                dmaPage = data
                dmaAddress = 0u
                dmaTransfer = true
            }
            in 0x4016u..0x4017u -> controllersSnapshot[(address and 0x1u).toInt()] =
                controllers[(address and 0x1u).toInt()]
        }
    }

    fun cpuRead(address: UShort, readOnly: Boolean = false): UByte {
        if (cartridge != null) {
            val (success, data) = cartridge!!.cpuRead(address, readOnly)
            if (success) return data
        }
        return when (address) {
            in 0x0000u..0x1FFFu -> cpuRAM[address.toInt() and 0x07FF]
            in 0x2000u..0x3FFFu -> ppu.cpuRead(address and 0x0007u, readOnly)
            in 0x4016u..0x4017u -> {
                val value = controllersSnapshot[(address and 0x1u).toInt()]
                val data: UByte = if (value and 0x80u > 0u) 1u else 0u
                controllersSnapshot[(address and 0x1u).toInt()] = value shl 1
                data
            }
            else -> 0u
        }
    }

    fun reset() {
        cpu.reset()
        clockCounter = 0
    }

    fun clock() {
        ppu.clock()

        // CPU is 3 times slower than PPU
        if (clockCounter % 3 == 0) {
            if (dmaTransfer) {
                manageDMA()
            } else {
                cpu.clock()
            }
        }

        if (ppu.nmiRequest) {
            ppu.nmiRequest = false
            cpu.nonMaskableInterrupt()
        }

        clockCounter++
    }

    private fun manageDMA() {
        if (dmaWaitForSync) {
            if (clockCounter and 0x1 == 1) {
                dmaWaitForSync = false
            }
        } else {
            if (clockCounter and 0x1 == 0) {
                dmaData = cpuRead(dmaPage concatenate dmaAddress)
            } else {
                ppu.oam[dmaAddress.toInt() shr 2][dmaAddress.toInt() and 0x3] = dmaData
                dmaAddress++
                if (dmaAddress.isZero()) {
                    dmaTransfer = false
                    dmaWaitForSync = true
                }
            }
        }
    }

}