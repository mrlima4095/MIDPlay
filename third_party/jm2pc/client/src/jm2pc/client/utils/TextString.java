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

package jm2pc.client.utils;

import java.io.DataInputStream;
import java.io.IOException;

import javax.microedition.lcdui.*;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.utils.Constants;

public class TextString extends TextBox implements CommandListener {

	private JM2PCMIDlet midlet;

	private Command cmBack;

	public TextString(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("text"), "",
				Constants.MAX_STRING_READ, TextField.ANY);
		this.midlet = midlet;

		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				1);

		addCommand(cmBack);
		setCommandListener(this);
	}

	public void updateText() throws IOException {
		DataInputStream dataIn = new DataInputStream(midlet.getIn());
		int tam = dataIn.readInt();

		byte[] data = new byte[tam];
		dataIn.readFully(data, 0, tam);
		String str = new String(data);
		this.setString(str);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmBack) {
			this.setString("");
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
