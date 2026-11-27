package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public class FailedToLoadItem extends Item {
    private StringItem titleItem;
    private StringItem descItem;

    private int padding;
    private int offsetX;
    private int offsetY;

    public FailedToLoadItem() {
        super(true);

        int align = Graphics.HCENTER | Graphics.TOP;

        titleItem = new StringItem(
            "Failed to load data",
            Fonts.bold, align);

        descItem = new StringItem(
            "Check your connection and login details, then refresh to try again.",
            Fonts.plain, align);
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        int origTranslate = g.getTranslateY();
        g.translate(offsetX, offsetY);

        titleItem.draw(g, screen, width, false);
        g.translate(0, titleItem.height + padding);

        descItem.draw(g, screen, width, false);
        g.translate(-offsetX, -g.getTranslateY() + origTranslate);
    }

    public void recalc(int width, ListScreen screen, Item aboveItem) {
        int cappedWidth = Math.min(width, Fonts.height*14);
        offsetX = (width - cappedWidth)/2;

        titleItem.recalc(cappedWidth);
        descItem.recalc(cappedWidth);

        padding = Fonts.height/5;
        height = titleItem.height + descItem.height + padding;
        offsetY = (screen.getHeight() - height)/2 - screen.itemPadding;
    }
}