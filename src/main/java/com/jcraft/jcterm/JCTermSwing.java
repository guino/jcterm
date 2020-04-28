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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.awt.image.*;

public class JCTermSwing extends JPanel implements KeyListener, Term {
	private static final long serialVersionUID = 3444724742030389924L;
	OutputStream out;
	InputStream in;
	Emulator emulator = null;

	Connection connection = null;

	private BufferedImage img;
	private BufferedImage altimg;
	private long altResized = 0;
	private Thread altResizeThread = null;
	private BufferedImage background;
	private Graphics2D graphics;
	private Color defaultbground = Color.black;
	private Color defaultfground = Color.white;
	private Color bground = Color.black;
	private Color fground = Color.white;
	private Component term_area = null;
	private Font font;

	private boolean bold = false;
	private boolean underline = false;
	private boolean reverse = false;
	private boolean showCursor = true;

	private int term_width = 80;
	private int term_height = 24;

	private int descent = 0;

	private int x = 0;
	private int y = 0;

	private int char_width;
	private int char_height;

	private int line_space = 0;
	@SuppressWarnings("unused")
	private int compression = 0;

	private boolean antialiasing = true;

	private final Color[] colors = { Color.black, Color.red, Color.green, Color.yellow, Color.blue, Color.magenta, Color.cyan, Color.white };

	public JCTermSwing() {

		enableEvents(AWTEvent.KEY_EVENT_MASK);
		addKeyListener(this);

		setFont("Monospaced-14");

		setSize(getTermWidth()+1, getTermHeight());

		clear();

		term_area = this;

		setFocusable(true);
		enableInputMethods(true);

		setFocusTraversalKeysEnabled(false);

	}

	public Font getFont() {
		return font;
	}

	void setFont(String fname) {
		// Calculate char width height and descent
		font = Font.decode(fname);
		BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = (Graphics2D) (img.getGraphics());
		graphics.setFont(font);
		{
			FontMetrics fo = graphics.getFontMetrics();
			descent = fo.getDescent();

//			System.out.println(fo.getDescent()); System.out.println(fo.getAscent());
//			System.out.println(fo.getLeading()); System.out.println(fo.getHeight());
//			System.out.println(fo.getMaxAscent());
//			System.out.println(fo.getMaxDescent());
//			System.out.println(fo.getMaxDecent());
//			System.out.println(fo.getMaxAdvance());

			char_width = (int) (fo.charWidth((char) '@'));
			char_height = (int) (fo.getHeight()) + (line_space * 2);
			descent += line_space;
		}
		img.flush();
		graphics.dispose();

		// Adjust background image size
		background = new BufferedImage(char_width, char_height, BufferedImage.TYPE_INT_RGB);
		{
			Graphics2D foog = (Graphics2D) (background.getGraphics());
			foog.setColor(getBackGround());
			foog.fillRect(0, 0, char_width, char_height);
			foog.dispose();
		}
	}

	public void setSize(int w, int h) {

		// Not changing dimensions, leave
		if(w==getTermWidth() && h==getTermHeight())
			return;

		BufferedImage imgOrg = getImg();
		if (graphics != null)
			graphics.dispose();

		int column = w / getCharWidth();
		int row = h / getCharHeight();
		term_width = column;
		term_height = row;

		// Resize panel, set its preferred size and set terminal size (in character size multiple)
		super.setSize(column*getCharWidth(), row*getCharHeight());
		setPreferredSize(new Dimension(column*getCharWidth(), row*getCharHeight()));

		if (emulator != null)
			emulator.reset();

		if(altimg!=null) {
			altimg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		} else {
			img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		}

		graphics = (Graphics2D) (getImg().getGraphics());
		graphics.setFont(font);

		clear_area(0, 0, w, h);
		redraw(0, 0, w, h);

		if (imgOrg != null) {
			Shape clip = graphics.getClip();
			graphics.setClip(0, 0, getTermWidth(), getTermHeight());
			graphics.drawImage(imgOrg, 0, 0, term_area);
			graphics.setClip(clip);
		}

		setAntiAliasing(antialiasing);

		if (connection != null) {
			connection.requestResize(this);
		}

		if (imgOrg != null) {
			imgOrg.flush();
			imgOrg = null;
		}
	}

