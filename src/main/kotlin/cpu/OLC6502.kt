package cpu

import WINDOW
import bus.Bus
import util.*

@ExperimentalUnsignedTypes
class OLC6502 {

    var bus: Bus? = null

    var accumulator: UByte = 0u
    var xRegister: UByte = 0u
    var yRegister: UByte = 0u
    var stackPointer: UByte = 0u
    var pc: UShort = 0u
    var status: UByte = 0u

    var fetched: UByte = 0u
    var absoluteAddress: UShort = 0u
    var relativeAddress: UShort = 0u

    var currentOpcode: UByte = 0u
    var cyclesLeft: UByte = 0u

    fun clock() {
        if (cyclesLeft > 0u) {
            cyclesLeft--
            return
        }

        currentOpcode = read(pc++)
        val instruction = Instruction.FETCH_TABLE[currentOpcode.toInt()]
        cyclesLeft = instruction.cycles
        val addCycleOne = instruction.addressingMode.call(this)
        val addCycleTwo = instruction.operation.call(this)
        if (!addCycleOne || !addCycleTwo) cyclesLeft--
    }

    fun reset() {
        accumulator = 0u
        xRegister = 0u
        yRegister = 0u
        stackPointer = 0xFDu
        status = 0x00u
        status = status or StatusFlag.UNUSED.mask

        pc = read(0xFFFDu) concatenate read(0xFFFCu)

        relativeAddress = 0u
        absoluteAddress = 0u
        fetched = 0u
        cyclesLeft = 8u
    }

    fun interruptRequest() {
        if (getFlag(StatusFlag.DISABLE_INTERRUPTS)) return
        pushToStack((pc shr 8).toUByte())
        pushToStack(pc.toUByte())

        setFlag(StatusFlag.BREAK, true)
        setFlag(StatusFlag.UNUSED, true)
        setFlag(StatusFlag.DISABLE_INTERRUPTS, true)
        pushToStack(status)

        pc = read(0xFFFFu) concatenate read(0xFFFEu)
        cyclesLeft = 7u
    }

    fun nonMaskableInterrupt() {
        pushToStack((pc shr 8).toUByte())
        pushToStack(pc.toUByte())
        setFlag(StatusFlag.BREAK, true)
        setFlag(StatusFlag.UNUSED, true)
        setFlag(StatusFlag.DISABLE_INTERRUPTS, true)
        pushToStack(status)

        pc = read(0xFFFBu) concatenate read(0xFFFAu)
        cyclesLeft = 8u
    }

    fun fetch(): UByte {
        val instruction = Instruction.FETCH_TABLE[currentOpcode.toInt()]
        if (instruction.addressingMode != OLC6502::IMP) {
            fetched = read(absoluteAddress)
        }
        return fetched;
    }

    // region OPCODES

    fun ADC(): Boolean {
        fetch()
        var temp = (accumulator.toUShort() + fetched.toUShort()).toUShort()
        if (getFlag(StatusFlag.CARRY_BIT)) temp++

        setFlag(StatusFlag.CARRY_BIT, temp > 255u)
        setFlag(StatusFlag.ZERO, (temp and 0x00FFu).isZero())
        setFlag(StatusFlag.NEGATIVE, (temp and 0x80u) > 0u)
        setFlag(
            StatusFlag.OVERFLOW,
            (accumulator xor fetched).toUShort().inv() and (accumulator.toUShort() xor temp) and 0x0080u > 0u
        )
        accumulator = temp.toUByte()
        return true
    }

    fun AND(): Boolean {
        fetch()
        accumulator = accumulator and fetched
        setFlag(StatusFlag.ZERO, accumulator.isZero())
        setFlag(StatusFlag.NEGATIVE, accumulator and 0x80u > 0u)
        return true
    }

    fun ASL(): Boolean {
        fetch()
        val temp = fetched.toUShort() shl 1
        setFlag(StatusFlag.CARRY_BIT, temp and 0xFF00u > 0u)
        setFlag(StatusFlag.ZERO, (temp and 0x00FFu).isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)

        if (Instruction.FETCH_TABLE[currentOpcode.toInt()].addressingMode == OLC6502::IMP) {
            accumulator = temp.toUByte()
        } else {
            write(absoluteAddress, temp.toUByte())
        }
        return false
    }

    fun BCC(): Boolean {
        if (!getFlag(StatusFlag.CARRY_BIT)) branch()
        return false
    }

    fun BCS(): Boolean {
        if (getFlag(StatusFlag.CARRY_BIT)) branch()
        return false
    }

