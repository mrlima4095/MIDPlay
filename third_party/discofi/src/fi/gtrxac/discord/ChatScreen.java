package fi.gtrxac.discord;

import cc.nnproject.json.*;
import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import java.util.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;

public class ChatScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;
    public static final int CMD_SEND = 2;
    public static final int CMD_REPLY = 3;
    public static final int CMD_EDIT = 4;
    public static final int CMD_DELETE = 5;
    public static final int CMD_REFRESH = 99;
    public static final int CMD_DELETE_PROMPT_YES = 100;
    public static final int CMD_DELETE_PROMPT_NO = 101;

    public static final int PAGE_INDETERMINATE = -1;

    public Channel channel;
    private Vector messages;
    public int page;
    public boolean hasLoaded;

    public String messageDraft = "";
    public String editDraft = "";
    public Message editDraftTarget;

    private static final Command sendCommand = 
        new Command("Send", "Send message", Command.SCREEN, CMD_SEND);

    private static final Command replyCommand =
        new Command("Reply", Command.ITEM, CMD_REPLY);

    private static final Command editCommand =
        new Command("Edit", Command.ITEM, CMD_EDIT);

    private static final Command deleteCommand =
        new Command("Delete", Command.ITEM, CMD_DELETE);

    private static final Command refreshCommand =
        new Command("Refresh", Command.SCREEN, CMD_REFRESH);

    private static SmallButtonItem olderMessagesButton =
        new SmallButtonItem("Older messages");

    private static SmallButtonItem newerMessagesButton =
        new SmallButtonItem("Newer messages");

    public ChatScreen(Channel channel) {
        // By default, fetch latest messages
        this(channel, null, null, 0);
    }

    public ChatScreen(Channel channel, String paginationType, String paginationMessage, int newPage) {
        super(0, Fonts.height/4);
        setTitle(channel.name);

        this.channel = channel;

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));

        fetchMessages(paginationType, paginationMessage, newPage, true);
    }

    public void fetchMessages(String paginationType, String paginationMessage, int newPage, boolean showLoadingAnimation) {
        removeCommand(sendCommand);
        removeCommand(replyCommand);
        removeCommand(refreshCommand);

        if (showLoadingAnimation) {
            removeAllItems();
            addItem(new LoadingItem());
        }
        else {
            setBannerText("Loading messages");
        }

        HTTPThread h = new HTTPThread(HTTPThread.ACTION_FETCH_MESSAGES, this);
        h.channel = channel;
        h.paginationType = paginationType;
        h.paginationMessage = paginationMessage;
        h.newPage = newPage;
        h.start();

        IconManager.iconCache.save();
    }

    /**
     * Show/hide commands according to which ones are relevant
     * for the currently highlighted item
     */
    protected void highlightedItemChanged() {
        Item highlighted = getHighlightedItem();

        // Don't allow message actions on status messages
        boolean messageHighlighted = (highlighted instanceof MessageItem) &&
            !((MessageItem) highlighted).message.isStatus;

        boolean ownMessageHighlighted =
            messageHighlighted && ((MessageItem) highlighted).message.isOwn();

        if (messageHighlighted) {
            addCommand(replyCommand);
        }
        else {
            removeCommand(replyCommand);
        }

        if (ownMessageHighlighted) {
            addCommand(editCommand);
            addCommand(deleteCommand);
        }
        else {
            removeCommand(editCommand);
            removeCommand(deleteCommand);
        }
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                IconManager.iconCache.save();
                App.popScreen();
                break;
            }
            case CMD_SEND: {
                sendTypingIndicator();
                App.disp.setCurrent(new MessageBox(this, messageDraft));
                break;
            }
            case CMD_REPLY: {
                sendTypingIndicator();
                MessageItem item = (MessageItem) getHighlightedItem();
                App.pushScreen(new ReplyScreen(channel, item.message, messageDraft));
                break;
            }
            case CMD_EDIT: {
                MessageItem item = (MessageItem) getHighlightedItem();
                App.disp.setCurrent(new MessageEditBox(this, item.message));
                break;
            }
            case CMD_DELETE: {
                showDeletePrompt();
                break;
            }
            case CMD_REFRESH: {
                fetchMessages(null, null, 0, false);
                break;
            }
            case CMD_DELETE_PROMPT_YES: {
                MessageItem item = (MessageItem) getHighlightedItem();

                HTTPThread h = new HTTPThread(HTTPThread.ACTION_DELETE_MESSAGE, this);
                h.editOrDeleteMessage = item.message;
                h.channel = channel;
                h.start();

                App.popScreen();
                break;
            }
            case CMD_DELETE_PROMPT_NO: {
                App.popScreen();
                break;
            }
        }
    }

    public static volatile long lastSentTypingTime;

    private void sendTypingIndicator() {
        if (!Settings.sendTypingIndicator) return;

        // to avoid API spam, only allow sending typing indicator once every 10 sec
        long now = System.currentTimeMillis();
        if (now < lastSentTypingTime + 10000) return;

        HTTPThread h = new HTTPThread(HTTPThread.ACTION_SEND_TYPING);
        h.channel = channel;
        h.start();

        // lastSentTypingTime is updated by thread
    }

    private void showDeletePrompt() {
        Font largerBold = Fonts.get(1, true, false, false);

        MessageItem item = (MessageItem) getHighlightedItem();
        String messagePreview = Util.stringToLength(item.message.content, 80);

        Dialog d = new Dialog();

        d.listView.addItem(new StringItem("Delete this message?", largerBold));
        d.listView.addItem("\"" + messagePreview + "\"");

        d.addCommand(new Command("Yes", Command.OK, CMD_DELETE_PROMPT_YES));
        d.addCommand(new Command("No", Command.BACK, CMD_DELETE_PROMPT_NO));
        d.setCommandListener(this);

        App.pushScreen(d);
    }

    public void itemSelected(Item i) {
        if (i == olderMessagesButton) {
            Message first = (Message) messages.elementAt(0);
            int newPage = (page == PAGE_INDETERMINATE) ? PAGE_INDETERMINATE : (page + 1);

            fetchMessages("before", first.id, newPage, false);
        }
        else if (i == newerMessagesButton) {
            Message last = (Message) messages.lastElement();
            int newPage = (page == PAGE_INDETERMINATE) ? PAGE_INDETERMINATE : (page - 1);

            fetchMessages("after", last.id, newPage, false);
        }
    }

    public void populate(JSONArray messagesJson, String paginationType) {
        addCommand(sendCommand);
        addCommand(refreshCommand);

        addMessages(messagesJson, paginationType);
        readdItems();

        hasLoaded = true;
    }

    private void addMessages(JSONArray messagesJson, String paginationType) {
        Vector oldMessages = messages;

        messages = new Vector(messagesJson.size());

        // Loaded a non-full page of messages when selecting "Newer messages" -> keep some of the old ones
        if (page == PAGE_INDETERMINATE && "after".equals(paginationType) &&
            messagesJson.size() < Settings.messageLoadCount) {

            for (int i = messagesJson.size(); i < oldMessages.size(); i++) {
                Object oldMessage = oldMessages.elementAt(i);
                messages.addElement(oldMessage);
            }
            
            // Also, that means we are at the newest messages
            page = 0;
        }

        oldMessages = null;

        for (int i = messagesJson.size() - 1; i >= 0; i--) {
            JSONObject messageJson = messagesJson.getObject(i);
            Message message = new Message(messageJson);

            messages.addElement(message);
        }
    }

    public void readdItems() {
        Vector newItems = new Vector();

        if (messages.size() >= Settings.messageLoadCount || page == PAGE_INDETERMINATE) {
            newItems.addElement(olderMessagesButton);
        }

        addMessageItems(newItems);

        if (page > 0 || page == PAGE_INDETERMINATE) {
            newItems.addElement(newerMessagesButton);
        }
        else if (newItems.size() > 0) {
            newItems.addElement(new BlankItem(-itemPadding));
        }

        calculateMessageGrouping();

        removeAllItems();
        addItems(newItems);
    }

    private void addMessageItems(Vector newItemsOutput) {
        for (int i = 0; i < messages.size(); i++) {
            Message message = (Message) messages.elementAt(i);

            newItemsOutput.addElement(new MessageItem(message));

            if (message.attachments != null) {
                for (int j = 0; j < message.attachments.size(); j++) {
                    Attachment att = (Attachment) message.attachments.elementAt(j);

                    newItemsOutput.addElement(new AttachmentItem(att));
                }
            }
        }
    }

    private void calculateMessageGrouping() {
        if (messages.size() == 0) return;

        Message first = (Message) messages.elementAt(0);
        Message above = first;
        String clusterStartID = first.id;
        first.showAuthor = true;

        if (messages.size() == 1) return;

        for (int i = 1; i < messages.size(); i++) {
            Message msg = (Message) messages.elementAt(i);

            // Recalculate "should show author" value for message
            msg.showAuthor = msg.shouldShowAuthor(above, clusterStartID);

            if (msg.showAuthor) clusterStartID = msg.id;
            above = msg;
        }
    }

    /**
     * Return true on success, i.e. if the message is on the current page
     */
    public boolean jumpToMessage(String messageID) {
        // Recalc to make sure correct item heights are used to calculate scroll
        recalcNow();

        for (int i = 0; i < items.size(); i++) {
            Item item = (Item) items.elementAt(i);

            if (!(item instanceof MessageItem)) continue;
            if (!((MessageItem) item).message.id.equals(messageID)) continue;

            setHighlightedItem(item);
            return true;
        }

        return false;
    }
}