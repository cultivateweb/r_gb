package ch.gb.io;

import ch.gb.utils.Utils;

public class Serial implements IOport {
	public static final int SB = 0xFF01;
	public static final int SC = 0xFF02;
	private byte serialtransferdata;
	private byte serialtransfercontrol;

	private final byte tmp[] = new byte[1];

	@Override
	public void write(int add, byte b) {
		if (add == SB) {
			tmp[0] = serialtransferdata = b;
			System.out.print(Utils.decASCII(tmp)); // HOLY SHIT
		} else if (add == SC) {
			if (b == 0x81) {
				System.out.print(Utils.decASCII(tmp));
				serialtransfercontrol = 1; // transfer "finished"
			}
		} else {
			throw new RuntimeException("Serial-> couldnt map write");
		}
	}

	@Override
	public byte read(int add) {
		if (add == SB) {
			return serialtransferdata;
		} else if (add == SC) {
			return serialtransfercontrol;
		}
		throw new RuntimeException("Serial-> coulnt map read");
	}

}