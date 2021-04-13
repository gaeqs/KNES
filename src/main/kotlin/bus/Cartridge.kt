package bus

import mapper.*
import util.Mirror
import util.isZero
import util.shl
import util.shr
import java.io.File
import java.nio.charset.StandardCharsets

@ExperimentalUnsignedTypes
class Cartridge(val file: File) {

    val mapperId: UByte
    val prgBanks: UByte
    val chrBanks: UByte

    private val prgMemory: UByteArray
    private val chrMemory: UByteArray

    val mapper: Mapper
    val mirror: Mirror
        get() {
            val mirror = mapper.mirror
            return if (mirror == Mirror.HARDWARE) field else mirror
        }

    init {
        val inStream = file.inputStream();
        val header = CartridgeHeader(
            String(inStream.readNBytes(4), 0, 4, StandardCharsets.US_ASCII),
            inStream.read().toUByte(),
            inStream.read().toUByte(),
            inStream.read().toUByte(),
            inStream.read().toUByte(),
            inStream.read().toUByte(),
            inStream.read().toUByte(),
            inStream.read().toUByte(),
            UByteArray(5) { inStream.read().toUByte() }
        )

        // Training data
        if (header.mapper1 and 0x04u > 0u) {
            inStream.skip(512)
        }

        mapperId = header.mapper2 shr 4 shl 4 or (header.mapper1 shr 4)

        val fileType = 1

        when (fileType) {
            1 -> {
                prgBanks = header.prgRomChunks
                prgMemory = UByteArray(prgBanks.toInt() * 0x4000) { inStream.read().toUByte() }

                chrBanks = header.chrRomChunks


                chrMemory =
                    UByteArray((if (chrBanks.isZero()) 1 else chrBanks.toInt()) * 0x2000) { inStream.read().toUByte() }
            }
            else -> {
                prgBanks = 0u
                chrBanks = 0u
                prgMemory = UByteArray(0)
                chrMemory = UByteArray(0)
            }
        }

        println("Mapper ID: $mapperId")
        println("File type: $fileType")
        println("PRG Banks: $prgBanks")
        println("CHR Banks: $chrBanks")
        mapper = when (mapperId.toInt()) {
            1 -> Mapper001(prgBanks, chrBanks)
            2 -> Mapper002(prgBanks, chrBanks)
            3 -> Mapper003(prgBanks, chrBanks)
            4 -> Mapper004(prgBanks, chrBanks)
            66 -> Mapper066(prgBanks, chrBanks)
            else -> Mapper000(prgBanks, chrBanks)
        }


        mirror = if (header.mapper1 and 0x01u > 0u) Mirror.VERTICAL else Mirror.HORIZONTAL
        inStream.close()
    }

    /**
     * CPU bus communication
     */
    fun cpuWrite(address: UShort, data: UByte): Boolean {
        val (success, mapped) = mapper.cpuMapWrite(address, data)
        if (success) {
            if (mapped != 0xFFFFFFFFu) {
                prgMemory[mapped.toInt()] = data
            }
            return true
        }
        return false
    }

    /**
     * CPU bus communication
     */
    fun cpuRead(address: UShort, readOnly: Boolean = false): Pair<Boolean, UByte> {
        val (success, mapped, value) = mapper.cpuMapRead(address)
        if (success) {
            return Pair(true, if (mapped == 0xFFFFFFFFu) value else prgMemory[mapped.toInt()])
        }
        return Pair(false, 0u)
    }

    /**
     * PPU bus communication
     */
    fun ppuWrite(address: UShort, data: UByte): Boolean {
        val (success, mapped) = mapper.ppuMapWrite(address, data)
        if (success) {
            chrMemory[mapped.toInt()] = data
            return true
        }
        return false
    }

    /**
     * PPU bus communication
     */
    fun ppuRead(address: UShort, readOnly: Boolean = false): Pair<Boolean, UByte> {
        val (success, mapped) = mapper.ppuMapRead(address)
        if (success) {
            return Pair(true, chrMemory[mapped.toInt()])
        }
        return Pair(false, 0u)
    }

    fun reset() {
        mapper.reset()
    }

}

@ExperimentalUnsignedTypes
data class CartridgeHeader(
    val name: String,
    val prgRomChunks: UByte,
    val chrRomChunks: UByte,
    val mapper1: UByte,
    val mapper2: UByte,
    val prgRamSize: UByte,
    val tvSystem1: UByte,
    val tvSystem2: UByte,
    val unused: UByteArray
)