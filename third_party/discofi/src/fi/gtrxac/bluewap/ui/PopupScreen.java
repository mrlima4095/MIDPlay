package fi.gtrxac.bluewap.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.CommandListener;
import java.util.*;

public class PopupScreen extends Screen implements CommandListener {
    public final ListView listView;

    private int maxWidth;
    protected int viewX;
    protected int viewY;
    protected int viewWidth;
    protected int viewHeight;

    public PopupScreen(int margin, int itemPadding, int maxWidth) {
        super(0);
        setTitle(AppBase.getCurrentScreen().getTitle());

        this.maxWidth = maxWidth;

        listView = new ListView(margin, itemPadding);
    }

    public PopupScreen() {
        this(Fonts.height/6, Fonts.height/6, Fonts.height*12);
    }

    public void draw(Graphics g) {
        drawPreviousScreenDimmed(g);
        listView.prepareAndDraw(g);
    }

    public void recalc() {
        viewWidth = Math.min(maxWidth, getWidth()*19/20);
        listView.setPosition(0, 0, viewWidth, 100);
        listView.recalcNow();

        Item viewLastItem = (Item) listView.items.lastElement();

        int viewContentHeight = (viewLastItem == null) ? 0 :
            (viewLastItem.y + viewLastItem.height + listView.itemPadding*2);

        viewHeight = Math.min(getHeight()*9/10, viewContentHeight);
        viewX = (getWidth() - viewWidth)/2;
        viewY = getHeight() - viewHeight;

        listView.setPosition(viewX, viewY, viewWidth, viewHeight);
        listView.recalcNow();
    }

    public void keyEvent(int keyCode, int gameAction) {
        listView.keyEvent(keyCode, gameAction);
    }

    public void pointerPressed(int x, int y) {
        if (y < viewY || x < viewX) return;
        listView.pointerPressed(x, y);
    }

    public void pointerDragged(int x, int y) {
        if (y < viewY || x < viewX) return;
        listView.pointerDragged(x, y);
    }
    
    public void pointerReleased(int x, int y) {
        if (y < viewY || x < viewX) return;
        listView.pointerReleased(x, y);
    }
}