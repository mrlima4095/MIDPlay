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

import javax.microedition.lcdui.*;

import jm2pc.client.*;

public class ListKeyboard extends List implements CommandListener {

	public static final int TEXT_KEYBOARD = 0;
	public static final int SPECIAL_KEYBOARD = 1;

	private JM2PCMIDlet midlet;

	private TextBox tbkeyboardText;
	private Form fmKeyboardShortcut;

	private Command cmBack;

	public ListKeyboard(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("keyboard"), List.IMPLICIT);

		this.midlet = midlet;

		tbkeyboardText = new TextKeyboard(midlet);
		fmKeyboardShortcut = new FormKeyboardShortcut(midlet);

		append(midlet.messages.getMessage("keyText"), null);
		append(midlet.messages.getMessage("shortcuts"), null);

		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				1);

		addCommand(cmBack);
		setCommandListener(this);
	}

	public void commandAction(Command c, Displayable d) {

		if (c == List.SELECT_COMMAND) {

			switch (getSelectedIndex()) {
			case TEXT_KEYBOARD:
				midlet.getDisplayManager().pushDisplayable(tbkeyboardText);
				break;

			case SPECIAL_KEYBOARD:
				midlet.getDisplayManager().pushDisplayable(fmKeyboardShortcut);
				break;
			}

		} else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		}

	}

}
