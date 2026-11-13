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

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class Images {

	public static Image createMobileImage() {

		int width = 20;
		int height = 40;

		Image celular = Image.createImage(width, height);

		Graphics g = celular.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height - 1); // contorno do corpo

		g.setColor(200, 200, 200); // cinza claro
		g.fillRect(1, 1, width - 2, height - 2); // preenche o corpo

		g.setColor(0, 0, 0); // preto
		g.fillRect(2, 2, width - 4, height / 2); // display

		g.setColor(0, 200, 0); // verde
		g.fillRect(2, (height / 2 + 2) + 2, 5, 2); // botão ligar

		g.setColor(200, 0, 0); // vermelho
		g.fillRect((width / 2) + 2, (height / 2 + 2) + 2, 5, 2); // botão
		// desligar

		// criar botões numéricos
		g.setColor(128, 128, 128); // cinza escuro
		g.fillRect(2, (height / 2 + 6) + 2, 3, 2);
		g.fillRect(8, (height / 2 + 6) + 2, 3, 2);
		g.fillRect(14, (height / 2 + 6) + 2, 3, 2);

		g.fillRect(2, (height / 2 + 10) + 2, 3, 2);
		g.fillRect(8, (height / 2 + 10) + 2, 3, 2);
		g.fillRect(14, (height / 2 + 10) + 2, 3, 2);

		g.fillRect(2, (height / 2 + 14) + 2, 3, 2);
		g.fillRect(8, (height / 2 + 14) + 2, 3, 2);
		g.fillRect(14, (height / 2 + 14) + 2, 3, 2);

		return celular;
	}

	public static Image createComputerImage() {

		int width = 40;
		int height = 60;

		Image computador = Image.createImage(width, height);

		Graphics g = computador.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height / 2 - 1); // contorno do monitor

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, 1, width - 2, height / 2 - 2); // preenche o monitor

		g.setColor(0, 0, 0); // preto
		g.fillRect(5, 5, width - 10, height / 2 - 10); // display

		g.fillRect(width / 2 - 2, height / 2, 4, 5); // apoio do monitor
		g.fillRect(width / 2 - 5, height / 2 + 5, 10, 4); // base do apoio do
		// monitor

		g.drawRect(0, (height / 2 + 9), width - 1, (height - 1)
				- (height / 2 + 9)); // contorno do gabinete

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, (height / 2 + 10), width - 2, (height - 1)
				- (height / 2 + 10)); // preenche o gabinete

		g.setColor(128, 128, 128); // cinza escuro
		g.drawRect(3, (height / 2 + 13), width / 2 - 5, 5);
		g.drawRect(3, (height / 2 + 20), width / 2 - 5, 5);

		g.fillArc(width / 2, (height / 2 + 13), 10, 5, 0, 360);
		g.fillArc(width / 2, (height / 2 + 20), 10, 5, 0, 360);

		g.setColor(0, 200, 0); // verde
		g.fillArc(width / 2 + 12, (height / 2 + 14), 4, 6, 0, 360);

		return computador;

	}

	public static Image createImageBluetooth() {

		int width = 21;
		int height = 29;

		Image bluetooth = Image.createImage(width, height);

		Graphics g = bluetooth.getGraphics();

		g.setColor(0, 0, 200); // azul
		g.fillArc(0, 0, width - 1, height - 1, 0, 360);

		g.setColor(255, 255, 255); // branco
		g.drawLine(8, 4, 8, 23);
		g.drawLine(9, 5, 9, 22);

		g.drawLine(4, 8, 15, 19);
		g.drawLine(4, 9, 14, 19);

		g.drawLine(14, 19, 9, 23);
		g.drawLine(14, 18, 8, 23);

		g.drawLine(4, 20, 15, 9);
		g.drawLine(4, 19, 14, 9);

		g.drawLine(15, 9, 9, 4);
		g.drawLine(14, 9, 9, 5);

		return bluetooth;
	}

	public static Image createIconServidor() {

		int width = 12;
		int height = 14;

		Image iconServ = Image.createImage(width, height);

		Graphics g = iconServ.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height / 2 + 1); // contorno do monitor

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, 1, width - 2, height / 2); // preenche o monitor

		g.setColor(0, 0, 0); // preto
		g.fillRect(2, 2, width - 4, height / 2 - 2); // display

		g.fillRect(width / 2 - 2, height / 2 + 1, 4, 1); // base do apoio do
		// monitor

		g.drawRect(0, (height / 2 + 2), width - 1, (height - 1)
				- (height / 2 + 2)); // contorno do gabinete

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, (height / 2 + 3), width - 2, (height - 1)
				- (height / 2 + 3)); // preenche o gabinete

		g.setColor(128, 128, 128); // cinza escuro
		g.drawRect(2, (height / 2 + 4), width / 2 - 6, 1);
		g.drawRect(5, (height / 2 + 4), width / 2 - 6, 1);

		g.setColor(0, 200, 0); // verde
		g.drawRect(8, (height / 2 + 4), width / 2 - 5, 1);

		return iconServ;

	}

	public static Image createIconServidorSSL() {

		int width = 12;
		int height = 14;

		Image iconServ = Image.createImage(width, height);

		Graphics g = iconServ.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height / 2 + 1); // contorno do monitor

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, 1, width - 2, height / 2); // preenche o monitor

		g.setColor(0, 0, 0); // preto
		g.fillRect(2, 2, width - 4, height / 2 - 2); // display

		g.fillRect(width / 2 - 2, height / 2 + 1, 4, 1); // base do apoio do
		// monitor

		g.drawRect(0, (height / 2 + 2), width - 1, (height - 1)
				- (height / 2 + 2)); // contorno do gabinete

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, (height / 2 + 3), width - 2, (height - 1)
				- (height / 2 + 3)); // preenche o gabinete

		g.setColor(128, 128, 128); // cinza escuro
		g.drawRect(2, (height / 2 + 4), width / 2 - 6, 1);
		g.drawRect(5, (height / 2 + 4), width / 2 - 6, 1);

		g.setColor(0, 200, 0); // verde
		g.drawRect(8, (height / 2 + 4), width / 2 - 5, 1);

		g.setColor(200, 200, 0);

		g.fillRect(3, 7, width - 6, height - 9);
		g.drawArc(3, 3, width - 6, 12, 0, 180);
		g.drawArc(4, 4, width - 7, 11, 0, 180);

		return iconServ;

	}

	public static Image createIconServidorBlueT() {

		int width = 12;
		int height = 14;

		Image iconServ = Image.createImage(width, height);

		Graphics g = iconServ.getGraphics();

		g.setColor(255, 255, 255); // branco
		g.drawRect(0, 0, width, height); // contorno do monitor

		g.setColor(0, 0, 195); // azul
		g.drawLine(1, 1, 5, 6);
		g.drawLine(1, 12, 5, 6);

		g.drawLine(5, 1, 5, 12);

		g.drawLine(5, 1, 11, 3);
		g.drawLine(11, 3, 5, 6);

		g.drawLine(5, 6, 11, 8);
		g.drawLine(11, 8, 5, 12);

		return iconServ;

	}

	public static Image createIconTela() {

		int width = 11;
		int height = 10;

		Image iconTela = Image.createImage(width, height);

		Graphics g = iconTela.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height - 1); // contorno do monitor

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, 1, width - 2, height - 2); // preenche o monitor

		g.setColor(0, 0, 0); // preto
		g.fillRect(2, 2, width - 4, height - 4); // display

		g.setColor(200, 0, 0); // vermelho
		g.drawLine(2, 2, 2, height - 3);

		g.setColor(0, 200, 0); // verde
		g.drawLine(3, 2, 3, height - 3);

		g.setColor(0, 0, 200); // azul
		g.drawLine(4, 2, 4, height - 3);

		g.setColor(200, 200, 0); // vermelho
		g.drawLine(5, 2, 5, height - 3);

		g.setColor(200, 0, 200); // verde
		g.drawLine(6, 2, 6, height - 3);

		g.setColor(0, 200, 200); // azul
		g.drawLine(7, 2, 7, height - 3);

		return iconTela;

	}

	public static Image createIconShell() {

		int width = 11;
		int height = 10;

		Image iconShell = Image.createImage(width, height);

		Graphics g = iconShell.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height - 1); // contorno do monitor

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, 1, width - 2, height - 2); // preenche o monitor

		g.setColor(0, 0, 0); // preto
		g.fillRect(2, 2, width - 4, height - 4); // display

		return iconShell;

	}

	public static Image createIconExecutar() {

		int width = 11;
		int height = 9;

		Image iconExec = Image.createImage(width, height);

		Graphics g = iconExec.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.fillRect(0, 0, width, height); // contorno do envelope

		g.setColor(0, 255, 0); // verde
		g.drawLine(1, height / 2, width / 2, height - 1);
		g.drawLine(width / 2, height - 1, width - 1, 1);

		return iconExec;

	}

	public static Image createIconMsg() {

		int width = 11;
		int height = 9;

		Image iconMsg = Image.createImage(width, height);

		Graphics g = iconMsg.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height - 1); // contorno do envelope

		g.setColor(255, 255, 255); // branco
		g.fillRect(1, 1, width - 3, height - 2); // preenche o envelope

		g.setColor(0, 0, 0); // preto
		g.drawLine(0, 0, width / 2, height / 2);
		g.drawLine(width / 2, height / 2, width, 0);

		return iconMsg;

	}

	public static Image createIconTeclado() {

		int width = 11;
		int height = 11;

		Image iconTela = Image.createImage(width, height);

		Graphics g = iconTela.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height - 1); // contorno da tecla

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, 1, width - 2, height - 2); // preenche a tecla

		g.setColor(0, 0, 0); // preto
		g.drawLine(width / 2, 3, 2, height - 3);
		g.drawLine(width / 2, 3, width - 3, height - 3);
		g.drawLine(3, height - 5, width - 4, height - 5);

		return iconTela;

	}

	public static Image createIconMouse() {

		int width = 9;
		int height = 11;

		Image iconMouse = Image.createImage(width, height);

		Graphics g = iconMouse.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height - 1); // contorno do mouse

		g.setColor(230, 230, 230); // gelo
		g.fillRect(1, 1, width - 2, height - 2); // preenche o mouse

		g.setColor(0, 0, 0); // preto
		g.drawLine(0, 4, width, 4);
		g.drawLine(width / 2, 4, width / 2, 0);

		return iconMouse;

	}

	public static Image createIconTransferencia() {

		int width = 9;
		int height = 9;

		Image iconTransferencia = Image.createImage(width, height);

		Graphics g = iconTransferencia.getGraphics();

		g.setColor(200, 0, 0); // vermelho
		g.fillRect(0, 0, width / 2, height);

		g.setColor(0, 200, 0); // verde
		g.fillRect(width / 2, 0, width, height);

		return iconTransferencia;

	}

	public static Image createIconPlugin() {

		int width = 11;
		int height = 10;

		Image iconPlugin = Image.createImage(width, height);

		Graphics g = iconPlugin.getGraphics();

		g.setColor(255, 255, 255); // branco
		g.drawRect(0, 0, width, height); // contorno do monitor

		g.setColor(170, 170, 170); // cinza
		g.drawLine(3, 1, 3, 8);
		g.fillRect(4, 2, 2, 6);
		g.fillRect(6, 4, 4, 2);

		g.setColor(200, 200, 0);
		g.drawLine(1, 3, 2, 3);
		g.drawLine(1, 6, 2, 6);

		return iconPlugin;

	}

	public static Image createIconSobre() {

		int width = 9;
		int height = 10;

		Image iconSobre = Image.createImage(width, height);

		Graphics g = iconSobre.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 0, width - 1, height - 1); // contorno do icone

		g.setColor(255, 255, 255); // cinza
		g.fillRect(1, 1, width - 2, height - 2); // preenche o icone

		g.setColor(9, 90, 150); // azul escuro
		g.drawArc(3, 1, 2, 2, 0, 360); // cabeça
		g.drawLine(2, 5, 6, 5); // braços
		g.drawLine(4, 4, 4, 6); // tronco

		g.drawLine(4, 6, 2, 9); // perna
		g.drawLine(4, 6, 6, 9); // perna

		return iconSobre;

	}

	public static Image createIconDirectory() {

		int width = 10;
		int height = 11;

		Image iconDirectory = Image.createImage(width, height);

		Graphics g = iconDirectory.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.drawRect(0, 3, width - 1, height - 4); // contorno do icone
		g.drawRect(0, 0, 4, 3);

		g.setColor(255, 255, 0); // amarelo
		g.fillRect(1, 4, width - 2, height - 5); // contorno do icone
		g.fillRect(1, 1, 3, 2);
		return iconDirectory;

	}

	public static Image createIconDeutsch() {

		int width = 10;
		int height = 9;

		Image iconDeutsch = Image.createImage(width, height);

		Graphics g = iconDeutsch.getGraphics();

		g.setColor(0, 0, 0); // preto
		g.fillRect(0, 0, width, height / 3);
		g.setColor(221, 0, 0);
		g.fillRect(0, height / 3, width, height / 3);
		g.setColor(255, 206, 0);
		g.fillRect(0, (height / 3) * 2, width, height / 3);

		return iconDeutsch;

	}

	public static Image createIconItalia() {

		int width = 9;
		int height = 8;

		Image iconItalia = Image.createImage(width, height);

		Graphics g = iconItalia.getGraphics();

		g.setColor(0, 141, 70); // verde
		g.fillRect(0, 0, width / 3, height);
		g.setColor(255, 255, 255);
		g.fillRect(width / 3, 0, width / 3, height);
		g.setColor(210, 35, 44);
		g.fillRect((width / 3) * 2, 0, width / 3, height);

		return iconItalia;

	}

	public static Image createIconEnglish() {

		int width = 10;
		int height = 8;

		Image iconEnglish = Image.createImage(width, height);

		Graphics g = iconEnglish.getGraphics();

		g.setColor(0, 36, 125); // azul
		g.fillRect(0, 0, width, height); // contorno do icone

		g.setColor(255, 255, 255); // branco
		g.drawLine(0, 0, width, height);
		g.drawLine(0, height, width, 0);

		g.setColor(207, 20, 43); // vermelho
		g.drawLine(0, 1, width, height - 1);
		g.drawLine(1, height, width - 1, 0);

		g.setColor(255, 255, 255); // branco
		g.fillRect(width / 2 - 1, 0, 3, height);
		g.fillRect(0, height / 2 - 1, width, 3);

		g.setColor(207, 20, 43); // vermelho

		g.fillRect(width / 2, 0, 1, height);
		g.fillRect(0, height / 2, width, 1);

		return iconEnglish;

	}

	public static Image createIconEspana() {

		int width = 10;
		int height = 8;

		Image iconEspana = Image.createImage(width, height);

		Graphics g = iconEspana.getGraphics();

		g.setColor(219, 0, 13); // vermelho
		g.fillRect(0, 0, width, height / 4);
		g.setColor(251, 234, 14);
		g.fillRect(0, height / 4, width, height / 2);
		g.setColor(219, 0, 13); // vermelho
		g.fillRect(0, (height / 4) + (height / 2), width, height / 4);

		return iconEspana;

	}

	public static Image createIconBrasil() {

		int width = 10;
		int height = 8;

		Image iconBrasil = Image.createImage(width, height);

		Graphics g = iconBrasil.getGraphics();

		g.setColor(0, 153, 76); // verde
		g.fillRect(0, 0, width, height);

		g.setColor(243, 231, 24); // amarelo
		g.drawLine(1, height / 2, width / 2, 1);
		g.drawLine(2, height / 2, width / 2, 2);
		g.drawLine(3, height / 2, width / 2, 3);
		g.drawLine(4, height / 2, width / 2, 4);

		g.drawLine(width / 2, 1, width - 1, height / 2);
		g.drawLine(width / 2, 2, width - 2, height / 2);
		g.drawLine(width / 2, 3, width - 3, height / 2);
		g.drawLine(width / 2, 4, width - 4, height / 2);

		g.drawLine(1, height / 2, width / 2, height - 1);
		g.drawLine(2, height / 2, width / 2, height - 2);
		g.drawLine(3, height / 2, width / 2, height - 3);
		g.drawLine(4, height / 2, width / 2, height - 4);

		g.drawLine(width / 2, height - 1, width - 1, height / 2);
		g.drawLine(width / 2, height - 2, width - 2, height / 2);
		g.drawLine(width / 2, height - 3, width - 3, height / 2);
		g.drawLine(width / 2, height - 4, width - 4, height / 2);

		g.setColor(0, 37, 128); // azul
		g.drawLine(3, height / 2, width - 3, height / 2);
		g.drawLine(4, height / 2 - 1, width - 4, height / 2 - 1);
		g.drawLine(4, height / 2 + 1, width - 4, height / 2 + 1);
		g.drawLine(5, height / 2 - 2, width - 5, height / 2 - 2);
		g.drawLine(5, height / 2 + 2, width - 5, height / 2 + 2);

		return iconBrasil;

	}

}