package fi.gtrxac.bluewap.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import java.util.*;
import fi.gtrxac.bluewap.*;

public class TabbedListScreen extends Screen {
    public int currentTabIndex;
    public Vector tabNames;
    public Vector shortTabNames;
    public Vector truncatedTabNames;
    public Vector tabs;

    private static int tabWidth;
    private static int tabMargin;
    private static int tabBarWidth;
    private static int tabBarHeight;

    public TabbedListScreen(int margin) {
        super(margin);
        currentTabIndex = -1;
        tabNames = new Vector();
        shortTabNames = new Vector();
        truncatedTabNames = new Vector();
        tabs = new Vector();
    }

    public TabbedListScreen() {
        this(0);
    }

    public void draw(Graphics g) {
        g.setColor(Colors.tabBarBackground);
        g.fillRect(0, 0, getWidth(), tabBarHeight);

        drawTabs(g);

        g.setColor(Colors.listScreenBackground);
        g.fillRect(0, tabBarHeight, getWidth(), tabMargin);

        ListView tab = getCurrentTab();
        if (tab != null) {
            tab.prepareAndDraw(g);
        }
    }

    private void drawTabs(Graphics g) {
        int tabDrawWidth = tabWidth - tabMargin;

        if (NetworkIndicator.isOnTopLeft) {
            g.translate(NetworkIndicator.width, 0);
        }

        for (int i = 0; i < tabs.size(); i++) {
            boolean selected = (i == currentTabIndex);
            String tabName = (String) truncatedTabNames.elementAt(i);

            int tabX = i*tabWidth + tabMargin;
            int tabY = selected ? tabMargin : Fonts.height/4;
            int tabHeight = tabBarHeight - tabY;

            g.setColor(selected ? Colors.listScreenBackground : Colors.tabBackground);
            g.fillRect(tabX, tabY, tabDrawWidth, tabHeight);

            // outline
            g.setColor(Colors.tabOutlineColor);
            g.drawRect(tabX, tabY, tabDrawWidth, tabHeight + 1);

            g.setColor(selected ? Colors.tabTextSel : Colors.tabText);
            g.setFont(Fonts.plain);
            g.drawString(
                tabName,
                tabX + tabDrawWidth/2,
                tabY + tabHeight/2 - Fonts.height/2,
                Graphics.HCENTER | Graphics.TOP);
        }

        // bottom line before and after the currently selected tab
        int selectedTabBegin = tabMargin + tabWidth*currentTabIndex;
        int selectedTabEnd = selectedTabBegin + tabWidth - tabMargin;
        int lineY = tabBarHeight - 1;

        g.setColor(Colors.tabOutlineColor);
        g.drawLine(-NetworkIndicator.width, lineY, selectedTabBegin, lineY);
        g.drawLine(selectedTabEnd, lineY, getWidth(), lineY);

        if (NetworkIndicator.isOnTopLeft) {
            g.translate(-NetworkIndicator.width, 0);
        }
    }

    public void recalc() {
        tabMargin = Fonts.height/7;
        tabBarWidth = getWidth() - NetworkIndicator.width;
        tabBarHeight = Platform.isTouch ? Fonts.height*7/4 : Fonts.height*3/2;

        if (tabs.size() == 0) return;

        tabWidth = (tabBarWidth - tabMargin)/tabs.size();
        int tabBeginY = tabBarHeight + tabMargin;
        int availableWidth = tabWidth - tabMargin*2;

        truncatedTabNames.setSize(0);

        for (int i = 0; i < tabs.size(); i++) {
            ListView tab = (ListView) tabs.elementAt(i);
            tab.setPosition(0, tabBeginY, getWidth(), getHeight() - tabBeginY);

            String truncatedName = getTruncatedTabName(i, availableWidth);
            truncatedTabNames.addElement(truncatedName);
        }
    }

    private String getTruncatedTabName(int index, int availableWidth) {
        String tabName = (String) tabNames.elementAt(index);
        String result = Util.stringToWidth(tabName, Fonts.plain, availableWidth);

        // If full tab name doesn't fit, try short tab name if present
        if (!result.equals(tabName)) {
            String shortName = (String) shortTabNames.elementAt(index);

            if (shortName != null) {
                result = Util.stringToWidth(shortName, Fonts.plain, availableWidth);
            }
        }

        return result;
    }

    public void addTab(String name, ListView view) {
        addTab(name, null, view);
    }

    public void addTab(String name, String shortName, ListView view) {
        synchronized (tabs) {
            tabNames.addElement(name);
            shortTabNames.addElement(shortName);
            tabs.addElement(view);

            if (currentTabIndex == -1) {
                currentTabIndex = 0;
                currentTabChanged(currentTabIndex);
            }
        }
        needRecalc();
        AppBase.repaint();
    }

    public void removeTab(int index) {
        synchronized (tabs) {
            tabNames.removeElementAt(index);
            shortTabNames.removeElementAt(index);
            tabs.removeElementAt(index);

            if (currentTabIndex >= index) {
                currentTabIndex--;
                currentTabChanged(currentTabIndex);
            }
        }
        AppBase.repaint();
    }

    public ListView getCurrentTab() {
        if (currentTabIndex == -1 || currentTabIndex >= tabs.size()) return null;
        return (ListView) tabs.elementAt(currentTabIndex);
    }

    public void currentTabChanged(int currentTabIndex) {}

    public void keyEvent(int keyCode, int gameAction) {
        if (gameAction == Canvas.LEFT) {
            currentTabIndex--;
            if (currentTabIndex < 0) currentTabIndex = tabs.size() - 1;
            currentTabChanged(currentTabIndex);
        }
        else if (gameAction == Canvas.RIGHT) {
            currentTabIndex++;
            if (currentTabIndex >= tabs.size()) currentTabIndex = 0;
            currentTabChanged(currentTabIndex);
        }
        else {
            // pass the event through to the tab's listview
            ListView tab = getCurrentTab();
            if (tab != null) tab.keyEvent(keyCode, gameAction);
        }
    }

    private boolean tappedOnBar;

    public void pointerPressed(int x, int y) {
        tappedOnBar = (y < tabBarHeight);
        if (tappedOnBar) return;
        ListView tab = getCurrentTab();
        if (tab != null) tab.pointerPressed(x, y);
    }

    public void pointerDragged(int x, int y) {
        if (tappedOnBar) return;
        ListView tab = getCurrentTab();
        if (tab != null) tab.pointerDragged(x, y);
    }
    
    public void pointerReleased(int x, int y) {
        if (tappedOnBar) {
            // handle tab selection
            if (NetworkIndicator.isOnTopLeft) {
                x -= NetworkIndicator.width;
            }

            currentTabIndex = (x - tabMargin)/tabWidth;

            if (currentTabIndex < 0) {
                currentTabIndex = 0;
            }
            else if (currentTabIndex >= tabs.size()) {
                currentTabIndex = tabs.size() - 1;
            }

            currentTabChanged(currentTabIndex);
        }
        else {
            ListView tab = getCurrentTab();
            
            if (tab != null) {
                tab.pointerReleased(x, y);
            }
        }
    }
}