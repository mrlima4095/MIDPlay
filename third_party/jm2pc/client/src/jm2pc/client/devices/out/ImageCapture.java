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

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;

import javax.microedition.lcdui.AlertType;

import javax.microedition.lcdui.Image;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.client.utils.CanvasWait;

public class ImageCapture {

	private JM2PCMIDlet midlet;
	private CanvasWait cvWait;

	public ImageCapture(JM2PCMIDlet midlet) {
		this.midlet = midlet;
		cvWait = midlet.getCanvasWait();
	}

	public void createImageCapture(int tipo, boolean exibeProgresso) {

		if (exibeProgresso)
			midlet.getDisplayManager().pushDisplayable(cvWait);

		cvWait.setMessage(midlet.messages.getMessage("getStream"));

		try {
			System.gc();
			Thread.sleep(200);
		} catch (InterruptedException e) {

		}

		Thread thread = new ReadImageThread(tipo);
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();

	}

	private class ReadImageThread extends Thread {

		private int tipo;

		public ReadImageThread(int t) {
			tipo = t;
		}

		public void run() {
			int tam = 0;
			InputStream in = midlet.getIn();

			try {

				DataInputStream dataInStrm = new DataInputStream(in);
				if (!dataInStrm.readBoolean()) // se houve erro quando o
				// servidor capturava a tela
				{
					midlet.showAlert(midlet.messages
							.getMessage("errorServerImage"), AlertType.WARNING,
							true, midlet.getMenu());
					return;
				}
				cvWait.setMessage(midlet.messages.getMessage("size"));
				tam = dataInStrm.readInt();
				midlet.setTotalBytes(midlet.getTotalBytes() + 4);
				dataInStrm = null;

				cvWait.setMessage(String.valueOf(tam));
				byte[] imData = new byte[tam];

				for (int i = 0; i < tam; i++) {
					imData[i] = (byte) in.read();
					cvWait.setMessage((i * 100) / tam);
				}

				cvWait.setMessage(midlet.messages.getMessage("render"));

				Image image = Image.createImage(imData, 0, tam);

				if (tipo == ListScreen.SCREEN_PLUGIN)
					midlet.showImage(image);
				else
					midlet.showScreen(image, tipo);

				imData = null;

			} catch (IOException ioe) {
				midlet.getDisplayManager().popDisplayable();
				midlet.showAlert(midlet.messages.getMessage("errorIOImage"),
						AlertType.ERROR, true, midlet.getMenu());
			} catch (OutOfMemoryError e) {
				cvWait.setMessage(midlet.messages.getMessage("clear"));
				try {
					int ch = 0;
					while (ch != -1)
						ch = in.read();

				} catch (Exception IOe) {

				}

				StringBuffer sb = new StringBuffer(midlet.messages
						.getMessage("outOfMemory"));
				sb.append(" - ");
				sb.append(tam);
				midlet.getDisplayManager().popDisplayable();
				midlet.showAlert(sb.toString(), AlertType.ERROR, true, midlet
						.getMenu());
			} catch (Exception exp) {
				StringBuffer sb = new StringBuffer(midlet.messages
						.getMessage("errorImage"));
				sb.append(": ");
				sb.append(exp.toString());
				midlet.showAlert(sb.toString(), AlertType.ERROR, true, midlet
						.getMenu());
			} finally {

				try {
					System.gc();
					Thread.sleep(200);
				} catch (InterruptedException e) {

				}

				midlet.setTotalBytes(midlet.getTotalBytes() + tam);

			}

		}

	}

}
