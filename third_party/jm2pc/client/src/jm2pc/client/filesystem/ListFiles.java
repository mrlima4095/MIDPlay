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

package jm2pc.client.filesystem;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.client.utils.CanvasWait;
import jm2pc.client.utils.Images;
import jm2pc.utils.Constants;

public class ListFiles extends List implements CommandListener {

	private JM2PCMIDlet midlet;
	private CanvasWait cvWait;

	private DataInputStream dataIn;

	private Vector list;

	private TextBox tbGoTo;

	private Command cmOpen;
	private Command cmOpenOnServer;
	private Command cmGoTo;
	private Command cmBack;

	private String absolutePath;

	public ListFiles(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("fileSystem"), List.IMPLICIT);
		this.midlet = midlet;

		cvWait = midlet.getCanvasWait();

		this.append("..", null);
		absolutePath = "/";

		tbGoTo = new TextBox(midlet.messages.getMessage("goTo"), absolutePath,
				100, TextField.ANY);

		list = new Vector();

		cmOpen = new Command(midlet.messages.getMessage("open"), Command.OK, 1);
		cmOpenOnServer = new Command(
				midlet.messages.getMessage("openOnServer"), Command.SCREEN, 2);
		cmGoTo = new Command(midlet.messages.getMessage("goTo"),
				Command.SCREEN, 3);
		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				4);

		this.addCommand(cmOpen);
		this.addCommand(cmOpenOnServer);
		this.addCommand(cmGoTo);
		this.addCommand(cmBack);

		this.setCommandListener(this);

		tbGoTo.addCommand(cmOpen);
		tbGoTo.addCommand(cmBack);
		tbGoTo.setCommandListener(this);

	}

	private void clearList() {
		for (int i = size() - 1; i >= 1; i--)
			delete(i);

		while (list.size() > 0)
			list.removeElementAt(0);
	}

	public void update() throws IOException {
		clearList();
		dataIn = new DataInputStream(midlet.getIn());

		absolutePath = dataIn.readUTF();

		cvWait.setMessage(midlet.messages.getMessage("directory"));
		readFilesList(File.TYPE_DIRECTORY);
		cvWait.setMessage(midlet.messages.getMessage("file"));
		readFilesList(File.TYPE_FILE);
		cvWait.setMessage(midlet.messages.getMessage("mounting"));
		fillList();

		setTitle(absolutePath);
	}

	private void readFilesList(int tipo) throws IOException {
		int count = dataIn.readInt();
		for (int i = 0; i < count; i++) {
			String nome = dataIn.readUTF();
			File a = new File(nome, tipo);
			list.addElement(a);
		}
	}

	private void fillList() {
		for (int i = 0; i < list.size(); i++) {
			File a = (File) list.elementAt(i);
			Image im = null;
			if (a.getType() == File.TYPE_DIRECTORY)
				im = Image.createImage(Images.createIconDirectory());

			this.append(a.getName(), im);
		}
	}

	private void backAbsolutePath() {
		char[] chars = absolutePath.toCharArray();
		int j = -1;
		for (int i = chars.length - 1; i >= 0; i--) {
			if (chars[i] == '/' || chars[i] == '\\') {
				j = i;
				break;
			}
		}

		if (j >= 0)
			absolutePath = absolutePath.substring(0, j + 1);

	}

	private String goTo() {
		StringBuffer sbParam = new StringBuffer();

		String path = tbGoTo.getString();

		if (path.trim().length() == 0) {
			path = "/";
		}

		sbParam.append(Constants.CMD_FS_TP_LIST);
		sbParam.append(' ');
		sbParam.append(path);

		return sbParam.toString();
	}

	private String open(boolean onServer) {
		StringBuffer sbParam = new StringBuffer();
		int index = getSelectedIndex();
		if (index > 0) {
			index--; // para eliminar o indice .. da lista
			File a = (File) list.elementAt(index);
			if (a.getType() != File.TYPE_DIRECTORY)
				if(onServer) {
					sbParam.append(Constants.CMD_FS_TP_OPEN_ON_SERVER);
				} else {
					sbParam.append(Constants.CMD_FS_TP_OPEN);
				}
			else
				sbParam.append(Constants.CMD_FS_TP_LIST);

			sbParam.append(' ');
			sbParam.append(absolutePath);
			sbParam.append('/');
			sbParam.append(a.getName());
		} else {
			backAbsolutePath();
			sbParam.append(Constants.CMD_FS_TP_LIST);
			sbParam.append(' ');
			sbParam.append(absolutePath);
		}

		return sbParam.toString();
	}

	public void commandAction(Command c, Displayable d) {
		if (c == SELECT_COMMAND || c == cmOpen) {

			String param = "";

			if (d == tbGoTo) {
				param = goTo();
				midlet.getDisplayManager().popDisplayable();
			} else {
				param = open(false);
			}
			midlet.sendCommand(Constants.CMD_FS, param);
		} else if (c == cmOpenOnServer) {
			midlet.sendCommand(Constants.CMD_FS, open(true));
		} else if (c == cmGoTo) {
			tbGoTo.setString(absolutePath);
			midlet.getDisplayManager().pushDisplayable(tbGoTo);
		} else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
