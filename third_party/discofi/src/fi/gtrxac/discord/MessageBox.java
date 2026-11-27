package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import javax.microedition.lcdui.*;
import fi.gtrxac.bluewap.ui.AppCanvas;

public class MessageBox extends TextBox implements CommandListener {
    public static final int CMD_SEND = 0;
    public static final int CMD_BACK = 1;

    private ChatScreen screen;

    public MessageBox(ChatScreen screen, String content) {
        super("Message " + screen.channel.name, content, 2000, 0);

        this.screen = screen;

        addCommand(new Command("Send", Command.OK, CMD_SEND));
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getPriority() == CMD_SEND) {
            HTTPThread h = new HTTPThread(HTTPThread.ACTION_SEND_MESSAGE, screen);
            h.channel = screen.channel;
            h.message = getString();
            h.start();
        }
        else {
            screen.messageDraft = getString();
        }

        App.disp.setCurrent(AppCanvas.instance);
    }
}