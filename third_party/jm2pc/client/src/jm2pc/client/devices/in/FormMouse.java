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

import jm2pc.client.JM2PCMIDlet;
import jm2pc.utils.Constants;

public class FormMouse extends Form implements CommandListener {

	private JM2PCMIDlet midlet;

	private ChoiceGroup cgButton;
	private ChoiceGroup cgAction;

	private TextField tfX;
	private TextField tfY;

	private Command cmBack;
	private Command cmOk;

	public static final int LEFT_BUTTON = 0;
	public static final int RIGHT_BUTTON = 1;
	public static final int NO_ACTION = 0;
	public static final int CLICK = 1;
	public static final int DOUBLE_CLICK = 2;

	public FormMouse(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("mouse"));

		this.midlet = midlet;

		cgButton = new ChoiceGroup(midlet.messages.getMessage("button"),
				Choice.EXCLUSIVE);
		int leftIndex = cgButton.append(midlet.messages.getMessage("left"),
				null);
		cgButton.append(midlet.messages.getMessage("right"), null);
		cgButton.setSelectedIndex(leftIndex, true);

		cgAction = new ChoiceGroup(midlet.messages.getMessage("action"),
				Choice.EXCLUSIVE);
		cgAction.append(midlet.messages.getMessage("noAction"), null);
		int cliqueIndex = cgAction.append(midlet.messages.getMessage("click"),
				null);
		cgAction.append(midlet.messages.getMessage("doubleClick"), null);
		cgAction.setSelectedIndex(cliqueIndex, true);

		tfX = new TextField("X:", null, 4, TextField.NUMERIC);
		tfY = new TextField("Y:", null, 4, TextField.NUMERIC);

		cmOk = new Command(midlet.messages.getMessage("send"), Command.OK, 1);
		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK,
				1);

		append(cgButton);
		append(cgAction);
		append(tfX);
		append(tfY);

		addCommand(cmOk);
		addCommand(cmBack);
		setCommandListener(this);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmBack)
			midlet.getDisplayManager().popDisplayable();
		else if (c == cmOk) {
			StringBuffer sbParam = new StringBuffer();
			sbParam.append(getAction());
			sbParam.append(' ');
			sbParam.append(getCoordinates());
			sbParam.append(' ');
			sbParam.append(getButton());

			midlet.sendCommand(Constants.CMD_MOUSE, sbParam.toString());
		}
	}

	public String getAction() {
		switch (cgAction.getSelectedIndex()) {
		case NO_ACTION:
			return Constants.CMD_MOUSE_TP_MOVE;

		case CLICK:
			return Constants.CMD_MOUSE_TP_CLICK;

		case DOUBLE_CLICK:
			return Constants.CMD_MOUSE_TP_DOUBLE_CLICK;

		default:
			return Constants.CMD_MOUSE_TP_MOVE;
		}
	}

	public String getCoordinates() {
		StringBuffer xy = new StringBuffer("");
		String x = tfX.getString();
		String y = tfY.getString();
		if (x.length() == 0) {
			x = "0";
		}
		if (y.length() == 0) {
			y = "0";
		}

		xy.append(x);
		xy.append(" ");
		xy.append(y);

		return xy.toString();

	}

	public String getButton() {
		if (getAction().equals(Constants.CMD_MOUSE_TP_MOVE))
			return "";

		String button = "";

		switch (cgButton.getSelectedIndex()) {
		case LEFT_BUTTON:
			button = String.valueOf(LEFT_BUTTON);
			break;

		case RIGHT_BUTTON:
			button = String.valueOf(RIGHT_BUTTON);
			break;
		}

		return button;
	}

}
