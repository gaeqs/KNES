package gui

import bus.Bus
import cpu.StatusFlag
import util.toHex
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.util.*
import javax.swing.JPanel

@ExperimentalUnsignedTypes
class NESWindow(width: Int, height: Int, val nes: Bus) : JPanel(true) {

    val disassembled: Map<Int, String>
    var lastTick: Long = 0
    var thread: Thread? = null
    var fps: Double = 0.0
    var selectedPalette: UByte = 0x0u
    var keyListener = KListener()
    val audioArray = LinkedList<Double>()


    init {
        println("Disassembling...")
        disassembled = nes.cpu.disassemble(0x1000u, 0xFFFFu)
        println("Disassembled.")
        setSize(width, height)

        addKeyListener(keyListener)
        isFocusable = true

        //nes.apu.beeper.onSample = {
        //    synchronized(audioArray) {
        //        audioArray += it
        //        if (audioArray.size > 1000) audioArray.removeFirst()
        //    }
        //}
    }

    override fun paint(g: Graphics?) {
        if (g !is Graphics2D) return
        g.color = Color.BLACK
        g.fillRect(0, 0, width, height)
        g.color = Color.WHITE

        g.font = Font("Monospaced", Font.BOLD, 15)

        g.drawImage(
            nes.ppu.screen, AffineTransformOp(
                AffineTransform.getScaleInstance(3.0, 3.0),
                RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            ),
            0, 0
        )

        g.drawString("FPS: $fps", 900, 15)
        drawAudio(g)
        drawStatus(g)
        drawRegisters(g)
        drawDisassembledCode(g)
        drawOEM(g)
        drawTables(g)
    }

    private fun drawStatus(g: Graphics2D) {
        g.drawString("STATUS:", 900, 30)
        g.color = if (nes.cpu.getFlag(StatusFlag.NEGATIVE)) Color.RED else Color.WHITE
        g.drawString("N", 970, 30)
        g.color = if (nes.cpu.getFlag(StatusFlag.OVERFLOW)) Color.RED else Color.WHITE
        g.drawString("V", 990, 30)
        g.color = if (nes.cpu.getFlag(StatusFlag.DECIMAL_MODE)) Color.RED else Color.WHITE
        g.drawString("D", 1010, 30)
        g.color = if (nes.cpu.getFlag(StatusFlag.DISABLE_INTERRUPTS)) Color.RED else Color.WHITE
        g.drawString("I", 1030, 30)
        g.color = if (nes.cpu.getFlag(StatusFlag.ZERO)) Color.RED else Color.WHITE
        g.drawString("Z", 1050, 30)
        g.color = if (nes.cpu.getFlag(StatusFlag.CARRY_BIT)) Color.RED else Color.WHITE
        g.drawString("C", 1070, 30)
        g.color = Color.WHITE
    }

    private fun drawRegisters(g: Graphics2D) {
        g.drawString("PC:     $${nes.cpu.pc.toHex(4)}", 900, 45)
        g.drawString("A:      $${nes.cpu.accumulator.toHex(2)} [${nes.cpu.accumulator}]", 900, 60)
        g.drawString("X:      $${nes.cpu.xRegister.toHex(2)} [${nes.cpu.xRegister}]", 900, 75)
        g.drawString("Y:      $${nes.cpu.yRegister.toHex(2)} [${nes.cpu.yRegister}]", 900, 90)
        g.drawString("STACK:  $${nes.cpu.stackPointer.toHex(4)}", 900, 105)
        g.drawString("Pal:    $selectedPalette", 900, 120)
    }

    private fun drawDisassembledCode(g: Graphics2D) {
        val pc = nes.cpu.pc
        var y = 145

        val from = (pc - 20u).toInt()
        val to = (pc + 50u).toInt()

        for (i in from..to) {
            val string = disassembled[i]
            if (string != null) {
                g.color = if (i == pc.toInt()) Color.RED else Color.WHITE
                g.drawString(string, 900, y)
                y += 15
            }
        }
        g.color = Color.WHITE
    }

    private fun drawOEM(g: Graphics2D) {
        var y = 15
        for (i in 0 until 20) {
            g.drawString(nes.ppu.oam[i].toString(), width - 256 * 2, y)
            y += 15
        }
    }

