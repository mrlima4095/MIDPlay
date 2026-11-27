package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;

/**
 * Adds Discofi-specific color entries which are used
 * in addition to the BlueWAP UI framework's colors.
 */
public class Theme extends Colors {
    /**
     * ChannelCategoryItem
     */
    public static int channelCategoryText;
    public static int channelCategoryBackground;

    /**
     * LabelItem
     */
    public static int labelItem;

    /**
     * LoadingItem
     */
    public static int loadingItemText;

    /**
     * MessageItem
     */
    public static int messageBackgroundSel;
    public static int username;
    public static int timestamp;
    public static int messageText;
    public static int messageTextSel;
    public static int messageTextStatus;
    public static int messageTextStatusSel;
    public static int replyConnectorLines;
    public static int replyUsername;
    public static int replyContent;
    public static int avatarPlaceholder;

    public static void loadLightTheme() {
        Colors.load();

        channelCategoryText = 0x111111;
        channelCategoryBackground = 0xE8E8E8;
        labelItem = 0x333333;
        loadingItemText = 0x333333;
        messageBackgroundSel = 0xAADDFF;
        username = 0x000000;
        timestamp = 0x666666;
        messageText = 0x111111;
        messageTextSel = 0x000000;
        messageTextStatus = 0x666666;
        messageTextStatusSel = 0x555555;
        replyConnectorLines = 0x888888;
        replyUsername = 0x000000;
        replyContent = 0x666666;
        avatarPlaceholder = 0x666666;
    }

    private static final int[] darkGradient = {
        0x000000, 0x111115, 0x22222A, 0x333340,
        0x444455, 0x525264, 0x616174, 0x707083,
        0x808093, 0x9191A2, 0xA2A2B2, 0xB3B3C1,
        0xC5C5D1, 0xD8D8E0, 0xEBEBF0, 0xFFFFFF
    };

    private static final int darkSelHighlight = 0x3A426B;

    public static void loadDarkTheme() {
        listScreenBackground = darkGradient[0x3];
        listScreenScrollbar = darkGradient[0x8];
        listScreenScrollbarSel = darkGradient[0xB];
        listScreenEmptyText = darkGradient[0xA];
        bannerBackground = darkGradient[0x5];
        bannerOutline = darkGradient[0x2];
        bannerText = darkGradient[0xF];
        tabBarBackground = darkGradient[0x0];
        tabBackground = darkGradient[0x2];
        tabOutlineColor = darkGradient[0x0];
        tabText = darkGradient[0xF];
        tabTextSel = darkGradient[0xF];
        stringItemText = darkGradient[0xF];
        titleBarBackground = darkGradient[0x2];
        titleBarOutline = darkGradient[0x0];
        titleBarText = darkGradient[0xE];
        softkeyBarBackgroundTouch = darkGradient[0x2];
        softkeyBackground = darkGradient[0x4];
        softkeyBackgroundSel = darkGradient[0x5];
        softkeyOutline = darkGradient[0x0];
        softkeyOutlineSel = darkGradient[0x0];
        softkeyText = darkGradient[0xF];
        softkeyTextSel = darkGradient[0xF];
        softkeyBarBackgroundNonTouch = darkGradient[0x2];
        softkeyBarOutline = darkGradient[0x0];
        softkeyBarText = darkGradient[0xE];
        highlightOutline = 0x6688FF;
        spacerItem = darkGradient[0x0];
        buttonBackground = darkGradient[0x5];
        buttonBackgroundSel = 0x445392;
        buttonOutline = darkGradient[0x2];
        buttonText = darkGradient[0xF];
        buttonTextSel = darkGradient[0xF];
        linkBackgroundSel = darkSelHighlight;
        linkText = 0x5577EE;
        linkTextSel = 0x6688FF;
        listItemBackgroundSel = darkSelHighlight;
        listItemText = darkGradient[0xF];
        listItemTextSel = darkGradient[0xF];
        listItemArrow = darkGradient[0xA];
        optionItemBackgroundSel = darkSelHighlight;
        optionItemText = darkGradient[0xF];
        optionItemTextSel = darkGradient[0xF];
        radioButtonItemBackgroundSel = darkSelHighlight;
        radioButtonOutline = darkGradient[0x2];
        radioButtonFill = darkGradient[0xA];
        radioButtonDot = darkGradient[0x0];
        textFieldOutline = darkGradient[0x8];
        textFieldBackgroundSel = darkSelHighlight;
        textFieldText = darkGradient[0xF];
        textFieldTextSel = darkGradient[0xF];

        channelCategoryText = darkGradient[0xE];
        channelCategoryBackground = darkGradient[0x2];
        labelItem = darkGradient[0xC];
        loadingItemText = darkGradient[0xC];
        messageBackgroundSel = 0x3A426B;
        username = darkGradient[0xF];
        timestamp = darkGradient[0xA];
        messageText = darkGradient[0xF];
        messageTextSel = darkGradient[0xF];
        messageTextStatus = darkGradient[0xA];
        messageTextStatusSel = darkGradient[0xB];
        replyConnectorLines = darkGradient[0x8];
        replyUsername = darkGradient[0xE];
        replyContent = darkGradient[0xB];
        avatarPlaceholder = darkGradient[0x7];
    }

    public static void load() {
        if (Settings.lightTheme) {
            loadLightTheme();
        }
        else {
            loadDarkTheme();
        }
    }
}