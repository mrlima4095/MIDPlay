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

import java.util.Vector;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.utils.Constants;

public class FormKeyboardShortcut extends Form implements CommandListener {

	private JM2PCMIDlet midlet;

	private ChoiceGroup cgKeys;
	private TextField tfCharacter;

	private Vector keys;

	private Command cmOk;
	private Command cmBack;
	private Command cmClear;

	public FormKeyboardShortcut(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("shortcuts"));
		this.midlet = midlet;

		cgKeys = new ChoiceGroup(midlet.messages.getMessage("specialKeys"),
				Choice.MULTIPLE);
		createKeys();

		tfCharacter = new TextField(midlet.messages.getMessage("char"), "", 1,
				TextField.ANY);

		append(tfCharacter);

		append(cgKeys);

		cmOk = new Command(midlet.messages.getMessage("send"), Command.OK, 1);
		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				2);
		cmClear = new Command(midlet.messages.getMessage("clear"),
				Command.SCREEN, 2);

		addCommand(cmOk);
		addCommand(cmBack);
		addCommand(cmClear);
		setCommandListener(this);

	}

	public void createKeys() {
		keys = new Vector();

		Key t = new Key();

		t.setName("Shift");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("SHIFT");

		keys.addElement(t);

		t = new Key();

		t.setName("Alt");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("ALT");

		keys.addElement(t);

		t = new Key();

		t.setName("Ctrl");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("CTRL");

		keys.addElement(t);

		t = new Key();

		t.setName("Enter");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("ENTER");

		keys.addElement(t);

		t = new Key();

		t.setName("Backspace");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("BACK");

		keys.addElement(t);

		t = new Key();

		t.setName("Esc");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("ESC");

		keys.addElement(t);

		t = new Key();

		t.setName("Tab");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("TAB");

		keys.addElement(t);

		t = new Key();

		t.setName(midlet.messages.getMessage("space"));
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("SPACE");

		keys.addElement(t);

		t = new Key();

		t.setName("Caps Lock");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("CAPS");

		keys.addElement(t);

		t = new Key();

		t.setName("F1");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F1");

		keys.addElement(t);

		t = new Key();

		t.setName("F2");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F2");

		keys.addElement(t);

		t = new Key();

		t.setName("F3");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F3");

		keys.addElement(t);

		t = new Key();

		t.setName("F4");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F4");

		keys.addElement(t);

		t = new Key();

		t.setName("F5");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F5");

		keys.addElement(t);

		t = new Key();

		t.setName("F6");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F6");

		keys.addElement(t);

		t = new Key();

		t.setName("F7");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F7");

		keys.addElement(t);

		t = new Key();

		t.setName("F8");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F8");

		keys.addElement(t);

		t = new Key();

		t.setName("F9");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F9");

		keys.addElement(t);

		t = new Key();

		t.setName("F10");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F10");

		keys.addElement(t);

		t = new Key();

		t.setName("F11");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F11");

		keys.addElement(t);

		t = new Key();

		t.setName("F12");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("F12");

		keys.addElement(t);

		t = new Key();

		t.setName("Insert");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("INSERT");

		keys.addElement(t);

		t = new Key();

		t.setName("Delete");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("DEL");

		keys.addElement(t);

		t = new Key();

		t.setName("Home");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("HOME");

		keys.addElement(t);

		t = new Key();

		t.setName("End");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("END");

		keys.addElement(t);

		t = new Key();

		t.setName("Page Up");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("PAGEUP");

		keys.addElement(t);

		t = new Key();

		t.setName("Page Down");
		t.setType(Key.SPECIAL_TYPE);
		t.setContent("PAGEDOWN");

		keys.addElement(t);

		for (int i = 0; i < keys.size(); i++) {

			Key tecla = (Key) keys.elementAt(i);
			cgKeys.append(tecla.getName(), null);

		}

	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmOk) {

			try {
				boolean[] selection = new boolean[cgKeys.size()];
				cgKeys.getSelectedFlags(selection);

				StringBuffer sbParam = new StringBuffer();
				sbParam.append(Constants.CMD_TECLADO_TP_ESPECIAL);
				sbParam.append(' ');

				int count = 0;

				for (int i = 0; i < selection.length; i++) {

					if (selection[i]) {
						count++;
						Key key = (Key) keys.elementAt(i);
						sbParam.append(key.getContent());
						sbParam.append(' ');

					}
				}

				String character = tfCharacter.getString();

				if (character.trim().length() > 0) {
					count++;
					sbParam.append(character.charAt(0));
				}

				if (count < 1 || count > 3) {
					midlet.showAlert(
							midlet.messages.getMessage("errorKey1to3"),
							AlertType.WARNING, false, this);
				} else {
					midlet.sendCommand(Constants.CMD_TECLADO, sbParam
							.toString());

				}
			} catch (ArrayIndexOutOfBoundsException e) {
				midlet.showAlert(midlet.messages.getMessage("invalidKey"),
						AlertType.WARNING, false, this);
			}

		} else if (c == cmClear) {
			cgKeys.setSelectedFlags(new boolean[cgKeys.size()]);
			tfCharacter.setString("");
		} else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
