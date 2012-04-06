package org.sini;

import static org.sini.Ops.*;

/**
 * Cpu.java
 * @version 1.0.1
 */
public final class Cpu {
    
    /**
     * As of DCPU-16 Version 1.1 the amount of memory is 128 kilobytes. 
     */
    private static final int AMOUNT_MEMORY = 0x10000;
    
    /**
     * The starting index in the memory array of video ram.
     */
    private static final int VIDEO_RAM = 0x8000;
    
    /**
     * As of DCPU-16 Version 1.1 the amount of registers is 8; they are
     * (A, B, C, X, Y, Z, I, and J in that order)
     */
    private static final int AMOUNT_REGISTERS = 8;
    
    /**
     * The program counter index in the registers array.
     */
    private static final int PC = 8;
    
    /**
     * The stack pointer index in the registers array.
     */
    private static final int SP = 9;
    
    /**
     * The overflow index in the registers array.
     */
    private static final int O = 10;
    
    /**
     * The cycle index in the registers array.
     */
    private static final int C = 11;
    
    /**
     * The local memory of this {@link Cpu}.
     */
    Cell[] m;
    
    /**
     * The registers for the {@link Cpu}.
     */
    Cell[] r;
    
    /**
     * Initializes this {@link Cpu}.
     */
    public void initialize() {
        m = new Cell[AMOUNT_MEMORY];
        for(int i = 0; i < m.length; i++)
            m[i] = new Cell();
        r = new Cell[AMOUNT_REGISTERS + 4];   
        for(int i = 0; i < r.length; i++)
            r[i] = new Cell();
        r[SP].v = 0xFFFF;
    }
    
    /**
     * Executes a program.
     * @param memory The short array that represents the memory of the program.
     */
    public void execute(int[] memory) {
        mount(memory);
        execute();
    }
    
    /**
     * Executes the currently mounted program.
     */
    public void execute() {
        r[PC].v = 0;
        while(true) {
            int op = m[r[PC].v++].v;
            if((op & 0xF) != 0) {
                Object a = getValue(op >>> 4 & 0x3F);
                if(!(a instanceof Cell))
                    throw new RuntimeException();
                Cell aValue = (Cell) a;
                Object b = getValue(op >>> 10 & 0x3F);
                int bValue = b instanceof Cell ? ((Cell) b).v : (Integer) b;
                switch(op & 0xF) {

                    case OP_SET:                   
                        ((Cell) a).v = bValue;
                        r[C].v++;
                        break;

                    case OP_ADD:                
                        int value = aValue.v + bValue;
                        if(value > 0xFFFF) {
                            r[O].v = 0x0001;
                            value &= 0xFFFF;
                        }
                        aValue.v = value;
                        r[C].v += 2;
                        break;

                    case OP_SUB:
                        value = aValue.v - bValue;
                        if(value < 0) {
                            r[O].v = 0xFFFF;
                            value &= 0xFFFF;
                        }
                        aValue.v = value;
                        r[C].v += 2;
                        break;

                    case OP_MUL:
                        value = aValue.v * bValue;
                        aValue.v = value & 0xFFFF;
                        r[O].v = value >>> 16;
                        r[C].v += 2;
                        break;

                    case OP_DIV:
                        if(bValue == 0) {
                            aValue.v = 0;
                            r[O].v = 0;
                        } else {
                            aValue.v = aValue.v/bValue & 0xFFFF;
                            r[O].v = (aValue.v << 16)/bValue & 0xFFFF;
                        }
                        r[C].v += 3;
                        break;

                    case OP_MOD:
                        if(bValue == 0) {
                            aValue.v = 0;
                        } else
                            aValue.v = aValue.v % bValue;
                        r[C].v += 3;
                        break;

                    case OP_SHL:
                        value = aValue.v << bValue;
                        r[O].v = value >>> 16;
                        aValue.v = value & 0xFFFF;
                        r[C].v += 2;
                        break;

                    case OP_SHR:
                        aValue.v = aValue.v >>> bValue & 0xFFFF;
                        r[O].v = aValue.v << 16 >>> bValue & 0xFFFF;
                        r[C].v += 2;
                        break;

                    case OP_AND:
                        aValue.v &= bValue;
                        r[C].v++;
                        break;

                    case OP_OR:
                        aValue.v |= bValue;
                        r[C].v++;
                        break;

                    case OP_XOR:
                        aValue.v ^= bValue;
                        r[C].v++;
                        break;

                    case OP_IFE:
                        if(aValue.v != bValue) {
                            op = m[r[PC].v++].v;
                            getValue(op >>> 4 & 0x3F);
                            getValue(op >>> 10 & 0x3F);
                            r[C].v++;
                        }
                        r[C].v += 2;
                        break;

                    case OP_IFN:
                        if(aValue.v == bValue) {
                            op = m[r[PC].v++].v;
                            getValue(op >>> 4 & 0x3F);
                            getValue(op >>> 10 & 0x3F);
                            r[C].v++;
                        }
                        r[C].v += 2;
                        break;

                    case OP_IFG:
                        if(aValue.v <= bValue) {
                            op = m[r[PC].v++].v;
                            getValue(op >>> 4 & 0x3F);
                            getValue(op >>> 10 & 0x3F);
                            r[C].v++;
                        }
                        r[C].v += 2;
                        break;

                     case OP_IFB:
                        if((aValue.v & bValue) == 0) {
                            op = m[r[PC].v++].v;
                            getValue(op >>> 4 & 0x3F);
                            getValue(op >>> 10 & 0x3F);
                            r[C].v++;
                        }
                        r[C].v += 2;
                        break;
                }
            } else {
                op >>>= 4;
                Object a = getValue(op >>> 6);
                int aValue = a instanceof Cell ? ((Cell) a).v : (Integer) a;
                switch(op & 0x3F) {

                    case 0:
                        return;
                        
                    case OP_JSR:
                        Cell cell = m[--r[SP].v];
                        cell.v = r[PC].v;
                        r[PC].v = aValue;
                        r[C].v += 2;
                        break;
                }
            }
        }
    }
    
