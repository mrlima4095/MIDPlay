package fi.gtrxac.bluewap.ui;

import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public class ButtonItem extends Item {
    public String text;

    public ButtonItem(String text) {
        super(true);
        this.text = text;
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        g.setColor(highlighted ? Colors.buttonBackgroundSel : Colors.buttonBackground);
        g.fillRect(0, 0, width, height);

        if (highlighted) {
            drawHighlight(g, width);
        } else {
            g.setColor(Colors.buttonOutline);
            g.drawRect(0, 0, width - 1, height - 1);
        }
        
        g.setFont(Fonts.bold);
        g.setColor(highlighted ? Colors.buttonTextSel : Colors.buttonText);
        g.drawString(text, width/2, (height - Fonts.boldHeight)/2, Graphics.HCENTER | Graphics.TOP);
    }

    public void recalc(int width) {
        height = Math.max(Fonts.height*3/2, Fonts.height + 6);
    }
}