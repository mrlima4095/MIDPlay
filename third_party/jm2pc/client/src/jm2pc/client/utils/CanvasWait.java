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

package jm2pc.client.utils;

import java.util.*;
import javax.microedition.lcdui.*;

import jm2pc.client.*;

public class CanvasWait extends Canvas implements CommandListener {

	public static final int TEMPO_ANIMACAO = 75;

	private JM2PCMIDlet midlet;

	private int width;
	private int height;
	private int position;
	private int step;
	private int iniWire;
	private int sizeWire;
	private boolean direction;

	private Image imMobile;
	private Image imComputer;

	private Timer timer;
	private TimerTask task;

	private Command cmCancel;

	private String title;
	private String message;

	public CanvasWait(JM2PCMIDlet midlet) {
		super();

		this.midlet = midlet;

		imMobile = Images.createMobileImage();
		imComputer = Images.createComputerImage();

		direction = true;

		width = getWidth();
		height = getHeight();
		position = 0;
		step = 2;
		iniWire = imMobile.getWidth() / 2 + 3;
		sizeWire = width - imComputer.getWidth() - iniWire;

		message = midlet.messages.getMessage("pleaseWait");

		StringBuffer sbTitulo = new StringBuffer(midlet.messages
				.getMessage("pleaseWait"));
		sbTitulo.append("...");

		title = sbTitulo.toString();

		task = new TimerTask() {

			public void run() {

				if (direction) {
					position += step;
				} else {
					position -= step;

				}

				if (position <= 0)
					direction = true;

				if ((position + step) >= sizeWire)
					direction = false;

				repaint();

			}

		};

		timer = new Timer();
		timer.schedule(task, 0, TEMPO_ANIMACAO);

		cmCancel = new Command(midlet.messages.getMessage("cancel"),
				Command.BACK, 1);
		addCommand(cmCancel);
		setCommandListener(this);

	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmCancel) {
			midlet.getDisplayManager().popDisplayable();
			midlet.closeConnection();
		}
	}

	public void setMessage(String msg) {
		message = msg;
		message = message.concat("...");
	}

	public void setMessage(int porcentagem) {

		StringBuffer sb = new StringBuffer(String.valueOf(porcentagem));
		sb.append("%");

		message = sb.toString();
	}

	protected void paint(Graphics g) {

		g.setColor(255, 255, 255); // branco
		g.fillRect(0, 0, width, height);

		g.setStrokeStyle(Graphics.DOTTED);

		g.setColor(0, 0, 200); // azul
		g.drawLine(iniWire, height / 2 + 12, width
				- (imComputer.getWidth() + 3), height / 2 + 12); // barra de
		// comunicação

		if (direction) {

			g.setColor(200, 0, 0);

		} else {

			g.setColor(0, 200, 0);

		}

		g.fillArc(iniWire + position, height / 2 + 10, 10, 7, 0, 360);

		g.drawImage(imMobile, 3, height / 2 + 8, Graphics.VCENTER
				| Graphics.LEFT); // desenha o celular
		g.drawImage(imComputer, width - 3, height / 2, Graphics.VCENTER
				| Graphics.RIGHT); // desenha o computador

		g.setColor(0, 0, 0); // preto
		g.drawString(title, width / 2, 2, Graphics.TOP | Graphics.HCENTER); // exibe
		// a
		// mensagem)
		g.drawString(message, width / 2, height - 3, Graphics.BOTTOM
				| Graphics.HCENTER); // exibe a mensagem

	}

}