	public void start(Connection connection) {
		this.connection = connection;
		in = connection.getInputStream();
		out = connection.getOutputStream();
		emulator = new EmulatorXTerm(this);
		emulator.reset();
		emulator.start();

		clear();
		redraw(0, 0, getTermWidth(), getTermHeight());
	}

	public void paintComponent(Graphics g) {
		//super.paintComponent(g); // NOT NECESSARY SINCE WE PAINT THE WHOLE AREA -- MAKES RESIZE UGLY
		if (getImg() != null) {
			g.drawImage(getImg(), 0, 0, term_area);
			// If showing cursor (on valid position)
			if(showCursor && y>0) {
				Color c = g.getColor();
				g.setColor(getForeGround());
				g.setXORMode(getBackGround());
				g.fillRect(x, y - char_height, char_width, char_height);
				g.setPaintMode();
				g.setColor(c);
			}
		}
	}

	public void paint(Graphics g) {
		super.paint(g);
	}

	public void processKeyEvent(KeyEvent e) {
		// System.out.println(e);
		int id = e.getID();
		if (id == KeyEvent.KEY_PRESSED) {
			keyPressed(e);
		} else if (id == KeyEvent.KEY_RELEASED) {
			/* keyReleased(e); */
		} else if (id == KeyEvent.KEY_TYPED) {
			keyTyped(e);/* keyTyped(e); */
		}
		e.consume(); // ??
	}

	byte[] obuffer = new byte[3];

	public void keyPressed(KeyEvent e) {
		int keycode = e.getKeyCode();
		byte[] code = null;
		switch (keycode) {
		case KeyEvent.VK_CONTROL:
		case KeyEvent.VK_SHIFT:
		case KeyEvent.VK_ALT:
		case KeyEvent.VK_CAPS_LOCK:
			return;
		case KeyEvent.VK_ENTER:
			code = emulator.getCodeENTER();
			break;
		case KeyEvent.VK_UP:
			code = emulator.getCodeUP();
			break;
		case KeyEvent.VK_DOWN:
			code = emulator.getCodeDOWN();
			break;
		case KeyEvent.VK_PAGE_UP:
			code = emulator.getCodePGUP();
			break;
		case KeyEvent.VK_PAGE_DOWN:
			code = emulator.getCodePGDOWN();
			break;
		case KeyEvent.VK_HOME:
			code = emulator.getCodeHOME();
			break;
		case KeyEvent.VK_END:
			code = emulator.getCodeEND();
			break;
		case KeyEvent.VK_DELETE:
			code = emulator.getCodeDEL();
			break;
		case KeyEvent.VK_RIGHT:
			code = emulator.getCodeRIGHT();
			break;
		case KeyEvent.VK_LEFT:
			code = emulator.getCodeLEFT();
			break;
		case KeyEvent.VK_F1:
			code = emulator.getCodeF1();
			break;
		case KeyEvent.VK_F2:
			code = emulator.getCodeF2();
			break;
		case KeyEvent.VK_F3:
			code = emulator.getCodeF3();
			break;
		case KeyEvent.VK_F4:
			code = emulator.getCodeF4();
			break;
		case KeyEvent.VK_F5:
			code = emulator.getCodeF5();
			break;
		case KeyEvent.VK_F6:
			code = emulator.getCodeF6();
			break;
		case KeyEvent.VK_F7:
			code = emulator.getCodeF7();
			break;
		case KeyEvent.VK_F8:
			code = emulator.getCodeF8();
			break;
		case KeyEvent.VK_F9:
			code = emulator.getCodeF9();
			break;
		case KeyEvent.VK_F10:
			code = emulator.getCodeF10();
			break;
		case KeyEvent.VK_TAB:
			code = emulator.getCodeTAB();
			break;
		}
		if (code != null) {
			try {
				out.write(code, 0, code.length);
				out.flush();
			} catch (Exception ee) {
			}
			return;
		}

		char keychar = e.getKeyChar();
		if ((keychar & 0xff00) == 0) {
			obuffer[0] = (byte) (e.getKeyChar());
			try {
				out.write(obuffer, 0, 1);
				out.flush();
			} catch (Exception ee) {
			}
		}
	}