    private fun drawTables(g: Graphics2D) {
        for (p in 0 until 8) {
            for (s in 0 until 4) {
                g.color = nes.ppu.getColorFromPaletteRam(p.toUByte(), s.toUByte())
                g.fillRect(width - 256 * 2 + p * 25 + s * 5, height - 280, 5, 5)
            }
        }


        g.drawImage(
            nes.ppu.getPatternTable(0u, selectedPalette), AffineTransformOp(
                AffineTransform.getScaleInstance(2.0, 2.0),
                RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            ),
            width - 128 * 2, height - 128 * 2
        )
        g.drawImage(
            nes.ppu.getPatternTable(1u, selectedPalette), AffineTransformOp(
                AffineTransform.getScaleInstance(2.0, 2.0),
                RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            ),
            width - 256 * 2, height - 128 * 2
        )


        g.color = Color.WHITE
    }

    private fun drawAudio(g: Graphics2D) {
        var x = 300
        synchronized(audioArray) {
            for (d in audioArray) {
                g.drawRect(x, height / 2 + (d * 100).toInt(), 1, 1)
                x++
            }
        }
    }

    private fun manageThread() {
        val maxDelay = 1000000000L / 61L
        lastTick = System.nanoTime()
        while (!Thread.currentThread().isInterrupted) {

            updateController()
            do {
                nes.clock()
                if (Thread.currentThread().isInterrupted) return
            } while (!nes.ppu.frameCompleted)
            nes.ppu.frameCompleted = false
            repaint()

            val nextTick = lastTick + maxDelay
            while (System.nanoTime() < nextTick);

            fps = 1000.0 / ((System.nanoTime() - lastTick) / 1000000.0)
            lastTick = System.nanoTime()
        }
    }

    private fun updateController() {
        var b: UByte = 0u
        b = b or if (keyListener.a) 0x80u else 0x0u
        b = b or if (keyListener.b) 0x40u else 0x0u
        b = b or if (keyListener.select) 0x20u else 0x0u
        b = b or if (keyListener.start) 0x10u else 0x0u
        b = b or if (keyListener.up) 0x08u else 0x0u
        b = b or if (keyListener.down) 0x04u else 0x0u
        b = b or if (keyListener.left) 0x02u else 0x0u
        b = b or if (keyListener.right) 0x01u else 0x0u
        nes.controllers[0] = b
    }


    inner class KListener : KeyListener {

        var a: Boolean = false
        var b: Boolean = false
        var start: Boolean = false
        var select: Boolean = false
        var up: Boolean = false
        var down: Boolean = false
        var left: Boolean = false
        var right: Boolean = false

        override fun keyTyped(e: KeyEvent) {
        }

        override fun keyPressed(e: KeyEvent) {
            // CONTROLLER
            when (e.keyCode) {
                38 -> up = true
                37 -> left = true
                40 -> down = true
                39 -> right = true
                88 -> a = true
                90 -> b = true
                65 -> start = true
                83 -> select = true
                else ->
                    when (e.keyChar) {

                        // RUNS / STOPS
                        32.toChar() -> {
                            if (thread == null) {
                                thread = Thread(this@NESWindow::manageThread)
                                thread?.start()
                            } else {
                                thread?.interrupt()
                                thread = null
                            }
                        }
                        // PALETTES
                        'u' -> {
                            selectedPalette--
                            selectedPalette = selectedPalette and 0x07u
                        }
                        'i' -> {
                            selectedPalette++
                            selectedPalette = selectedPalette and 0x07u
                        }
                        //RESETS
                        'r' -> {
                            if (thread != null) {
                                thread?.interrupt()
                                thread = null
                            }
                            nes.reset()
                            repaint()
                        }
                        // EXECUTES ONE FRAME
                        'f' -> {
                            updateController()
                            if (thread != null) {
                                thread?.interrupt()
                                thread = null
                            }

                            do {
                                nes.clock()
                            } while (!nes.ppu.frameCompleted)
                            do {
                                nes.clock()
                            } while (!nes.cpu.isCycleCompleted())
                            nes.ppu.frameCompleted = false
                        }
                        // EXECUTES ONE INSTRUCTION
                        else -> {
                            if (thread != null) {
                                thread?.interrupt()
                                thread = null
                            }
                            updateController()
                            // Waits for the PPU
                            do {
                                nes.clock()
                            } while (nes.cpu.isCycleCompleted())

                            do {
                                nes.clock()
                            } while (!nes.cpu.isCycleCompleted())
                        }
                    }
            }
            repaint()
        }

        override fun keyReleased(e: KeyEvent) {
            when (e.keyCode) {
                38 -> up = false
                37 -> left = false
                40 -> down = false
                39 -> right = false
                88 -> a = false
                90 -> b = false
                65 -> start = false
                83 -> select = false
            }
        }
    }
}
