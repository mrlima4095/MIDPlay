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

package jm2pc.client.shell;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.utils.Constants;

public class TextRun extends TextBox implements CommandListener {

	private JM2PCMIDlet midlet;

	private Command cmRun;
	private Command cmBack;

	public TextRun(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("run"), null, 100, TextField.ANY);
		this.midlet = midlet;

		cmRun = new Command(midlet.messages.getMessage("run"), Command.OK, 1);
		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				2);

		addCommand(cmRun);
		addCommand(cmBack);
		setCommandListener(this);
	}

	private void run() {
		String cmd = getString();
		if (cmd.trim().length() == 0) {
			midlet.showAlert(midlet.messages.getMessage("errorRun"),
					AlertType.ERROR, false, this);
		} else {
			cmd = cmd.replace('\n', '-');

			StringBuffer sbParam = new StringBuffer();
			sbParam.append(Constants.CMD_PROCESSO_TP_EXECUTAR);
			sbParam.append(' ');
			sbParam.append(cmd);

			midlet.sendCommand(Constants.CMD_PROCESSO, sbParam.toString());

		}
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmRun) {
			run();
		} else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
