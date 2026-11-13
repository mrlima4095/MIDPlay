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

package jm2pc.client.i18n;

import javax.microedition.lcdui.*;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.client.utils.Images;

public class ListLanguage extends List implements CommandListener {

	private static final int DE = 0;
	private static final int EN = 1;
	private static final int ES = 2;
	private static final int IT = 3;
	private static final int PT = 4;

	private JM2PCMIDlet midlet;

	private Command cmBack;
	private Command cmOk;

	public ListLanguage(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("language"), Choice.IMPLICIT);

		this.midlet = midlet;

		append("Deutsch", Image.createImage(Images.createIconDeutsch()));
		append("English", Image.createImage(Images.createIconEnglish()));
		append("Español", Image.createImage(Images.createIconEspana()));
		append("Italiano", Image.createImage(Images.createIconItalia()));
		append("Português", Image.createImage(Images.createIconBrasil()));

		cmOk = new Command(midlet.messages.getMessage("ok"), Command.OK, 1);
		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				2);

		addCommand(cmOk);
		addCommand(cmBack);
		setCommandListener(this);

	}

	public void commandAction(Command c, Displayable d) {
		if (c == SELECT_COMMAND || c == cmOk) {
			switch (getSelectedIndex()) {
			case DE:
				midlet.changeLanguage("de");
				break;
			case EN:
				midlet.changeLanguage("en");
				break;
			case ES:
				midlet.changeLanguage("es");
				break;
			case IT:
				midlet.changeLanguage("it");
				break;
			case PT:
				midlet.changeLanguage("pt");
				break;

			default:
				midlet.showAlert(midlet.messages.getMessage("noLanguage"),
						AlertType.WARNING, true, this);
			}

		} else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
