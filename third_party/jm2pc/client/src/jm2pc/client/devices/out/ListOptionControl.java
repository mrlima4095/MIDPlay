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

package jm2pc.client.devices.out;

import javax.microedition.lcdui.*;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.client.plugins.PlugIns;

public class ListOptionControl extends List implements CommandListener {

	private JM2PCMIDlet midlet;

	private Command cmOk;

	public static final int MOUSE = 0;
	public static final int SCROLL = 1;

	public ListOptionControl(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("control"), Choice.EXCLUSIVE);
		this.midlet = midlet;

		cmOk = new Command(midlet.messages.getMessage("ok"), Command.OK, 1);

		this.append(midlet.messages.getMessage("mouse"), null);
		this.append(midlet.messages.getMessage("zoom"), null);

		this.setSelectedIndex(MOUSE, true);

		this.addCommand(cmOk);
		this.setCommandListener(this);

	}

	public void setControl(int i) {
		this.setSelectedIndex(i, true);
	}

	public int getControl() {
		return this.getSelectedIndex();
	}

	private void clearPlugins() {
		for (int i = size() - 1; i >= 2; i--)
			delete(i);
	}

	private void loadPlugins() {
		for (int i = 0; i < PlugIns.getPluginCount(); i++) {
			append(PlugIns.getPlugin(i), null);
		}
	}

	public void updatePlugins() {
		clearPlugins();
		loadPlugins();
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmOk) {
			midlet.getDisplayManager().popDisplayable();
		}
	}

}
