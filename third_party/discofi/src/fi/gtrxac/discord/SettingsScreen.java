package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.TextField;

public class SettingsScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;

    private RadioButtonGroup fontSizeGroup;
    private RadioButtonGroup themeGroup;
    private RadioButtonGroup avatarGroup;
    private RadioButtonGroup timeFormatGroup;
    private TextFieldItem messageCountField;
    private RadioButtonGroup sendTypingGroup;

    public SettingsScreen() {
        super();
        setTitle("Settings");

        addItem(new ChannelCategoryItem("Appearance"));

        fontSizeGroup = new RadioButtonGroup();
        addItem("Font size:");
        addItem(new RadioButtonItem(fontSizeGroup, "Small"));
        addItem(new RadioButtonItem(fontSizeGroup, "Medium"));
        addItem(new RadioButtonItem(fontSizeGroup, "Large"));

        int sizeIndex = Settings.fontSize == Font.SIZE_SMALL ? 0 :
            Settings.fontSize == Font.SIZE_MEDIUM ? 1 : 2;

        fontSizeGroup.setTickedIndex(sizeIndex);

        themeGroup = new RadioButtonGroup();
        addItem("Theme:");
        addItem(new RadioButtonItem(themeGroup, "Dark"));
        addItem(new RadioButtonItem(themeGroup, "Light"));

        themeGroup.setTickedIndex(Settings.lightTheme ? 1 : 0);

        avatarGroup = new RadioButtonGroup();
        addItem("Avatars:");
        addItem(new RadioButtonItem(avatarGroup, "Circle"));
        addItem(new RadioButtonItem(avatarGroup, "Square"));

        avatarGroup.setTickedIndex(Settings.squareAvatars ? 1 : 0);

        timeFormatGroup = new RadioButtonGroup();
        addItem("Time format:");
        addItem(new RadioButtonItem(timeFormatGroup, "12 hours"));
        addItem(new RadioButtonItem(timeFormatGroup, "24 hours"));

        timeFormatGroup.setTickedIndex(Settings.use12hTime ? 0 : 1);

        addItem(new BlankItem(0));
        addItem(new ChannelCategoryItem("Behavior"));

        messageCountField = new TextFieldItem(
            "Message count", Integer.toString(Settings.messageLoadCount), 3, TextField.NUMERIC);

        addItem("Message load count:");
        addItem(messageCountField);

        sendTypingGroup = new RadioButtonGroup();
        addItem("Send typing indicator:");
        addItem(new RadioButtonItem(sendTypingGroup, "On"));
        addItem(new RadioButtonItem(sendTypingGroup, "Off"));

        sendTypingGroup.setTickedIndex(Settings.sendTypingIndicator ? 0 : 1);

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
    }

    private void writeSettings() {
        int[] fontSizes = { Font.SIZE_SMALL, Font.SIZE_MEDIUM, Font.SIZE_LARGE };
        Settings.fontSize = fontSizes[fontSizeGroup.getTickedIndex()];

        Settings.lightTheme = (themeGroup.getTickedIndex() == 1);
        Settings.squareAvatars = (avatarGroup.getTickedIndex() == 1);
        Settings.use12hTime = (timeFormatGroup.getTickedIndex() == 0);
        Settings.messageLoadCount = parseMessageLoadCount();
        Settings.sendTypingIndicator = (sendTypingGroup.getTickedIndex() == 0);

        // HTTPQueue.maxSlots = Integer.parseInt(connLimitField.getValue());
        // if (HTTPQueue.maxSlots < 1) HTTPQueue.maxSlots = 1;
    }

    private int parseMessageLoadCount() {
        try {
            int result = Integer.parseInt(messageCountField.getValue());

            if (result < 1) {
                return 1;
            }
            if (result > 100) {
                return 100;
            }
            return result;
        }
        catch (NumberFormatException e) {
            return 20;
        }
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                writeSettings();
                Settings.save();
                App.popScreen();
                break;
            }
        }
    }

    public void itemSelected(Item i) {
        writeSettings();
        Settings.refresh();
    }
}