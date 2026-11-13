/*
    JM2PC - Java Mobile to PC
    Copyright (C) 2005-2009 - Walter Alves Wanderley
    Recife - PE - Brazil

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package jm2pc.client.devices.out;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.client.devices.in.FormMouse;
import jm2pc.client.devices.in.FormKeyboardShortcut;
import jm2pc.client.devices.in.TextKeyboard;
import jm2pc.client.plugins.PlugIns;
import jm2pc.utils.Constants;

public class CanvasScreen extends Canvas implements CommandListener {

	private JM2PCMIDlet midlet;

	private Zoom zoom;

	private ListOptionControl lsControl;

	private Command cmBack;
	private Command cmControl;
	private Command cmDoubleClick;
	private Command cmKeyboardText;
	private Command cmKeyboardShortcut;

	private Image image;

	private int width;
	private int height;

	private int cursorX;
	private int cursorY;
	private int type;

	public boolean pressing;

	public CanvasScreen(JM2PCMIDlet midlet) {

		this.midlet = midlet;

		zoom = midlet.getImageZoom();

		width = getWidth();
		height = getHeight();

		image = null;
		cursorX = 0;
		cursorY = 0;

		pressing = false;
		lsControl = new ListOptionControl(midlet);

		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				1);
		cmControl = new Command(midlet.messages.getMessage("control"),
				Command.SCREEN, 2);
		cmDoubleClick = new Command(midlet.messages.getMessage("doubleClick"),
				Command.SCREEN, 3);
		cmKeyboardShortcut = new Command(midlet.messages
				.getMessage("shortcuts"), Command.SCREEN, 4);
		cmKeyboardText = new Command(midlet.messages.getMessage("keyText"),
				Command.SCREEN, 5);

		addCommand(cmBack);
		addCommand(cmControl);
		addCommand(cmDoubleClick);
		addCommand(cmKeyboardShortcut);
		addCommand(cmKeyboardText);

		setCommandListener(this);
	}

	class MoveThread extends Thread {

		private int x;
		private int y;

		public MoveThread(int x, int y) {
			this.x = x;
			this.y = y;

			setPriority(Thread.NORM_PRIORITY);
			start();
		}

		public void run() {
			while (pressing) {
				cursorMove(x, y);
				repaint();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
		}

		public void cursorMove(int x, int y) {
			cursorX += x;
			cursorY += y;

			if (cursorX < 0)
				cursorX = 0;
			else if (cursorX > width - 1)
				cursorX = width - 1;

			if (cursorY < 0)
				cursorY = 0;
			else if (cursorY > height - 1)
				cursorY = height - 1;
		}

	}

	public void setType(int tipo) {
		this.type = tipo;
	}

	public void setImage(Image im) {
		image = im;
	}

	public void getServerImage(int x, int y) {

		midlet.setScreenXY(x, y);

		StringBuffer sb = new StringBuffer();
		sb.append(Constants.CMD_TELA);
		sb.append(' ');
		sb.append(Constants.CMD_TELA_TP_XY);
		sb.append(' ');
		sb.append(midlet.getScreenX());
		sb.append(' ');
		sb.append(midlet.getScreenY());
		sb.append(' ');
		sb.append(zoom.getZoomImg());
		sb.append('\n');

		midlet.executeCommand(sb.toString());
	}

	protected void paint(Graphics g) {
		g.setColor(0, 0, 0);
		g.fillRect(0, 0, width, height);

		if (image != null)
			g.drawImage(image, width / 2, height / 2, Graphics.VCENTER
					| Graphics.HCENTER);

		if (lsControl.getControl() == ListOptionControl.MOUSE) {
			g.setColor(0, 0, 0);

			g.drawLine(cursorX, cursorY, cursorX, cursorY + 14);

			g.drawLine(cursorX, cursorY, cursorX + 10, cursorY + 10);

			g.drawLine(cursorX, cursorY + 14, cursorX + 10, cursorY + 10);

			g.drawLine(cursorX + 4, cursorY + 12, cursorX + 9, cursorY + 22);
			g.drawLine(cursorX + 6, cursorY + 12, cursorX + 10, cursorY + 21);

			g.setColor(255, 255, 255);

			g.drawLine(cursorX + 5, cursorY + 12, cursorX + 9, cursorY + 21);

			g.drawLine(cursorX + 1, cursorY + 2, cursorX + 1, cursorY + 13);
			g.drawLine(cursorX + 2, cursorY + 3, cursorX + 2, cursorY + 12);
			g.drawLine(cursorX + 3, cursorY + 4, cursorX + 3, cursorY + 12);
			g.drawLine(cursorX + 4, cursorY + 5, cursorX + 4, cursorY + 12);
			g.drawLine(cursorX + 5, cursorY + 6, cursorX + 5, cursorY + 11);
			g.drawLine(cursorX + 6, cursorY + 7, cursorX + 6, cursorY + 11);
			g.drawLine(cursorX + 7, cursorY + 8, cursorX + 7, cursorY + 10);
			g.drawLine(cursorX + 8, cursorY + 9, cursorX + 8, cursorY + 10);

		} else if (lsControl.getControl() == ListOptionControl.SCROLL) {
			g.setColor(0, 0, 0);

			g.drawLine(width / 2 - 1, height / 2 - 5, width / 2 - 1,
					height / 2 + 5);
			g.drawLine(width / 2 + 1, height / 2 - 5, width / 2 + 1,
					height / 2 + 5);

			g.drawLine(width / 2 - 5, height / 2 - 1, width / 2 + 5,
					height / 2 - 1);
			g.drawLine(width / 2 - 5, height / 2 + 1, width / 2 + 5,
					height / 2 + 1);

			g.setColor(255, 255, 255);

			g.drawLine(width / 2, height / 2 - 5, width / 2, height / 2 + 5);
			g.drawLine(width / 2 - 5, height / 2, width / 2 + 5, height / 2);

		}
	}

	public void commandAction(Command c, Displayable d) {

		if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		} else if (c == cmControl) {
			midlet.getDisplayManager().pushDisplayable(lsControl, true);
			repaint();
		} else if (c == cmDoubleClick) {

			int cursorRealX = midlet.getScreenX() + cursorX * zoom.getZoomImg();
			int cursorRealY = midlet.getScreenY() + cursorY * zoom.getZoomImg();

			StringBuffer sbCommand = new StringBuffer();
			sbCommand.append(Constants.CMD_MOUSE);
			sbCommand.append(' ');
			sbCommand.append(Constants.CMD_MOUSE_TP_DOUBLE_CLICK);
			sbCommand.append(' ');
			sbCommand.append(cursorRealX);
			sbCommand.append(' ');
			sbCommand.append(cursorRealY);
			sbCommand.append(' ');
			sbCommand.append(FormMouse.LEFT_BUTTON);
			sbCommand.append('\n');

			midlet.executeCommand(sbCommand.toString());
		} else if (c == cmKeyboardShortcut) {
			midlet.getDisplayManager().pushDisplayable(
					new FormKeyboardShortcut(midlet), true);
		} else if (c == cmKeyboardText) {
			midlet.getDisplayManager().pushDisplayable(
					new TextKeyboard(midlet), true);
		}
	}

	public int getCursorX() {
		return cursorX;
	}

	public int getCursorY() {
		return cursorY;
	}

	public ListOptionControl getControlOption() {
		return lsControl;
	}

	private void sendPluginCommand(int keyCode, int control) {
		String cmd = PlugIns.getPlugin(control - 2);
		String param = getKeyName(keyCode);
		midlet.sendCommand(cmd, param);
	}

	protected void keyPressed(int keyCode) {

		pressing = true;

		int control = lsControl.getControl();

		switch (keyCode) {
		case KEY_NUM1: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(-3, -3);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX()
						- (super.getWidth() * zoom.getZoomImg()), midlet
						.getScreenY()
						- (super.getHeight() * zoom.getZoomImg()));
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_NUM2: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(0, -3);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX(), midlet.getScreenY()
						- (super.getHeight() * zoom.getZoomImg()));
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_NUM3: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(3, -3);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX()
						+ (super.getWidth() * zoom.getZoomImg()), midlet
						.getScreenY()
						- (super.getHeight() * zoom.getZoomImg()));
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_NUM4: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(-3, 0);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX()
						- (super.getWidth() * zoom.getZoomImg()), midlet
						.getScreenY());
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}

		case KEY_NUM5: {
			StringBuffer sbCommand = new StringBuffer();
			sbCommand.append(Constants.CMD_TELA);
			sbCommand.append(' ');

			if (type == ListScreen.SCREEN_XY) {
				sbCommand.append(Constants.CMD_TELA_TP_XY);
				sbCommand.append(' ');
				sbCommand.append(midlet.getScreenX());
				sbCommand.append(' ');
				sbCommand.append(midlet.getScreenY());
				sbCommand.append(' ');
				sbCommand.append(midlet.getImageZoom().getZoomImg());

			} else {
				sbCommand.append(Constants.CMD_TELA_TP_INT);
			}

			sbCommand.append('\n');
			midlet.executeCommand(sbCommand.toString());
			break;
		}
		case KEY_NUM6: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(3, 0);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX()
						+ (super.getWidth() * zoom.getZoomImg()), midlet
						.getScreenY());
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_NUM7: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(-3, 3);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX()
						- (super.getWidth() * zoom.getZoomImg()), midlet
						.getScreenY()
						+ (super.getHeight() * zoom.getZoomImg()));
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_NUM8: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(0, 3);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX(), midlet.getScreenY()
						+ (super.getHeight() * zoom.getZoomImg()));
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_NUM9: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(3, 3);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX()
						+ (super.getWidth() * zoom.getZoomImg()), midlet
						.getScreenY()
						+ (super.getHeight() * zoom.getZoomImg()));
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_STAR: // '*'
		{
			int cursorRealX;
			int cursorRealY;

			if (control == ListOptionControl.MOUSE
					|| control == ListOptionControl.SCROLL) {
				if (type == ListScreen.SCREEN_XY) {
					if (control == ListOptionControl.MOUSE) {
						cursorRealX = midlet.getScreenX() + cursorX
								* zoom.getZoomImg();
						cursorRealY = midlet.getScreenY() + cursorY
								* zoom.getZoomImg();

						StringBuffer sbComando = new StringBuffer();
						sbComando.append(Constants.CMD_MOUSE);
						sbComando.append(' ');
						sbComando.append(Constants.CMD_MOUSE_TP_CLICK);
						sbComando.append(' ');
						sbComando.append(cursorRealX);
						sbComando.append(' ');
						sbComando.append(cursorRealY);
						sbComando.append(' ');
						sbComando.append(FormMouse.LEFT_BUTTON);
						sbComando.append('\n');

						midlet.executeCommand(sbComando.toString());
					} else if (control == ListOptionControl.SCROLL) {
						if (zoom.getZoomImg() <= Zoom.ZOOM_MAX)
							break;
						else {
							zoom.setZoomImg(zoom.getZoomImg() - 1);
							cursorRealX = midlet.getScreenX()
									+ ((super.getWidth() / 2) * zoom
											.getZoomImg());
							cursorRealY = midlet.getScreenY()
									+ ((super.getHeight() / 2) * zoom
											.getZoomImg());
							getServerImage(cursorRealX, cursorRealY);
						}

					}
				} else if (type == ListScreen.SCREEN_INT) {
					cursorRealX = (midlet.getServerScreenWidth() / this
							.getWidth())
							* cursorX;
					cursorRealY = (midlet.getServerScreenHeight() / this
							.getHeight())
							* cursorY;

					getServerImage(cursorRealX, cursorRealY);
				}
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_POUND: // '#'
		{
			int cursorRealX = 0;
			int cursorRealY = 0;

			if (control == ListOptionControl.MOUSE
					|| control == ListOptionControl.SCROLL) {

				if (type == ListScreen.SCREEN_INT) {
					break;
				} else if (type == ListScreen.SCREEN_XY) {

					if (control == ListOptionControl.MOUSE) {
						cursorRealX = midlet.getScreenX() + cursorX
								* zoom.getZoomImg();
						cursorRealY = midlet.getScreenY() + cursorY
								* zoom.getZoomImg();

						StringBuffer sbComando = new StringBuffer();
						sbComando.append(Constants.CMD_MOUSE);
						sbComando.append(' ');
						sbComando.append(Constants.CMD_MOUSE_TP_CLICK);
						sbComando.append(' ');
						sbComando.append(cursorRealX);
						sbComando.append(' ');
						sbComando.append(cursorRealY);
						sbComando.append(' ');
						sbComando.append(FormMouse.RIGHT_BUTTON);
						sbComando.append('\n');

						midlet.executeCommand(sbComando.toString());
					} else if (control == ListOptionControl.SCROLL) {
						if (zoom.getZoomImg() >= Zoom.ZOOM_LOWEST)
							break;
						else {
							cursorRealX = midlet.getScreenX()
									- ((super.getWidth() / 2) * zoom
											.getZoomImg());
							cursorRealY = midlet.getScreenY()
									- ((super.getHeight() / 2) * zoom
											.getZoomImg());
							zoom.setZoomImg(zoom.getZoomImg() + 1);
							getServerImage(cursorRealX, cursorRealY);
						}

					}
				}
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case KEY_NUM0: {

			if (control == ListOptionControl.MOUSE) {
				int cursorRealX = 0;
				int cursorRealY = 0;

				if (type == ListScreen.SCREEN_XY) {
					Zoom pref = midlet.getImageZoom();

					cursorRealX = midlet.getScreenX() + cursorX
							* pref.getZoomImg();
					cursorRealY = midlet.getScreenY() + cursorY
							* pref.getZoomImg();
				} else if (type == ListScreen.SCREEN_INT) {
					cursorRealX = (midlet.getServerScreenWidth() / this
							.getWidth())
							* cursorX;
					cursorRealY = (midlet.getServerScreenHeight() / this
							.getHeight())
							* cursorY;
				}

				StringBuffer sbComando = new StringBuffer();
				sbComando.append(Constants.CMD_MOUSE);
				sbComando.append(' ');
				sbComando.append(Constants.CMD_MOUSE_TP_MOVE);
				sbComando.append(' ');
				sbComando.append(cursorRealX);
				sbComando.append(' ');
				sbComando.append(cursorRealY);
				sbComando.append('\n');

				midlet.executeCommand(sbComando.toString());
			} else if (control == ListOptionControl.SCROLL) {
				StringBuffer sbComando = new StringBuffer();
				sbComando.append(Constants.CMD_TELA);
				sbComando.append(' ');
				sbComando.append(Constants.CMD_TELA_TP_INT);
				sbComando.append('\n');

				midlet.executeCommand(sbComando.toString());
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		default:
			doJoystickKeysAction(keyCode);
		}
	}

	protected void doJoystickKeysAction(int keyCode) {

		pressing = true;

		int control = lsControl.getControl();

		switch (getGameAction(keyCode)) {
		case UP: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(0, -3);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX(), midlet.getScreenY()
						- (super.getHeight() * zoom.getZoomImg()));
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case DOWN: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(0, 3);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX(), midlet.getScreenY()
						+ (super.getHeight() * zoom.getZoomImg()));
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case LEFT: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(-3, 0);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX()
						- (super.getWidth() * zoom.getZoomImg()), midlet
						.getScreenY());
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case RIGHT: {
			if (control == ListOptionControl.MOUSE) {
				new MoveThread(3, 0);
			} else if (control == ListOptionControl.SCROLL) {
				getServerImage(midlet.getScreenX()
						+ (super.getWidth() * zoom.getZoomImg()), midlet
						.getScreenY());
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		case FIRE: {
			int cursorRealX;
			int cursorRealY;

			if (control == ListOptionControl.MOUSE
					|| control == ListOptionControl.SCROLL) {
				if (type == ListScreen.SCREEN_XY) {
					if (control == ListOptionControl.MOUSE) {
						cursorRealX = midlet.getScreenX() + cursorX
								* zoom.getZoomImg();
						cursorRealY = midlet.getScreenY() + cursorY
								* zoom.getZoomImg();

						StringBuffer sbComando = new StringBuffer();
						sbComando.append(Constants.CMD_MOUSE);
						sbComando.append(' ');
						sbComando.append(Constants.CMD_MOUSE_TP_CLICK);
						sbComando.append(' ');
						sbComando.append(cursorRealX);
						sbComando.append(' ');
						sbComando.append(cursorRealY);
						sbComando.append(' ');
						sbComando.append(FormMouse.LEFT_BUTTON);
						sbComando.append('\n');

						midlet.executeCommand(sbComando.toString());
					} else if (control == ListOptionControl.SCROLL) {
						if (zoom.getZoomImg() <= Zoom.ZOOM_MAX)
							break;
						else {
							zoom.setZoomImg(zoom.getZoomImg() - 1);
							cursorRealX = midlet.getScreenX()
									+ ((super.getWidth() / 2) * zoom
											.getZoomImg());
							cursorRealY = midlet.getScreenY()
									+ ((super.getHeight() / 2) * zoom
											.getZoomImg());
							getServerImage(cursorRealX, cursorRealY);
						}

					}
				} else if (type == ListScreen.SCREEN_INT) {
					cursorRealX = (midlet.getServerScreenWidth() / this
							.getWidth())
							* cursorX;
					cursorRealY = (midlet.getServerScreenHeight() / this
							.getHeight())
							* cursorY;

					getServerImage(cursorRealX, cursorRealY);
				}
			} else {
				sendPluginCommand(keyCode, control);
			}
			break;
		}
		}
	}

	protected void keyReleased(int keyCode) {
		pressing = false;
	}
}