	public void keyTyped(KeyEvent e) {
		char keychar = e.getKeyChar();
		if ((keychar & 0xff00) != 0) {
			char[] foo = new char[1];
			foo[0] = keychar;
			try {
				byte[] goo = new String(foo).getBytes("EUC-JP");
				out.write(goo, 0, goo.length);
				out.flush();
			} catch (Exception eee) {
			}
		}
	}

	public int getTermWidth() {
		return char_width * term_width;
	}

	public int getTermHeight() {
		return char_height * term_height;
	}

	public int getCharWidth() {
		return char_width;
	}

	public int getCharHeight() {
		return char_height;
	}

	public int getColumnCount() {
		return term_width;
	}

	public int getRowCount() {
		return term_height;
	}

	public void clear() {
		graphics.setColor(getBackGround());
		graphics.fillRect(0, 0, char_width * term_width, char_height * term_height);
		graphics.setColor(getForeGround());
	}

	public void setCursor(int x, int y) {
		redraw(this.x, this.y - char_height, char_width, char_height);
		this.x = x;
		this.y = y;
		redraw(x, y - char_height, char_width, char_height);
	}

	public void draw_cursor() {
	}

	public void redraw(int x, int y, int width, int height) {
		repaint(x, y, width, height);
	}

	public void clear_area(int x1, int y1, int x2, int y2) {
		// System.out.println("clear_area: "+x1+" "+y1+" "+x2+" "+y2);
		graphics.setColor(getBackGround());
		graphics.fillRect(x1, y1, x2 - x1, y2 - y1);
		graphics.setColor(getForeGround());
	}

	public void scroll_area(int x, int y, int w, int h, int dx, int dy) {
		//System.out.println("scroll_area: "+x+" "+y+" "+w+" "+h+" "+dx+" "+dy);
		graphics.copyArea(x, y, w, h, dx, dy);
		redraw(x + dx, y + dy, w, h);
	}

	public void drawBytes(byte[] buf, int s, int len, int x, int y) {
		try {
			graphics.drawBytes(buf, s, len, x, y - descent);
			if (bold)
				graphics.drawBytes(buf, s, len, x + 1, y - descent);

			if (underline) {
				graphics.drawLine(x, y - 1, x + len * char_width, y - 1);
			}
		} catch (Exception e) {
		}
	}

	public void drawString(String str, int x, int y) {
		graphics.drawString(str, x, y - descent);
		if (bold)
			graphics.drawString(str, x + 1, y - descent);

		if (underline) {
			graphics.drawLine(x, y - 1, x + str.length() * char_width, y - 1);
		}

	}

	public int getWidth(String txt) {
		FontMetrics fo = graphics.getFontMetrics();
		return fo.stringWidth(txt)-1; // Give it 1 pixel tolerance
	}

	public void beep() {
		Toolkit.getDefaultToolkit().beep();
	}

	/** Ignores key released events. */
	public void keyReleased(KeyEvent event) {
	}

	public void setLineSpace(int foo) {
		this.line_space = foo;
	}

	public boolean getAntiAliasing() {
		return antialiasing;
	}

	public void setAntiAliasing(boolean foo) {
		if (graphics == null)
			return;
		antialiasing = foo;
		Object mode = foo ? RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
		RenderingHints hints = new RenderingHints(RenderingHints.KEY_TEXT_ANTIALIASING, mode);
		graphics.setRenderingHints(hints);
	}

	public void setCompression(int compression) {
		if (compression < 0 || 9 < compression)
			return;
		this.compression = compression;
	}

	static Color toColor(Object o) {
		if (o instanceof String) {
			try {
				return Color.decode(((String) o).trim());
			} catch (NumberFormatException e) {
			}
			return Color.getColor(((String) o).trim());
		}
		if (o instanceof Color) {
			return (Color) o;
		}
		return Color.white;
	}

	public void setDefaultForeGround(Object f) {
		defaultfground = toColor(f);
	}

	public Color getDefaultForeGround() {
		return defaultfground;
	}

	public void setDefaultBackGround(Object f) {
		defaultbground = toColor(f);
	}

	public Color getDefaultBackGround() {
		return defaultfground;
	}

	public void setForeGround(Object f) {
		fground = toColor(f);
		graphics.setColor(getForeGround());
	}

	public void setBackGround(Object b) {
		bground = toColor(b);
		Graphics2D foog = (Graphics2D) (background.getGraphics());
		foog.setColor(getBackGround());
		foog.fillRect(0, 0, char_width, char_height);
		foog.dispose();
	}

