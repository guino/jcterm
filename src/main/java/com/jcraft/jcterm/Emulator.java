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

import java.io.IOException;

public abstract class Emulator {
	Term term = null;

	public Emulator(Term term) {
		this.term = term;
	}

	public abstract void start();

	public abstract byte[] getCodeENTER();

	public abstract byte[] getCodeUP();

	public abstract byte[] getCodeDOWN();

	public abstract byte[] getCodePGUP();

	public abstract byte[] getCodePGDOWN();

	public abstract byte[] getCodeHOME();

	public abstract byte[] getCodeEND();

	public abstract byte[] getCodeDEL();

	public abstract byte[] getCodeRIGHT();

	public abstract byte[] getCodeLEFT();

	public abstract byte[] getCodeF1();

	public abstract byte[] getCodeF2();

	public abstract byte[] getCodeF3();

	public abstract byte[] getCodeF4();

	public abstract byte[] getCodeF5();

	public abstract byte[] getCodeF6();

	public abstract byte[] getCodeF7();

	public abstract byte[] getCodeF8();

	public abstract byte[] getCodeF9();

	public abstract byte[] getCodeF10();

	public abstract byte[] getCodeTAB();

	public void reset() {
		term_width = term.getColumnCount();
		term_height = term.getRowCount();
		char_width = term.getCharWidth();
		char_height = term.getCharHeight();
		region_y1 = 1;
		region_y2 = term_height;
	}

	byte[] buf = new byte[1024];
	int bufs = 0;
	int buflen = 0;

	byte getChar() throws java.io.IOException {
		if (buflen == 0) {
			fillBuf();
		}
		buflen--;
		return buf[bufs++];
	}

	void fillBuf() throws java.io.IOException {
		buflen = bufs = 0;
		buflen = term.getConnection().getInputStream().read(buf, bufs, buf.length - bufs);

//		System.out.print("fillBuf: ");
//		for(int i=0; i<buflen; i++) {
//			if((buf[i]&0xff)==0xd) {
//				System.out.print("\\d[d]");
//			} else if((buf[i]&0xff)==0xa) {
//				System.out.print("\\a[a]");
//			} else {
//				System.out.print(((char)(buf[i]&0xff))+"["+Integer.toHexString(buf[i]&0xff)+"]");
//			}
//		}
//		System.out.println("");

		if (buflen <= 0) {
			buflen = 0;
			throw new IOException("fillBuf");
		}
	}

	void pushChar(byte foo) throws java.io.IOException {
		buflen++;
		buf[--bufs] = foo;
	}

	int getASCII(int len) throws java.io.IOException {
		if (buflen == 0) {
			fillBuf();
		}
		if (len > buflen)
			len = buflen;
		int foo = len;
		byte tmp;
		while (len > 0) {
			tmp = buf[bufs++];
			if (0x20 <= tmp && tmp <= 0x7f) {
				buflen--;
				len--;
				continue;
			}
			bufs--;
			break;
		}
		return foo - len;
	}

	protected int term_width = 80;
	protected int term_height = 24;

	protected int x = 0;
	protected int y = 0;
	private int saved_x = 0;
	private int saved_y = 0;
	private int altsaved_x = 0;
	private int altsaved_y = 0;

	protected int char_width;
	protected int char_height;

	private int region_y2;
	private int region_y1;

	protected int tab = 8;

	// Insert lines at cursor position
	protected void insert_lines(int lines) {
		if(lines==0)
			lines=1;
		int fy = y-char_height; // Position we're moving data From
		int dy = lines*char_height; // Displacement Y
		int mh = (region_y2*char_height)-(fy+dy); // Move height
		debug("IL fy="+fy+" dy="+dy+" mh="+mh);
		term.scroll_area(0, fy, term_width*char_width, mh, 0, dy);
		term.clear_area(x, fy, term_width*char_width, fy+dy);
		term.redraw(0, (region_y1 - 1) * char_height, term_width * char_width, (region_y2 - region_y1 + 1) * char_height);
	}

	// Delete lines from cursor position
	protected void delete_lines(int lines) {
		if(lines==0)
			lines=1;
		int fy = y-char_height; // Position to move into
		int dy = lines*char_height; // Displacement Y
		int mh = (region_y2*char_height)-(fy+dy); // Move height
		int cy = (region_y2*char_height)-dy; // Clear Y start
		debug("DL fy="+fy+" dy="+dy+" mh="+mh+" cy="+cy);
		term.scroll_area(0, fy+dy, term_width*char_width, mh, 0, -dy);
		term.clear_area(x, cy, term_width * char_width, cy+dy);
		term.redraw(0, (region_y1 - 1) * char_height, term_width * char_width, (region_y2 - region_y1 + 1) * char_height);
	}

