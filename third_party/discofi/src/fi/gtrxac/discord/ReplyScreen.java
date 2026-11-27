package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

public class ReplyScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;
    public static final int CMD_SEND = 2;

    private TextFieldItem messageField;
    private RadioButtonGroup mentionGroup;
    private Channel channel;
    private Message replyTarget;

    public ReplyScreen(Channel channel, Message replyTarget, String messageDraft) {
        super();
        String title = "Message " + channel.name;
        setTitle(title);

        this.channel = channel;
        this.replyTarget = replyTarget;

        addItem(new StringItem("Replying to " + replyTarget.author.name, Fonts.bold));
        addItem(replyTarget.content);

        addItem(new StringItem("Your message", Fonts.bold));
        addItem(messageField = new TextFieldItem(title, messageDraft, 2000, 0));
        setHighlightedItem(messageField);

        addItem(new StringItem("Mention author", Fonts.bold));

        mentionGroup = new RadioButtonGroup();
        addItem(new RadioButtonItem(mentionGroup, "On"));
        addItem(new RadioButtonItem(mentionGroup, "Off"));

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(new Command("Send", Command.OK, CMD_SEND));
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                Screen s = App.getPreviousScreen();

                if (s instanceof ChatScreen) {
                    ((ChatScreen) s).messageDraft = messageField.getValue();
                }
                App.popScreen();
                break;
            }
            case CMD_SEND: {
                HTTPThread h = new HTTPThread(HTTPThread.ACTION_SEND_MESSAGE, App.getPreviousScreen());
                h.channel = channel;
                h.message = messageField.getValue();
                h.recipientMessage = replyTarget.id;
                h.sendPing = mentionGroup.getTickedIndex() == 0;
                h.start();

                App.popScreen();
            }
        }
    }

    public void itemSelected(Item i) {
    }
}