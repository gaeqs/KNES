package ppu

import bus.Cartridge
import bus.Mirror
import util.flip
import util.isZero
import util.shl
import util.shr
import java.awt.Color
import java.awt.image.BufferedImage

@ExperimentalUnsignedTypes
class OLC2C02 {

    var cartridge: Cartridge? = null
    val nameTables = Array(2) { UByteArray(1024) }
    val palette = UByteArray(32)
    val patternTables = Array(2) { UByteArray(4096) }
    val oam = Array(64) { PPUSprite(0u, 0u, 0u, 0u) }

    val control = Control(0u)
    val status = Status(0u)
    val mask = Mask(0u)
    val vRamAddress = Loopy(0u)
    val tRamAddress = Loopy(0u)
    var fineX: UByte = 0u
    var nmiRequest = false

    private var addressLatch: Boolean = false
    private var ppuDataBuffer: UByte = 0u
    private var oamAddress: UByte = 0u

    private val backgroundRenderer = BackgroundRenderer(this)

    private val scanlineSprites = Array(64) { PPUSprite(0u, 0u, 0u, 0u) }
    private var spriteCount: UByte = 0u
    private val spriteShifterPatternLow = UByteArray(8)
    private val spriteShifterPatternHigh = UByteArray(8)

    private var spriteZeroHitPossible = false
    private var spriteZeroBeingRendered = false

    /**
     * CPU bus communication
     */
    fun cpuWrite(address: UShort, data: UByte) {
        when (address.toUInt()) {
            // Control
            0x0000u -> {
                control.value = data
                tRamAddress.nameTableX = control.nameTableX
                tRamAddress.nameTableY = control.nameTableY
            }
            // Mask
            0x0001u -> mask.value = data
            // Status
            0x0002u -> {
            }
            // OAM Address
            0x0003u -> {
                oamAddress = data
            }
            // OAM Data
            0x0004u -> {
                oam[oamAddress.toInt() shr 2][oamAddress.toInt() and 0x3] = data
            }
            // Scroll
            0x0005u -> {
                if (addressLatch) {
                    tRamAddress.fineY = data and 0x7u
                    tRamAddress.coarseY = data shr 3
                    addressLatch = false
                } else {
                    fineX = data and 0x7u
                    tRamAddress.coarseX = data shr 3
                    addressLatch = true
                }
            }
            // PPU Address
            0x0006u -> {
                if (addressLatch) {
                    tRamAddress.value = tRamAddress.value and 0xFF00u or data.toUShort()
                    vRamAddress.value = tRamAddress.value
                    addressLatch = false
                } else {
                    tRamAddress.value = tRamAddress.value and 0x00FFu or (data.toUShort() shl 8)
                    addressLatch = true
                }
            }
            // PPU Data
            0x0007u -> {
                ppuWrite(vRamAddress.value, data)
                vRamAddress.value = (vRamAddress.value + (if (control.incrementMode > 0u) 32u else 1u)).toUShort()
            }
        }
    }

    /**
     * CPU bus communication
     */
    fun cpuRead(address: UShort, readOnly: Boolean = false): UByte {
        return when (address.toUInt()) {
            // Control
            0x0000u -> 0u
            // Mask
            0x0001u -> 0u
            // Status
            0x0002u -> {
                val data = (status.value and 0xE0u) or (ppuDataBuffer and 0x1Fu)
                status.verticalBlank = 0u
                addressLatch = false
                data
            }
            // OAM Address
            0x0003u -> {
                0u
            }
            // OAM Data
            0x0004u -> {
                oam[oamAddress.toInt() shr 2][oamAddress.toInt() and 0x3]
            }
            // Scroll
            0x0005u -> {
                0u
            }
            // PPU Address
            0x0006u -> {
                0u
            }
            // PPU Data
            0x0007u -> {
                var data = ppuDataBuffer

                ppuDataBuffer = ppuRead(vRamAddress.value)

                data = if (vRamAddress.value >= 0x3F00u) ppuDataBuffer else data

                vRamAddress.value = (vRamAddress.value + (if (control.incrementMode > 0u) 32u else 1u)).toUShort()
                data
            }
            else -> 0u
        }
    }

