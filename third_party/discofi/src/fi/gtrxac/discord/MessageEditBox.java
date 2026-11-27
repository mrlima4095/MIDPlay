package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import javax.microedition.lcdui.*;
import fi.gtrxac.bluewap.ui.AppCanvas;

public class MessageEditBox extends TextBox implements CommandListener {
    public static final int CMD_EDIT = 0;
    public static final int CMD_BACK = 1;

    private ChatScreen screen;
    private Message message;

    public MessageEditBox(ChatScreen screen, Message message) {
        super("Edit message", message.content, 2000, 0);

        if (screen.editDraftTarget == message) {
            setString(screen.editDraft);
        }

        this.screen = screen;
        this.message = message;

        addCommand(new Command("OK", Command.OK, CMD_EDIT));
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getPriority() == CMD_EDIT) {
            HTTPThread h = new HTTPThread(HTTPThread.ACTION_EDIT_MESSAGE, screen);
            h.channel = screen.channel;
            h.editOrDeleteMessage = message;
            h.message = getString();
            h.start();
        }
        else {
            screen.editDraft = getString();
            screen.editDraftTarget = message;
        }

        App.disp.setCurrent(AppCanvas.instance);
    }
}