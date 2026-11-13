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

package jm2pc.client.devices.in;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.utils.Constants;

public class CanvasMouse extends Canvas implements CommandListener {

	private JM2PCMIDlet midlet;

	private int width;

	private int height;

	private int cursorX;

	private int cursorY;

	private String lastCommand;

	private boolean live;
	private boolean moving;
	private boolean pressing;

	private Form fmMouse;

	private Command cmBack;
	private Command cmType;
	private Command cmMouseXY;

	private static final int MOVE_SIZE = 30;
	private static final int MOVE_MIDLE_SIZE = 15;

	public CanvasMouse(JM2PCMIDlet midlet) {
		this.midlet = midlet;

		fmMouse = new FormMouse(midlet);

		width = getWidth();
		height = getHeight();

		cursorX = width / 2;
		cursorY = height / 2;

		lastCommand = null;
		live = true;
		moving = false;
		pressing = false;

		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				1);

		cmType = new Command("Mode", Command.SCREEN, 2);

		cmMouseXY = new Command("Mouse XY", Command.SCREEN, 3);

		addCommand(cmBack);
		addCommand(cmType);
		addCommand(cmMouseXY);
		setCommandListener(this);

	}

	public void paint(Graphics g) {
		g.setColor(0, 0, 0);
		g.fillRect(0, 0, getWidth(), getHeight());

		g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_BOLD,
				Font.SIZE_MEDIUM));
		g.setColor(0, 0, 230);
		g.drawString("Mouse", width / 2, 5, Graphics.TOP | Graphics.HCENTER);

		g.drawImage(createImageState(), width / 2, height / 2, Graphics.HCENTER
				| Graphics.VCENTER);

		g.drawImage(createButtonsImage(), width / 2,
				height / 2 + MOVE_SIZE + 2, Graphics.TOP | Graphics.HCENTER);

		createCursorImage(g);

	}

	private Image createImageState() {

		int width = MOVE_SIZE * 2;
		int height = MOVE_SIZE * 2;

		Image im = Image.createImage(width, height);

		Graphics g = im.getGraphics();

		g.setColor(0, 0, 0);
		g.fillRect(0, 0, width, height);

		if (live)
			g.setColor(230, 230, 0);
		else {
			g.setColor(230, 0, 0);
		}

		// inicio seta p/ cima
		g.drawLine(width / 2, height / 2, width / 2, 0);
		g.drawLine(width / 2 - 4, 4, width / 2, 0);
		g.drawLine(width / 2 + 4, 4, width / 2, 0);
		// fim seta p/ baixo

		// inicio seta p/ baixo
		g.drawLine(width / 2, height / 2, width / 2, height);
		g.drawLine(width / 2 - 4, height - 4, width / 2, height);
		g.drawLine(width / 2 + 4, height - 4, width / 2, height);
		// fim seta p/ baixo

		// inicio seta p/ esquerda
		g.drawLine(width / 2, height / 2, 0, height / 2);
		g.drawLine(4, height / 2 - 4, 0, height / 2);
		g.drawLine(4, height / 2 + 4, 0, height / 2);
		// fim seta p/ esquerda

		// inicio seta p/direita
		g.drawLine(width / 2, height / 2, width, height / 2);
		g.drawLine(width - 4, height / 2 - 4, width, height / 2);
		g.drawLine(width - 4, height / 2 + 4, width, height / 2);
		// fim seta p/ direita

		return im;
	}

	private Image createButtonsImage() {
		int width = MOVE_SIZE * 2;
		int height = MOVE_SIZE;

		Image im = Image.createImage(width, height);

		Graphics g = im.getGraphics();

		g.setColor(0, 0, 0);
		g.fillRect(0, 0, width, height);

		g.setColor(130, 131, 59);
		g.fillRoundRect(2, 2, width / 2 - 2, height - 2, 15, height - 2);

		g.setColor(130, 137, 102);
		g.fillRoundRect(width / 2 + 2, 2, width / 2 - 2, height - 2, 15,
				height - 2);

		return im;

	}

	private void createCursorImage(Graphics g) {

		if (pressing)
			g.setColor(255, 255, 255);
		else {
			g.setColor(0, 0, 0);
		}

		g.drawLine(cursorX, cursorY, cursorX, cursorY + 14);

		g.drawLine(cursorX, cursorY, cursorX + 10, cursorY + 10);

		g.drawLine(cursorX, cursorY + 14, cursorX + 10, cursorY + 10);

		g.drawLine(cursorX + 4, cursorY + 12, cursorX + 9, cursorY + 22);
		g.drawLine(cursorX + 6, cursorY + 12, cursorX + 10, cursorY + 21);

		if (pressing)
			g.setColor(0, 0, 0);
		else {
			g.setColor(255, 255, 255);
		}

		g.drawLine(cursorX + 5, cursorY + 12, cursorX + 9, cursorY + 21);

		g.drawLine(cursorX + 1, cursorY + 2, cursorX + 1, cursorY + 13);
		g.drawLine(cursorX + 2, cursorY + 3, cursorX + 2, cursorY + 12);
		g.drawLine(cursorX + 3, cursorY + 4, cursorX + 3, cursorY + 12);
		g.drawLine(cursorX + 4, cursorY + 5, cursorX + 4, cursorY + 12);
		g.drawLine(cursorX + 5, cursorY + 6, cursorX + 5, cursorY + 11);
		g.drawLine(cursorX + 6, cursorY + 7, cursorX + 6, cursorY + 11);
		g.drawLine(cursorX + 7, cursorY + 8, cursorX + 7, cursorY + 10);
		g.drawLine(cursorX + 8, cursorY + 9, cursorX + 8, cursorY + 10);
	}

	public void keyPressed(int keyCode) {

		lastCommand = null;
		moving = true;

		StringBuffer sb = new StringBuffer(Constants.CMD_MOUSE_TP_LIVE);

		switch (keyCode) {
		case KEY_NUM0:
			lastCommand = "0";
			cursorX = width / 2;
			cursorY = height / 2;
			moving = false;
			break;
		case KEY_NUM1:
			lastCommand = "1";
			cursorX = width / 2 - MOVE_MIDLE_SIZE;
			cursorY = height / 2 - MOVE_MIDLE_SIZE;
			break;
		case KEY_NUM2:
			lastCommand = "2";
			cursorX = width / 2;
			cursorY = height / 2 - MOVE_SIZE;
			break;
		case KEY_NUM3:
			lastCommand = "3";
			cursorX = width / 2 + MOVE_MIDLE_SIZE;
			cursorY = height / 2 - MOVE_MIDLE_SIZE;
			break;
		case KEY_NUM4:
			lastCommand = "4";
			cursorX = width / 2 - MOVE_SIZE;
			cursorY = height / 2;
			break;
		case KEY_NUM5:
			lastCommand = "5";
			cursorX = width / 2;
			cursorY = height / 2;
			moving = false;
			break;
		case KEY_NUM6:
			lastCommand = "6";
			cursorX = width / 2 + MOVE_SIZE;
			cursorY = height / 2;
			break;
		case KEY_NUM7:
			lastCommand = "7";
			cursorX = width / 2 - MOVE_MIDLE_SIZE;
			cursorY = height / 2 + MOVE_MIDLE_SIZE;
			break;

		case KEY_NUM8:
			lastCommand = "8";
			cursorX = width / 2;
			cursorY = height / 2 + MOVE_SIZE;
			break;
		case KEY_NUM9:
			lastCommand = "9";
			cursorX = width / 2 + MOVE_MIDLE_SIZE;
			cursorY = height / 2 + MOVE_MIDLE_SIZE;
			break;

		case KEY_STAR:
			if (pressing) {
				lastCommand = "r";
				cursorX = width / 2;
				cursorY = height / 2;
				pressing = false;
			} else {
				lastCommand = "*";
				cursorX = width / 2 - MOVE_MIDLE_SIZE;
				cursorY = height / 2 + MOVE_SIZE + 10;
			}
			moving = false;
			break;
		case KEY_POUND:
			lastCommand = "#";
			cursorX = width / 2 + MOVE_MIDLE_SIZE;
			cursorY = height / 2 + MOVE_SIZE + 10;
			moving = false;
			break;

		default:
			lastCommand = doJoystickKeyAction(keyCode);

		}

		if (lastCommand != null) {
			sb.append(" ");
			sb.append(lastCommand);
			midlet.sendCommand(Constants.CMD_MOUSE, sb.toString());
		} else {
			moving = false;
		}

		repaint();

	}

	private String doJoystickKeyAction(int keyCode) {
		String command = null;

		switch (getGameAction(keyCode)) {

		case UP:
			command = "2";
			cursorX = width / 2;
			cursorY = height / 2 - MOVE_SIZE;
			break;

		case DOWN:
			command = "8";
			cursorX = width / 2;
			cursorY = height / 2 + MOVE_SIZE;
			break;

		case LEFT:
			command = "4";
			cursorX = width / 2 - MOVE_SIZE;
			cursorY = height / 2;
			break;

		case RIGHT:
			command = "6";
			cursorX = width / 2 + MOVE_SIZE;
			cursorY = height / 2;
			break;

		case FIRE:
			if (pressing) {
				command = "r";
				cursorX = width / 2;
				cursorY = height / 2;
				pressing = false;
			} else {
				command = "*";
				cursorX = width / 2 - MOVE_MIDLE_SIZE;
				cursorY = height / 2 + MOVE_SIZE + 10;
			}
			moving = false;
			break;

		}

		return command;
	}

	public void keyReleased(int keyCode) {
		if (live && moving) {
			midlet.sendCommand(Constants.CMD_MOUSE, "LIVE 0");
			cursorX = width / 2;
			cursorY = height / 2;
			moving = false;
			repaint();
		}
	}

	public void keyRepeated(int keyCode) {
		if ((keyCode == KEY_STAR || getGameAction(keyCode) == FIRE)
				&& !pressing) {
			midlet.sendCommand(Constants.CMD_MOUSE, "LIVE p");
			cursorX = width / 2 - MOVE_MIDLE_SIZE;
			cursorY = height / 2 + MOVE_SIZE + 10;
			pressing = true;
			moving = false;
			repaint();
		}
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		} else if (c == cmType) {
			live = !live;
			repaint();
		} else if (c == cmMouseXY) {
			midlet.getDisplayManager().pushDisplayable(fmMouse);
		}
	}

}
