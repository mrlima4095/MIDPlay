package fi.gtrxac.bluewap.ui;

public class Colors {
    /**
     * ListScreen
     */
    public static int listScreenBackground;
    public static int listScreenScrollbar;
    public static int listScreenScrollbarSel;
    public static int listScreenEmptyText;
    public static int bannerBackground;
    public static int bannerOutline;
    public static int bannerText;

    /**
     * TabbedListScreen
     */
    public static int tabBarBackground;
    public static int tabBackground;
    public static int tabOutlineColor;
    public static int tabText;
    public static int tabTextSel;

    /**
     * StringItem and similar text-only items (ListItem, RichTextItem)
     */
    public static int stringItemText;

    /**
     * Custom title bar
     */
    public static int titleBarBackground;
    public static int titleBarOutline;
    public static int titleBarText;

    /**
     * Custom softkey bar (for touchscreen)
     */
    public static int softkeyBarBackgroundTouch;
    public static int softkeyBackground;
    public static int softkeyBackgroundSel;
    public static int softkeyOutline;
    public static int softkeyOutlineSel;
    public static int softkeyText;
    public static int softkeyTextSel;

    /**
     * Custom softkey bar (for non-touchscreen)
     */
    public static int softkeyBarBackgroundNonTouch;
    public static int softkeyBarOutline;
    public static int softkeyBarText;

    /**
     * Common highlight used by Items
     */
    public static int highlightOutline;
    
    /**
     * Common spacer line (SpacerItem and ListItem)
     */
    public static int spacerItem;

    /**
     * ButtonItem
     * Uses highlightOutline when selected
     */
    public static int buttonBackground;
    public static int buttonBackgroundSel;
    public static int buttonOutline;
    public static int buttonText;
    public static int buttonTextSel;

    /**
     * LinkItem
     */
    public static int linkBackgroundSel;
    public static int linkText;
    public static int linkTextSel;

    /**
     * ListItem
     */
    public static int listItemBackgroundSel;
    public static int listItemText;
    public static int listItemTextSel;
    public static int listItemArrow;

    /**
     * OptionItem
     */
    public static int optionItemBackgroundSel;
    public static int optionItemText;
    public static int optionItemTextSel;

    /**
     * RadioButtonItem
     */
    public static int radioButtonItemBackgroundSel;
    public static int radioButtonOutline;
    public static int radioButtonFill;
    public static int radioButtonDot;

    /**
     * TextFieldItem
     */
    public static int textFieldOutline;
    public static int textFieldBackgroundSel;
    public static int textFieldText;
    public static int textFieldTextSel;

    /**
     * Load built-in color scheme (white, gray, blue)
     */
    public static void loadDefaults() {
        listScreenBackground = 0xFFFFFF;
        listScreenScrollbar = 0xCCCCCC;
        listScreenScrollbarSel = 0x888888;
        listScreenEmptyText = 0x666666;
        bannerBackground = 0xEEEEEE;
        bannerOutline = 0xAAAAAA;
        bannerText = 0x000000;
        tabBarBackground = 0xBBBBBB;
        tabBackground = 0xDDDDDD;
        tabOutlineColor = 0x9A9A9A;
        tabText = 0x111111;
        tabTextSel = 0x000000;
        stringItemText = 0x111111;
        titleBarBackground = 0xDDDDDD;
        titleBarOutline = 0x9B9B9B;
        titleBarText = 0x111111;
        softkeyBarBackgroundTouch = 0xEEEEEE;
        softkeyBackground = 0xDDDDDD;
        softkeyBackgroundSel = 0xBBBBBB;
        softkeyOutline = 0x9B9B9B;
        softkeyOutlineSel = 0x838383;
        softkeyText = 0x111111;
        softkeyTextSel = 0x000000;
        softkeyBarBackgroundNonTouch = 0xDDDDDD;
        softkeyBarOutline = 0x9B9B9B;
        softkeyBarText = 0x111111;
        highlightOutline = 0x5599FF;
        spacerItem = 0x888888;
        buttonBackground = 0xDDDDDD;
        buttonBackgroundSel = 0xAADDFF;
        buttonOutline = 0x9A9A9A;
        buttonText = 0x111111;
        buttonTextSel = 0x000000;
        linkBackgroundSel = 0xEEF8FF;
        linkText = 0x3355CC;
        linkTextSel = 0x2244AA;
        listItemBackgroundSel = 0xAADDFF;
        listItemText = 0x111111;
        listItemTextSel = 0x000000;
        listItemArrow = 0x444444;
        optionItemBackgroundSel = 0xAADDFF;
        optionItemText = 0x111111;
        optionItemTextSel = 0x000000;
        radioButtonItemBackgroundSel = 0xEEF8FF;
        radioButtonOutline = 0x9A9A9A;
        radioButtonFill = 0xDDDDDD;
        radioButtonDot = 0x111111;
        textFieldOutline = 0x999999;
        textFieldBackgroundSel = 0xEEF8FF;
        textFieldText = 0x222222;
        textFieldTextSel = 0x000000;
    }

    public static void load() {
        loadDefaults();
    }
}