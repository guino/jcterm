/* JCTerm
 * Copyright (C) 2002,2007 ymnk, JCraft,Inc.
 *
 * Written by: ymnk <ymnk@jcaft.com>
 * Modified by: Guino <wbbo@hotmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package com.jcraft.jcterm;

import java.awt.Color;

public class EmulatorXTerm extends Emulator {

	// Same as parent
	public EmulatorXTerm(Term term) {
		super(term);
	}

	// Main sub
	public void start() {
		// Reset terminal variables
		reset();

		// Prepare space and variables to parse incomming data
		int[] intarg = new int[10];
		int intargi = 0;
		int args = 0;
		byte b;

		// Default cursor position
		x = 0;
		y = char_height;

		try {
			// Forever (until an exception -- likely disconnection)
			while (true) {
				// Get one character/byte
				b = getChar();
				// If zero, ignore it
				if (b == 0) {
					continue;
				}
				// If it's an ESC
				if (b == 0x1b) {
					// Get another character/byte
					b = getChar();

					// -- Below are C1 (8-Bit) Control Characters --

					if (b == 'M') { // Reverse Index (scroll text down)
						scroll_reverse();
						continue;
					}

					if (b == 'D') { // Index (scroll text up)
						scroll_forward();
						continue;
					}

					if (b == ']') { // Operating System Command
						String data = "";
						byte lastb = 0;
						while (true) {
							b = getChar();
							if ((b & 0xff) == 0x07)
								break;
							if ((b & 0xff) == 0x9c && (lastb & 0xff) == 0x1b)
								break;
							data += (char) b;
							lastb = b;
						}
						if(data.equals("10;?")) {
							// Get Foreground color cmd
							debug("Get FG Color CMD");
						} else if(data.equals("11;?")) {
							// Get Background color cmd
							debug("Get BG Color CMD");
						} else {
							debug("Got OSC " + data);
						}
						continue;
					}

					// -- Below are VT100 8-Bit Control Characters --

					if (b == '7') {
						debug("Save cursor");
						save_cursor();
						continue;
					}

					if (b == '8') {
						debug("Restore cursor");
						restore_cursor();
						continue;
					}

					if (b == '(' || b == ')') { // Switch Character Set (not implemented)
						b = getChar();
						if (b == 'B') {
							debug("US ASCII");
							continue;
						}
						if (b == '0') { // enacs
							ena_acs();
							continue;
						}
					}

					if(b=='=') { // Application keypad
						continue;
					}

					if(b=='>') { // Normal keypad
						continue;
					}

					if (b != '[') { // If it's something else we don't know (not implemented)
						System.out.print("Unknown code: ESC " + ((char) b) + "[" + Integer.toHexString(b & 0xff) + "]");
						pushChar(b);
						continue;
					}

					// Reset variables to read parameters after CSI
					intargi = 0;
					intarg[intargi] = 0;
					int digit = 0;
					args = 0;

					// Read all parameters we can
					while (true) {
						// Get character/byte
						b = getChar();
						// If it's parameter delimiter
						if (b == ';') {
							// If we read any digits
							if (digit > 0) {
								// Increment arguments and parameter counts, reset next parameter values
								args++;
								intargi++;
								intarg[intargi] = 0;
								digit = 0;
							}
							continue;
						}

						// If it's a numeric digit
						if ('0' <= b && b <= '9') {
							// Add it to current parameter
							intarg[intargi] = intarg[intargi] * 10 + (b - '0');
							digit++;
							continue;
						}

						// Got here so we're done reading parameters/digits
						pushChar(b);
						break;
					}
					// Increment argument count if we had any digit left
					if(digit>0)
						args++;

					// Read the command character/byte
					b = getChar();
					if (b == 'm') {
						if (digit == 0 && intargi == 0) {
							b = getChar();
							if (b == 0x0f) { // sgr0
								exit_attribute_mode();
								continue;
							} else { // rmso, rmul
								exit_underline_mode();
								exit_standout_mode();
								pushChar(b);
								continue;
							}
						}

						// Process each parameter
						for (int i = 0; i <= intargi; i++) {
							Color fg = null;
							Color bg = null;

							//debug("CSI "+intarg[i]+" m");

							switch (intarg[i]) {
							case 0: // Reset all attributes
								exit_standout_mode();
								continue;
							case 1: // Bright // bold
								enter_bold_mode();
								continue;
							case 2: // Dim/Faint
								continue;
							case 3: // Italic
								continue;
							case 4: // Underline
								enter_underline_mode();
								continue;
							case 5: // Blink
								continue;
							case 7: // reverse
								enter_reverse_mode();
								continue;
							case 8: // Hidden
								continue;
							case 9: // -Crossed-
								continue;
							case 21: // Double underline
								continue;
							case 22: // NOT Bolt and NOT Dim/Faint
								continue;
							case 23: // NOT Italic
								continue;
							case 24: // NOT Underlined
								exit_underline_mode();
								continue;
							case 25: // NOT Blink
								continue;
							case 27: // exit reverse
								exit_reverse_mode();
								continue;
							case 28: // NOT Hidden
								continue;
							case 29: // NOT Crossed
								continue;
							case 30:
							case 31:
							case 32:
							case 33:
							case 34:
							case 35:
							case 36:
							case 37:
								fg = term.getColor(intarg[i] - 30);
								break;
							case 39:
								fg = term.getDefaultForeGround();
								break;
							case 40:
							case 41:
							case 42:
							case 43:
							case 44:
							case 45:
							case 46:
							case 47:
								bg = term.getColor(intarg[i] - 40);
								break;
							case 49:
								bg = term.getDefaultBackGround();
								break;
							default:
								debug("Unknown code: CSI "+intarg[i]+" m");
								continue;
							}
							if (fg != null)
								term.setForeGround(fg);
							if (bg != null)
								term.setBackGround(bg);
						}
						continue;
					}

					if (b == 'r') { // csr
						change_scroll_region(intarg[0], intarg[1]);
						continue;
					}

					if (b == 'H') { // cup
						if (digit == 0 && intargi == 0) {
							intarg[0] = intarg[1] = 1;
						}
						cursor_address(intarg[0], intarg[1]);
						continue;
					}

					if (b == 'd') { // Line Position Absolute
						absolute_cursorY(intarg[0]);
						continue;
					}

					if (b == 'G') { // Column Position Absolute
						absolute_cursorX(intarg[0]);
						continue;
					}

					if (b == 'L') { // Insert lines
						insert_lines(intarg[0]);
						continue;
					}

					if (b == 'M') { // Delete lines
						delete_lines(intarg[0]);
						continue;
					}

					if (b == 'P') { // Delete chars
						delete_chars(intarg[0]);
						continue;
					}

					if (b == '@') { // Insert chars
						insert_chars(intarg[0]);
						continue;
					}

					if (b == 'B') { // cud
						parm_down_cursor(intarg[0]);
						continue;
					}

					if (b == 'D') { // cub
						parm_left_cursor(intarg[0]);
						continue;
					}

					if (b == 'C') { // cuf
						if (digit == 0 && intargi == 0) {
							intarg[0] = 1;
						}
						parm_right_cursor(intarg[0]);
						continue;
					}

					if (b == 'K') { // el
						if (digit == 0 && intargi == 0) { // el
							clr_eol();
						} else { // el1
							clr_bol();
						}
						continue;
					}

					if (b == 'J') {
						clr_eos();
						continue;
					}

					if (b == 'S') {
						if(intarg[0]==0)
							intarg[0]=1;
						debug("Scroll forward "+intarg[0]);
						for(int l=0;l<intarg[0];l++)
							scroll_forward();
						continue;
					}

					if (b == 'T') {
						if(intarg[0]==0)
							intarg[0]=1;
						debug("Scroll reverse "+intarg[0]);
						for(int l=0;l<intarg[0];l++)
							scroll_reverse();
						continue;
					}

					if (b == 'A') { // cuu
						if (digit == 0 && intargi == 0) {
							intarg[0] = 1;
						}
						parm_up_cursor(intarg[0]);
						continue;
					}

					// DEC Private Mode Reset/set (CSI ? . l/h) and Send Device Attributes (CSI > . c)
					if (b == '?' || b == '>') {
						String cmd = (char)b+" ";
						while(true) {
							b = getChar();
							if(('0' <= b && b <= '9') || b==';') {
								cmd += (char)b;
							} else {
								// Split by ; and process each code separately
								for(String code : cmd.substring(2).split(";")) {
									String m = null;
									if(code.equals("1") && b=='l') {
										// Normal Cursor Keys
										m = "Normal Cursor Keys";
									} else if(code.equals("1") && b=='h') {
										// Application Cursor Keys
										m = "App Cursor Keys";
									} else if(code.equals("7") && b=='h') {
										// Wrap around mode
										m = "Wraparound mode";
									} else if(code.equals("25") && b=='l') {
										// Hide cursor
										m = "Hide Cursor";
										term.setShowCursor(false);
									} else if(code.equals("25") && b=='h') {
										// Show cursor
										m = "Show Cursor";
										term.setShowCursor(true);
									} else if(code.equals("12") && b=='l') {
										// Stop Blinking Cursor
										m = "Stop Blink Cursor";
									} else if(code.equals("12") && b=='h') {
										// Start Blinking Cursor
										m = "Start Blink Cursor";
									} else if(code.equals("1047") && b=='l') {
										// Restore Cursor
										m = "RESTORE CURSOR";
										restore_cursoralt();
									} else if(code.equals("1047") && b=='h') {
										// Save Cursor
										m = "SAVE CURSOR";
										save_cursoralt();
									} else if(code.equals("1048") && b=='l') {
										// Alternate Screen Buffer OFF
										m = "ALT SCR OFF";
										term.setAltScreen(false);
									} else if(code.equals("1048") && b=='h') {
										// Alternate Screen Buffer ON
										m = "ALT SCR ON";
										term.setAltScreen(true);
									} else if(code.equals("1049") && b=='l') {
										// Alternate Screen Buffer OFF + restore cursor
										m = "ALT SCR OFF RESTORE CURSOR";
										term.setAltScreen(false);
										restore_cursoralt();
									} else if(code.equals("1049") && b=='h') {
										// Alternate Screen Buffer ON + save cursor
										m = "ALT SCR ON SAVE CURSOR";
										save_cursoralt();
										term.setAltScreen(true);
									} else if(code.equals("2004") && b=='l') {
										// Stop bracketed paste mode
										m = "Stop Brack paste";
									} else if(code.equals("2004") && b=='h') {
										// Start bracketed paste mode
										m = "Start Brack paste";
									} else if(code.isEmpty() && b=='c') {
										m = "Get Dev Attributes";
									} else if(b=='m') {
										m = "Set/reset key modifier options";
									}
									if(m!=null) {
										debug(m);
									} else {
										System.out.println("Unknown code: "+code+" in CSI "+cmd+(char)b);
									}
								}
								break;
							}
						}
						continue;
					}

					if (b == 'h') { // kh \Eh home key
						continue;
					}

					if (b == 't') { // Title save/restore?
						continue;
					}

					if (b == 'l') { // Keyboard mode : 0=reset, 2=action, 4=replace, 12=send/receive, 20=normal line feed
						debug("Keyboard mode: "+intarg[0]);
						continue;
					}

					if (b == 'n') { // Status report 5='OK', 6=Cursor position
						debug("Status report: "+intarg[0]);
						continue;
					}

					// Got here so we don't know what this was (not implemented)
					System.out.print("Unknown code: CSI ");
					for(int a=0;a<args;a++) {
						System.out.print(intarg[a]+(a==args-1 ? "" : ";"));
					}
					System.out.println((char)b);
					continue;
				}

				if (b == 0x07) { // bel ^G
					bell();
					continue;
				}

				if (b == 0x09) { // ht(^I)
					tab();
					continue;
				}

				if (b == 0x0f) { // rmacs ^O // end alternate character set (P)
					exit_alt_charset_mode();
					continue;
				}

				if (b == 0x0e) { // smacs ^N // start alternate character set (P)
					enter_alt_charset_mode();
					continue;
				}

				if (b == 0x0d) {
					carriage_return();
					continue;
				}

				if (b == 0x08) {
					cursor_left();
					continue;
				}

				if (b == 0x0a) { // '\n'
					cursor_down();
					continue;
				}

				if (b != 0x0a) { // !'\n'
					pushChar(b);
					draw_text();
					continue;
				}
			}
		} catch (Exception e) {
		}
	}

	private static byte[] ENTER = { (byte) 0x0d };
	private static byte[] UP = { (byte) 0x1b, (byte) 0x4f, (byte) 0x41 };
	private static byte[] DOWN = { (byte) 0x1b, (byte) 0x4f, (byte) 0x42 };
	private static byte[] PGUP = { (byte) 0x1b, (byte) '[', (byte) '5', (byte) '~'};
	private static byte[] PGDOWN = { (byte) 0x1b, (byte) '[',  (byte) '6', (byte) '~'};
	private static byte[] HOME = { (byte) 0x1b, (byte) '[', (byte) '1', (byte) '~'};
	private static byte[] END = { (byte) 0x1b, (byte) '[',  (byte) '4', (byte) '~'};
	private static byte[] DEL = { (byte) 0x1b, (byte) '[',  (byte) '3', (byte) '~'};
	private static byte[] RIGHT = { (byte) 0x1b, (byte) /* 0x5b */0x4f, (byte) 0x43 };
	private static byte[] LEFT = { (byte) 0x1b, (byte) /* 0x5b */0x4f, (byte) 0x44 };
	private static byte[] F1 = { (byte) 0x1b, (byte) 0x4f, (byte) 'P' };
	private static byte[] F2 = { (byte) 0x1b, (byte) 0x4f, (byte) 'Q' };
	private static byte[] F3 = { (byte) 0x1b, (byte) 0x4f, (byte) 'R' };
	private static byte[] F4 = { (byte) 0x1b, (byte) 0x4f, (byte) 'S' };
	private static byte[] F5 = { (byte) 0x1b, (byte) 0x4f, (byte) 't' };
	private static byte[] F6 = { (byte) 0x1b, (byte) 0x4f, (byte) 'u' };
	private static byte[] F7 = { (byte) 0x1b, (byte) 0x4f, (byte) 'v' };
	private static byte[] F8 = { (byte) 0x1b, (byte) 0x4f, (byte) 'I' };
	private static byte[] F9 = { (byte) 0x1b, (byte) 0x4f, (byte) 'w' };
	private static byte[] F10 = { (byte) 0x1b, (byte) 0x4f, (byte) 'x' };
	private static byte[] tab = { (byte) 0x09 };

	public byte[] getCodeENTER() {
		return ENTER;
	}

	public byte[] getCodeUP() {
		return UP;
	}

	public byte[] getCodeDOWN() {
		return DOWN;
	}

	public byte[] getCodePGUP() {
		return PGUP;
	}

	public byte[] getCodePGDOWN() {
		return PGDOWN;
	}

	public byte[] getCodeHOME() {
		return HOME;
	}

	public byte[] getCodeEND() {
		return END;
	}

	public byte[] getCodeDEL() {
		return DEL;
	}

	public byte[] getCodeRIGHT() {
		return RIGHT;
	}

	public byte[] getCodeLEFT() {
		return LEFT;
	}

	public byte[] getCodeF1() {
		return F1;
	}

	public byte[] getCodeF2() {
		return F2;
	}

	public byte[] getCodeF3() {
		return F3;
	}

	public byte[] getCodeF4() {
		return F4;
	}

	public byte[] getCodeF5() {
		return F5;
	}

	public byte[] getCodeF6() {
		return F6;
	}

	public byte[] getCodeF7() {
		return F7;
	}

	public byte[] getCodeF8() {
		return F8;
	}

	public byte[] getCodeF9() {
		return F9;
	}

	public byte[] getCodeF10() {
		return F10;
	}

	public byte[] getCodeTAB() {
		return tab;
	}
}
