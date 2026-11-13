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
import jm2pc.utils.Constants;

public class ListScreen extends List implements CommandListener {

	private JM2PCMIDlet midlet;

	private Form fmXY;
	private TextField tfXY_X;
	private TextField tfXY_Y;
	private Gauge gaXYZoom;

	private Command cmBack;
	private Command cmUpdateXY;
	private Command cmSend;

	public static final int SCREEN_INT = 0;
	public static final int SCREEN_XY = 1;
	public static final int SCREEN_PLUGIN = 2;

	public ListScreen(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("screen"), List.IMPLICIT);

		this.midlet = midlet;

		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				2);
		cmSend = new Command(midlet.messages.getMessage("send"), Command.OK, 1);
		cmUpdateXY = new Command(midlet.messages.getMessage("refreshXY"),
				Command.SCREEN, 3);

		append(midlet.messages.getMessage("fullscreen"), null);
		append(midlet.messages.getMessage("xyScreen"), null);

		addCommand(cmBack);
		setCommandListener(this);

		fmXY = new Form(midlet.messages.getMessage("xyScreen"));

		tfXY_X = new TextField("X:", null, 4, TextField.NUMERIC);
		tfXY_Y = new TextField("Y:", null, 4, TextField.NUMERIC);

		gaXYZoom = new Gauge(midlet.messages.getMessage("zoom"), true, 3, 2);

		fmXY.append(tfXY_X);
		fmXY.append(tfXY_Y);
		fmXY.append(gaXYZoom);
		fmXY.addCommand(cmBack);
		fmXY.addCommand(cmSend);
		fmXY.addCommand(cmUpdateXY);
		fmXY.setCommandListener(this);
	}

	public void getFullScreen() {
		midlet.sendCommand(Constants.CMD_TELA, Constants.CMD_TELA_TP_INT);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == List.SELECT_COMMAND)
			switch (getSelectedIndex()) {
			case SCREEN_INT:
				getFullScreen();
				break;

			case SCREEN_XY:
				midlet.getDisplayManager().pushDisplayable(fmXY);
				break;
			}
		else if (c == cmBack) {
			midlet.getDisplayManager().popDisplayable();
		} else if (c == cmSend) {
			String x = tfXY_X.getString();

			if (x.length() == 0)
				x = "0";

			String y = tfXY_Y.getString();

			if (y.length() == 0)
				y = "0";

			int zoom = Math.abs(gaXYZoom.getValue() - gaXYZoom.getMaxValue()) + 1;
			midlet.setImageZoom(zoom);
			midlet.setScreenXY(Integer.parseInt(x), Integer.parseInt(y));

			StringBuffer sbParam = new StringBuffer();
			sbParam.append(Constants.CMD_TELA_TP_XY);
			sbParam.append(' ');
			sbParam.append(midlet.getScreenX());
			sbParam.append(' ');
			sbParam.append(midlet.getScreenY());
			sbParam.append(' ');
			sbParam.append(zoom);

			midlet.sendCommand(Constants.CMD_TELA, sbParam.toString());
		} else if (c == cmUpdateXY) {
			tfXY_X.setString(String.valueOf(midlet.getScreenX()));
			tfXY_Y.setString(String.valueOf(midlet.getScreenY()));
		}
	}

}