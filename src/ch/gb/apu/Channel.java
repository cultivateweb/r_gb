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
package ch.gb.apu;

public abstract class Channel {

    protected byte nr0;
    protected byte nr1;
    protected byte nr2;
    protected byte nr3;
    protected byte nr4;
    protected int divider = 1;
    protected int triggermask = 0x80;

    abstract void write(int add, byte b);

    abstract byte read(int add);

    abstract void reset();
    
    protected boolean clockLenNext(int seqstep){
        //TODO: improve by testing for uneven
        return (seqstep==0) || (seqstep==2) || (seqstep==4) || (seqstep==6);
    }
}