    /**
     * PPU bus communication
     */
    fun ppuWrite(address: UShort, data: UByte) {
        if (cartridge?.ppuWrite(address, data) == true) return

        when (address) {
            in 0x0000u..0x1FFFu -> {
                patternTables[(address and 0x1000u shr 12).toInt()][(address and 0x0FFFu).toInt()] = data
            }
            in 0x2000u..0x3EFFu -> {
                when (cartridge?.mirror ?: Mirror.VERTICAL) {
                    Mirror.VERTICAL -> {
                        when (address and 0x0FFFu) {
                            in 0x0000u..0x03FFu,
                            in 0x0800u..0x0BFFu -> nameTables[0][(address and 0x03FFu).toInt()] = data
                            else -> nameTables[1][(address and 0x03FFu).toInt()] = data
                        }
                    }
                    Mirror.HORIZONTAL -> {
                        when (address and 0x0FFFu) {
                            in 0x0000u..0x07FFu -> nameTables[0][(address and 0x03FFu).toInt()] = data
                            else -> nameTables[1][(address and 0x03FFu).toInt()] = data
                        }
                    }
                    else -> {
                    }
                }
            }
            in 0x3F00u..0x3FFFu -> {
                val masked = when ((address and 0x001Fu).toUInt()) {
                    0x0010u -> 0x0000
                    0x0014u -> 0x0004
                    0x0018u -> 0x0008
                    0x001Cu -> 0x000C
                    else -> (address.toInt() and 0x001F)
                }
                palette[masked] = data
            }
        }
    }

    /**
     * PPU bus communication
     */
    fun ppuRead(address: UShort, readOnly: Boolean = false): UByte {
        if (cartridge != null) {
            val (success, data) = cartridge!!.ppuRead(address, readOnly)
            if (success) return data
        }

        return when (address) {
            in 0x0000u..0x1FFFu -> {
                patternTables[(address and 0x1000u shr 12).toInt()][(address and 0x0FFFu).toInt()]
            }
            in 0x2000u..0x3EFFu -> {
                when (cartridge?.mirror ?: Mirror.VERTICAL) {
                    Mirror.VERTICAL -> {
                        when (address and 0x0FFFu) {
                            in 0x0000u..0x03FFu,
                            in 0x0800u..0x0BFFu -> nameTables[0][(address and 0x03FFu).toInt()]
                            else -> nameTables[1][(address and 0x03FFu).toInt()]
                        }
                    }
                    Mirror.HORIZONTAL -> {
                        when (address and 0x0FFFu) {
                            in 0x0000u..0x07FFu -> nameTables[0][(address and 0x03FFu).toInt()]
                            else -> nameTables[1][(address and 0x03FFu).toInt()]
                        }
                    }
                    else -> {
                        println("NOT SUPPORTED")
                        0u
                    }
                }
            }
            in 0x3F00u..0x3FFFu -> {
                val masked = when ((address and 0x001Fu).toUInt()) {
                    0x0010u -> 0x0000
                    0x0014u -> 0x0004
                    0x0018u -> 0x0008
                    0x001Cu -> 0x000C
                    else -> (address.toInt() and 0x001F)
                }
                palette[masked]
            }
            else -> 0u
        }
    }

