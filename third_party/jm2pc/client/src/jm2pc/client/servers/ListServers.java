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

import java.util.Vector;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.client.config.FormConfig;
import jm2pc.client.i18n.ListLanguage;
import jm2pc.client.utils.Images;

public class ListServers extends List implements CommandListener {

	private JM2PCMIDlet midlet;

	private ServerRepository repServers;

	private List lsServerType;
	private FormServer fmServer;

	private Image icon;
	private Image iconSSL;
	private Image iconBlueT;

	private Vector servers;

	private Command cmConnect;
	private Command cmConfig;
	private Command cmLanguage;
	private Command cmEdit;
	private Command cmDelete;
	private Command cmNew;
	private Command cmExit;

	public ListServers(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("servers"), Choice.IMPLICIT);
		this.midlet = midlet;

		icon = Image.createImage(Images.createIconServidor());
		iconSSL = Image.createImage(Images.createIconServidorSSL());
		iconBlueT = Image.createImage(Images.createIconServidorBlueT());

		repServers = new ServerRepository();
		servers = new Vector();

		fillList();

		cmConnect = new Command(midlet.messages.getMessage("connect"),
				Command.OK, 1);
		cmConfig = new Command(midlet.messages.getMessage("config"),
				Command.SCREEN, 2);
		cmNew = new Command(midlet.messages.getMessage("new"), Command.SCREEN,
				3);
		cmEdit = new Command(midlet.messages.getMessage("edit"),
				Command.SCREEN, 4);
		cmDelete = new Command(midlet.messages.getMessage("delete"),
				Command.SCREEN, 5);
		cmLanguage = new Command(midlet.messages.getMessage("language"),
				Command.SCREEN, 6);
		cmExit = new Command(midlet.messages.getMessage("exit"), Command.EXIT,
				7);

		this.addCommand(cmConnect);
		this.addCommand(cmConfig);
		this.addCommand(cmNew);
		this.addCommand(cmEdit);
		this.addCommand(cmDelete);
		this.addCommand(cmLanguage);
		this.addCommand(cmExit);

		this.setCommandListener(this);

		fmServer = new FormServer(midlet, this, null);
		lsServerType = new ListServerType(midlet, fmServer);

	}

	private void clearList() {
		for (int i = size() - 1; i >= 0; i--)
			delete(i);
	}

	public void fillList() {
		try {
			servers = repServers.loadServers();
			clearList();

			for (int i = 0; i < servers.size(); i++) {
				Server server = (Server) servers.elementAt(i);
				switch (server.getType()) {

				case Server.TYPE_SOCKET:
					append(server.getDescription(), icon);
					break;
				case Server.TYPE_SSL:
					append(server.getDescription(), iconSSL);
					break;
				case Server.TYPE_BTSPP:
					append(server.getDescription(), iconBlueT);
					break;
				}
			}
		} catch (Exception e) {
			midlet.showAlert(midlet.messages
					.getMessage("errorServerListRefresh"), AlertType.ERROR,
					true, null);
		}

	}

	public ServerRepository getRepServers() {
		return repServers;
	}

	private void connect() {
		try {
			Server server = (Server) servers.elementAt(getSelectedIndex());
			midlet.connect(server);
		} catch (ArrayIndexOutOfBoundsException e) {
			midlet.showAlert(midlet.messages.getMessage("noServerSelected"),
					AlertType.WARNING, false, this);
		}
	}

	private void delete() {
		try {
			Server server = (Server) servers.elementAt(getSelectedIndex());
			repServers.deleteServer(server);
			fillList();
		} catch (ArrayIndexOutOfBoundsException e) {
			midlet.showAlert(midlet.messages.getMessage("noServerSelected"),
					AlertType.WARNING, false, this);
		} catch (Exception e) {
			StringBuffer sb = new StringBuffer(midlet.messages
					.getMessage("errorServerDelete"));
			sb.append(" [");
			sb.append(e.toString());
			sb.append("]");
			midlet.showAlert(sb.toString(), AlertType.WARNING, false, this);
		}
	}

	private void edit() {
		try {
			Server server = (Server) servers.elementAt(this.getSelectedIndex());
			fmServer.setServer(server);
			midlet.getDisplayManager().pushDisplayable(fmServer);
		} catch (ArrayIndexOutOfBoundsException e) {
			midlet.showAlert(midlet.messages.getMessage("noServerSelected"),
					AlertType.WARNING, false, this);
		}
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmConnect) {
			connect();
		} else if (c == List.SELECT_COMMAND) {
			connect();
		} else if (c == cmConfig) {
			midlet.getDisplayManager().pushDisplayable(new FormConfig(midlet));
		} else if (c == cmEdit) {
			edit();
		} else if (c == cmNew) {
			midlet.getDisplayManager().pushDisplayable(lsServerType);
		} else if (c == cmDelete) {
			delete();
		} else if (c == cmLanguage) {
			midlet.getDisplayManager()
					.pushDisplayable(new ListLanguage(midlet));
		} else if (c == cmExit) {
			midlet.destroyApp(true);
		}
	}

}
