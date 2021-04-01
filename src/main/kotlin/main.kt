import bus.Bus
import bus.Cartridge
import cpu.OLC6502
import gui.NESWindow
import ppu.OLC2C02
import java.io.File
import javax.swing.JFrame
import javax.swing.WindowConstants

@ExperimentalUnsignedTypes
lateinit var WINDOW : NESWindow

@ExperimentalUnsignedTypes
fun main(args: Array<String>) {
    println("Hello World!")
    val cpu = OLC6502()
    val ppu = OLC2C02()
    val bus = Bus(cpu, ppu)

    bus.cartridge = Cartridge(File("roms/dk.nes"))
    bus.reset()

    WINDOW = NESWindow(800, 600, bus)
    val frame = JFrame("KNES")
    frame.add(WINDOW)
    frame.setSize(800, 600)
    frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
    frame.isVisible = true
}