    fun clock() {
        // SCANLINES FROM -1 TO 240
        if (scanline in -1 until 240) {

            if (scanline == 0 && cycle == 0) {
                cycle = 1 // Odd frame cycle skip
            }

            if (scanline == -1 && cycle == 1) {
                status.verticalBlank = 0u
                status.spriteOverflow = 0u
                status.verticalZeroHit = 0u
                spriteShifterPatternLow.fill(0u)
                spriteShifterPatternHigh.fill(0u)
            }

            // FOREGROUND
            if (cycle in 2 until 258 || cycle in 321 until 338) {
                updateShifters()
            }

            if (cycle == 257 && scanline >= 0) {
                spriteZeroHitPossible = false
                scanlineSprites.forEach { it.fill(0xFFu) }
                spriteCount = 0u
                var entry = 0
                while (entry < 64 && status.spriteOverflow.isZero()) {
                    val diff = (scanline.toShort() - oam[entry].y.toShort())

                    if (diff >= 0 && diff < if (control.spriteSize > 0u) 16 else 8) {
                        if (spriteCount < 8u) {
                            if (entry == 0) spriteZeroHitPossible = true
                            scanlineSprites[spriteCount.toInt()].moveFrom(oam[entry])
                            spriteCount++
                        } else {
                            status.spriteOverflow = 1u
                        }
                    }
                    entry++
                }
            }

            if (cycle == 340) {
                for (i in 0 until spriteCount.toInt()) {
                    var spritePatternBitsLow: UByte
                    var spritePatternBitsHigh: UByte
                    var spritePatternAddressLow: UShort
                    var spritePatternAddressHigh: UShort

                    if (control.spriteSize > 0u) {
                        // 8x16 mode
                        if ((scanlineSprites[i].attribute and 0x80u).isZero()) {
                            // No vertical flip
                            if (scanline - scanlineSprites[i].y.toInt() < 8) {
                                // Top part
                                spritePatternAddressLow = (control.patternSprite.toUShort() and 0x01u shl 12) or
                                        (scanlineSprites[i].id.toUShort() and 0xFEu shl 4) or
                                        ((scanline.toUShort() - scanlineSprites[i].y.toUShort()).toUShort() and 0x07u)
                            } else {
                                spritePatternAddressLow = (control.patternSprite.toUShort() and 0x01u shl 12) or
                                        (((scanlineSprites[i].id.toUShort() and 0xFEu) + 1u).toUShort() shl 4) or
                                        ((scanline.toUShort() - scanlineSprites[i].y.toUShort()).toUShort() and 0x07u)
                            }
                        } else {
                            // Vertical flip
                            if (scanline - scanlineSprites[i].y.toInt() < 8) {
                                // Top part
                                spritePatternAddressLow = (control.patternSprite.toUShort() and 0x01u shl 12) or
                                        (scanlineSprites[i].id.toUShort() and 0xFEu shl 4) or
                                        ((7u - scanline.toUShort() + scanlineSprites[i].y.toUShort()).toUShort() and 0x07u)
                            } else {
                                spritePatternAddressLow = (control.patternSprite.toUShort() and 0x01u shl 12) or
                                        (((scanlineSprites[i].id.toUShort() and 0xFEu) + 1u).toUShort() shl 4) or
                                        ((7u - scanline.toUShort() + scanlineSprites[i].y.toUShort()).toUShort() and 0x07u)
                            }
                        }
                    } else {
                        // 8x8 mode
                        if ((scanlineSprites[i].attribute and 0x80u).isZero()) {
                            // No vertical flip
                            spritePatternAddressLow = (control.patternSprite.toUShort() shl 12) or
                                    (scanlineSprites[i].id.toUShort() shl 4) or
                                    (scanline.toUShort() - scanlineSprites[i].y.toUShort()).toUShort()
                        } else {
                            // Vertical flip
                            spritePatternAddressLow = (control.patternSprite.toUShort() shl 12) or
                                    (scanlineSprites[i].id.toUShort() shl 4) or
                                    (7u - scanline.toUShort() + scanlineSprites[i].y.toUShort()).toUShort()
                        }
                    }

                    spritePatternAddressHigh = (spritePatternAddressLow + 8u).toUShort()
                    spritePatternBitsLow = ppuRead(spritePatternAddressLow)
                    spritePatternBitsHigh = ppuRead(spritePatternAddressHigh)

                    if (scanlineSprites[i].attribute and 0x40u > 0u) {
                        // Horizontal flip
                        spritePatternBitsLow = spritePatternBitsLow.flip()
                        spritePatternBitsHigh = spritePatternBitsHigh.flip()
                    }

                    spriteShifterPatternLow[i] = spritePatternBitsLow
                    spriteShifterPatternHigh[i] = spritePatternBitsHigh
                }
            }
        }

        val (bgPixel, bgPalette) = backgroundRenderer.clock(scanline, cycle)

        // SCANLINE 240 does nothing :)

        if (scanline == 241 && cycle == 1) {
            status.verticalBlank = 1u
            if (control.enableNmi > 0u) {
                nmiRequest = true
            }
        }

        // Fake noise
        //if (cycle - 1 in 0 until screen.width && scanline in 0 until screen.height) {
        //    screen.setRGB(cycle - 1, scanline, paletteColors[(Math.random() * paletteColors.size).toInt()].rgb)
        //}

        var fgPixel: UByte = 0u
        var fgPalette: UByte = 0u
        var fgPriority = false

        if (mask.showSprites > 0u) {
            spriteZeroBeingRendered = false
            for (i in 0 until spriteCount.toInt()) {
                if (scanlineSprites[i].x.isZero()) {
                    val p0Pixel = spriteShifterPatternLow[i] and 0x80u > 0u
                    val p1Pixel = spriteShifterPatternHigh[i] and 0x80u > 0u
                    fgPixel = (if (p1Pixel) 2u else 0u).toUByte() or (if (p0Pixel) 1u else 0u).toUByte()
                    fgPalette = ((scanlineSprites[i].attribute and 0x03u) + 0x04u).toUByte()
                    fgPriority = (scanlineSprites[i].attribute and 0x20u).isZero()

                    // Pixel found, break
                    if (fgPixel > 0u) {
                        if (i == 0) spriteZeroBeingRendered = true
                        break
                    }
                }
            }
        }

        val pixel: UByte
        val palette: UByte

        if (bgPixel.isZero() && fgPixel.isZero()) {
            pixel = 0u
            palette = 0u
        } else if (bgPixel.isZero() && !fgPixel.isZero()) {
            pixel = fgPixel
            palette = fgPalette
        } else if (!bgPixel.isZero() && fgPixel.isZero()) {
            pixel = bgPixel
            palette = bgPalette
        } else {
            if (fgPriority) {
                pixel = fgPixel
                palette = fgPalette
            } else {
                pixel = bgPixel
                palette = bgPalette
            }
            // Zero hit collection
            if(spriteZeroHitPossible && spriteZeroBeingRendered && mask.showBackground > 0u && mask.showSprites > 0u) {
                if((mask.showBackgroundInLeftmost or mask.showSpritesInLeft).inv() > 0u) {
                    if(cycle in 9 until 258) {
                        status.verticalZeroHit = 1u
                    }
                } else {
                    if(cycle in 1 until 258) {
                        status.verticalZeroHit = 1u
                    }
                }
            }
        }

        if (cycle - 1 in 0 until screen.width && scanline in 0 until screen.height) {
            screen.setRGB(cycle - 1, scanline, getColorFromPaletteRam(palette, pixel).rgb)
        }

        cycle++
        if (cycle > 340) {
            cycle = 0
            scanline++
            if (scanline >= 260) {
                scanline = -1
                frameCompleted = true
            }
        }
    }