    fun BEQ(): Boolean {
        if (getFlag(StatusFlag.ZERO)) branch()
        return false
    }

    fun BIT(): Boolean {
        fetch()
        val temp = accumulator and fetched
        setFlag(StatusFlag.ZERO, (temp and 0x00FFu).isZero())
        setFlag(StatusFlag.NEGATIVE, fetched and 0x80u > 0u)
        setFlag(StatusFlag.OVERFLOW, fetched and 0x40u > 0u)
        return false
    }

    fun BMI(): Boolean {
        if (getFlag(StatusFlag.NEGATIVE)) branch()
        return false
    }

    fun BNE(): Boolean {
        if (!getFlag(StatusFlag.ZERO)) branch()
        return false
    }

    fun BPL(): Boolean {
        if (!getFlag(StatusFlag.NEGATIVE)) branch()
        return false
    }

    fun BRK(): Boolean {
        pc++
        setFlag(StatusFlag.DISABLE_INTERRUPTS, true)
        pushToStack((pc shr 8).toUByte())
        pushToStack(pc.toUByte())
        setFlag(StatusFlag.BREAK, true)
        pushToStack(status)
        setFlag(StatusFlag.BREAK, false)
        pc = read(0xFFFFu) concatenate read(0xFFFEu)
        return false
    }

    fun BVC(): Boolean {
        if (!getFlag(StatusFlag.OVERFLOW)) branch()
        return false
    }

    fun BVS(): Boolean {
        if (getFlag(StatusFlag.OVERFLOW)) branch()
        return false
    }

    fun CLC(): Boolean {
        setFlag(StatusFlag.CARRY_BIT, false)
        return false
    }

    fun CLD(): Boolean {
        setFlag(StatusFlag.DECIMAL_MODE, false)
        return false
    }

    fun CLI(): Boolean {
        setFlag(StatusFlag.DISABLE_INTERRUPTS, false)
        return false
    }

    fun CLV(): Boolean {
        setFlag(StatusFlag.OVERFLOW, false)
        return false
    }

    fun CMP(): Boolean {
        fetch()
        val temp = (accumulator.toUShort() - fetched.toUShort()).toUShort()
        setFlag(StatusFlag.CARRY_BIT, accumulator >= fetched)
        setFlag(StatusFlag.ZERO, (temp and 0x00FFu).isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)
        return true
    }

    fun CPX(): Boolean {
        fetch()
        val temp = (xRegister.toUShort() - fetched.toUShort()).toUShort()
        setFlag(StatusFlag.CARRY_BIT, xRegister >= fetched)
        setFlag(StatusFlag.ZERO, (temp and 0x00FFu).isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)
        return false
    }

    fun CPY(): Boolean {
        fetch()
        val temp = (yRegister.toUShort() - fetched.toUShort()).toUShort()
        setFlag(StatusFlag.CARRY_BIT, yRegister >= fetched)
        setFlag(StatusFlag.ZERO, (temp and 0x00FFu).isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)
        return false
    }

    fun DEC(): Boolean {
        fetch()
        val temp = fetched.dec()
        write(absoluteAddress, temp)
        setFlag(StatusFlag.ZERO, temp.isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)
        return false
    }

    fun DEX(): Boolean {
        xRegister--
        setFlag(StatusFlag.ZERO, xRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, xRegister and 0x80u > 0u)
        return false
    }

    fun DEY(): Boolean {
        yRegister--
        setFlag(StatusFlag.ZERO, yRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, yRegister and 0x80u > 0u)
        return false
    }

    fun EOR(): Boolean {
        fetch()
        accumulator = accumulator xor fetched
        setFlag(StatusFlag.ZERO, accumulator.isZero())
        setFlag(StatusFlag.NEGATIVE, accumulator and 0x80u > 0u)
        return true
    }

    fun INC(): Boolean {
        fetch()
        val temp = fetched.inc()
        write(absoluteAddress, temp)
        setFlag(StatusFlag.ZERO, temp.isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)
        return false
    }

    fun INX(): Boolean {
        xRegister++
        setFlag(StatusFlag.ZERO, xRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, xRegister and 0x80u > 0u)
        return false
    }

    fun INY(): Boolean {
        yRegister++
        setFlag(StatusFlag.ZERO, yRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, yRegister and 0x80u > 0u)
        return false
    }

    fun JMP(): Boolean {
        pc = absoluteAddress
        return false
    }

    fun JSR(): Boolean {
        pc--

        pushToStack((pc shr 8).toUByte())
        pushToStack(pc.toUByte())

        pc = absoluteAddress

        return false
    }

