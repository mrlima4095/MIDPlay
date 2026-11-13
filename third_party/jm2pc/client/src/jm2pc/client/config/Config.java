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

package jm2pc.client.config;

public class Config {

	private int maxBytesDownload;
	private int bitsColor;
	private boolean alertOk;
	private boolean receivePlugins;
	private boolean jpegImages;

	public Config() {
		// valores padrão
		maxBytesDownload = 90000;
		bitsColor = 24;
		alertOk = false;
		receivePlugins = true;
		jpegImages = true;
	}

	public int getBitsColor() {
		return bitsColor;
	}

	public void setBitsColor(int bitsColor) {
		this.bitsColor = bitsColor;
	}

	public int getMaxBytesDownload() {
		return maxBytesDownload;
	}

	public void setMaxBytesDownload(int maxBytesImage) {
		this.maxBytesDownload = maxBytesImage;
	}

	public boolean isAlertOk() {
		return alertOk;
	}

	public void setAlertOk(boolean alertOk) {
		this.alertOk = alertOk;
	}

	public void setReceivePlugins(boolean receivePlugins) {
		this.receivePlugins = receivePlugins;
	}

	public boolean isReceivePlugins() {
		return receivePlugins;
	}

	public boolean isJpegImages() {
		return jpegImages;
	}

	public void setJpegImages(boolean jpegImages) {
		this.jpegImages = jpegImages;
	}

}