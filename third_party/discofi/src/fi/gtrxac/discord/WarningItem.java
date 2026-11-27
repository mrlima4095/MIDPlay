package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public class WarningItem extends StringItem {
    private static int padding;
    private static int rounding;
    private static int backgroundColor;
    private static int outlineColor;

    public WarningItem(String text) {
        super(text);
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        g.setColor(backgroundColor);
        g.fillRoundRect(
            padding,
            padding,
            width - padding*2,
            height - padding*2,
            rounding,
            rounding);

        g.setColor(outlineColor);
        g.drawRoundRect(
            padding,
            padding,
            width - padding*2,
            height - padding*2,
            rounding,
            rounding);

        g.translate(padding*2, padding*2);
        super.draw(g, screen, width, highlighted);
        g.translate(-padding*2, -padding*2);
    }

    public void recalc(int width) {
        padding = Fonts.height/4;
        rounding = Math.max(Fonts.height/4, 6);

        // TODO: themable
        backgroundColor = Util.blend(Theme.listScreenBackground, 0xFF8822, 6);
        outlineColor = Util.blend(Theme.listScreenBackground, 0xFF8822, 3);

        super.recalc(width - padding*4);
        height += padding*4;
    }
}