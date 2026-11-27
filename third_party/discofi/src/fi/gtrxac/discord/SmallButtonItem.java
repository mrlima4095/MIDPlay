package fi.gtrxac.bluewap.ui;

import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public class SmallButtonItem extends ButtonItem {
    private int buttonWidth;
    private int offsetX;

    public SmallButtonItem(String text) {
        super(text);
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        g.translate(offsetX, 0);
        super.draw(g, screen, buttonWidth, highlighted);
        g.translate(-offsetX, 0);
    }

    public void recalc(int width) {
        buttonWidth = Math.min(Fonts.height*12, width*4/5);
        offsetX = (width - buttonWidth)/2;
        super.recalc(buttonWidth);
    }
}