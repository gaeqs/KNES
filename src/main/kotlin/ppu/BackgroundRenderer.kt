package ppu

import util.shl
import util.shr

@ExperimentalUnsignedTypes
class BackgroundRenderer(val ppu: OLC2C02) {

    private var bgNextTileId: UByte = 0u
    private var bgNextTileAttribute: UByte = 0u
    private var bgNextTileLSB: UByte = 0u
    private var bgNextTileMSB: UByte = 0u
    private var bgShifterPatternLow: UShort = 0u
    private var bgShifterPatternHigh: UShort = 0u
    private var bgShifterAttributeLow: UShort = 0u
    private var bgShifterAttributeHigh: UShort = 0u


    fun clock(scanline: Int, cycle: Int): Pair<UByte, UByte> {
        // BACKGROUND
        if (scanline in -1 until 240) {
            if (cycle in 2 until 258 || cycle in 321 until 338) {
                updateShifters()
                when ((cycle - 1) % 8) {
                    0 -> {
                        loadBackgroundShifters()
                        loadBackgroundNextTileId()
                    }
                    2 -> loadBackgroundNextTileAttribute()
                    4 -> loadBackgroundNextLeastSignificantBytes()
                    6 -> loadBackgroundNextMostSignificantBytes()
                    7 -> incrementScrollX()
                }
            }

            if (cycle == 256) {
                incrementScrollY()
            } else if (cycle == 257) {
                loadBackgroundShifters()
                transferAddressX()
            }

            if (cycle == 338 || cycle == 340) {
                loadBackgroundNextTileId()
            }

            if (scanline == -1 && cycle in 280 until 305) {
                transferAddressY()
            }
        }

        var bgPixel: UByte = 0u
        var bgPalette: UByte = 0u

        if (ppu.mask.showBackground > 0u) {
            val bitMux = (0x8000u).toUShort() shr ppu.fineX.toInt()

            val p0Pixel = bgShifterPatternLow and bitMux > 0u
            val p1Pixel = bgShifterPatternHigh and bitMux > 0u
            bgPixel = (if (p1Pixel) 2u else 0u).toUByte() or (if (p0Pixel) 1u else 0u).toUByte()

            val bgPal0 = bgShifterAttributeLow and bitMux > 0u
            val bgPal1 = bgShifterAttributeHigh and bitMux > 0u
            bgPalette = (if (bgPal1) 2u else 0u).toUByte() or (if (bgPal0) 1u else 0u).toUByte()
        }

        return Pair(bgPixel, bgPalette)

    }


    private fun loadBackgroundNextTileId() {
        var address: UShort = 0x2000u
        address = (address or (ppu.vRamAddress.value and 0x0FFFu))
        bgNextTileId = ppu.ppuRead(address)
    }

    private fun loadBackgroundNextTileAttribute() {
        var address: UShort = 0x23C0u
        address = address or (ppu.vRamAddress.nameTableY.toUShort() shl 11)
        address = address or (ppu.vRamAddress.nameTableX.toUShort() shl 10)
        address = address or (ppu.vRamAddress.coarseY.toUShort() shr 2 shl 3)
        address = address or (ppu.vRamAddress.coarseX.toUShort() shr 2)

        bgNextTileAttribute = ppu.ppuRead(address)
        if (ppu.vRamAddress.coarseY and 0x02u > 0u) bgNextTileAttribute = bgNextTileAttribute shr 4
        if (ppu.vRamAddress.coarseX and 0x02u > 0u) bgNextTileAttribute = bgNextTileAttribute shr 2
        bgNextTileAttribute = bgNextTileAttribute and 0x03u
    }

    private fun loadBackgroundNextLeastSignificantBytes() {
        var address = ppu.control.patternBackground.toUShort() shl 12
        address = (address + (bgNextTileId.toUShort() shl 4)).toUShort()
        address = (address + (ppu.vRamAddress.fineY) + 0u).toUShort()
        bgNextTileLSB = ppu.ppuRead(address)
    }

    private fun loadBackgroundNextMostSignificantBytes() {
        var address = ppu.control.patternBackground.toUShort() shl 12
        address = (address + (bgNextTileId.toUShort() shl 4)).toUShort()
        address = (address + (ppu.vRamAddress.fineY) + 8u).toUShort()
        bgNextTileMSB = ppu.ppuRead(address)
    }

    private fun incrementScrollX() {
        if (ppu.mask.showBackground > 0u || ppu.mask.showSprites > 0u) {
            if (ppu.vRamAddress.coarseX.toUInt() == 31u) {
                ppu.vRamAddress.coarseX = 0u
                ppu.vRamAddress.nameTableX = ppu.vRamAddress.nameTableX.inv()
            } else {
                ppu.vRamAddress.coarseX++
            }
        }
    }

    private fun incrementScrollY() {
        if (ppu.mask.showBackground > 0u || ppu.mask.showSprites > 0u) {
            if (ppu.vRamAddress.fineY < 7u) {
                ppu.vRamAddress.fineY++
            } else {
                ppu.vRamAddress.fineY = 0u

                when (ppu.vRamAddress.coarseY.toUInt()) {
                    29u -> {
                        ppu.vRamAddress.coarseY = 0u
                        ppu.vRamAddress.nameTableY = ppu.vRamAddress.nameTableY.inv()
                    }
                    31u -> {
                        ppu.vRamAddress.coarseY = 0u
                    }
                    else -> {
                        ppu.vRamAddress.coarseY++
                    }
                }
            }
        }
    }

    private fun transferAddressX() {
        if (ppu.mask.showBackground > 0u || ppu.mask.showSprites > 0u) {
            ppu.vRamAddress.nameTableX = ppu.tRamAddress.nameTableX
            ppu.vRamAddress.coarseX = ppu.tRamAddress.coarseX
        }
    }

    private fun transferAddressY() {
        if (ppu.mask.showBackground > 0u || ppu.mask.showSprites > 0u) {
            ppu.vRamAddress.fineY = ppu.tRamAddress.fineY
            ppu.vRamAddress.nameTableY = ppu.tRamAddress.nameTableY
            ppu.vRamAddress.coarseY = ppu.tRamAddress.coarseY
        }
    }

    private fun loadBackgroundShifters() {
        bgShifterPatternLow = bgShifterPatternLow and 0xFF00u or bgNextTileLSB.toUShort()
        bgShifterPatternHigh = bgShifterPatternHigh and 0xFF00u or bgNextTileMSB.toUShort()

        bgShifterAttributeLow =
            (bgShifterAttributeLow and 0xFF00u) or if (bgNextTileAttribute and 0b01u > 0u) 0xFFu else 0x00u
        bgShifterAttributeHigh =
            (bgShifterAttributeHigh and 0xFF00u) or if (bgNextTileAttribute and 0b10u > 0u) 0xFFu else 0x00u
    }

    private fun updateShifters() {
        if (ppu.mask.showBackground > 0u) {
            bgShifterPatternLow = bgShifterPatternLow shl 1
            bgShifterPatternHigh = bgShifterPatternHigh shl 1
            bgShifterAttributeLow = bgShifterAttributeLow shl 1
            bgShifterAttributeHigh = bgShifterAttributeHigh shl 1
        }
    }
}