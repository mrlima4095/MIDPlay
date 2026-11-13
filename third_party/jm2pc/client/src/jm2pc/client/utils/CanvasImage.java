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

import javax.microedition.lcdui.*;

import jm2pc.client.JM2PCMIDlet;



public class CanvasImage extends Canvas implements CommandListener
{
	
	private JM2PCMIDlet midlet;
	
	private Command cmBack;
	private Image im;
	
	public CanvasImage(JM2PCMIDlet midlet)
	{
		super();
		this.midlet = midlet;
		
		im = null;
		cmBack = new Command(midlet.messages.getMessage("back"), Command.BACK, 1);		
		
		addCommand(cmBack);
		setCommandListener(this);
	}

	public void paint(Graphics g)
	{
		g.setColor(0,0,0);
		g.fillRect(0, 0, getWidth(), getHeight());
		if(im != null)
			g.drawImage(im, getWidth()/2, getHeight()/2, Graphics.VCENTER | Graphics.HCENTER);
	}
	
	public void updateImage(Image newImage)
	{
		im = newImage;
		repaint();
	}
	
	public void commandAction(Command c, Displayable d)
	{
		if(c == cmBack)
		{
			im = null;
			midlet.getDisplayManager().popDisplayable();
		}
	}
	
	public void keyPressed(int keyCode) {
		if(keyCode == KEY_NUM5) {
			String cmd = midlet.getLastCommand();
			if(cmd != null)
				midlet.executeCommand(cmd);
		}
	}

}
