package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import tube42.lib.imagelib.ImageUtils;

public class MessageItem extends Item {
    public Message message;
    private String[] textLines;
    private String displayedAuthor;
    private int timestampX;
    private int timestampY;

    private int replyAuthorWidth;
    private String displayedReplyAuthor;
    private String displayedReplyContent;
    private boolean replyImageHighlighted;
    private Image replyImage;

    private final Object replyImageLock = new Object();

    private static Font smallerFont;
    private static Font smallerBoldFont;
    private static Font smallerItalicFont;
    
    private static String unloadedReplyText;
    private static int leftMargin;
    private static int avatarSize;
    public static int contentX;
    public static int contentWidth;
    private static int replyHeight;

    public MessageItem(Message m) {
        super(true);
        this.message = m;
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        int pad = screen.itemPadding;

        // Highlight background
        if (highlighted) {
            g.setColor(Theme.messageBackgroundSel);
            g.fillRect(
                0,
                -pad/2,
                width,
                height + pad/2*2);
        }

        int textY = 0;

        // Author line
        if (message.showAuthor) {
            // Reply
            if (message.isReply) {
                // Lines connecting reply to message
                drawReplyConnectorLines(g);
                drawReplyOnScreen(g, highlighted);
                textY += replyHeight;
            }

            drawAvatar(g, message.author, leftMargin, textY + pad, avatarSize);

            // Username
            g.setFont(Fonts.bold);
            g.setColor(Theme.username);
            g.drawString(displayedAuthor, contentX, textY, 0);

            // Timestamp
            g.setFont(smallerFont);
            g.setColor(Theme.timestamp);
            g.drawString(message.timestamp, timestampX, timestampY, 0);

            textY += Fonts.boldHeight;
        }

        // Message content
        drawMessageContent(g, textY, highlighted);
    }

    private void drawReplyConnectorLines(Graphics g) {
        int linesBeginX = contentX/2;
        int linesBeginY = replyHeight/2;
        int horizontalLineEndX = contentX*5/6;
        int verticalLineEndY = replyHeight*5/6;

        g.setColor(Theme.replyConnectorLines);
        g.drawLine(linesBeginX, linesBeginY, horizontalLineEndX, linesBeginY);
        g.drawLine(linesBeginX, linesBeginY, linesBeginX, verticalLineEndY);
    }

    private void drawReplyOnScreen(Graphics g, boolean highlighted) {
        if (Settings.fontSize == Font.SIZE_SMALL) {
            synchronized (replyImageLock) {
                checkUpdateReplyImage(highlighted);
                g.drawImage(replyImage, contentX, Fonts.height/6, 0);
            }
        }
        else {
            drawReply(g, contentX, Fonts.height/6);
        }
    }

    private void drawReply(Graphics g, int x, int y) {
        if (message.replyAuthor != null) {
            drawLoadedReply(g, x, y);
        }
        else {
            drawUnloadedReply(g, x, y);
        }
    }

    private void drawLoadedReply(Graphics g, int x, int y) {
        int avatarSize = smallerFont.getHeight();
        drawAvatar(g, message.replyAuthor, x, y, avatarSize);

        x += avatarSize + Fonts.height/3;
        
        // Username
        g.setColor(Theme.replyUsername);
        g.setFont(smallerBoldFont);
        g.drawString(displayedReplyAuthor, x, y, 0);

        x += replyAuthorWidth;

        // Message content
        g.setColor(Theme.replyContent);
        g.setFont(smallerFont);
        g.drawString(displayedReplyContent, x, y, 0);
    }

    private void drawUnloadedReply(Graphics g, int x, int y) {
        int avatarSize = smallerFont.getHeight();
        drawAvatarPlaceholder(g, x, y, avatarSize);

        x += avatarSize + Fonts.height/3;

        // Message content
        g.setColor(Theme.replyContent);
        g.setFont(smallerItalicFont);
        g.drawString(unloadedReplyText, x, y, 0);
    }

    private void drawAvatar(Graphics g, User user, int x, int y, int size) {
        Image avatar = IconManager.get(user, size, this);

        if (avatar != null) {
            g.drawImage(avatar, x, y, Graphics.TOP | Graphics.LEFT);
        }
        else {
            drawAvatarPlaceholder(g, x, y, size);
        }
    }

    private void drawAvatarPlaceholder(Graphics g, int x, int y, int size) {
        g.setColor(Theme.avatarPlaceholder);

        if (Settings.squareAvatars) {
            g.fillRect(x, y, size, size);
        }
        else {
            g.fillArc(x, y, size, size, 0, 360);
        }
    }

    private void drawMessageContent(Graphics g, int y, boolean highlighted) {
        if (message.isStatus || message.isForwarded) {
            g.setColor(highlighted ? Theme.messageTextStatusSel : Theme.messageTextStatus);
        }

        if (message.isForwarded) {
            g.setFont(Fonts.italic);
            g.drawString("Forwarded", contentX, y, 0);
            y += Fonts.height;
        }

        if (!message.isStatus) {
            g.setColor(highlighted ? Theme.messageTextSel : Theme.messageText);
        }

        g.setFont(Fonts.plain);

        for (int i = 0; i < textLines.length; i++) {
            g.drawString(textLines[i], contentX, y, 0);
            y += Fonts.height;
        }
    }

    // Callback
    public void avatarLoaded() {
        updateReplyImage();
        App.repaint();
    }

    private void updateReplyImage() {
        synchronized (replyImageLock) {
            replyImage = null;
        }
    }

