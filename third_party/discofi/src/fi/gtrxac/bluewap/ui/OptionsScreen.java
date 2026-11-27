package fi.gtrxac.bluewap.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.CommandListener;
import java.util.*;

public class OptionsScreen extends PopupScreen implements CommandListener, ListViewItemListener {
    private Vector commands;

    public OptionsScreen(Vector commands) {
        super();

        this.commands = createSortedCommands(commands);

        for (int i = 0; i < this.commands.size(); i++) {
            Command c = (Command) this.commands.elementAt(i);
            listView.addItem(new OptionItem(c));
        }

        addCommand(new Command("Back", Command.BACK, 0));
        setCommandListener(this);

        listView.setItemListener(this);
    }

    // sort commands by increasing priority number into a separate vector
    // input vector is cleared
    private Vector createSortedCommands(Vector commands) {
        int numCommands = commands.size();
        Vector result = new Vector(numCommands);

        for (int i = 0; i < numCommands; i++) {
            Command lowestCommand = null;

            for (int j = 0; j < commands.size(); j++) {
                Command c = (Command) commands.elementAt(j);

                if (lowestCommand == null || c.getPriority() < lowestCommand.getPriority()) {
                    lowestCommand = c;
                }
            }
            result.addElement(lowestCommand);
            commands.removeElement(lowestCommand);
        }
        return result;
    }

    public void itemSelected(Item i) {
        AppBase.popScreen();
        CommandListener l = AppBase.getCurrentScreen().getCommandListener();

        if (l != null) {
            l.commandAction(((OptionItem) i).command, AppCanvas.instance);
        }
    }

    public void commandAction(Command c, Displayable d) {
        AppBase.popScreen();
    }

    public void keyEvent(int keyCode, int gameAction) {
        listView.keyEvent(keyCode, gameAction);
    }
    
    public void pointerReleased(int x, int y) {
        if (y < viewY || x < viewX) {
            AppBase.popScreen();
        }
        else {
            listView.pointerReleased(x, y);
        }
    }
}