	// Insert chars from cursor position
	protected void insert_chars(int chars) {
		if(chars==0)
			chars=1;
		int fy = y-char_height; // Y Position to move into
		int dx = chars*char_width; // Displacement X
		int mw = (term_width*char_width)-x; // Move width
		debug("IC fy="+fy+" dx="+dx+" mw="+mw);
		term.scroll_area(x, fy, mw, char_height, dx, 0);
		term.clear_area(x, fy, x+dx, y);
		term.redraw(0, (region_y1 - 1) * char_height, term_width * char_width, (region_y2 - region_y1 + 1) * char_height);
	}

	// Delete chars from cursor position
	protected void delete_chars(int chars) {
		if(chars==0)
			chars=1;
		int fy = y-char_height; // Y Position to move into
		int dx = chars*char_width; // Displacement X
		int mw = (term_width*char_width)-x; // Move width
		int cx = (term_width*char_width)-dx;
		debug("DC y="+y+" x="+x+"("+(x/char_width)+") dx="+dx+" mw="+mw+" cx="+cx);
		term.scroll_area(x+dx, fy, mw, char_height, -dx, 0);
		term.clear_area(cx, fy, term_width*char_width, y);
		term.redraw(0, (region_y1 - 1) * char_height, term_width * char_width, (region_y2 - region_y1 + 1) * char_height);
	}

	// Reverse scroll (lines move down)
	protected void scroll_reverse() {
		debug("SRWD");
		term.draw_cursor();
		term.scroll_area(0, (region_y1 - 1) * char_height, term_width * char_width, (region_y2 - region_y1) * char_height, 0, char_height);
		term.clear_area(x, y - char_height, term_width * char_width, y);
		term.redraw(0, (region_y1 - 1) * char_height, term_width * char_width, (region_y2 - region_y1 + 1) * char_height);
		term.draw_cursor();
	}

	// Normal scroll one line (lines move UP)
	protected void scroll_forward() {
		debug("SFWD");
		term.draw_cursor();
		term.scroll_area(0, (region_y1 - 0) * char_height, term_width * char_width, (region_y2 - region_y1 + 1) * char_height, 0, -char_height);
		term.clear_area(0, region_y2 * char_height - char_height, term_width * char_width, region_y2 * char_height);
		term.redraw(0, (region_y1 - 1) * char_height, term_width * char_width, (region_y2 - region_y1 + 1) * char_height);
		term.draw_cursor();
	}

	// Save cursor position
	protected void save_cursor() {
		saved_x = x;
		saved_y = y;
	}

	// Restore cursor position
	protected void restore_cursor() {
		x = saved_x;
		y = saved_y;
	}

	// Save cursor position alternate screen
	protected void save_cursoralt() {
		altsaved_x = x;
		altsaved_y = y;
	}

	// Restore cursor position alternate screen
	protected void restore_cursoralt() {
		x = altsaved_x;
		y = altsaved_y;
	}

	// Enable alternate character set
	protected void ena_acs() {
		// System.out.println("enable alterate char set");
	}

	protected void exit_alt_charset_mode() {
		// System.out.println("end alternate character set (P)");
	}

	protected void enter_alt_charset_mode() {
		// System.out.println("start alternate character set (P)");
	}

	protected void exit_attribute_mode() {
		debug("Turn off all attributes");
		term.resetAllAttributes();
	}

	protected void exit_standout_mode() {
		term.resetAllAttributes();
	}

	protected void exit_underline_mode() {
		term.setUnderline(false);
	}

	protected void enter_bold_mode() {
		term.setBold();
	}

	protected void enter_underline_mode() {
		term.setUnderline(true);
	}

	protected void enter_reverse_mode() {
		debug("Enter Reverse");
		term.setReverse(true);
	}

	protected void exit_reverse_mode() {
		debug("Exit Reverse");
		term.setReverse(false);
	}

	protected void change_scroll_region(int y1, int y2) {
		region_y1 = y1;
		region_y2 = y2;
	}

