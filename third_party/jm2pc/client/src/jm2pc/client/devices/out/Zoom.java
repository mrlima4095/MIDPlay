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

public class Zoom {

	public static final int ZOOM_MAX = 1;
	public static final int ZOOM_MIDLE = 2;
	public static final int ZOOM_LOW = 3;
	public static final int ZOOM_LOWEST = 4;

	private int zoomImg = ZOOM_MIDLE;

	public int getZoomImg() {
		return zoomImg;
	}

	public void setZoomImg(int zoomImg) {
		if (zoomImg > ZOOM_LOWEST) {
			this.zoomImg = ZOOM_MAX;
		} else if (zoomImg < ZOOM_MAX) {
			this.zoomImg = ZOOM_MAX;
		} else {
			this.zoomImg = zoomImg;
		}
	}

}