    /**
     * Gets the v from a v opcode.
     * @param op The opcode.
     * @return The v from the opcode.
     */
    public Object getValue(int op) {
        switch(op) {
            
            case 0x00:
            case 0x01:
            case 0x02:
            case 0x03:
            case 0x04:
            case 0x05:
            case 0x06:
            case 0x07:
                return r[op];
                
            case 0x08:
            case 0x09:
            case 0x0A:
            case 0x0B:
            case 0x0C:
            case 0x0D:
            case 0x0E:
            case 0x0F:
                return m[r[op - 0x08].v];
            
            case 0x10:
            case 0x11:
            case 0x12:
            case 0x13:
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x17:
                r[C].v ++;
                return m[r[op - 0x10].v + m[r[PC].v++].v];
                
            case 0x18:
                return m[r[SP].v++];
                
            case 0x19:
                return m[r[SP].v];      
                
            case 0x1A:
                return m[--r[SP].v];
            
            case 0x1B:
                return r[SP]; 
            
            case 0x1C:
                return r[PC];
                
            case 0x1D:
                return r[O];
                
            case 0x1E:
                r[C].v ++;
                return m[m[r[PC].v++].v];
                
            case 0x1F:
                r[C].v ++;
                return m[r[PC].v++];
                
            case 0x20:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x25:
            case 0x26:
            case 0x27:
            case 0x28:
            case 0x29:
            case 0x2A:
            case 0x2B:
            case 0x2C:
            case 0x2D:
            case 0x2E:
            case 0x2F:
            case 0x30:
            case 0x31:
            case 0x32:
            case 0x33:
            case 0x34:
            case 0x35:
            case 0x36:
            case 0x37:
            case 0x38:
            case 0x39:
            case 0x3A:
            case 0x3B:
            case 0x3C:
            case 0x3D:
            case 0x3E:
            case 0x3F:
                return op - 0x20;
                
            default:
                throw new RuntimeException("Unknown op in fetch: " + op);
                
        }
    }
    
    /**
     * Mount the memory of a program onto the memory of the {@link Cpu}.
     * @param memory The short array that represents the memory of the program.
     */
    public void mount(int[] memory) {
        initialize();
        for(int i = 0; i < memory.length; i++) 
            m[i].v = memory[i];
    }
    
    /**
     * The main entry point for this program.
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        int[] instructions = new int[] { 0x7c01, 0x0030, 0x7de1, 0x1000, 0x0020, 0x7803, 0x1000, 0xc00d,
                                         0x7dc1, 0x001a, 0xa861, 0x7c01, 0x2000, 0x2161, 0x2000, 0x8463,
                                         0x806d, 0x7dc1, 0x000d, 0x9031, 0x7c10, 0x0018, 0x7dc1, 0x001a,
                                         0x9037, 0x61c1, 0x7dc1, 0x001a, 0x0000, 0x0000, 0x0000, 0x0000
                                       };
        Cpu cpu = new Cpu();
        //cpu.execute(instructions);
        Asm asm = new Asm();
        asm.assemble(";hello\n"
                   + ":hello\n"
                   + "set CP, [0xffff] \n"
                   + "");
    }
}