    private void checkUpdateReplyImage(boolean highlighted) {
        if (replyImage != null && replyImageHighlighted == highlighted) return;

        replyImage = null;
        replyImage = Image.createImage(contentWidth*4/3, Fonts.height);

        Graphics g = replyImage.getGraphics();

        g.setColor(highlighted ? Theme.messageBackgroundSel : Theme.listScreenBackground);
        g.fillRect(0, 0, contentWidth*4/3, Fonts.height);

        drawReply(g, 0, 0);

        replyImage = ImageUtils.resize(replyImage, contentWidth, Fonts.height*3/4, true, true);
        replyImageHighlighted = highlighted;
    }

    public void recalc(int width, ListScreen screen, Item aboveItem) {
        leftMargin = Fonts.height/3;
        avatarSize = Fonts.height*5/4;
        int avatarRightMargin = Fonts.height/3;

        contentX = leftMargin + avatarSize + avatarRightMargin;
        contentWidth = width - contentX;

        String displayedContent = message.isUnsupported() ?
            "(unsupported message)" :
            message.content;

        textLines = Util.wordWrap(displayedContent, contentWidth, Fonts.plain);
        height = Fonts.height*textLines.length;

        if (message.isForwarded) {
            height += Fonts.height;  // extra line for the "Forwarded" text
        }

        calcExtras();

        if (message.content.length() == 0 && !message.isUnsupported()) {
            // Make the message more closely connected to its attachment links
            // if the message has no text content
            height -= screen.itemPadding/2;
        }
    }

    private void calcExtras() {
        if (!message.showAuthor) return;
        calcFonts();
        calcAuthorHeight();
        calcTimestampAndAuthor();

        if (!message.isReply) return;
        calcReplyHeight();
        calcUnloadedReplyContent();
        updateReplyImage();

        if (message.replyAuthor == null) return;
        calcReplyContent();
    }

    private void calcFonts() {
        smallerFont = Fonts.get(-1, false, false, false);
        smallerBoldFont = Fonts.get(-1, true, false, false);
        smallerItalicFont = Fonts.get(-1, false, true, false);
    }

    private void calcAuthorHeight() {
        height += Fonts.boldHeight;
    }

    private void calcTimestampAndAuthor() {
        timestampY = Fonts.bold.getBaselinePosition() - smallerFont.getBaselinePosition();

        int timestampPadding = Fonts.plain.stringWidth("  ");
        int timestampWidth = timestampPadding + smallerFont.stringWidth(message.timestamp);
        int authorAvailableWidth = contentWidth - timestampWidth;
        
        displayedAuthor = Util.stringToWidth(
            message.author.name, Fonts.bold, authorAvailableWidth);

        int authorWidth = Fonts.bold.stringWidth(displayedAuthor);
        
        timestampX = contentX + authorWidth + timestampPadding;
    }

    private void calcReplyHeight() {
        int replyTopMargin = Fonts.height/6;
        int replyBottomMargin = Fonts.height/5;

        int replyContentHeight = (Settings.fontSize == Font.SIZE_SMALL) ?
            Fonts.height*3/4 :
            smallerFont.getHeight();

        replyHeight = replyTopMargin + replyContentHeight + replyBottomMargin;
        height += replyHeight;
        timestampY += replyHeight;
    }

    private void calcUnloadedReplyContent() {
        // Replies usually don't load in the inbox screen, so there we instead have "Replied to you"
        String fullText = (App.getCurrentScreen() instanceof ChatScreen) ?
            "Message could not be loaded" : "Replied to you";

        unloadedReplyText = getDisplayedReplyContent(fullText, smallerItalicFont, 0);
    }

    private void calcReplyContent() {
        int replyAuthorRightMargin = Fonts.height/3;

        if (message.replyAuthor != null) {
            displayedReplyAuthor = Util.stringToWidth(
                message.replyAuthor.name, smallerBoldFont, contentWidth/2);

            replyAuthorWidth = smallerBoldFont.stringWidth(displayedReplyAuthor)
                + replyAuthorRightMargin;

            displayedReplyContent = getDisplayedReplyContent(
                message.replyContent, smallerFont, replyAuthorWidth);
        }
    }

    private static String getDisplayedReplyContent(String text, Font font, int replyAuthorWidth) {
        if (Settings.fontSize == Font.SIZE_SMALL) {
            // Small font size
            // -> replies are drawn to separate image to be downscaled
            // -> truncation not needed as the text is already cut off by image borders
            return text;
        }
        else {
            // Larger font size
            // -> replies are drawn directly on screen
            // -> needs truncation so the text doesn't draw over the scrollbar
            int avatarAreaWidth = smallerFont.getHeight() + Fonts.height/3;

            int replyContentAvailableWidth =
                contentWidth - avatarAreaWidth - replyAuthorWidth;

            return Util.stringToWidth(text, font, replyContentAvailableWidth);
        }
    }

    public void itemSelected() {
        Screen curr = App.getCurrentScreen();

        if (curr instanceof ChatScreen) {
            String replyID = message.replyID;
            if (replyID == null) return;

            ChatScreen chatScreen = (ChatScreen) curr;

            // jump to reply, or load the page that has it
            if (!chatScreen.jumpToMessage(replyID)) {
                chatScreen.fetchMessages(
                    "around", replyID, ChatScreen.PAGE_INDETERMINATE, false);
            }
        }
        else if (curr instanceof MainScreen) {
            // For inbox tab: open chat and go to that message
            // For now, this only works if the channel is in a cached guild
            Account acc = Accounts.getActive();
            Channel channel = acc.channelCache.getChannelByID(null, message.channelID);
            if (channel == null) return;
            
            App.pushScreen(new ChatScreen(
                channel, "around", message.id, ChatScreen.PAGE_INDETERMINATE));
        }
    }
}