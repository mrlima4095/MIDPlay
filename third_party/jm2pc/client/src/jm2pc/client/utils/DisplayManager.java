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

import java.util.Stack;
import javax.microedition.lcdui.*;

public class DisplayManager extends Stack
{
    
    private Display display;
    private Displayable displayableMain;
    
    private static final int ALERT_DEFAULT_TIME = 2000;
    
    public DisplayManager(Display display, Displayable displayableMain)
    {
        this.display = display;
        this.displayableMain = displayableMain;        
    }
    
    public void pushDisplayable(Displayable d, boolean forcarEmpilhamento)
    {       	
    	if(forcarEmpilhamento)
    	{   		    			
   			push(display.getCurrent());    		
    	}    	
        display.setCurrent(d);        
    }
    
    public void pushDisplayable(Displayable d)
    {
    	
    	if(!(display.getCurrent() instanceof Canvas))
    	{    		
            push(display.getCurrent());
    	}    	
   
        display.setCurrent(d);   
    }
    
    public void home()
    {
        while(elementCount > 1)
            pop();
        
        display.setCurrent(displayableMain);
    }
    
    public void popDisplayable()
    {    	
        if(isEmpty())
        	display.setCurrent(displayableMain);
        else
        {        	
            display.setCurrent((Displayable) pop());
        }
    }
    
    public void showAlert(String msg, AlertType type, boolean modal, Displayable displayable)
    {
        
        Alert alStatus = new Alert("Status", msg, null, type);
        
        if(modal)
            alStatus.setTimeout(Alert.FOREVER);
        else
        {
            alStatus.setTimeout(ALERT_DEFAULT_TIME);
        }
        if(displayable == null)
            display.setCurrent(alStatus);
        else
        {
            display.setCurrent(alStatus, displayable);
        }
    }

}