    fun LDA(): Boolean {
        fetch()
        accumulator = fetched
        setFlag(StatusFlag.ZERO, accumulator.isZero())
        setFlag(StatusFlag.NEGATIVE, accumulator and 0x80u > 0u)
        return true
    }

    fun LDX(): Boolean {
        fetch()
        xRegister = fetched
        setFlag(StatusFlag.ZERO, xRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, xRegister and 0x80u > 0u)
        return true
    }

    fun LDY(): Boolean {
        fetch()
        yRegister = fetched
        setFlag(StatusFlag.ZERO, yRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, yRegister and 0x80u > 0u)
        return true
    }

    fun LSR(): Boolean {
        fetch()
        setFlag(StatusFlag.CARRY_BIT, fetched and 0x1u > 0u)
        val temp = fetched shr 1
        setFlag(StatusFlag.ZERO, temp.isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)

        if (Instruction.FETCH_TABLE[currentOpcode.toInt()].addressingMode == OLC6502::IMP) {
            accumulator = temp
        } else {
            write(absoluteAddress, temp)
        }

        return false
    }

    fun NOP(): Boolean {

        return when (currentOpcode.toUInt()) {
            0x1Cu,
            0x3Cu,
            0x5Cu,
            0x7Cu,
            0xDCu,
            0xFCu,
            -> true
            else -> false
        }
    }

    fun ORA(): Boolean {
        fetch()
        accumulator = accumulator or fetched
        setFlag(StatusFlag.ZERO, accumulator.isZero())
        setFlag(StatusFlag.NEGATIVE, accumulator and 0x80u > 0u)
        return true
    }

    fun PHA(): Boolean {
        pushToStack(accumulator)
        return false
    }

    fun PHP(): Boolean {
        pushToStack(status or StatusFlag.BREAK.mask or StatusFlag.UNUSED.mask)
        setFlag(StatusFlag.BREAK, false)
        setFlag(StatusFlag.UNUSED, false)
        return false
    }

    fun PLA(): Boolean {
        accumulator = popFromStack()
        setFlag(StatusFlag.ZERO, accumulator.isZero())
        setFlag(StatusFlag.NEGATIVE, accumulator and 0x80u > 0u)
        return false
    }

    fun PLP(): Boolean {
        status = popFromStack()
        setFlag(StatusFlag.UNUSED, true)
        return false
    }

    fun ROL(): Boolean {
        fetch()
        val temp = fetched.toUShort() shl 1 or if (getFlag(StatusFlag.CARRY_BIT)) 1u else 0u
        setFlag(StatusFlag.CARRY_BIT, temp and 0xFF00u > 0u)
        setFlag(StatusFlag.ZERO, (temp and 0x00FFu).isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)

        if (Instruction.FETCH_TABLE[currentOpcode.toInt()].addressingMode == OLC6502::IMP) {
            accumulator = temp.toUByte()
        } else {
            write(absoluteAddress, temp.toUByte())
        }

        return false
    }

    fun ROR(): Boolean {
        fetch()

        val temp = (if (getFlag(StatusFlag.CARRY_BIT)) 128u else 0u).toUByte() or (fetched shr 1)
        setFlag(StatusFlag.CARRY_BIT, fetched and 0x1u > 0u)
        setFlag(StatusFlag.ZERO, temp.isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)

        if (Instruction.FETCH_TABLE[currentOpcode.toInt()].addressingMode == OLC6502::IMP) {
            accumulator = temp
        } else {
            write(absoluteAddress, temp)
        }

        return false
    }

    fun RTI(): Boolean {
        status = popFromStack()
        status = status and StatusFlag.BREAK.mask.inv()
        status = status and StatusFlag.UNUSED.mask.inv()

        val low = popFromStack()
        val high = popFromStack()
        pc = high concatenate low

        return false
    }

    fun RTS(): Boolean {
        val low = popFromStack()
        val high = popFromStack()
        pc = high concatenate low
        pc++
        return false
    }

    fun SBC(): Boolean {
        fetch()
        val invert = fetched.toUShort() xor 0x00FFu
        var temp = (accumulator.toUShort() + invert).toUShort()
        if (getFlag(StatusFlag.CARRY_BIT)) temp++

        setFlag(StatusFlag.CARRY_BIT, temp and 0xFF00u > 0u)
        setFlag(StatusFlag.ZERO, (temp and 0x00FFu).isZero())
        setFlag(StatusFlag.NEGATIVE, temp and 0x80u > 0u)
        setFlag(
            StatusFlag.OVERFLOW,
            (accumulator.toUShort() xor temp) and (invert xor temp) and 0x0080u > 0u
        )
        accumulator = temp.toUByte()
        return true
    }

