/*******************************************************************************
 *     <A simple gameboy emulator>
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package ch.gb.gpu;

import ch.gb.Component;
import ch.gb.GB;
import ch.gb.GBComponents;
import ch.gb.cpu.CPU;
import ch.gb.mem.Memory;
import ch.gb.utils.Utils;

public class GPU implements Component {

    public static final int LCD_C = 0xFF40;
    public static final int STAT = 0xFF41;

    public static final int SCY = 0xFF42;
    public static final int SCX = 0xFF43;

    public static final int LY = 0xFF44;
    public static final int LYC = 0xFF45;

    public static final int WY = 0xFF4A;
    public static final int WX = 0xFF4B;

    public static final int BGP = 0xFF47;
    public static final int OBP0 = 0xFF48;
    public static final int OBP1 = 0xFF49;

    private byte lcdc;
    private boolean lcdEnabled;
    private int windowTilemap;
    private boolean windowEnable;
    private int bgWiTiledata;
    private int bgTilemap;
    private boolean spr8x16;
    private boolean sprEnable;
    private boolean bgEnable;

    private byte stat;
    private int mode;
    private int coincidence;

    private int scy;
    private int scx;

    private int ly;// scanlinecounter
    private int lyc;

    private int wx;
    private int wy;

    private byte bgpraw;
    private byte obp0raw;
    private byte obp1raw;
    private final byte[] bgp = {0, 0, 0, 0};
    private final byte[][] obp = {{0, 0, 0, 0}, {0, 0, 0, 0}};
	// FORMAT: RGBA 10er hex
    // dark green: 015;056;015 -> 0xF , 0x38, 0xF
    // green : 048;098;048 -> 0x30, 0x62, 0x30
    // bright grn: 139;172;015 -> 0x8B, 0xAC, 0xF
    // brgter grn: 155;188;015 -> 0x9B, 0xBC, 0xF
    // brighter, bright, green, darkgreen
    // private final int[] palette = { 0x9BBC0FFF, 0x8BAC0FFF,
    // 0x306230FF,0x0F380FFF };//wiki
    // private final int[] palette = { 0xE0f8d0ff, 0x88C070ff, 0x346856,
    // 0x081820ff };//BGP
    private final int[] palette = {0xE8E8E8FF, 0xA0A0A0FF, 0x585858FF, 0x101010FF};// b/w
    private int scanlinecyc = 456;

    private final GB emu;
    private Memory mem;

    public int[][] videobuffer;

    public GPU(GB emu) {
        this.emu = emu;
        videobuffer = new int[160][144];
    }

    @Override
    public void reset() {
        write(LCD_C, (byte) 0);
        stat = 0;
        mode = 0;
        coincidence = 0;
        scy = 0;
        scx = 0;
        ly = 0;
        lyc = 0;
        wx = 0;
        wy = 0;
        bgpraw = 0;
        obp0raw = 0;
        obp1raw = 0;
        for (int i = 0; i < 4; i++) {
            bgp[i] = 0;
            obp[0][i] = 0;
            obp[1][i] = 0;
        }
        for (int y = 0; y < 144; y++) {
            for (int x = 0; x < 160; x++) {
                videobuffer[x][y] = 0;
            }
        }
    }

    @Override
    public void connect(GBComponents comps) {
        this.mem = comps.mem;

    }

    public void write(int add, byte b) {
        if (add == LCD_C) {
            lcdc = b;
            lcdEnabled = (b & 0x80) == 0x80;
            windowTilemap = (b & 0x40) == 0x40 ? 0x9C00 : 0x9800;// nametable
            windowEnable = (b & 0x20) == 0x20;

            bgWiTiledata = (b & 0x10) == 0x10 ? 0x8000 : 0x9000;// patterns

            bgTilemap = (b & 8) == 8 ? 0x9C000 : 0x9800;// nametable
            spr8x16 = (b & 4) == 4;
            sprEnable = (b & 2) == 2;
            bgEnable = (b & 1) == 1;
        } else if (add == STAT) {
            b &= 0x78; // clear lower 3 bits and 7
            stat &= 87;// clear 6-3
            stat |= b;

        } else if (add == SCX) {
            scx = b & 0xff;
        } else if (add == SCY) {
            scy = b & 0xff;
        } else if (add == LY) {
            ly = 0;
        } else if (add == LYC) {
            lyc = b & 0xff;
        } else if (add == WY) {
            wy = b & 0xff;
        } else if (add == WX) {
            wx = (b & 0xff) - 7;
        } else if (add == BGP) {
            bgpraw = b;
            bgp[0] = (byte) (b & 3);
            bgp[1] = (byte) (b >> 2 & 3);
            bgp[2] = (byte) (b >> 4 & 3);
            bgp[3] = (byte) (b >> 6 & 3);
        } else if (add == OBP0) {
            obp0raw = b;
            obp[0][0] = (byte) (b & 3);
            obp[0][1] = (byte) (b >> 2 & 3);
            obp[0][2] = (byte) (b >> 4 & 3);
            obp[0][3] = (byte) (b >> 6 & 3);
        } else if (add == OBP1) {
            obp1raw = b;
            obp[1][0] = (byte) (b & 3);
            obp[1][1] = (byte) (b >> 2 & 3);
            obp[1][2] = (byte) (b >> 4 & 3);
            obp[1][3] = (byte) (b >> 6 & 3);
        }

    }

    public byte read(int add) {
        if (add == LCD_C) {
            return lcdc;
        } else if (add == STAT) {
            return (byte) (stat | (coincidence << 2 & 4) | mode & 3);
        } else if (add == SCX) {
            return (byte) scx;
        } else if (add == SCY) {
            return (byte) scy;
        } else if (add == LY) {
            return (byte) ly;
        } else if (add == LYC) {
            return (byte) lyc;
        } else if (add == WY) {
            return (byte) wy;
        } else if (add == WX) {
            return (byte) wx;
        } else if (add == BGP) {
            return bgpraw;
        } else if (add == OBP0) {
            return obp0raw;
        } else if (add == OBP1) {
            return obp1raw;
        } else {
            throw new RuntimeException("GPU->couldnt decode address:" + Utils.dumpHex(add) + " (Read)");
        }
    }

    /**
     * 160x144 pixels to draw
     *
     * @param cpucycles
     */
    public void clock(int cpucycles) {

        if (!lcdEnabled) {
            scanlinecyc = 456;
            ly = 0;
            mode = 1;
        } else {
            int oldMode = mode;
			// mode 0: 204 cycles
            // mode 1:4560 cycles
            // mode 2: 80 cycles
            // mode 3: 172 cycles

            if (ly >= 144) {
                mode = 1;
            } else if (scanlinecyc >= 456 - 80) { // counting downwards!
                mode = 2;
            } else if (scanlinecyc >= 456 - 80 - 172) {
                mode = 3;
            } else {
                mode = 0;
            }
			// request interrupt if entered a new mode and if interrupt flag is
            // set
            // (mode 3 has no interrupt)
            if (mode != 3 && oldMode != mode && (((stat >> (3 + mode)) & 1) == 1)) {
                mem.requestInterrupt(CPU.LCD_IR);
            }

            // coincidence flag
            coincidence = 0;
            if (ly == lyc && (stat & 0x40) == 0x40) {
                mem.requestInterrupt(CPU.LCD_IR);// TODO: this gets spammed
                coincidence = 1;
            }
        }
        if (!lcdEnabled) {
            return;
        }

        scanlinecyc -= cpucycles;
        if (scanlinecyc <= 0) {
            scanlinecyc = 456 + scanlinecyc; // adjust if taken too many
            ly++;

            // VBlank?
            if (ly == 144) {
                mem.requestInterrupt(CPU.VBLANK_IR);
                emu.flushScreen();// flush data into video driver
            }
            if (ly > 153) {
                ly = 0;
            }

            // draw renderscanline
            if (ly < 144) {
                if (bgEnable) {
                    drawBg();
                }
                if (sprEnable) {
                    drawSpr();
                }
            }
        }
    }

    public void drawBg() { // bg and window scanline
        int y = ly + scy;// which scanline in the tilemap
        y = y % 256;// wrap around bg map

        int bgInTiley = y % 8 * 2;
        int wiInTiley = ((ly - wy) % 8) * 2;

        boolean signed = bgWiTiledata == 0x9000;
        int bgEntry = bgTilemap + y / 8 * 32;// 32 tiles per nametablerow
        int wiEntry = windowTilemap + (ly - wy) / 8 * 32;
        // first check wether this can be a window scanline
        boolean canWindow = windowEnable && wy <= ly;
        int targetTilemap = bgEntry;
        int targetTiley = bgInTiley;
        for (int x = 0; x < 160; x++) {

            int tx = (x + scx) & 0xff;

            if (x >= wx && canWindow) {
                // once switched not possible anymore to switch back to bgEntry
                targetTilemap = wiEntry;
                tx = x - wx;// to window space
                targetTiley = wiInTiley;

            }

            // fetch namtable byte
            byte tileid = mem.readByte(targetTilemap + tx / 8);

            int tileloc = bgWiTiledata + (signed ? (int) tileid : tileid & 0xff) * 16;

            // fetch tile pattern
            byte lo = mem.readByte(tileloc + targetTiley);
            byte hi = mem.readByte(tileloc + targetTiley + 1);

            int intilex = tx % 8;

            int color = palette[bgp[(lo >> (7 - intilex) & 1) | ((hi >> (7 - intilex) & 1) << 1)]];
            videobuffer[x][ly] = color;
        }

    }

    public void drawSpr() {

        for (int i = 0; i < 40; i++) {
            int ypos = (mem.readByte(0xFE00 + i * 4) & 0xff) - 16;
            int xpos = (mem.readByte(0xFE00 + i * 4 + 1) & 0xff) - 8;
            int tileid = (mem.readByte(0xFE00 + i * 4 + 2) & 0xff);
            byte attr = mem.readByte(0xFE00 + i * 4 + 3);

            // 8x8 mode
            int priority = (attr >> 7) & 1;// TODO: not yet implemented
            int yflip = (attr >> 6) & 1;
            int xflip = (attr >> 5) & 1;

            int pal = (attr >> 4) & 1;
            int size = spr8x16 ? 16 : 8; // 16 mode works

            // clipping
            if (ly >= ypos && ly < (ypos + size)) {
                int line = (ly - ypos);
                line = yflip == 1 ? (spr8x16 ? 15 : 7) - line : line;

                int patternentry = 0x8000 + tileid * 16 + line * 2;
                byte lo = mem.readByte(patternentry);
                byte hi = mem.readByte(patternentry + 1);

                for (int x = 0; x < 8; x++) {

                    int newx = (xflip == 1 ? x : 7 - x);
                    int tx = xpos + x;
                    // TODO:wrong since only consider bg[0], not assigned color
                    if (tx < 0 || tx >= 160) {
                        continue;
                    }

                    if (priority == 1 && videobuffer[tx][ly] != palette[bgp[0]]) {
                        continue;
                    }

                    int index = (lo >> (newx)) & 1 | ((hi >> (newx)) & 1) << 1;
                    int palcolor = obp[pal][index];
                    if (index != 0)// spr 3colors
                    {
                        videobuffer[tx][ly] = palette[palcolor];
                    }

                }
            }
        }
    }

    public int[] get8spr(int line, byte sprid, byte attr) {
        int priority = (attr >> 7) & 1;
        int yflip = (attr >> 6) & 1;
        int xflip = (attr >> 5) & 1;
        int pal = (attr >> 4) & 1;

        line = line % 8;
        line = yflip == 1 ? 8 - line : line;

        int table = 0x8000;
        int tile = sprid & 0xff;
        int patternentry = table + tile * 16 + line * 2;

        byte lo = mem.readByte(patternentry);
        byte hi = mem.readByte(patternentry + 1);

        int[] ib = new int[8];
        for (int i = 0; i < 8; i++) {
            int shift = seq[xflip][i];
            ib[i] = palette[obp[pal][(lo >> (shift)) & 1 | ((hi >> (shift)) & 1) << 1]];
        }
        return ib;
    }

    /**
     * Debug method, gets 8 pixel from the pattern table
     */
    public int[] get8bg(int line, byte tile, int table) {
        line = line % 8;
        // int target = table == 0 ? 0x8000 : 0x9000;
        int target = bgWiTiledata;
        boolean signed = target == 0x9000;
        int realtile = (signed ? (int) tile : tile & 0xff);
        int patternentry = target + realtile * 16 + line * 2;
        byte lo = mem.readByte(patternentry);
        byte hi = mem.readByte(patternentry + 1);
        int[] ib = new int[8];
        for (int i = 0; i < 8; i++) {
            ib[i] = palette[bgp[(lo >> (7 - i)) & 1 | ((hi >> (7 - i)) & 1) << 1]];
        }
        return ib;
    }

    private final int[][] seq = {{7, 6, 5, 4, 3, 2, 1, 0}, {0, 1, 2, 3, 4, 5, 6, 7}};

}
