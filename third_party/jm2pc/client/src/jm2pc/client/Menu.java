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

package jm2pc.client;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;

import jm2pc.client.devices.in.CanvasMouse;
import jm2pc.client.devices.in.ListKeyboard;
import jm2pc.client.devices.out.ListScreen;
import jm2pc.client.filesystem.ListFiles;
import jm2pc.client.plugins.FormPlugins;
import jm2pc.client.shell.FormShell;
import jm2pc.client.shell.TextRun;
import jm2pc.client.utils.CanvasAbout;
import jm2pc.client.utils.Images;

public class Menu extends List implements CommandListener {

	private JM2PCMIDlet midlet;

	private Canvas cvMouse;

	private FormPlugins fmPlugin;
	private Form fmShell;

	private TextBox tbExecute;
	private TextBox tbMessage;

	private List lsKeyboard;
	private List lsScreen;
	private ListFiles lsFiles;

	private Command cmSend;
	private Command cmBack;
	private Command cmDisconnect;

	public static final int ABOUT = 0;
	public static final int EXEC = 1;
	public static final int SHELL = 2;
	public static final int FS = 3;
	public static final int MSG = 4;
	public static final int MOUSE = 5;
	public static final int KEY = 6;
	public static final int SCREEN = 7;
	public static final int PLUG = 8;
	public static final int TRANSF = 9;

	public Menu(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("menu"), List.IMPLICIT);
		this.midlet = midlet;

		lsScreen = new ListScreen(midlet);

		lsFiles = new ListFiles(midlet);

		append(midlet.messages.getMessage("about"), Image.createImage(Images
				.createIconSobre()));
		append(midlet.messages.getMessage("run"), Image.createImage(Images
				.createIconExecutar()));
		append(midlet.messages.getMessage("shell"), Image.createImage(Images
				.createIconShell()));
		append(midlet.messages.getMessage("fileSystem"), Image
				.createImage(Images.createIconDirectory()));
		append(midlet.messages.getMessage("message"), Image.createImage(Images
				.createIconMsg()));
		append(midlet.messages.getMessage("mouse"), Image.createImage(Images
				.createIconMouse()));
		append(midlet.messages.getMessage("keyboard"), Image
				.createImage(Images.createIconTeclado()));
		append(midlet.messages.getMessage("screen"), Image.createImage(Images
				.createIconTela()));
		append(midlet.messages.getMessage("plugin"), Image.createImage(Images
				.createIconPlugin()));
		append(midlet.messages.getMessage("transferred"), Image
				.createImage(Images.createIconTransferencia()));

		cmDisconnect = new Command(midlet.messages.getMessage("disconnect"),
				Command.EXIT, 1);
		addCommand(cmDisconnect);
		setCommandListener(this);

		cvMouse = new CanvasMouse(midlet);

		fmPlugin = new FormPlugins(midlet);

		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				1);
		cmSend = new Command(midlet.messages.getMessage("send"), Command.OK, 1);

		fmShell = new FormShell(midlet);

		tbMessage = new TextBox(midlet.messages.getMessage("message"), null,
				800, 0);

		tbExecute = new TextRun(midlet);

		tbMessage.addCommand(cmBack);
		tbMessage.addCommand(cmSend);
		tbMessage.setCommandListener(this);

		lsKeyboard = new ListKeyboard(midlet);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == List.SELECT_COMMAND) {
			switch (getSelectedIndex()) {
			case ABOUT:
				midlet.getDisplayManager().pushDisplayable(
						new CanvasAbout(midlet, this));
				break;
			case EXEC:
				midlet.getDisplayManager().pushDisplayable(tbExecute);
				break;

			case SHELL:
				midlet.getDisplayManager().pushDisplayable(fmShell);
				break;

			case FS:
				midlet.getDisplayManager().pushDisplayable(lsFiles);
				break;

			case MSG:
				midlet.getDisplayManager().pushDisplayable(tbMessage);
				break;

			case MOUSE:
				midlet.getDisplayManager().pushDisplayable(cvMouse);
				break;

			case KEY:
				midlet.getDisplayManager().pushDisplayable(lsKeyboard);
				break;

			case SCREEN:
				midlet.getDisplayManager().pushDisplayable(lsScreen);
				break;

			case PLUG:
				midlet.getDisplayManager().pushDisplayable(fmPlugin);
				break;

			case TRANSF:
				midlet.showTransferredBytes();
				break;
			}
		} else if (c == cmDisconnect) {
			midlet.disconnect();
		} else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		} else if (c == cmSend) {
			if (d == tbMessage) {
				midlet.sendCommand("MSG", tbMessage.getString().replace('\n',
						'|'));
			}
		}
	}

	public Displayable getTeclado() {
		return lsKeyboard;
	}

	public Form getFormShell() {
		return fmShell;
	}

	public List getListArquivos() {
		return lsFiles;
	}

	public FormPlugins getPluginForm() {
		return fmPlugin;
	}

}
