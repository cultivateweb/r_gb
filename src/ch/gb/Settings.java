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
package ch.gb;

import java.util.HashMap;

import com.badlogic.gdx.Input.Keys;

public class Settings {
	public static HashMap<Integer, Integer> joymap;
	public static String root = "savegames/";
	static {
		// default Joypad mapping
		// 7: start
		// 6: select
		// 5: button B
		// 4: button A
		// 3: down
		// 2: up
		// 1: left
		// 0: right
		joymap = new HashMap<Integer, Integer>();
		joymap.put(Keys.RIGHT, 0);
		joymap.put(Keys.LEFT, 1);
		joymap.put(Keys.UP, 2);
		joymap.put(Keys.DOWN, 3);

		joymap.put(Keys.S, 4);
		joymap.put(Keys.A, 5);
		joymap.put(Keys.SHIFT_RIGHT, 6);
		joymap.put(Keys.ENTER, 7);
	}
	public static int numChannels=1;
	public static int samplingrate = 44100;
	public static float mastervolume = 1f;// between 0 and 1
	public static int ch1enable = 1;// 0 disabled, 1 enabled,
	public static int ch2enable = 1;
	public static int ch3enable = 1;
	public static int ch4enable = 1;
	
	public static int speedup = 10;
	public static int zoom = 2;
	
	
	public static boolean checkSettings(){
		return false;
	}
}