	protected void cursor_address(int r, int c) {
		term.draw_cursor();
		x = (c - 1) * char_width;
		y = r * char_height;
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void absolute_cursorY(int newY) {
		term.draw_cursor();
		y = newY * char_height;
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void absolute_cursorX(int newX) {
		term.draw_cursor();
		x = (newX) * char_width;
		term.setCursor(x, y);
		term.draw_cursor();
	}
	protected void parm_down_cursor(int lines) {
		term.draw_cursor();
		y += (lines) * char_height;
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void parm_left_cursor(int chars) {
		term.draw_cursor();
		x -= (chars) * char_width;
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void parm_right_cursor(int chars) {
		term.draw_cursor();
		x += (chars) * char_width;
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void clr_eol() {
		term.draw_cursor();
		term.clear_area(x, y - char_height, term_width * char_width, y);
		term.redraw(x, y - char_height, (term_width) * char_width - x, char_height);
		term.draw_cursor();
	}

	protected void clr_bol() {
		term.draw_cursor();
		term.clear_area(0, y - char_height, x, y);
		term.redraw(0, y - char_height, x, char_height);
		term.draw_cursor();
	}

	protected void clr_eos() {
		term.draw_cursor();
		term.clear_area(x, y - char_height, term_width * char_width, term_height * char_height);
		term.redraw(x, y - char_height, term_width * char_width - x, term_height * char_height - y + char_height);
		term.draw_cursor();
	}

	protected void parm_up_cursor(int lines) {
		term.draw_cursor();
		y -= (lines) * char_height;
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void bell() {
		term.beep();
	}

	protected void tab() {
		term.draw_cursor();
		x = (((x / char_width) / tab + 1) * tab * char_width);
		if (x >= term_width * char_width) {
			x = 0;
			y += char_height;
		}
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void carriage_return() {
		term.draw_cursor();
		x = 0;
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void cursor_left() {
		term.draw_cursor();
		x -= char_width;
		if (x < 0) {
			y -= char_height;
			x = term_width * char_width - char_width;
		}
		term.setCursor(x, y);
		term.draw_cursor();
	}

	protected void cursor_down() {
		term.draw_cursor();
		y += char_height;
		term.setCursor(x, y);
		term.draw_cursor();

		check_region();
	}

	protected void draw_text() throws java.io.IOException {

		int rx;
		int ry;
		int w;
		byte[] b4 = new byte[4];

		check_region();

		rx = x;
		ry = y;

		String str = null;
		byte b = getChar();
		term.draw_cursor();

		// Check for multiple byte characters (UTF-8)
		if ((b&0xff)>=0xc2 && (b&0xff)<0xe0) { // UTF8 2 byte ?
			b4[0] = b;
			b4[1] = getChar();
			str = new String(b4, 0, 2, "UTF-8");
			debug("UTF8 2 "+str);
		} else if((b&0xff)>=0xe0 && (b&0xff)<0xf0) { // UTF8 3 byte ?
			b4[0] = b;
			b4[1] = getChar();
			b4[2] = getChar();
			str = new String(b4, 0, 3, "UTF-8");
			debug("UTF8 3 "+str);
		} else if((b&0xff)>=0xf0) { // UTF8 4 byte ?
			b4[0] = b;
			b4[1] = getChar();
			b4[2] = getChar();
			b4[3] = getChar();
			str = new String(b4, 0, 4, "UTF-8");
			debug("UTF8 4 "+str);
		} else if ((b & 0x80) != 0) { // EUC ?
			b4[0] = b;
			b4[1] = getChar();
			str = new String(b4, 0, 2, "EUC-JP");
			debug("EUC 2 "+str);
		}

		// If it was multiple byte
		if (str!=null) {
			int sw = term.getWidth(str);
			w = char_width;
			while(w<sw)
				w += char_width;
			term.clear_area(x, y - char_height, x + w, y);
			term.drawString(str, x, y);
			x += w;
		} else {
			pushChar(b);
			int foo = getASCII(term_width - (x / char_width));
			if (foo != 0) {
				term.clear_area(x, y - char_height, x + foo * char_width, y);
				term.drawBytes(buf, bufs - foo, foo, x, y);
			} else {
				foo = 1;
				term.clear_area(x, y - char_height, x + foo * char_width, y);
				b4[0] = getChar();
				term.drawBytes(b4, 0, foo, x, y);
			}
			x += (char_width * foo);
			w = char_width * foo;
		}
		term.redraw(rx, ry - char_height, w, char_height);
		term.setCursor(x, y);
		term.draw_cursor();
	}

	private void check_region() {
		// If cursor is past end of line
		if (x >= term_width * char_width) {
			// Move it to start of next line
			x = 0;
			y += char_height;
		}

		// If cursor is past defined region
		if (y > region_y2 * char_height) {
			// Decrease cursor line until it's within region again
			while (y > region_y2 * char_height) {
				y -= char_height;
			}
			// Scroll region one line UP, clear cursor line
			term.draw_cursor();
			term.scroll_area(0, region_y1 * char_height, term_width * char_width, (region_y2 - region_y1) * char_height, 0, -char_height);
			term.clear_area(0, y - char_height, term_width * char_width, y);
			term.redraw(0, 0, term_width * char_width, region_y2 * char_height);
			term.setCursor(x, y);
			term.draw_cursor();
		}
	}

	void debug(String msg) {
		//System.out.println(msg);
	}

}
