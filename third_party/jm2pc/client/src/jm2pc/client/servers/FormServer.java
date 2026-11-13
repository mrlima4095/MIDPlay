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

import jm2pc.client.*;

public class FormServer extends Form implements CommandListener {

	private JM2PCMIDlet midlet;

	private ListServers lsServers;
	private Server server;

	private TextField tfDescription;
	private TextField tfAddress;
	private TextField tfPort;
	private TextField tfPassword;

	private Command cmConnect;
	private Command cmSave;
	private Command cmCancel;

	public FormServer(JM2PCMIDlet midlet, ListServers lsServers,
			Server server) {
		super(midlet.messages.getMessage("server"));

		this.midlet = midlet;
		this.lsServers = lsServers;

		tfDescription = new TextField(
				midlet.messages.getMessage("description"), null, 80,
				TextField.ANY);
		tfAddress = new TextField(midlet.messages.getMessage("host"), null, 80,
				TextField.ANY);
		tfPort = new TextField(midlet.messages.getMessage("port"), null, 5,
				TextField.NUMERIC);
		tfPassword = new TextField(midlet.messages.getMessage("password"),
				null, 30, TextField.ANY | TextField.PASSWORD);

		setServer(server);

		this.append(tfDescription);
		this.append(tfAddress);
		this.append(tfPort);
		this.append(tfPassword);

		cmConnect = new Command(midlet.messages.getMessage("connect"),
				Command.OK, 1);
		cmSave = new Command(midlet.messages.getMessage("save"),
				Command.SCREEN, 2);
		cmCancel = new Command(midlet.messages.getMessage("cancel"),
				Command.CANCEL, 3);

		this.addCommand(cmConnect);
		this.addCommand(cmSave);
		this.addCommand(cmCancel);

		this.setCommandListener(this);
	}

	public void setServer(Server server) {

		if (server != null) {
			this.server = server;

			tfDescription.setString(server.getDescription());
			tfAddress.setString(server.getAddress());
			tfPort.setString(String.valueOf(server.getPort()));
			tfPassword.setString(server.getPassword());

			switch (server.getType()) {
			case Server.TYPE_BTSPP:
				tfAddress.setLabel(midlet.messages.getMessage("btAddress"));
				tfPort.setLabel(midlet.messages.getMessage("channel"));
				break;
			default:
				tfAddress.setLabel(midlet.messages.getMessage("host"));
				tfPort.setLabel(midlet.messages.getMessage("port"));
			}
		} else {
			this.server = new Server();
			clear();
		}
	}

	public void clear() {
		tfDescription.setString("");
		tfAddress.setString("");
		tfPort.setString("");
		tfPassword.setString("");
	}

	private boolean validateData() {
		String description = tfDescription.getString();
		String address = tfAddress.getString();
		String port = tfPort.getString();
		String password = tfPassword.getString();

		if (address.trim().length() == 0) {
			midlet.showAlert(midlet.messages.getMessage("noHost"),
					AlertType.ERROR, true, this);
			return false;
		}
		if (port.trim().length() == 0) {
			midlet.showAlert(midlet.messages.getMessage("noPort"),
					AlertType.ERROR, true, this);
			return false;
		}
		if (password.trim().length() == 0) {
			midlet.showAlert(midlet.messages.getMessage("noPassword"),
					AlertType.ERROR, true, this);
			return false;
		}
		if (description.trim().length() == 0) {
			description = midlet.messages.getMessage("unknown");
		}

		server.setDescription(description);
		server.setAddress(address);
		server.setPort(Integer.parseInt(port));
		server.setPassword(password);

		return true;
	}

	private void save() {
		if (validateData()) {
			try {
				lsServers.getRepServers().saveServer(server);
				lsServers.fillList();
				midlet.getDisplayManager().popDisplayable();
			} catch (Exception e) {
				StringBuffer sb = new StringBuffer(midlet.messages
						.getMessage("errorSaveServer"));
				sb.append(" [");
				sb.append(e.toString());
				sb.append("]");

				midlet.showAlert(sb.toString(), AlertType.ERROR, true, this);
			}
		}
	}

	private void connect() {
		if (validateData()) {
			try {
				midlet.connect(server);
			} catch (Exception e) {

			}
		}
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmSave) {
			save();
		} else if (c == cmConnect) {
			connect();
		} else if (c == cmCancel) {
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
