/* 
 * Copyright (C) 2017 bluew
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.gb.cpu;

import ch.gb.Component;
import ch.gb.Config;
import ch.gb.GBComponents;
import ch.gb.io.Timer;
import ch.gb.mem.Memory;
import ch.gb.utils.Utils;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mixture between 8080 and z80
 *
 * @author bluew
 *
 */
public class CPU implements Component {

    public static final int CLOCK = 4194304;// Hz

    // flags of register F
    public static final int Z = 0x80;
    public static final int N = 0x40;
    public static final int H = 0x20;
    public static final int C = 0x10;

    // register addresses
    public static final int RG_A = 0;
    public static final int RG_F = 1;
    public static final int RG_B = 2;
    public static final int RG_C = 3;
    public static final int RG_D = 4;
    public static final int RG_E = 5;
    public static final int RG_H = 6;
    public static final int RG_L = 7;

    // double register addresses
    public static final int RG_AF = 0;
    public static final int RG_BC = 2;
    public static final int RG_DE = 4;
    public static final int RG_HL = 6;
    public static final int RG_SP = 8;

    // jump conditions
    public static final int JMP_ALWAYS = 0;
    public static final int JMP_NZ = 0;
    public static final int JMP_Z = 0x80;
    public static final int JMP_NC = 0;
    public static final int JMP_C = 0x10;

    // register memory
    private final byte[] regs = new byte[8];

    public int pc;
    int sp;

    // interrupt specific
    public static final int VEC_VBLANK = 0x40;
    public static final int VEC_LCD = 0x48;
    public static final int VEC_TIMER = 0x50;
    public static final int VEC_SERIAL = 0x58;
    public static final int VEC_JOY = 0x60;

    public static final int VBLANK_IR = 0;
    public static final int LCD_IR = 1;
    public static final int TIMER_IR = 2;
    public static final int SERIAL_IR = 3;
    public static final int JOYPAD_IR = 4;

    public static final int IE_REG = 0xFFFF;// interrupt enable (masks)
    public static final int IF_REG = 0xFF0F;// interrupt flag (requests)

    //private int delayIE;
    private boolean ime;// interrupt master enabled flag
    private boolean halt;
    private Memory mem;

    public static boolean DEBUG_ENABLED = false;
    private int debugpc;
    private String debuginf;

    private Instr[] nInstr;// normal
    private Instr[] eInstr;// extended

    private static final int[] nCycles = {1, 3, 2, 2, 1, 1, 2, 1, 5, 2, 2, 2, 1, 1, 2, 1, 0, 3, 2, 2, 1, 1, 2, 1, 3,
        2, 2, 2, 1, 1, 2, 1, 2, 3, 2, 2, 1, 1, 2, 1, 2, 2, 2, 2, 1, 1, 2, 1, 2, 3, 2, 2, 3, 3, 3, 1, 2, 2, 2, 2, 1,
        1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1,
        1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2, 2, 2, 2, 2, 2, 0, 2, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1,
        1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1,
        1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2, 3, 3, 4, 3, 4, 2, 4, 2, 4, 3, 0, 3,
        6, 2, 4, 2, 3, 3, 0, 3, 4, 2, 4, 2, 4, 3, 0, 3, 0, 2, 4, 3, 3, 2, 0, 0, 4, 2, 4, 4, 1, 4, 0, 0, 0, 2, 4, 3,
        3, 2, 1, 0, 4, 2, 4, 3, 2, 4, 1, 0, 0, 2, 4};
    private static final int[] eCycles = {2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2,
        2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2,
        2, 4, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2,
        2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2,
        2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2,
        2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2,
        2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2,
        2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2};
    private static final int[] nCyclesTaken = {1, 3, 2, 2, 1, 1, 2, 1, 5, 2, 2, 2, 1, 1, 2, 1, 0, 3, 2, 2, 1, 1, 2, 1,
        3, 2, 2, 2, 1, 1, 2, 1, 3, 3, 2, 2, 1, 1, 2, 1, 3, 2, 2, 2, 1, 1, 2, 1, 3, 3, 2, 2, 3, 3, 3, 1, 3, 2, 2, 2,
        1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1,
        1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2, 2, 2, 2, 2, 2, 0, 2, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1,
        1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1,
        1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 5, 3, 4, 4, 6, 4, 2, 4, 5, 4, 4, 0,
        6, 6, 2, 4, 5, 3, 4, 0, 6, 4, 2, 4, 5, 4, 4, 0, 6, 0, 2, 4, 3, 3, 2, 0, 0, 4, 2, 4, 4, 1, 4, 0, 0, 0, 2, 4,
        3, 3, 2, 1, 0, 4, 2, 4, 3, 2, 4, 1, 0, 0, 2, 4};

    private int consumedCycles;

    private BufferedWriter bw = null;
    boolean debugfail = false;

    public CPU() {
        generateInstructions();
    }

    @Override
    public void reset() {
        halt = false;
        ime = true;
        pc = 0x100;
        sp = 0xFFFE;
        wr16reg(RG_AF, (Config.gbType == 2 ? 0x11B0 : 0x01B0));// for GBC its 0x11B0
        wr16reg(RG_BC, 0x0013);
        wr16reg(RG_DE, 0x00D8);
        wr16reg(RG_HL, 0x014D);
    }

    @Override
    public void connect(GBComponents comps) {
        this.mem = comps.mem;
    }