    private fun updateShifters() {
        if (mask.showSprites > 0u && cycle in 1 until 258) {
            for (i in 0 until spriteCount.toInt()) {
                if (scanlineSprites[i].x > 0u) {
                    scanlineSprites[i].x--
                } else {
                    spriteShifterPatternLow[i] = spriteShifterPatternLow[i] shl 1
                    spriteShifterPatternHigh[i] = spriteShifterPatternHigh[i] shl 1
                }
            }
        }
    }

    // region DISPLAY INFO

    val paletteColors = Array<Color>(0x40) { Color.BLACK }
    val screen = BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB)

    private val nameTableSprites = arrayOf(
        BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB),
        BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB)
    )

    private val patternTableSprites = arrayOf(
        BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB),
        BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB)
    )

    var frameCompleted = false

    private var scanline = 0
    private var cycle = 0

    fun getPatternTable(index: UInt, palette: UByte): BufferedImage {

        for (y in 0u until 16u) {
            for (x in 0u until 16u) {
                val offset = (y * 256u + x * 16u).toUShort()

                for (row in 0u until 8u) {

                    var tileLeastBit = ppuRead((index * 0x1000u + offset + row + 0u).toUShort(), true)
                    var tileMostBit = ppuRead((index * 0x1000u + offset + row + 8u).toUShort(), true)

                    for (column in 0u until 8u) {
                        val pixel = ((tileLeastBit and 0x01u shl 1) or (tileMostBit and 0x01u)).toUByte()
                        tileLeastBit = tileLeastBit shr 1
                        tileMostBit = tileMostBit shr 1

                        patternTableSprites[index.toInt()].setRGB(
                            (x * 8u + (7u - column)).toInt(),
                            (y * 8u + row).toInt(),
                            getColorFromPaletteRam(palette, pixel).rgb
                        )
                    }
                }

            }
        }

        return patternTableSprites[index.toInt()]
    }

    fun getColorFromPaletteRam(palette: UByte, pixel: UByte): Color {
        return paletteColors[ppuRead((0x03F00u + (palette shl 2) + pixel).toUShort(), false).toInt()]
    }

    init {
        paletteColors[0x00] = Color(84, 84, 84)
        paletteColors[0x01] = Color(0, 30, 116)
        paletteColors[0x02] = Color(8, 16, 144)
        paletteColors[0x03] = Color(48, 0, 136)
        paletteColors[0x04] = Color(68, 0, 100)
        paletteColors[0x05] = Color(92, 0, 48)
        paletteColors[0x06] = Color(84, 4, 0)
        paletteColors[0x07] = Color(60, 24, 0)
        paletteColors[0x08] = Color(32, 42, 0)
        paletteColors[0x09] = Color(8, 58, 0)
        paletteColors[0x0A] = Color(0, 64, 0)
        paletteColors[0x0B] = Color(0, 60, 0)
        paletteColors[0x0C] = Color(0, 50, 60)
        paletteColors[0x0D] = Color(0, 0, 0)
        paletteColors[0x0E] = Color(0, 0, 0)
        paletteColors[0x0F] = Color(0, 0, 0)

        paletteColors[0x10] = Color(152, 150, 152)
        paletteColors[0x11] = Color(8, 76, 196)
        paletteColors[0x12] = Color(48, 50, 236)
        paletteColors[0x13] = Color(92, 30, 228)
        paletteColors[0x14] = Color(136, 20, 176)
        paletteColors[0x15] = Color(160, 20, 100)
        paletteColors[0x16] = Color(152, 34, 32)
        paletteColors[0x17] = Color(120, 60, 0)
        paletteColors[0x18] = Color(84, 90, 0)
        paletteColors[0x19] = Color(40, 114, 0)
        paletteColors[0x1A] = Color(8, 124, 0)
        paletteColors[0x1B] = Color(0, 118, 40)
        paletteColors[0x1C] = Color(0, 102, 120)
        paletteColors[0x1D] = Color(0, 0, 0)
        paletteColors[0x1E] = Color(0, 0, 0)
        paletteColors[0x1F] = Color(0, 0, 0)

        paletteColors[0x20] = Color(236, 238, 236)
        paletteColors[0x21] = Color(76, 154, 236)
        paletteColors[0x22] = Color(120, 124, 236)
        paletteColors[0x23] = Color(176, 98, 236)
        paletteColors[0x24] = Color(228, 84, 236)
        paletteColors[0x25] = Color(236, 88, 180)
        paletteColors[0x26] = Color(236, 106, 100)
        paletteColors[0x27] = Color(212, 136, 32)
        paletteColors[0x28] = Color(160, 170, 0)
        paletteColors[0x29] = Color(116, 196, 0)
        paletteColors[0x2A] = Color(76, 208, 32)
        paletteColors[0x2B] = Color(56, 204, 108)
        paletteColors[0x2C] = Color(56, 180, 204)
        paletteColors[0x2D] = Color(60, 60, 60)
        paletteColors[0x2E] = Color(0, 0, 0)
        paletteColors[0x2F] = Color(0, 0, 0)

        paletteColors[0x30] = Color(236, 238, 236)
        paletteColors[0x31] = Color(168, 204, 236)
        paletteColors[0x32] = Color(188, 188, 236)
        paletteColors[0x33] = Color(212, 178, 236)
        paletteColors[0x34] = Color(236, 174, 236)
        paletteColors[0x35] = Color(236, 174, 212)
        paletteColors[0x36] = Color(236, 180, 176)
        paletteColors[0x37] = Color(228, 196, 144)
        paletteColors[0x38] = Color(204, 210, 120)
        paletteColors[0x39] = Color(180, 222, 120)
        paletteColors[0x3A] = Color(168, 226, 144)
        paletteColors[0x3B] = Color(152, 226, 180)
        paletteColors[0x3C] = Color(160, 214, 228)
        paletteColors[0x3D] = Color(160, 162, 160)
        paletteColors[0x3E] = Color(0, 0, 0)
        paletteColors[0x3F] = Color(0, 0, 0)
    }

    // endregion

}