	private Color getForeGround() {
		if (reverse)
			return bground;
		return fground;
	}

	private Color getBackGround() {
		if (reverse)
			return fground;
		return bground;
	}

	public Color getColor(int index) {
		if (colors == null || index < 0 || colors.length <= index)
			return null;
		return colors[index];
	}

	public void setBold() {
		bold = true;
	}

	public void setUnderline(boolean useUnderline) {
		underline = useUnderline;
	}

	public void setReverse(boolean useReverse) {
		reverse = useReverse;
		if (graphics != null)
			graphics.setColor(useReverse ? getForeGround() : getBackGround());
	}

	public void resetAllAttributes() {
		bold = false;
		underline = false;
		reverse = false;
		bground = defaultbground;
		fground = defaultfground;
		if (graphics != null)
			graphics.setColor(getForeGround());
	}

	private static ConfigurationRepository defaultCR = new ConfigurationRepository() {
		private final Configuration conf = new Configuration();

		public Configuration load(String name) {
			return conf;
		}

		public void save(Configuration conf) {
		}
	};

	private static ConfigurationRepository cr = defaultCR;

	public static synchronized void setCR(ConfigurationRepository _cr) {
		if (_cr == null)
			_cr = defaultCR;
		cr = _cr;
	}

	public static synchronized ConfigurationRepository getCR() {
		return cr;
	}

	public void setAltScreen(boolean useAltScreen) {
		//System.out.println("ALT SCREEN "+useAltScreen);
		// Based on request
		if(useAltScreen) {
			// If we don't yet have alternate screen
			if(altimg == null) {
				// Create an alternate screen (temporary to flag we'll be using alternate screen)
				altimg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
				// Create new screen with correct size (force execution)
				setSize(getTermWidth()+1, getTermHeight());
				// Clear new screen and reset cursor
				clear();
				setCursor(0,char_height);
			}
		} else {
			// If we have an alternate screen
			if(altimg != null) {
				// Did the size change while on alternate screen ?
				//System.out.println("aw="+altimg.getWidth()+" ah="+altimg.getHeight()+" iw="+img.getWidth()+" ih="+img.getHeight());
				// If resized, save time for dealing with it later
				if(altimg.getWidth()!=img.getWidth() || altimg.getHeight()!=img.getHeight())
					altResized = System.nanoTime();
				// Destroy alternate screen
				altimg = null;
				// If size changed
				if(altResized!=0) {
					//System.out.println("DIFFERENT SIZE!");
					// The threaded wait is here because 'nano' for some reason likes to switch to main and back to alternate real quick during resize
					// This may be because we're not implementing some sort of function 'nano' wants to use so we avoid the researching what it is
					// If we don't have a thread to deal with the resize yet
					if(altResizeThread==null) {
						// Create one
						final Term term = this;
						altResizeThread = new Thread() {
							public void run() {
								// Stay waiting until either 0.5s on main window OR till alternate image is back on
								while(System.nanoTime()<altResized+500000000L && altimg==null) {
									try { Thread.sleep(50); } catch(Exception e) {};
								}
								//if(altimg!=null) System.out.println("BACK ON ALT!");
								// If we're still on main screen
								if(altimg==null) {
									// This is a RIG to force setPtySize() to work during setSize()
									// It appears setPtySize() ignores repeat calls with same values despite having an alternate screen being used
									// If we don't do this then setPtySize() calls during alternate screen use doesn't seem to apply to main screen
									term_width++;
									connection.requestResize(term);
									term_width--;
									connection.requestResize(term);
									// We completed the resize on main screen, reset alternate resized flag
									altResized = 0;
								}
								// Allow another thread to start later
								altResizeThread = null;
							};
						};
						// Start thread
						altResizeThread.start();
					}
				}
				// Restore main screen (force execution)
				setSize(getTermWidth()+1, getTermHeight());
			}
		}
	}

	// Returns current image (screen)
	private BufferedImage getImg() {
		if(altimg!=null)
			return altimg;
		return img;
	}

	// Return terminal connection
	public Connection getConnection() {
		return connection;
	}

	// Show/Hide Cursor
	public void setShowCursor(boolean showCursor) {
		this.showCursor = showCursor;
	}

}
