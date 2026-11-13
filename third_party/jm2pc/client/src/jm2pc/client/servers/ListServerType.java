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

package jm2pc.client.servers;

import javax.microedition.lcdui.*;

import jm2pc.client.JM2PCMIDlet;

public class ListServerType extends List implements CommandListener {

	private JM2PCMIDlet midlet;
	private FormServer fmServer;

	private Command cmBack;
	private Command cmOk;

	public ListServerType(JM2PCMIDlet midlet, FormServer fmServidor) {
		super(midlet.messages.getMessage("connectionType"), List.IMPLICIT);
		this.midlet = midlet;
		this.fmServer = fmServidor;

		append("Internet (GPRS/EDGE/3G)", null);
		append("SSL - Internet (GPRS/EDGE/3G)", null);

		cmOk = new Command(midlet.messages.getMessage("ok"), Command.OK, 1);
		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				2);

		addCommand(cmOk);
		addCommand(cmBack);

		setCommandListener(this);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmOk || c == SELECT_COMMAND) {
			Server server = new Server();

			Displayable nextDisplay = fmServer;

			switch (getSelectedIndex()) {
			case 0:
				server.setType(Server.TYPE_SOCKET);
				break;
			case 1:
				server.setType(Server.TYPE_SSL);
				break;

			}

			fmServer.setServer(server);
			midlet.getDisplayManager().pushDisplayable(nextDisplay, false);

		} else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
