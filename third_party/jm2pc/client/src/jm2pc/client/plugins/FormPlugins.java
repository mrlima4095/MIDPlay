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

package jm2pc.client.plugins;

import javax.microedition.lcdui.*;

import jm2pc.client.JM2PCMIDlet;

public class FormPlugins extends Form implements CommandListener {

	private JM2PCMIDlet midlet;

	private CanvasPlugins cvPlugin;

	private TextField tfParam;
	private ChoiceGroup cgPlugins;

	private Command cmSend;
	private Command cmInteractive;
	private Command cmBack;

	public FormPlugins(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("plugin"));
		this.midlet = midlet;

		cvPlugin = new CanvasPlugins(midlet);

		tfParam = new TextField(midlet.messages.getMessage("param"), "", 100,
				TextField.ANY);

		cgPlugins = new ChoiceGroup(midlet.messages.getMessage("plugin"),
				Choice.EXCLUSIVE);

		cmSend = new Command(midlet.messages.getMessage("send"), Command.OK, 1);
		cmInteractive = new Command(midlet.messages.getMessage("interactive"),
				Command.SCREEN, 2);
		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				1);

		this.append(tfParam);
		this.append(cgPlugins);

		this.addCommand(cmSend);
		this.addCommand(cmInteractive);
		this.addCommand(cmBack);

		this.setCommandListener(this);
	}

	private void clearPluginsList() {
		for (int i = cgPlugins.size() - 1; i >= 0; i--)
			cgPlugins.delete(i);
	}

	private void fillPluginsList() {
		for (int i = 0; i < PlugIns.getPluginCount(); i++) {
			cgPlugins.append(PlugIns.getPlugin(i), null);
		}
	}

	public void updatePlugins() {
		clearPluginsList();
		fillPluginsList();
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmSend) {
			int i = cgPlugins.getSelectedIndex();
			if (i != -1) {
				String params = tfParam.getString();
				if (params.trim().length() == 0) {
					midlet.getDisplayManager().showAlert("errorNoParam",
							AlertType.ERROR, true, this);
				} else {
					params.replace('\n', '-');
					midlet.sendCommand(PlugIns.getPlugin(i), params);
				}
			}
		} else if (c == cmInteractive) {
			int i = cgPlugins.getSelectedIndex();
			if (i != -1) {
				String plugin = PlugIns.getPlugin(i);
				cvPlugin.setPlugin(plugin);
				midlet.getDisplayManager().pushDisplayable(cvPlugin);
			}
		} else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
