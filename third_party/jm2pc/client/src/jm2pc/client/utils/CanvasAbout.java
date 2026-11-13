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

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import jm2pc.client.JM2PCMIDlet;

public class CanvasAbout extends Canvas {

	private JM2PCMIDlet midlet;

	private Displayable nextDisplayable;

	private Image imMobile;
	private Image imComputer;

	public CanvasAbout(JM2PCMIDlet midlet, Displayable nextDisplayable) {
		super();

		this.midlet = midlet;
		this.nextDisplayable = nextDisplayable;

		imMobile = Images.createMobileImage();
		imComputer = Images.createComputerImage();

	}

	protected void paint(Graphics g) {

		int width = getWidth();
		int height = getHeight();

		g.setColor(255, 255, 255); // branco
		g.fillRect(0, 0, width, height);

		g.setColor(200, 10, 15);

		for (int i = 0; i < height / 5; i = i + 7)
			g.drawArc(i, i, width - (i * 2) - 1, height - (i * 2) - 1, 0, 360);

		g.drawImage(imComputer, width / 2 + imMobile.getWidth(), height / 2,
				Graphics.VCENTER | Graphics.RIGHT); // desenha o computador
		g.drawImage(imMobile, width / 2 - imMobile.getWidth() - 6,
				height / 2 + 8, Graphics.VCENTER | Graphics.LEFT); // desenha o
		// celular

		g.setColor(9, 90, 150); // azul escuro
		g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_BOLD,
				Font.SIZE_LARGE));
		g.drawString("JM2PC BT", width / 2, height / 2
				- (imComputer.getHeight() / 2) - 2, Graphics.BOTTOM
				| Graphics.HCENTER); // exibe a mensagem)

		g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_BOLD,
				Font.SIZE_SMALL));
		g.drawString("jm2pc.sourceforge.net", width / 2, height / 2
				+ (imComputer.getHeight() / 2) + 2, Graphics.TOP
				| Graphics.HCENTER); // exibe meu nome
		g.setColor(0, 0, 0); // preto
		g.drawString("Walter Wanderley", width / 2, height / 2
				+ (imComputer.getHeight() / 2) + 16, Graphics.TOP
				| Graphics.HCENTER); // exibe meu e-mail

	}

	public void keyPressed(int keyCode) {
		midlet.getDisplayManager().pushDisplayable(nextDisplayable);
	}

}