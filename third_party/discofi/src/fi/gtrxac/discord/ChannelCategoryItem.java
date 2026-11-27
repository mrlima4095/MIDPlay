package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public class ChannelCategoryItem extends StringItem {
    private static int padding;

    public ChannelCategoryItem(String text) {
        super(text);
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        g.setColor(Theme.channelCategoryBackground);
        g.fillRect(0, -screen.itemPadding, width, height + screen.itemPadding*2);

        g.translate(padding, padding);
        super.draw(g, screen, width - padding*2, highlighted);
        g.translate(-padding, -padding);
    }

    public void recalc(int width) {
        definedFont = Fonts.bold;
        color = Theme.channelCategoryText;
        
        padding = Fonts.height/7;
        super.recalc(width - padding*2);
        height += padding*2;
    }
}