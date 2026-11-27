package fi.gtrxac.bluewap.ui;

import java.util.Vector;
import javax.microedition.lcdui.*;
import fi.gtrxac.bluewap.*;

public class Dialog extends PopupScreen implements CommandListener {
    private static final Command DISMISS_COMMAND = new Command("OK", Command.BACK, 0);

    private Screen lastScreen;
    private Screen nextScreen;
    private int commandCount;
    private CommandListener listener;

    public Dialog() {
        this((Screen) null);
    }

    public Dialog(String text) {
        this(text, null);
    }

    public Dialog(Screen nextScreen) {
        super(Fonts.height/2, Fonts.height/2, Fonts.height*16);
        super.setCommandListener(this);

        lastScreen = AppBase.getCurrentScreen();
        this.nextScreen = nextScreen;

        commandCount = 0;
        super.addCommand(DISMISS_COMMAND);
    }

    public Dialog(String text, Screen nextScreen) {
        this(nextScreen);
        listView.addItem(text);
    }

    public void addCommand(Command c) {
        super.removeCommand(DISMISS_COMMAND);
        super.addCommand(c);
        commandCount++;
    }

    public void removeCommand(Command c) {
        super.removeCommand(c);
        commandCount--;
        if (commandCount == 0) {
            super.addCommand(DISMISS_COMMAND);
        }
    }

    public void setCommandListener(CommandListener l) {
        listener = l;
    }

    public void commandAction(Command c, Displayable d) {
        if (listener == null) {
            if (nextScreen == null) {
                AppBase.popScreen();
            } else {
                AppBase.replaceScreen(nextScreen);
            }
        } else {
            listener.commandAction(c, d);
        }
    }
}