    fun SEC(): Boolean {
        setFlag(StatusFlag.CARRY_BIT, true)
        return false
    }

    fun SED(): Boolean {
        setFlag(StatusFlag.DECIMAL_MODE, true)
        return false
    }

    fun SEI(): Boolean {
        setFlag(StatusFlag.DISABLE_INTERRUPTS, true)
        return false
    }

    fun STA(): Boolean {
        write(absoluteAddress, accumulator)
        return false
    }

    fun STX(): Boolean {
        write(absoluteAddress, xRegister)
        return false
    }

    fun STY(): Boolean {
        write(absoluteAddress, yRegister)
        return false
    }

    fun TAX(): Boolean {
        xRegister = accumulator;
        setFlag(StatusFlag.ZERO, xRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, xRegister and 0x80u > 0u)
        return false
    }

    fun TAY(): Boolean {
        yRegister = accumulator;
        setFlag(StatusFlag.ZERO, yRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, yRegister and 0x80u > 0u)
        return false
    }

    fun TSX(): Boolean {
        xRegister = stackPointer
        setFlag(StatusFlag.ZERO, xRegister.isZero())
        setFlag(StatusFlag.NEGATIVE, xRegister and 0x80u > 0u)
        return false
    }

    fun TXA(): Boolean {
        accumulator = xRegister
        setFlag(StatusFlag.ZERO, accumulator.isZero())
        setFlag(StatusFlag.NEGATIVE, accumulator and 0x80u > 0u)
        return false
    }

    fun TXS(): Boolean {
        stackPointer = xRegister
        return false
    }

    fun TYA(): Boolean {
        accumulator = yRegister
        setFlag(StatusFlag.ZERO, accumulator.isZero())
        setFlag(StatusFlag.NEGATIVE, accumulator and 0x80u > 0u)
        return false
    }

    fun ILL(): Boolean {
        return false
    }

    //endregion

    // region ADDRESSING MODES

    fun IMP(): Boolean {
        fetched = accumulator
        return false
    }

    fun IMM(): Boolean {
        absoluteAddress = pc++
        return false
    }

    fun ZP0(): Boolean {
        absoluteAddress = read(pc++).toUShort()
        return false
    }

    fun ZPX(): Boolean {
        absoluteAddress = (read(pc++) + xRegister).toUShort() and 0x00FFu
        return false
    }

    fun ZPY(): Boolean {
        absoluteAddress = (read(pc++) + yRegister).toUShort() and 0x00FFu
        return false
    }

    fun REL(): Boolean {
        relativeAddress = read(pc++).toUShort()

        //If negative, extend the sign
        if (relativeAddress and 0x80u > 0u) {
            relativeAddress = relativeAddress or 0xFF00u
        }
        return false
    }

    fun ABS(): Boolean {
        val low = read(pc++)
        val high = read(pc++)
        absoluteAddress = high concatenate low
        return false
    }

    fun ABX(): Boolean {
        val low = read(pc++)
        val high = read(pc++)
        absoluteAddress = high concatenate low
        absoluteAddress = (absoluteAddress + xRegister).toUShort()

        return absoluteAddress and 0xFF00u != high.toUShort() shl 8
    }

    fun ABY(): Boolean {
        val low = read(pc++)
        val high = read(pc++)
        absoluteAddress = high concatenate low
        absoluteAddress = (absoluteAddress + yRegister).toUShort()

        return absoluteAddress and 0xFF00u != high.toUShort() shl 8
    }

    fun IND(): Boolean {
        val low = read(pc++)
        val high = read(pc++)

        val ptr: UShort = high concatenate low

        // Page boundary bug!
        absoluteAddress = if (low == UByte.MAX_VALUE) {
            read(ptr and 0xFF00u).toUShort() shl 8 or read(ptr).toUShort()
        } else {
            read(ptr.inc()).toUShort() shl 8 or read(ptr).toUShort()
        }

        return false
    }

    fun IZX(): Boolean {
        val temp = (read(pc++) + xRegister).toUShort()

        val low = read(temp and 0x00FFu)
        val high = read((temp + 1u).toUShort() and 0x00FFu)
        absoluteAddress = high concatenate low

        return false
    }

