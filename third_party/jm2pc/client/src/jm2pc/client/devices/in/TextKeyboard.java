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

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import jm2pc.client.JM2PCMIDlet;
import jm2pc.utils.Constants;




public class TextKeyboard extends TextBox implements CommandListener 
{	
	
	private JM2PCMIDlet midlet;	
	
	private Command cmEnviar;	
	private Command cmAtalho;
	private Command cmVoltar;	
	
	public TextKeyboard(JM2PCMIDlet midlet) 
	{
		super(midlet.messages.getMessage("keyText"), "", 100, TextField.ANY);
		
		this.midlet = midlet;		
		
		cmEnviar = new Command(midlet.messages.getMessage("send"), Command.OK, 1);
		cmAtalho = new Command(midlet.messages.getMessage("shortcuts"), Command.OK, 2);
		cmVoltar = new Command(midlet.messages.getMessage("back"), Command.BACK, 3);
		
		addCommand(cmEnviar);
		addCommand(cmAtalho);
		addCommand(cmVoltar);
		setCommandListener(this);
	}
	
	public void commandAction(Command c, Displayable d)
	{
		if(c == cmEnviar)
		{			
			
			String txt = getString();
			txt = txt.replace('\n', '-');
			StringBuffer sbParam = new StringBuffer();
			sbParam.append(Constants.CMD_TECLADO_TP_TEXTO);
			sbParam.append(' ');
			sbParam.append(txt);
			midlet.sendCommand(Constants.CMD_TECLADO, sbParam.toString());
		}
		else if(c == cmAtalho)
		{
			midlet.getDisplayManager().pushDisplayable(new FormKeyboardShortcut(midlet));
		}
		else if(c == cmVoltar)
		{
			midlet.getDisplayManager().popDisplayable();			
		}
		
	}
	
}