    public int clock() {
        debugpc = pc;
        debuginf = "idling";
        if (DEBUG_ENABLED) {
//            for(int i=0;i<256;i++){
//                if(nCycles[i]!=nCyclesTaken[i]){
//                    System.out.print(i+"  ");
//                    System.out.println(nCyclesTaken[i]-nCycles[i]);
//                }
//            }
//            //this is the jump to the failure message
//            if(pc==0xC2EF && (((rd16reg(RG_AF) >> 4 )&0x8)!=0x8)){
//                debugfail=true;
//                System.out.println("ERROR");
//            }

        }
        consumedCycles = 0;
        checkInterrupts();

        if (halt) {
            return 4;
        }

        byte opcode = mem.readByte(pc++);
        byte b2 = mem.readByte(pc);
        byte b3 = mem.readByte(pc + 1);

        if (DEBUG_ENABLED) {
            debuginf = "PC:" + Utils.dumpHex(debugpc) + "  " + Disassembler.disassemble(opcode, b2, b3) + "  "
                    + "   AF:" + Utils.dumpHex(rd16reg(RG_AF)) + "   BC:" + Utils.dumpHex(rd16reg(RG_BC))
                    + "   DE:" + Utils.dumpHex(rd16reg(RG_DE)) + "   HL:" + Utils.dumpHex(rd16reg(RG_HL))
                    + "   SP:" + Utils.dumpHex(rd16reg(RG_SP))
                    + "   ie:" + Utils.dumpHex(mem.peek(CPU.IE_REG))
                    + "   if:" + Utils.dumpHex(mem.peek(CPU.IF_REG))
                    + "   znhc:" + String.format("%4s", Integer.toBinaryString((rd16reg(RG_AF) >> 4) & 0xf)).replace(' ', '0')
                    + "   tima:" + Utils.dumpHex(mem.peek(Timer.TIMAAddr) & 0xff);
            if (debugfail) {
                debuginf = debuginf + "   :FAILURE";
                debugfail = false;
            }
        }

        if ((opcode & 0xff) == 0xCB) {
            int np = mem.readByte(pc++) & 0xff;
            eInstr[np].compile();
            consumedCycles += eCycles[np] * 4;// machine -> clock cycles
        } else {
            nInstr[opcode & 0xff].compile();
            consumedCycles += nCycles[opcode & 0xff] * 4;// machine -> clock cycles
        }

        if (DEBUG_ENABLED) {
            debuginf += ("   cycles:" + consumedCycles / 4);
            if (bw == null) {
                try {
                    bw = new BufferedWriter(new FileWriter("debugdump.txt"));
                } catch (IOException ex) {
                    Logger.getLogger(CPU.class.getName()).log(Level.SEVERE, null, ex);
                }

            }

            try {
                bw.write(debuginf);
                bw.newLine();
            } catch (IOException ex) {
                Logger.getLogger(CPU.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return consumedCycles;// assume 8 clockcycles
    }

    private void checkInterrupts() {
        if (mem.readByte(IF_REG) != 0) {
            halt = false;
        }
        if (ime) {
            byte irq = mem.readByte(IF_REG);
            byte ie = mem.readByte(IE_REG);

            if ((irq & 0x1f) != 0) {
                if (halt) {
                    halt = false; //interrupt occured, we leave halt mode
                }

                if ((irq & 1) == 1 && (ie & 1) == 1) {
                    // V-Blank
                    ime = false;
                    push2(pc);
                    irq &= 0xFE;
                    mem.writeByte(IF_REG, irq);
                    pc = VEC_VBLANK;
                    consumedCycles += 20; //can't put this out of here because ie has to be checked too
                    return;
                } else if ((irq & 2) == 2 && (ie & 2) == 2) {
                    // LCD
                    ime = false;
                    push2(pc);
                    irq &= 0xFD;
                    mem.writeByte(IF_REG, irq);
                    pc = VEC_LCD;
                    consumedCycles += 20;
                    return;
                } else if ((irq & 4) == 4 && (ie & 4) == 4) {
                    // Timer
                    ime = false;
                    push2(pc);
                    irq &= 0xFB;
                    mem.writeByte(IF_REG, irq);
                    pc = VEC_TIMER;
                    consumedCycles += 20;
                    return;
                } else if ((irq & 8) == 8 && (ie & 8) == 8) {
                    // Serial
                    ime = false;
                    push2(pc);
                    irq &= 0xF7;
                    mem.writeByte(IF_REG, irq);
                    pc = VEC_SERIAL;
                    consumedCycles += 20;
                    return;
                } else if ((irq & 0x10) == 0x10 && (ie & 0x10) == 0x10) {
                    // Joypad
                    ime = false;
                    push2(pc);
                    irq &= 0xEF;
                    mem.writeByte(IF_REG, irq);
                    pc = VEC_JOY;
                    consumedCycles += 20;
                    return;
                }
            }
        }
    }

    // -----------------------------------------------------------
    // instructions
    private class InstrGen {

        CPU cpu;

        InstrGen(CPU cpu) {
            this.cpu = cpu;
        }

        // ----------------------------------------------------------
        // 8-bit loads
        Instr createLD8_RegAddr(final int reg, final int dreg) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    regs[reg] = mem.readByte(rd16reg(dreg));
                }
            };
        }

        Instr createLD8_AddrReg(final int dreg, final int reg) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    mem.writeByte(rd16reg(dreg), regs[reg]);
                }
            };
        }

        Instr createLD8_RegReg(final int reg1, final int reg2) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    regs[reg1] = regs[reg2];
                }
            };
        }

        Instr createLD8_RegImm(final int reg) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    regs[reg] = mem.readByte(pc++);
                }
            };
        }

        Instr createLD8_AddrImm(final int dreg) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    mem.writeByte(rd16reg(dreg), mem.readByte(pc++));
                }
            };
        }

        Instr createLD8_RegImAd(final int reg) {// immediate address
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    regs[reg] = mem.readByte(mem.read2Byte(pc));
                    pc += 2;
                }
            };
        }

        Instr createLD8_ImAdReg(final int reg) {// immediate address
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    mem.writeByte(mem.read2Byte(pc), regs[reg]);
                    pc += 2;
                }
            };
        }

        Instr createLD8_RegIOn(final int reg) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    regs[reg] = mem.readByte(0xFF00 + (mem.readByte(pc++) & 0xff));
                }
            };
        }

        Instr createLD8_IOnReg(final int reg) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    mem.writeByte(0xFF00 + (mem.readByte(pc++) & 0xff), regs[reg]);
                }
            };
        }

        Instr createLD8_RegIOc(final int reg) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    regs[reg] = mem.readByte(0xFF00 + (regs[RG_C] & 0xff));
                }
            };
        }

        Instr createLD8_IOcReg(final int reg) {
            return new Instr(cpu, "LDA  ") {
                @Override
                void compile() {
                    mem.writeByte(0xFF00 + (regs[RG_C] & 0xff), regs[reg]);
                }
            };
        }

        Instr createLD_ID_HLA(final boolean inc) {
            String name = (inc ? "LDI  " : "LDD  ");
            return new Instr(cpu, name) {
                @Override
                void compile() {
                    mem.writeByte(rd16reg(RG_HL), regs[RG_A]);
                    if (inc) {
                        inc16re(RG_HL);
                    } else {
                        dec16re(RG_HL);
                    }
                }
            };
        }

        Instr createLD_ID_AHL(final boolean inc) {
            String name = (inc ? "LDI  " : "LDD  ");
            return new Instr(cpu, name) {
                @Override
                void compile() {
                    regs[RG_A] = mem.readByte(rd16reg(RG_HL));
                    if (inc) {
                        inc16re(RG_HL);
                    } else {
                        dec16re(RG_HL);
                    }
                }
            };
        }

        // ----------------------------------------------------------
        // 16-bit loads
        Instr createLD16_RegImm(final int dreg) {
            return new Instr(cpu, "LD   ") {
                @Override
                void compile() {
                    wr16reg(dreg, mem.read2Byte(pc));
                    pc += 2;
                }
            };
        }

        Instr createLD16_SPHL() {
            return new Instr(cpu, "LD   ") {
                @Override
                void compile() {
                    sp = rd16reg(RG_HL);
                }
            };
        }

        Instr createLDHL16_SPn() {
            return new Instr(cpu, "LDHL ") {
                @Override
                void compile() {

                    byte n = mem.readByte(pc++);
                    wr16reg(RG_HL, (sp + n) & 0xffff);

                    int check = sp ^ (n) ^ ((sp + n) & 0xffff);
                    regs[RG_F] = 0;

                    if ((check & 0x100) == 0x100) {
                        regs[RG_F] |= C;
                    }
                    if ((check & 0x10) == 0x10) {
                        regs[RG_F] |= H;
                    }

                }
            };
        }

        Instr createLD16_ImmSP() {
            return new Instr(cpu, "LD   ") {
                @Override
                void compile() {
                    int n = mem.read2Byte(pc);
                    pc += 2;
                    mem.write2Byte(n, sp);
                }
            };
        }

        Instr createPUSH(final int dreg) {
            return new Instr(cpu, "PUSH ") {
                @Override
                void compile() {
                    push2(cpu.rd16reg(dreg));
                }
            };
        }

        Instr createPOP(final int dreg) {
            return new Instr(cpu, "POP  ") {
                @Override
                void compile() {
                    if (dreg != RG_AF) {
                        cpu.wr16reg(dreg, pop2());
                    } else {
                        cpu.wr16reg(RG_AF, pop2() & 0xFFF0);
                    }

                }
            };
        }

        // ----------------------------------------------------------
        // 8-bit-Arithmetic/logical commands
        Instr createADD(final int ldtype) {
            return new Instr(cpu, "ADD  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = 0;

                    if (((regs[RG_A] & 0xF) + (n & 0xF)) > 0xF) {
                        regs[RG_F] |= H;
                    }

                    if (((regs[RG_A] & 0xff) + (n & 0xff)) > 0xFF) {
                        regs[RG_F] |= C;
                    }

                    regs[RG_A] += (n & 0xff);

                    if (regs[RG_A] == 0) {
                        regs[RG_F] |= Z;
                    }
                }
            };
        }

        Instr createADC(final int ldtype) {
            return new Instr(cpu, "ADC  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);

                    int carry = (regs[RG_F] >> 4) & 1;
                    regs[RG_F] = 0;

                    if (((regs[RG_A] & 0xF) + (n & 0xF) + carry) > 0xF) {
                        regs[RG_F] |= H;
                    }

                    if (((regs[RG_A] & 0xff) + (n & 0xff) + carry) > 0xFF) {
                        regs[RG_F] |= C;
                    }

                    regs[RG_A] += (n & 0xff) + carry;

                    if (regs[RG_A] == 0) {
                        regs[RG_F] |= Z;
                    }
                }
            };
        }

        Instr createSUB(final int ldtype) {
            return new Instr(cpu, "SUB  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = 0;
                    regs[RG_F] |= N;

                    if (((regs[RG_A] & 0xF) < (n & 0xF))) {
                        regs[RG_F] |= H;
                    }

                    if ((regs[RG_A] & 0xff) < (n & 0xff)) {
                        regs[RG_F] |= C;
                    }

                    regs[RG_A] -= n;

                    if (regs[RG_A] == 0) {
                        regs[RG_F] |= Z;
                    }
                }
            };
        }

        Instr createSBC(final int ldtype) {
            return new Instr(cpu, "SBC  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    int un = n & 0xff;
                    byte tmpa = regs[RG_A];
                    int ua = regs[RG_A] & 0xff;
                    // regs[RG_F] = 0;
                    ua -= un;
                    ua -= (regs[RG_F] & C) == C ? 1 : 0;
                    regs[RG_F] = ua < 0 ? (byte) 0x50 : (byte) 0x40;
                    ua &= 0xff;
                    if (ua == 0) {
                        regs[RG_F] |= Z;
                    }
                    if (((ua ^ un ^ tmpa) & 0x10) == 0x10) {
                        regs[RG_F] |= H;
                    }

                    regs[RG_A] = (byte) ua;
                }
            };
        }

        Instr createAND(final int ldtype) {
            return new Instr(cpu, "AND  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = 0;
                    regs[RG_F] |= (H);
                    regs[RG_A] &= n;
                    if (regs[RG_A] == 0) {
                        regs[RG_F] |= Z;
                    }
                }
            };
        }

        Instr createOR(final int ldtype) {
            return new Instr(cpu, "OR   ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = 0;
                    regs[RG_A] |= n;
                    if (regs[RG_A] == 0) {
                        regs[RG_F] |= Z;
                    }
                }
            };
        }

        Instr createXOR(final int ldtype) {
            return new Instr(cpu, "XOR  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = 0;
                    regs[RG_A] = (byte) ((regs[RG_A] & 0xff) ^ (n & 0xff));
                    if (regs[RG_A] == 0) {
                        regs[RG_F] |= Z;
                    }
                }
            };
        }

        Instr createCP(final int ldtype) {
            return new Instr(cpu, "CP   ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = 0;
                    regs[RG_F] |= N;
                    if (regs[RG_A] == n) {
                        regs[RG_F] |= Z;
                    }
                    if ((regs[RG_A] & 0xF) < (n & 0xF)) {
                        regs[RG_F] |= H;
                    }
                    if ((regs[RG_A] & 0xff) < (n & 0xff)) {
                        regs[RG_F] |= C;
                    }
                }
            };
        }

        Instr createINC(final int ldtype) {
            return new Instr(cpu, "INC  ") {
                @Override
                void compile() {
                    regs[RG_F] &= C;
                    byte result = 0;
                    if (ldtype < 8) {// reg inc
                        result = ++regs[ldtype];
                    } else {// HL inc
                        int addr = rd16reg(RG_HL);
                        mem.writeByte(addr, (result = (byte) (mem.readByte(addr) + 1)));
                    }
                    if (result == 0) {
                        regs[RG_F] |= 0xA0;// Z and H flag
                    } else if ((result & 0xF) == 0) {
                        regs[RG_F] |= H;
                    }
                }
            };
        }

        Instr createDEC(final int ldtype) {
            return new Instr(cpu, "DEC  ") {
                @Override
                void compile() {
                    regs[RG_F] &= C;
                    regs[RG_F] |= N;
                    byte result = 0;
                    if (ldtype < 8) {// reg inc
                        result = --regs[ldtype];
                    } else {// HL inc
                        int addr = rd16reg(RG_HL);
                        mem.writeByte(addr, (result = (byte) (mem.readByte(addr) - 1)));
                    }
                    if (result == 0) {
                        regs[RG_F] |= Z;
                    }
                    if ((result & 0xF) == 0xF) {
                        regs[RG_F] |= H;
                    }
                }
            };
        }

        private byte ldByType(int reg) {// 0-7 is reg, 8 is immediate, 9 is (HL)
            if (reg < 8) {
                return regs[reg];
            } else if (reg == LDType.byImm) {
                return mem.readByte(pc++);
            } else if (reg == LDType.byHL) {
                return mem.readByte(rd16reg(RG_HL));
            } else {
                throw new RuntimeException("private method _load_ received value above 9");
            }
        }

        // ----------------------------------------------------------
        // 16-bit-Arithmetic/logical commands
        Instr createADD16(final int dreg) {
            return new Instr(cpu, "ADD  ") {
                @Override
                void compile() {
                    regs[RG_F] &= Z;
                    int n = rd16reg(dreg);

                    int hl = rd16reg(RG_HL);

                    if ((n & 0xFFF) + (hl & 0xFFF) > 0xFFF)// overflow 11
                    {
                        regs[RG_F] |= H;
                    }
                    hl += n;
                    if (hl > 0xFFFF) {
                        regs[RG_F] |= C;
                    }

                    wr16reg(RG_HL, hl);
                }
            };
        }

        Instr createADD16_SPn() {
            return new Instr(cpu, "ADD  ") {
                @Override
                void compile() {
                    regs[RG_F] = 0;
                    byte n = mem.readByte(pc++);// immediate

                    int check = sp ^ n ^ ((sp + n) & 0xffff);

                    sp = ((sp + n) & 0xffff);
                    regs[RG_F] = 0;

                    if ((check & 0x100) == 0x100) {
                        regs[RG_F] |= C;
                    }
                    if ((check & 0x10) == 0x10) {
                        regs[RG_F] |= H;
                    }
                }
            };
        }

        Instr createINC16(final int dreg) {
            return new Instr(cpu, "INC  ") {
                @Override
                void compile() {
                    inc16re(dreg);
                }
            };
        }

        Instr createDEC16(final int dreg) {
            return new Instr(cpu, "DEC  ") {
                @Override
                void compile() {
                    dec16re(dreg);
                }
            };
        }

        // ----------------------------------------------------------
        // MISC
        Instr createSWAP(final int ldtype) {
            return new Instr(cpu, "SWAP ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = 0;
                    n = (byte) ((n << 4 & 0xF0) | (n >> 4 & 0xF));
                    if (n == 0) {
                        regs[RG_F] |= Z;
                    }

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        Instr createDAA() {
            return new Instr(cpu, "DAA  ") {
                @Override
                void compile() {
                    // by blargg
                    byte flags = regs[RG_F];
                    int a = regs[RG_A] & 0xff;

                    if ((flags & N) == 0) {
                        if ((flags & H) == H || (a & 0x0F) > 9) {
                            a += 6;
                        }

                        if ((flags & C) == C || (a > 0x9F)) {
                            a += 0x60;
                        }
                    } else {
                        if ((flags & H) == H) {
                            a = (a - 6) & 0xff;
                        }

                        if ((flags & C) == C) {
                            a -= 0x60;
                        }
                    }

                    regs[RG_F] &= ~(H | Z);
                    if ((a & 0x100) == 0x100) {
                        regs[RG_F] |= C;
                    }

                    regs[RG_A] = (byte) a;
                    if (regs[RG_A] == 0) {
                        regs[RG_F] |= Z;
                    }
                }
            };
        }

        Instr createCPL() {
            return new Instr(cpu, "CPL  ") {
                @Override
                void compile() {
                    regs[RG_F] |= N;
                    regs[RG_F] |= H;
                    regs[RG_A] = (byte) ~regs[RG_A];
                }
            };
        }

        Instr createCCF() {
            return new Instr(cpu, "CCF  ") {
                @Override
                void compile() {
                    regs[RG_F] ^= C;
                    regs[RG_F] &= 0x90;// reset N and H
                }
            };
        }

        Instr createSCF() {
            return new Instr(cpu, "SCF  ") {
                @Override
                void compile() {
                    regs[RG_F] |= C;
                    regs[RG_F] &= 0x90;// reset N and H
                }
            };
        }

        Instr createNOP() {
            return new Instr(cpu, "NOP  ") {
                @Override
                void compile() {
                    // best instr
                }
            };
        }

        Instr createHALT() {
            return new Instr(cpu, "HALT ") {
                @Override
                void compile() {

                    if (ime) {
                        halt = true; //TODO: model halt bug properly
                    } else {
                        halt = true;
                    }
                }
            };
        }

        Instr createSTOP() {
            return new Instr(cpu, "STOP ") {
                @Override
                void compile() {
                    // TODO: stop CPU AND LCD till button pressed
                }
            };
        }

        Instr createDI() {
            return new Instr(cpu, "DI   ") {
                @Override
                void compile() {
                    ime = false;
                }
            };
        }

        Instr createEI() {
            return new Instr(cpu, "EI   ") {
                @Override
                void compile() {
                    ime = true;
                }
            };
        }

        // ----------------------------------------------------------
        // Rotates and shifts
        Instr createRLCA() {
            return new Instr(cpu, "RLCA ") {
                @Override
                void compile() {
                    regs[RG_F] = (byte) ((regs[RG_A] & 0x80) >> 3);// also
                    // clears Z,
                    // N, H
                    regs[RG_A] <<= 1;
                    regs[RG_A] |= (regs[RG_F] >> 4 & 1);
                    // if (regs[RG_A] == 0)//fixes it
                    // regs[RG_F] |= Z;
                }
            };
        }

        Instr createRLA() {
            return new Instr(cpu, "RLA   ") {
                @Override
                void compile() {
                    regs[RG_F] &= C;
                    regs[RG_F] |= regs[RG_A] >> 7 & 1;
                    regs[RG_A] <<= 1;
                    regs[RG_A] |= regs[RG_F] >> 4 & 1;
                    regs[RG_F] <<= 4;
                    // if (regs[RG_A] == 0)
                    // regs[RG_F] |= Z;
                }
            };
        }

        Instr createRRCA() {
            return new Instr(cpu, "RRCA ") {
                @Override
                void compile() {
                    regs[RG_F] = (byte) ((regs[RG_A] & 1) << 4);// bit 0 to
                    // carryflag
                    regs[RG_A] = (byte) ((regs[RG_A] & 0xff) >> 1);// java sucks
                    // at
                    // rightshifting
                    regs[RG_A] |= (regs[RG_F] << 3 & 0x80);// carry to bit 7
                    // if (regs[RG_A] == 0)
                    // regs[RG_F] |= Z;
                }
            };
        }

        Instr createRRA() {
            return new Instr(cpu, "RRA  ") {
                @Override
                void compile() {
                    regs[RG_F] &= C;
                    regs[RG_F] |= regs[RG_A] & 1;
                    regs[RG_A] = (byte) ((regs[RG_A] & 0xff) >> 1);
                    regs[RG_A] |= (regs[RG_F] & C) << 3; // bit 4 to 7
                    regs[RG_F] <<= 4; // bit 0 to carry
                    // if (regs[RG_A] == 0)
                    // regs[RG_F] |= Z;
                }
            };
        }

        Instr createRLC(final int ldtype) {
            return new Instr(cpu, "RLC  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = (byte) ((n & 0x80) >> 3);
                    n <<= 1;
                    n |= regs[RG_F] >> 4 & 1;
                    if (n == 0) {
                        regs[RG_F] |= Z;
                    }

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        Instr createRL(final int ldtype) {
            return new Instr(cpu, "RL   ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] &= C;
                    regs[RG_F] |= n >> 7 & 1;
                    n <<= 1;
                    n |= regs[RG_F] >> 4 & 1;
                    regs[RG_F] <<= 4;
                    if (n == 0) {
                        regs[RG_F] |= Z;
                    }

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        Instr createRRC(final int ldtype) {
            return new Instr(cpu, "RRC  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = (byte) ((n & 1) << 4);
                    n = (byte) ((n & 0xff) >> 1);
                    n |= regs[RG_F] << 3 & 0x80;
                    if (n == 0) {
                        regs[RG_F] |= Z;
                    }

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        Instr createRR(final int ldtype) {
            return new Instr(cpu, "RR   ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] &= C;
                    regs[RG_F] |= n & 1; // save to bit 0
                    n = (byte) ((n & 0xff) >> 1);
                    n |= (regs[RG_F] & C) << 3;
                    regs[RG_F] <<= 4;
                    if (n == 0) {
                        regs[RG_F] |= Z;
                    }

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        Instr createSLA(final int ldtype) {
            return new Instr(cpu, "SLA  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = (byte) ((n & 0x80) >> 3);
                    n <<= 1;
                    if (n == 0) {
                        regs[RG_F] |= Z;
                    }

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        Instr createSRA(final int ldtype) {
            return new Instr(cpu, "SRA  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = (byte) ((n & 1) << 4);
                    n = (byte) (((n & 0xff) >> 1) | (n & 0x80));// keep msb
                    if (n == 0) {
                        regs[RG_F] |= Z;
                    }

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        Instr createSRL(final int ldtype) {
            return new Instr(cpu, "SRL  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] = (byte) ((n & 1) << 4);
                    n = (byte) ((n & 0xff) >> 1);
                    if (n == 0) {
                        regs[RG_F] |= Z;
                    }

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        // -----------------------------------------------------------
        // BIT opcodes
        Instr createBIT(final int bit, final int ldtype) {
            return new Instr(cpu, "BIT  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    regs[RG_F] &= C;
                    regs[RG_F] |= H;
                    if ((n >> bit & 1) == 0) {
                        regs[RG_F] |= Z;
                    }
                }
            };
        }

        Instr createSET(final int bit, final int ldtype) {
            return new Instr(cpu, "SET  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    n |= (1 << bit);

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        Instr createRES(final int bit, final int ldtype) {
            return new Instr(cpu, "RES  ") {
                @Override
                void compile() {
                    byte n = ldByType(ldtype);
                    n &= ~(1 << bit);

                    if (ldtype == LDType.byHL) {
                        mem.writeByte(rd16reg(RG_HL), n);
                    } else {
                        regs[ldtype] = n;
                    }
                }
            };
        }

        // -----------------------------------------------------------
        // Jumps //TODO: adjust clocks
        Instr createJP(final int flag, final int condition) {
            return new Instr(cpu, "JP   ") {
                @Override
                void compile() {
                    int n = mem.read2Byte(pc);
                    pc += 2;
                    if ((regs[RG_F] & flag) == condition) {
                        pc = n;
                        if (flag != JMP_ALWAYS) {
                            consumedCycles += 4;
                        }
                    }
                }
            };
        }

        Instr createJPHL() {
            return new Instr(cpu, "JP   ") {
                @Override
                void compile() {
                    pc = rd16reg(RG_HL);
                }
            };
        }

        Instr createJR(final int flag, final int condition) {
            return new Instr(cpu, "JR   ") {
                @Override
                void compile() {
                    byte n = mem.readByte(pc++);
                    if ((regs[RG_F] & flag) == condition) {
                        pc += n;
                        if (flag != JMP_ALWAYS) {
                            consumedCycles += 4;
                        }
                    }
                }
            };
        }

        // -----------------------------------------------------------
        // CALLS //TODO: adjust clocks
        Instr createCALL(final int flag, final int condition) {
            return new Instr(cpu, "CALL ") {
                @Override
                void compile() {
                    int n = mem.read2Byte(pc);
                    pc += 2;
                    if ((regs[RG_F] & flag) == condition) {
                        push2(pc);
                        pc = n;
                        if (flag != JMP_ALWAYS) {
                            consumedCycles += 12;
                        }
                    }
                }
            };
        }

        // -----------------------------------------------------------
        // RESTARTS //TODO: adjust clocks
        Instr createRST(final int args) {
            return new Instr(cpu, "RST  ") {
                @Override
                void compile() {
                    push2(pc);
                    pc = args;
                }
            };
        }

        // -----------------------------------------------------------
        // RETURNS //TODO: adjust clocks
        Instr createRET(final int flag, final int condition) {
            return new Instr(cpu, "RET  ") {
                @Override
                void compile() {
                    if ((regs[RG_F] & flag) == condition) {
                        pc = pop2();
                        if (flag != JMP_ALWAYS) {
                            consumedCycles += 12;
                        }
                    }
                }
            };
        }

        Instr createRETI() {
            return new Instr(cpu, "RETI ") {
                @Override
                void compile() {
                    pc = pop2();
                    ime = true;
                }
            };
        }
    }

    // -----------------------------------------------------------
    // Utility classes and methods
    void wr16reg(int reg, int s) {
        if (reg == 8) {
            sp = s & 0xffff;// overflow control
        } else {
            regs[reg] = (byte) (s >> 8 & 0xff);
            regs[reg + 1] = (byte) (s & 0xff);
        }
    }

    int rd16reg(int reg) {
        return reg == 8 ? sp & 0xffff : (regs[reg] << 8 & 0xff00) | (regs[reg + 1] & 0xff);
    }

    void inc16re(int reg) {
        wr16reg(reg, rd16reg(reg) + 1);
    }

    void dec16re(int reg) {
        wr16reg(reg, rd16reg(reg) - 1);
    }

    void push2(int data) {
        sp = (sp - 1) & 0xffff;
        mem.writeByte(sp, (byte) ((data >> 8) & 0xff));
        sp = (sp - 1) & 0xffff;
        mem.writeByte(sp, (byte) (data & 0xff));

        // sp -= 2;
        // mem.write2Byte(sp, data);
        // mem.writeByte(sp - 1, (byte) ((data >> 8) & 0xff));
        // mem.writeByte(sp - 2, (byte) (data & 0xff));
        // sp -= 2;
    }

    int pop2() {
        // int data = mem.read2Byte(sp);
        // sp += 2;

        int data = mem.readByte(sp) & 0xff;
        sp = (sp + 1) & 0xffff;
        data |= ((mem.readByte(sp) & 0xff) << 8);
        sp = (sp + 1) & 0xffff;

        return data;
    }

    private class Instr {

        CPU cpu;
        String name;

        Instr(CPU cpu, String name) {
            this.cpu = cpu;
            this.name = name;
        }

        void compile() {

        }
    }

    private static class LDType {

        static int byImm = 9;
        static int byHL = 8;
    }

    private void generateInstructions() {
        InstrGen gen = new InstrGen(this);
        nInstr = new Instr[256];
        eInstr = new Instr[256];
        // ---------8-bit loads-----------
        // reg, imm
        nInstr[0x06] = gen.createLD8_RegImm(RG_B);
        nInstr[0x0E] = gen.createLD8_RegImm(RG_C);
        nInstr[0x16] = gen.createLD8_RegImm(RG_D);
        nInstr[0x1E] = gen.createLD8_RegImm(RG_E);
        nInstr[0x26] = gen.createLD8_RegImm(RG_H);
        nInstr[0x2E] = gen.createLD8_RegImm(RG_L);
        // reg, reg
        nInstr[0x7F] = gen.createLD8_RegReg(RG_A, RG_A);
        nInstr[0x78] = gen.createLD8_RegReg(RG_A, RG_B);
        nInstr[0x79] = gen.createLD8_RegReg(RG_A, RG_C);
        nInstr[0x7A] = gen.createLD8_RegReg(RG_A, RG_D);
        nInstr[0x7B] = gen.createLD8_RegReg(RG_A, RG_E);
        nInstr[0x7C] = gen.createLD8_RegReg(RG_A, RG_H);
        nInstr[0x7D] = gen.createLD8_RegReg(RG_A, RG_L);
        nInstr[0x7E] = gen.createLD8_RegAddr(RG_A, RG_HL);

        nInstr[0x40] = gen.createLD8_RegReg(RG_B, RG_B);
        nInstr[0x41] = gen.createLD8_RegReg(RG_B, RG_C);
        nInstr[0x42] = gen.createLD8_RegReg(RG_B, RG_D);
        nInstr[0x43] = gen.createLD8_RegReg(RG_B, RG_E);
        nInstr[0x44] = gen.createLD8_RegReg(RG_B, RG_H);
        nInstr[0x45] = gen.createLD8_RegReg(RG_B, RG_L);
        nInstr[0x46] = gen.createLD8_RegAddr(RG_B, RG_HL);

        nInstr[0x48] = gen.createLD8_RegReg(RG_C, RG_B);
        nInstr[0x49] = gen.createLD8_RegReg(RG_C, RG_C);
        nInstr[0x4A] = gen.createLD8_RegReg(RG_C, RG_D);
        nInstr[0x4B] = gen.createLD8_RegReg(RG_C, RG_E);
        nInstr[0x4C] = gen.createLD8_RegReg(RG_C, RG_H);
        nInstr[0x4D] = gen.createLD8_RegReg(RG_C, RG_L);
        nInstr[0x4E] = gen.createLD8_RegAddr(RG_C, RG_HL);

        nInstr[0x50] = gen.createLD8_RegReg(RG_D, RG_B);
        nInstr[0x51] = gen.createLD8_RegReg(RG_D, RG_C);
        nInstr[0x52] = gen.createLD8_RegReg(RG_D, RG_D);
        nInstr[0x53] = gen.createLD8_RegReg(RG_D, RG_E);
        nInstr[0x54] = gen.createLD8_RegReg(RG_D, RG_H);
        nInstr[0x55] = gen.createLD8_RegReg(RG_D, RG_L);
        nInstr[0x56] = gen.createLD8_RegAddr(RG_D, RG_HL);

        nInstr[0x58] = gen.createLD8_RegReg(RG_E, RG_B);
        nInstr[0x59] = gen.createLD8_RegReg(RG_E, RG_C);
        nInstr[0x5A] = gen.createLD8_RegReg(RG_E, RG_D);
        nInstr[0x5B] = gen.createLD8_RegReg(RG_E, RG_E);
        nInstr[0x5C] = gen.createLD8_RegReg(RG_E, RG_H);
        nInstr[0x5D] = gen.createLD8_RegReg(RG_E, RG_L);
        nInstr[0x5E] = gen.createLD8_RegAddr(RG_E, RG_HL);

        nInstr[0x60] = gen.createLD8_RegReg(RG_H, RG_B);
        nInstr[0x61] = gen.createLD8_RegReg(RG_H, RG_C);
        nInstr[0x62] = gen.createLD8_RegReg(RG_H, RG_D);
        nInstr[0x63] = gen.createLD8_RegReg(RG_H, RG_E);
        nInstr[0x64] = gen.createLD8_RegReg(RG_H, RG_H);
        nInstr[0x65] = gen.createLD8_RegReg(RG_H, RG_L);
        nInstr[0x66] = gen.createLD8_RegAddr(RG_H, RG_HL);

        nInstr[0x68] = gen.createLD8_RegReg(RG_L, RG_B);
        nInstr[0x69] = gen.createLD8_RegReg(RG_L, RG_C);
        nInstr[0x6A] = gen.createLD8_RegReg(RG_L, RG_D);
        nInstr[0x6B] = gen.createLD8_RegReg(RG_L, RG_E);
        nInstr[0x6C] = gen.createLD8_RegReg(RG_L, RG_H);
        nInstr[0x6D] = gen.createLD8_RegReg(RG_L, RG_L);
        nInstr[0x6E] = gen.createLD8_RegAddr(RG_L, RG_HL);

        nInstr[0x70] = gen.createLD8_AddrReg(RG_HL, RG_B);
        nInstr[0x71] = gen.createLD8_AddrReg(RG_HL, RG_C);
        nInstr[0x72] = gen.createLD8_AddrReg(RG_HL, RG_D);
        nInstr[0x73] = gen.createLD8_AddrReg(RG_HL, RG_E);
        nInstr[0x74] = gen.createLD8_AddrReg(RG_HL, RG_H);
        nInstr[0x75] = gen.createLD8_AddrReg(RG_HL, RG_L);
        nInstr[0x36] = gen.createLD8_AddrImm(RG_HL);

        nInstr[0x0A] = gen.createLD8_RegAddr(RG_A, RG_BC);
        nInstr[0x1A] = gen.createLD8_RegAddr(RG_A, RG_DE);
        nInstr[0x7E] = gen.createLD8_RegAddr(RG_A, RG_HL);
        nInstr[0xFA] = gen.createLD8_RegImAd(RG_A);
        nInstr[0x3E] = gen.createLD8_RegImm(RG_A);

        nInstr[0x47] = gen.createLD8_RegReg(RG_B, RG_A);
        nInstr[0x4F] = gen.createLD8_RegReg(RG_C, RG_A);
        nInstr[0x57] = gen.createLD8_RegReg(RG_D, RG_A);
        nInstr[0x5F] = gen.createLD8_RegReg(RG_E, RG_A);
        nInstr[0x67] = gen.createLD8_RegReg(RG_H, RG_A);
        nInstr[0x6F] = gen.createLD8_RegReg(RG_L, RG_A);
        nInstr[0x02] = gen.createLD8_AddrReg(RG_BC, RG_A);
        nInstr[0x12] = gen.createLD8_AddrReg(RG_DE, RG_A);
        nInstr[0x77] = gen.createLD8_AddrReg(RG_HL, RG_A);
        nInstr[0xEA] = gen.createLD8_ImAdReg(RG_A);

        nInstr[0xF2] = gen.createLD8_RegIOc(RG_A);

        nInstr[0xE2] = gen.createLD8_IOcReg(RG_A);

        nInstr[0x3A] = gen.createLD_ID_AHL(false);// dec

        nInstr[0x32] = gen.createLD_ID_HLA(false);// dec

        nInstr[0x2A] = gen.createLD_ID_AHL(true);// inc

        nInstr[0x22] = gen.createLD_ID_HLA(true);// inc

        nInstr[0xE0] = gen.createLD8_IOnReg(RG_A);

        nInstr[0xF0] = gen.createLD8_RegIOn(RG_A);

        // ---------16-bit loads-----------
        nInstr[0x01] = gen.createLD16_RegImm(RG_BC);
        nInstr[0x11] = gen.createLD16_RegImm(RG_DE);
        nInstr[0x21] = gen.createLD16_RegImm(RG_HL);
        nInstr[0x31] = gen.createLD16_RegImm(RG_SP);

        nInstr[0xF9] = gen.createLD16_SPHL();

        nInstr[0xF8] = gen.createLDHL16_SPn();

        nInstr[0x08] = gen.createLD16_ImmSP();

        nInstr[0xF5] = gen.createPUSH(RG_AF);
        nInstr[0xC5] = gen.createPUSH(RG_BC);
        nInstr[0xD5] = gen.createPUSH(RG_DE);
        nInstr[0xE5] = gen.createPUSH(RG_HL);

        nInstr[0xF1] = gen.createPOP(RG_AF);
        nInstr[0xC1] = gen.createPOP(RG_BC);
        nInstr[0xD1] = gen.createPOP(RG_DE);
        nInstr[0xE1] = gen.createPOP(RG_HL);

        // ---------8-BIT ALU -----------
        nInstr[0x87] = gen.createADD(RG_A);
        nInstr[0x80] = gen.createADD(RG_B);
        nInstr[0x81] = gen.createADD(RG_C);
        nInstr[0x82] = gen.createADD(RG_D);
        nInstr[0x83] = gen.createADD(RG_E);
        nInstr[0x84] = gen.createADD(RG_H);
        nInstr[0x85] = gen.createADD(RG_L);
        nInstr[0x86] = gen.createADD(LDType.byHL);
        nInstr[0xC6] = gen.createADD(LDType.byImm);

        nInstr[0x8F] = gen.createADC(RG_A);
        nInstr[0x88] = gen.createADC(RG_B);
        nInstr[0x89] = gen.createADC(RG_C);
        nInstr[0x8A] = gen.createADC(RG_D);
        nInstr[0x8B] = gen.createADC(RG_E);
        nInstr[0x8C] = gen.createADC(RG_H);
        nInstr[0x8D] = gen.createADC(RG_L);
        nInstr[0x8E] = gen.createADC(LDType.byHL);
        nInstr[0xCE] = gen.createADC(LDType.byImm);

        nInstr[0x97] = gen.createSUB(RG_A);
        nInstr[0x90] = gen.createSUB(RG_B);
        nInstr[0x91] = gen.createSUB(RG_C);
        nInstr[0x92] = gen.createSUB(RG_D);
        nInstr[0x93] = gen.createSUB(RG_E);
        nInstr[0x94] = gen.createSUB(RG_H);
        nInstr[0x95] = gen.createSUB(RG_L);
        nInstr[0x96] = gen.createSUB(LDType.byHL);
        nInstr[0xD6] = gen.createSUB(LDType.byImm);

        nInstr[0x9F] = gen.createSBC(RG_A);
        nInstr[0x98] = gen.createSBC(RG_B);
        nInstr[0x99] = gen.createSBC(RG_C);
        nInstr[0x9A] = gen.createSBC(RG_D);
        nInstr[0x9B] = gen.createSBC(RG_E);
        nInstr[0x9C] = gen.createSBC(RG_H);
        nInstr[0x9D] = gen.createSBC(RG_L);
        nInstr[0x9E] = gen.createSBC(LDType.byHL);
        nInstr[0xDE] = gen.createSBC(LDType.byImm); // unknown

        nInstr[0xA7] = gen.createAND(RG_A);
        nInstr[0xA0] = gen.createAND(RG_B);
        nInstr[0xA1] = gen.createAND(RG_C);
        nInstr[0xA2] = gen.createAND(RG_D);
        nInstr[0xA3] = gen.createAND(RG_E);
        nInstr[0xA4] = gen.createAND(RG_H);
        nInstr[0xA5] = gen.createAND(RG_L);
        nInstr[0xA6] = gen.createAND(LDType.byHL);
        nInstr[0xE6] = gen.createAND(LDType.byImm);

        nInstr[0xB7] = gen.createOR(RG_A);
        nInstr[0xB0] = gen.createOR(RG_B);
        nInstr[0xB1] = gen.createOR(RG_C);
        nInstr[0xB2] = gen.createOR(RG_D);
        nInstr[0xB3] = gen.createOR(RG_E);
        nInstr[0xB4] = gen.createOR(RG_H);
        nInstr[0xB5] = gen.createOR(RG_L);
        nInstr[0xB6] = gen.createOR(LDType.byHL);
        nInstr[0xF6] = gen.createOR(LDType.byImm);

        nInstr[0xAF] = gen.createXOR(RG_A);
        nInstr[0xA8] = gen.createXOR(RG_B);
        nInstr[0xA9] = gen.createXOR(RG_C);
        nInstr[0xAA] = gen.createXOR(RG_D);
        nInstr[0xAB] = gen.createXOR(RG_E);
        nInstr[0xAC] = gen.createXOR(RG_H);
        nInstr[0xAD] = gen.createXOR(RG_L);
        nInstr[0xAE] = gen.createXOR(LDType.byHL);
        nInstr[0xEE] = gen.createXOR(LDType.byImm);

        nInstr[0xBF] = gen.createCP(RG_A);
        nInstr[0xB8] = gen.createCP(RG_B);
        nInstr[0xB9] = gen.createCP(RG_C);
        nInstr[0xBA] = gen.createCP(RG_D);
        nInstr[0xBB] = gen.createCP(RG_E);
        nInstr[0xBC] = gen.createCP(RG_H);
        nInstr[0xBD] = gen.createCP(RG_L);
        nInstr[0xBE] = gen.createCP(LDType.byHL);
        nInstr[0xFE] = gen.createCP(LDType.byImm);

        nInstr[0x3C] = gen.createINC(RG_A);
        nInstr[0x04] = gen.createINC(RG_B);
        nInstr[0x0C] = gen.createINC(RG_C);
        nInstr[0x14] = gen.createINC(RG_D);
        nInstr[0x1C] = gen.createINC(RG_E);
        nInstr[0x24] = gen.createINC(RG_H);
        nInstr[0x2C] = gen.createINC(RG_L);
        nInstr[0x34] = gen.createINC(LDType.byHL);

        nInstr[0x3D] = gen.createDEC(RG_A);
        nInstr[0x05] = gen.createDEC(RG_B);
        nInstr[0x0D] = gen.createDEC(RG_C);
        nInstr[0x15] = gen.createDEC(RG_D);
        nInstr[0x1D] = gen.createDEC(RG_E);
        nInstr[0x25] = gen.createDEC(RG_H);
        nInstr[0x2D] = gen.createDEC(RG_L);
        nInstr[0x35] = gen.createDEC(LDType.byHL);

        // ---------16-BIT artihmetic -----------
        nInstr[0x09] = gen.createADD16(RG_BC);
        nInstr[0x19] = gen.createADD16(RG_DE);
        nInstr[0x29] = gen.createADD16(RG_HL);
        nInstr[0x39] = gen.createADD16(RG_SP);

        nInstr[0xE8] = gen.createADD16_SPn();

        nInstr[0x03] = gen.createINC16(RG_BC);
        nInstr[0x13] = gen.createINC16(RG_DE);
        nInstr[0x23] = gen.createINC16(RG_HL);
        nInstr[0x33] = gen.createINC16(RG_SP);

        nInstr[0x0B] = gen.createDEC16(RG_BC);
        nInstr[0x1B] = gen.createDEC16(RG_DE);
        nInstr[0x2B] = gen.createDEC16(RG_HL);
        nInstr[0x3B] = gen.createDEC16(RG_SP);

        // ---------MISC -----------
        eInstr[0x37] = gen.createSWAP(RG_A);
        eInstr[0x30] = gen.createSWAP(RG_B);
        eInstr[0x31] = gen.createSWAP(RG_C);
        eInstr[0x32] = gen.createSWAP(RG_D);
        eInstr[0x33] = gen.createSWAP(RG_E);
        eInstr[0x34] = gen.createSWAP(RG_H);
        eInstr[0x35] = gen.createSWAP(RG_L);
        eInstr[0x36] = gen.createSWAP(LDType.byHL);

        nInstr[0x27] = gen.createDAA();

        nInstr[0x2F] = gen.createCPL();

        nInstr[0x3F] = gen.createCCF();

        nInstr[0x37] = gen.createSCF();

        nInstr[0x00] = gen.createNOP();

        nInstr[0x76] = gen.createHALT();

        nInstr[0x10] = gen.createSTOP();

        nInstr[0xF3] = gen.createDI();

        nInstr[0xFB] = gen.createEI();

        // ---------ROTATES AND SHIFTS -----------
        nInstr[0x07] = gen.createRLCA();

        nInstr[0x17] = gen.createRLA();

        nInstr[0x0F] = gen.createRRCA();

        nInstr[0x1F] = gen.createRRA();
        // --------------------------------------
        // extended OP codes
        // -----------------------------------------
        eInstr[0x07] = gen.createRLC(RG_A);
        eInstr[0x00] = gen.createRLC(RG_B);
        eInstr[0x01] = gen.createRLC(RG_C);
        eInstr[0x02] = gen.createRLC(RG_D);
        eInstr[0x03] = gen.createRLC(RG_E);
        eInstr[0x04] = gen.createRLC(RG_H);
        eInstr[0x05] = gen.createRLC(RG_L);
        eInstr[0x06] = gen.createRLC(LDType.byHL);

        eInstr[0x17] = gen.createRL(RG_A);
        eInstr[0x10] = gen.createRL(RG_B);
        eInstr[0x11] = gen.createRL(RG_C);
        eInstr[0x12] = gen.createRL(RG_D);
        eInstr[0x13] = gen.createRL(RG_E);
        eInstr[0x14] = gen.createRL(RG_H);
        eInstr[0x15] = gen.createRL(RG_L);
        eInstr[0x16] = gen.createRL(LDType.byHL);

        eInstr[0x0F] = gen.createRRC(RG_A);
        eInstr[0x08] = gen.createRRC(RG_B);
        eInstr[0x09] = gen.createRRC(RG_C);
        eInstr[0x0A] = gen.createRRC(RG_D);
        eInstr[0x0B] = gen.createRRC(RG_E);
        eInstr[0x0C] = gen.createRRC(RG_H);
        eInstr[0x0D] = gen.createRRC(RG_L);
        eInstr[0x0E] = gen.createRRC(LDType.byHL);

        eInstr[0x1F] = gen.createRR(RG_A);
        eInstr[0x18] = gen.createRR(RG_B);
        eInstr[0x19] = gen.createRR(RG_C);
        eInstr[0x1A] = gen.createRR(RG_D);
        eInstr[0x1B] = gen.createRR(RG_E);
        eInstr[0x1C] = gen.createRR(RG_H);
        eInstr[0x1D] = gen.createRR(RG_L);
        eInstr[0x1E] = gen.createRR(LDType.byHL);

        eInstr[0x27] = gen.createSLA(RG_A);
        eInstr[0x20] = gen.createSLA(RG_B);
        eInstr[0x21] = gen.createSLA(RG_C);
        eInstr[0x22] = gen.createSLA(RG_D);
        eInstr[0x23] = gen.createSLA(RG_E);
        eInstr[0x24] = gen.createSLA(RG_H);
        eInstr[0x25] = gen.createSLA(RG_L);
        eInstr[0x26] = gen.createSLA(LDType.byHL);

        eInstr[0x2F] = gen.createSRA(RG_A);
        eInstr[0x28] = gen.createSRA(RG_B);
        eInstr[0x29] = gen.createSRA(RG_C);
        eInstr[0x2A] = gen.createSRA(RG_D);
        eInstr[0x2B] = gen.createSRA(RG_E);
        eInstr[0x2C] = gen.createSRA(RG_H);
        eInstr[0x2D] = gen.createSRA(RG_L);
        eInstr[0x2E] = gen.createSRA(LDType.byHL);

        eInstr[0x3F] = gen.createSRL(RG_A);
        eInstr[0x38] = gen.createSRL(RG_B);
        eInstr[0x39] = gen.createSRL(RG_C);
        eInstr[0x3A] = gen.createSRL(RG_D);
        eInstr[0x3B] = gen.createSRL(RG_E);
        eInstr[0x3C] = gen.createSRL(RG_H);
        eInstr[0x3D] = gen.createSRL(RG_L);
        eInstr[0x3E] = gen.createSRL(LDType.byHL);

        // BIT
        for (int i = 0; i < 4; i++) {
            eInstr[0x40 + 0x10 * i] = gen.createBIT(0 + 2 * i, RG_B);
            eInstr[0x41 + 0x10 * i] = gen.createBIT(0 + 2 * i, RG_C);
            eInstr[0x42 + 0x10 * i] = gen.createBIT(0 + 2 * i, RG_D);
            eInstr[0x43 + 0x10 * i] = gen.createBIT(0 + 2 * i, RG_E);
            eInstr[0x44 + 0x10 * i] = gen.createBIT(0 + 2 * i, RG_H);
            eInstr[0x45 + 0x10 * i] = gen.createBIT(0 + 2 * i, RG_L);
            eInstr[0x46 + 0x10 * i] = gen.createBIT(0 + 2 * i, LDType.byHL);
            eInstr[0x47 + 0x10 * i] = gen.createBIT(0 + 2 * i, RG_A);
        }

        for (int i = 0; i < 4; i++) {
            eInstr[0x48 + 0x10 * i] = gen.createBIT(1 + 2 * i, RG_B);
            eInstr[0x49 + 0x10 * i] = gen.createBIT(1 + 2 * i, RG_C);
            eInstr[0x4A + 0x10 * i] = gen.createBIT(1 + 2 * i, RG_D);
            eInstr[0x4B + 0x10 * i] = gen.createBIT(1 + 2 * i, RG_E);
            eInstr[0x4C + 0x10 * i] = gen.createBIT(1 + 2 * i, RG_H);
            eInstr[0x4D + 0x10 * i] = gen.createBIT(1 + 2 * i, RG_L);
            eInstr[0x4E + 0x10 * i] = gen.createBIT(1 + 2 * i, LDType.byHL);
            eInstr[0x4F + 0x10 * i] = gen.createBIT(1 + 2 * i, RG_A);
        }

        // RES
        for (int i = 0; i < 4; i++) {
            eInstr[0x80 + 0x10 * i] = gen.createRES(0 + 2 * i, RG_B);
            eInstr[0x81 + 0x10 * i] = gen.createRES(0 + 2 * i, RG_C);
            eInstr[0x82 + 0x10 * i] = gen.createRES(0 + 2 * i, RG_D);
            eInstr[0x83 + 0x10 * i] = gen.createRES(0 + 2 * i, RG_E);
            eInstr[0x84 + 0x10 * i] = gen.createRES(0 + 2 * i, RG_H);
            eInstr[0x85 + 0x10 * i] = gen.createRES(0 + 2 * i, RG_L);
            eInstr[0x86 + 0x10 * i] = gen.createRES(0 + 2 * i, LDType.byHL);
            eInstr[0x87 + 0x10 * i] = gen.createRES(0 + 2 * i, RG_A);
        }

        for (int i = 0; i < 4; i++) {
            eInstr[0x88 + 0x10 * i] = gen.createRES(1 + 2 * i, RG_B);
            eInstr[0x89 + 0x10 * i] = gen.createRES(1 + 2 * i, RG_C);
            eInstr[0x8A + 0x10 * i] = gen.createRES(1 + 2 * i, RG_D);
            eInstr[0x8B + 0x10 * i] = gen.createRES(1 + 2 * i, RG_E);
            eInstr[0x8C + 0x10 * i] = gen.createRES(1 + 2 * i, RG_H);
            eInstr[0x8D + 0x10 * i] = gen.createRES(1 + 2 * i, RG_L);
            eInstr[0x8E + 0x10 * i] = gen.createRES(1 + 2 * i, LDType.byHL);
            eInstr[0x8F + 0x10 * i] = gen.createRES(1 + 2 * i, RG_A);
        }

        // SET
        for (int i = 0; i < 4; i++) {
            eInstr[0xC0 + 0x10 * i] = gen.createSET(0 + 2 * i, RG_B);
            eInstr[0xC1 + 0x10 * i] = gen.createSET(0 + 2 * i, RG_C);
            eInstr[0xC2 + 0x10 * i] = gen.createSET(0 + 2 * i, RG_D);
            eInstr[0xC3 + 0x10 * i] = gen.createSET(0 + 2 * i, RG_E);
            eInstr[0xC4 + 0x10 * i] = gen.createSET(0 + 2 * i, RG_H);
            eInstr[0xC5 + 0x10 * i] = gen.createSET(0 + 2 * i, RG_L);
            eInstr[0xC6 + 0x10 * i] = gen.createSET(0 + 2 * i, LDType.byHL);
            eInstr[0xC7 + 0x10 * i] = gen.createSET(0 + 2 * i, RG_A);
        }

        for (int i = 0; i < 4; i++) {
            eInstr[0xC8 + 0x10 * i] = gen.createSET(1 + 2 * i, RG_B);
            eInstr[0xC9 + 0x10 * i] = gen.createSET(1 + 2 * i, RG_C);
            eInstr[0xCA + 0x10 * i] = gen.createSET(1 + 2 * i, RG_D);
            eInstr[0xCB + 0x10 * i] = gen.createSET(1 + 2 * i, RG_E);
            eInstr[0xCC + 0x10 * i] = gen.createSET(1 + 2 * i, RG_H);
            eInstr[0xCD + 0x10 * i] = gen.createSET(1 + 2 * i, RG_L);
            eInstr[0xCE + 0x10 * i] = gen.createSET(1 + 2 * i, LDType.byHL);
            eInstr[0xCF + 0x10 * i] = gen.createSET(1 + 2 * i, RG_A);
        }

        // ---------JUMPS -----------
        nInstr[0xC3] = gen.createJP(JMP_ALWAYS, JMP_ALWAYS);

        nInstr[0xC2] = gen.createJP(Z, JMP_NZ); //differentiate between taken/not taken
        nInstr[0xCA] = gen.createJP(Z, JMP_Z); //differentiate between taken/not taken
        nInstr[0xD2] = gen.createJP(C, JMP_NC);//differentiate between taken/not taken
        nInstr[0xDA] = gen.createJP(C, JMP_C);//differentiate between taken/not taken

        nInstr[0xE9] = gen.createJPHL();

        nInstr[0x18] = gen.createJR(JMP_ALWAYS, JMP_ALWAYS);

        nInstr[0x20] = gen.createJR(Z, JMP_NZ);//differentiate between taken/not taken
        nInstr[0x28] = gen.createJR(Z, JMP_Z);//differentiate between taken/not taken
        nInstr[0x30] = gen.createJR(C, JMP_NC);//differentiate between taken/not taken
        nInstr[0x38] = gen.createJR(C, JMP_C);//differentiate between taken/not taken

        nInstr[0xCD] = gen.createCALL(JMP_ALWAYS, JMP_ALWAYS);

        nInstr[0xC4] = gen.createCALL(Z, JMP_NZ);//3 differentiate between taken/not taken
        nInstr[0xCC] = gen.createCALL(Z, JMP_Z);//3 differentiate between taken/not taken
        nInstr[0xD4] = gen.createCALL(C, JMP_NC);//3 differentiate between taken/not taken
        nInstr[0xDC] = gen.createCALL(C, JMP_C);//3 differentiate between taken/not taken

        // ---------RESTARTS -----------
        nInstr[0xC7] = gen.createRST(0x00);
        nInstr[0xCF] = gen.createRST(0x08);
        nInstr[0xD7] = gen.createRST(0x10);
        nInstr[0xDF] = gen.createRST(0x18);
        nInstr[0xE7] = gen.createRST(0x20);
        nInstr[0xEF] = gen.createRST(0x28);
        nInstr[0xF7] = gen.createRST(0x30);
        nInstr[0xFF] = gen.createRST(0x38);

        // ---------RETURNS -----------
        nInstr[0xC9] = gen.createRET(JMP_ALWAYS, JMP_ALWAYS);

        nInstr[0xC0] = gen.createRET(Z, JMP_NZ);//3 differentiate between taken/not taken 
        nInstr[0xC8] = gen.createRET(Z, JMP_Z);//3 differentiate between taken/not taken
        nInstr[0xD0] = gen.createRET(C, JMP_NC);//3 differentiate between taken/not taken
        nInstr[0xD8] = gen.createRET(C, JMP_C);//3 differentiate between taken/not taken

        nInstr[0xD9] = gen.createRETI();

    }
}