    fun IZY(): Boolean {
        val temp = read(pc++).toUShort()

        val low = read(temp and 0x00FFu)
        val high = read((temp + 1u).toUShort() and 0x00FFu)
        absoluteAddress = high concatenate low
        absoluteAddress = (absoluteAddress + yRegister).toUShort()

        return absoluteAddress and 0xFF00u != high.toUShort() shl 8
    }

    //endregion

    // region HELPER FUNCTIONS

    fun isCycleCompleted() = cyclesLeft.isZero()

    private fun branch() {
        cyclesLeft++
        absoluteAddress = (pc + relativeAddress).toUShort()

        if (absoluteAddress and 0xFF00u != pc and 0xFF00u) cyclesLeft++
        pc = absoluteAddress
    }

    private fun read(address: UShort) = bus!!.cpuRead(address, false)

    private fun write(address: UShort, data: UByte) = bus!!.cpuWrite(address, data)

    fun getFlag(flag: StatusFlag) = status and flag.mask > 0u

    fun setFlag(flag: StatusFlag, value: Boolean) {
        status = if (value) {
            status or flag.mask
        } else {
            status and flag.mask.inv()
        }
    }

    private fun pushToStack(value: UByte) = write((0x0100u + stackPointer--).toUShort(), value)

    private fun popFromStack() = read((0x0100u + ++stackPointer).toUShort())

    //endregion

    // region DIASSEMBLE

    fun disassemble(from: UShort, to: UShort): Map<Int, String> {

        var address = from
        var value: UByte
        var low: UByte
        var high: UByte
        val map = HashMap<Int, String>()
        var lineStart = address.toInt()

        while (address <= to) {
            if (lineStart > address.toInt()) break
            lineStart = address.toInt()
            var string = "$${address.toString(16)}:  "
            val opcode = bus!!.cpuRead(address++, true)
            val instruction = Instruction.FETCH_TABLE[opcode.toInt()]
            string += "${instruction.mnemonic}  "

            string += when (instruction.addressingMode) {
                OLC6502::IMP -> "{IMP}"
                OLC6502::IMM -> {
                    value = bus!!.cpuRead(address++, true)
                    "#$${value.toHex(2)} {IMM}"
                }
                OLC6502::ZP0 -> {
                    low = bus!!.cpuRead(address++, true)
                    "$${low.toHex(2)}, {ZP0}"
                }
                OLC6502::ZPX -> {
                    low = bus!!.cpuRead(address++, true)
                    "$${low.toHex(2)}, X {ZPX}"
                }
                OLC6502::ZPY -> {
                    low = bus!!.cpuRead(address++, true)
                    "$${low.toHex(2)}, Y {ZPY}"
                }
                OLC6502::IZX -> {
                    low = bus!!.cpuRead(address++, true)
                    "($${low.toHex(2)}, X) {IZX}"
                }
                OLC6502::IZY -> {
                    low = bus!!.cpuRead(address++, true)
                    "($${low.toHex(2)}), Y {IZY}"
                }
                OLC6502::ABS -> {
                    low = bus!!.cpuRead(address++, true)
                    high = bus!!.cpuRead(address++, true)
                    "$${(high concatenate low).toHex(4)} {ABS}"
                }
                OLC6502::ABX -> {
                    low = bus!!.cpuRead(address++, true)
                    high = bus!!.cpuRead(address++, true)
                    "$${(high concatenate low).toHex(4)}, X {ABX}"
                }
                OLC6502::ABY -> {
                    low = bus!!.cpuRead(address++, true)
                    high = bus!!.cpuRead(address++, true)
                    "$${(high concatenate low).toHex(4)}, Y {ABY}"
                }
                OLC6502::IND -> {
                    low = bus!!.cpuRead(address++, true)
                    high = bus!!.cpuRead(address++, true)
                    "($${(high concatenate low).toHex(4)}) {IND}"
                }
                OLC6502::REL -> {
                    value = bus!!.cpuRead(address++, true)
                    "$${value.toHex(2)} [$${(address.toInt() + value.toByte()).toString(16)}] {REL}"
                }
                else -> "ILL"
            }
            map[lineStart] = string
        }

        return map
    }

    //endregion

}

@ExperimentalUnsignedTypes
enum class StatusFlag(val mask: UByte) {

    CARRY_BIT(0b00000001u),
    ZERO(0b00000010u),
    DISABLE_INTERRUPTS(0b00000100u), // Unused for NES
    DECIMAL_MODE(0b00001000u),
    BREAK(0b00010000u),
    UNUSED(0b00100000u),
    OVERFLOW(0b01000000u),
    NEGATIVE(0b10000000u)

}