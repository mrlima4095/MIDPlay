package fi.gtrxac.bluewap.ui;

import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * A ListScreen item that shows lines of text as a bullet-point list.
 */
public class StringListItem extends Item {
    private StringItem[] stringItems;

    private static int bulletWidth;
    private static int availableWidth;

    public StringListItem(String[] lines) {
        super(false);
        stringItems = new StringItem[lines.length];

        for (int i = 0; i < lines.length; i++) {
            stringItems[i] = new StringItem(lines[i]);
        }
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        int origTranslate = g.getTranslateY();

        g.translate(bulletWidth, 0);
        g.setColor(Colors.stringItemText);

        for (int i = 0; i < stringItems.length; i++) {
            g.drawChar('-', -bulletWidth, 0, 0);

            stringItems[i].draw(g, screen, availableWidth, highlighted);
            g.translate(0, stringItems[i].height);
        }

        g.translate(-bulletWidth, -g.getTranslateY() + origTranslate);
    }

    public void recalc(int width) {
        bulletWidth = Fonts.plain.stringWidth("- ");
        availableWidth = width - bulletWidth;

        int linePadding = Fonts.height/7;
        height = -linePadding;

        for (int i = 0; i < stringItems.length; i++) {
            stringItems[i].recalc(availableWidth);
            stringItems[i].height += linePadding;
            height += stringItems[i].height;
        }
    }
}