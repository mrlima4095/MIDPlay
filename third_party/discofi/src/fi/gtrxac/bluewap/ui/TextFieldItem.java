package fi.gtrxac.bluewap.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;
import fi.gtrxac.bluewap.*;

/**
 * A ListScreen item that contains and handles text input from the user.
 * When the item is selected, text can be entered via LCDUI TextBox.
 */
public class TextFieldItem extends Item implements CommandListener {
    private String title;
    private String value;
    private String displayText;
    private int maxSize;
    private int constraints;

    public TextFieldItem(String title, String value, int maxSize, int constraints) {
        super(true);
        this.title = title;
        this.value = (value != null) ? value : "";
        this.maxSize = maxSize;
        this.constraints = constraints;
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        if (highlighted) {
            g.setColor(Colors.textFieldBackgroundSel);
            g.fillRect(0, 0, width, height);
            drawHighlight(g, width);
            g.setColor(Colors.textFieldTextSel);
        } else {
            g.setColor(Colors.textFieldOutline);
            g.drawRect(0, 0, width - 1, height - 1);
            g.setColor(Colors.textFieldText);
        }

        int x = Math.max(3, Fonts.height/6);
        int y = (height - Fonts.height)/2;

        g.setFont(Fonts.plain);
        g.drawString(displayText, x, y, 0);
    }

    public void recalc(int width) {
        height = Math.max(Fonts.height*3/2, Fonts.height + 6);

        int padding = Math.max(3, Fonts.height/6);
        displayText = Util.stringToWidth(value, Fonts.plain, width - padding*2);
    }

    public void itemSelected() {
        int textBoxMaxSize = Math.max(maxSize, value.length());
        TextBox t = null;

        try {
            t = new TextBox(title, value, textBoxMaxSize, constraints);
        }
        catch (IllegalArgumentException e) {
            // value was invalid for the constraints - try with no constraints
            t = new TextBox(title, value, textBoxMaxSize, 0);
        }
        
        t.addCommand(new Command("OK", Command.OK, 0));
        t.addCommand(new Command("Cancel", Command.BACK, 1));
        t.setCommandListener(this);
        AppBase.disp.setCurrent(t);
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getPriority() == 0) {
            TextBox t = (TextBox) d;
            value = t.getString();
            needRecalc();
            valueChanged(value);
        }
        AppBase.disp.setCurrent(AppCanvas.instance);
    }

    /**
     * Get the text currently contained in this item.
     */
    public String getValue() {
        return value;
    }

    public void valueChanged(String newValue) {}
}