package fi.gtrxac.bluewap.ui;

import java.util.*;
import javax.microedition.lcdui.*;
import fi.gtrxac.bluewap.*;

/**
 * The canvas that shows and handles all screens managed by the UI framework.
 */
public class AppCanvas extends Canvas {
    public static final AppCanvas instance = new AppCanvas();
    private final Vector canvasCommands = new Vector();

    private String leftSoftkeyLabel;
    private String rightSoftkeyLabel;
    private Command leftSideCommand;
    private Command rightSideCommand;
    private boolean softkeyAreaPressed;
    private boolean leftSoftkeyPressed;
    private boolean rightSoftkeyPressed;
    private boolean optionsCommandShown;

    private boolean use12hTime;
    private String currentClock;
    private String currentTitle;

    private AppCanvas() {
        super();
        setFullScreenMode(true);
        
        new ClockThread().start();
        updateClock();

        KeyRepeatThread.instance.start();
    }

    protected void paint(Graphics g) {
        Screen curr = AppBase.getCurrentScreen();
        
        if (titleBarVisible()) {
            drawTitleBar(g);
            g.translate(0, getTitleBarHeight());
        }

        if (curr != null) {
            g.setClip(0, 0, getWidth(), getHeight());
            curr.prepareAndDraw(g);
        }

        int softkeyBarY = getHeight();

        if (titleBarVisible()) {
            softkeyBarY += getTitleBarHeight();
        }

        g.translate(-g.getTranslateX(), -g.getTranslateY() + softkeyBarY);
        g.setClip(0, 0, getWidth(), getSoftkeyBarHeight());
        drawSoftkeys(g);

        // debug:
        // drawMemoryUsage(g);
    }

    private static boolean titleBarVisible() {
        Screen curr = AppBase.getCurrentScreen();

        return curr != null && curr.getTitle() != null;
    }

    private void drawTitleBar(Graphics g) {
        int barHeight = getTitleBarHeight();

        g.setColor(Colors.titleBarBackground);
        g.fillRect(0, 0, getWidth(), barHeight);

        g.setColor(Colors.titleBarOutline);
        g.drawLine(0, barHeight - 1, getWidth(), barHeight - 1);

        int titleX = Fonts.boldHeight/4;
        int clockX = getWidth() - Fonts.boldHeight/4;

        if (NetworkIndicator.isOnTopLeft) {
            titleX += NetworkIndicator.width;
        }
        else if (NetworkIndicator.isOnTopRight) {
            clockX -= NetworkIndicator.width;
        }

        g.setColor(Colors.titleBarText);
        g.setFont(Fonts.bold);
        g.drawString(
            currentTitle,
            titleX,
            (barHeight - Fonts.boldHeight)/2,
            0);

        g.setFont(Fonts.plain);
        g.drawString(
            currentClock,
            clockX,
            (barHeight - Fonts.height)/2,
            Graphics.TOP | Graphics.RIGHT);
    }

    private void drawSoftkeys(Graphics g) {
        int barHeight = getSoftkeyBarHeight();

        if (Platform.isTouch) {
            drawSoftkeysTouch(g, barHeight);
        }
        else {
            drawSoftkeysNonTouch(g, barHeight);
        }
    }

    private void drawSoftkeysNonTouch(Graphics g, int barHeight) {
        g.setColor(Colors.softkeyBarBackgroundNonTouch);
        g.fillRect(0, 0, getWidth(), barHeight);

        g.setColor(Colors.softkeyBarOutline);
        g.drawLine(0, 0, getWidth(), 0);

        g.setColor(Colors.softkeyText);
        g.setFont(Fonts.bold);

        int margin = Fonts.boldHeight/4;

        if (leftSoftkeyLabel != null) {
            g.drawString(
                leftSoftkeyLabel,
                margin,
                (barHeight - Fonts.boldHeight)/2,
                Graphics.TOP | Graphics.LEFT);
        }

        if (rightSoftkeyLabel != null) {
            g.drawString(
                rightSoftkeyLabel,
                getWidth() - margin,
                (barHeight - Fonts.boldHeight)/2,
                Graphics.TOP | Graphics.RIGHT);
        }
    }

    private void drawSoftkeysTouch(Graphics g, int barHeight) {
        g.setColor(Colors.softkeyBarBackgroundTouch);
        g.fillRect(0, 0, getWidth(), barHeight);

        int margin = Math.max(3, Fonts.boldHeight/7);
        int softkeyWidth = (getWidth() - margin*3)/2;
        int softkeyHeight = barHeight - margin*2;
        int textY = barHeight/2 - Fonts.boldHeight/2;
        int leftSoftkeyX = margin;
        int rightSoftkeyX = getWidth() - margin - softkeyWidth;

        g.setFont(Fonts.bold);
        
        drawSoftkeyTouch(
            g, leftSoftkeyLabel, leftSoftkeyX, margin,
            softkeyWidth, softkeyHeight, textY, leftSoftkeyPressed);

        drawSoftkeyTouch(
            g, rightSoftkeyLabel, rightSoftkeyX, margin,
            softkeyWidth, softkeyHeight, textY, rightSoftkeyPressed);
    }

    private void drawSoftkeyTouch(Graphics g, String label, int x, int y, int width, int height, int textY, boolean pressed) {
        if (label == null) return;

        g.setColor(pressed ? Colors.softkeyBackgroundSel : Colors.softkeyBackground);
        g.fillRect(x, y, width, height);

        // darkened border colors equal to Util.blend(fillRectColor, 0x000000, 7)
        g.setColor(pressed ? Colors.softkeyOutlineSel : Colors.softkeyOutline);
        g.drawRect(x, y, width, height);

        g.setColor(pressed ? Colors.softkeyTextSel : Colors.softkeyText);
        g.drawString(label, x + width/2, textY, Graphics.TOP | Graphics.HCENTER);
    }

    private void drawMemoryUsage(Graphics g) {
        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory()/1000;
        long usedMem = (rt.totalMemory() - rt.freeMemory())/1000;

        String memStr = usedMem + "/" + totalMem + "K";
        int memStrWidth = Fonts.plain.stringWidth(memStr);

        g.translate(-g.getTranslateX(), -g.getTranslateY());
        g.setClip(0, 0, memStrWidth, Fonts.height);
        
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, memStrWidth, Fonts.height);

        g.setColor(0xFF0000);
        g.setFont(Fonts.plain);
        g.drawString(memStr, 0, 0, 0);

        repaint();
    }

    private static int getTitleBarHeight() {
        return Fonts.boldHeight + Fonts.boldHeight/5*2;
    }

    private static int getSoftkeyBarHeight() {
        return Platform.isTouch ? Fonts.boldHeight*2 : getTitleBarHeight();
    }

    public static int getBaseY() {
        return titleBarVisible() ? getTitleBarHeight() : 0;
    }

    public int getHeight() {
        int result = super.getHeight() - getSoftkeyBarHeight();

        if (titleBarVisible()) {
            result -= getTitleBarHeight();
        }
        return result;
    }

    protected void sizeChanged(int w, int h) {
        Fonts.reloadFonts();
        AppBase.recalcAllScreens();
        updateTitle();
    }

    private static volatile boolean isKeyPressed = false;
    public static volatile long beginRepeatTime;

    protected void keyPressed(int keyCode) {
        if (Softkeys.isLeftSoftkey(keyCode)) {
            leftSoftkeyPressed();
            return;
        }

        if (Softkeys.isRightSoftkey(keyCode)) {
            rightSoftkeyPressed();
            return;
        }

        keyEvent(keyCode);

        if (!isKeyPressed) {
            isKeyPressed = true;
            beginRepeatTime = System.currentTimeMillis() + 500;

            synchronized (KeyRepeatThread.instance) {
                KeyRepeatThread.activeKey = keyCode;
                KeyRepeatThread.instance.notify();
            }
        }
    }

    public void keyEvent(int keyCode) {
        Screen curr = AppBase.getCurrentScreen();
        if (curr == null) return;

        curr.keyEvent(keyCode, getGameAction(keyCode));
        AppBase.repaint();
    }

    protected void keyReleased(int key) {
        if (isKeyPressed) {
            isKeyPressed = false;
            KeyRepeatThread.activeKey = 0;
        }
    }

    protected void pointerPressed(int x, int y) {
        y -= getBaseY();
        if (handleSoftkeyPress(x, y, false)) return;

        Screen curr = AppBase.getCurrentScreen();
        if (curr != null) {
            curr.pointerPressed(x, y);
            AppBase.repaint();
        }
    }

    protected void pointerDragged(int x, int y) {
        if (softkeyAreaPressed) return;

        Screen curr = AppBase.getCurrentScreen();
        if (curr != null) {
            curr.pointerDragged(x, y - getBaseY());
            AppBase.repaint();
        }
    }

    protected void pointerReleased(int x, int y) {
        y -= getBaseY();
        if (softkeyAreaPressed && handleSoftkeyPress(x, y, true)) return;

        Screen curr = AppBase.getCurrentScreen();
        if (curr != null) {
            curr.pointerReleased(x, y);
            AppBase.repaint();
        }
    }

    protected boolean handleSoftkeyPress(int x, int y, boolean runCommand) {
        leftSoftkeyPressed = false;
        rightSoftkeyPressed = false;
        softkeyAreaPressed = (y >= getHeight());

        if (!softkeyAreaPressed) {
            AppBase.repaint();
            return false;
        }

        boolean pressedLeft = (x < getWidth()/2);

        if (runCommand) {
            if (pressedLeft) {
                leftSoftkeyPressed();
            }
            else {
                rightSoftkeyPressed();
            }
        }
        else {
            if (pressedLeft) {
                leftSoftkeyPressed = true;
            }
            else {
                rightSoftkeyPressed = true;
            }
        }

        AppBase.repaint();
        return true;
    }

    private void leftSoftkeyPressed() {
        if (optionsCommandShown) {
            AppBase.pushScreen(new OptionsScreen(remainingCommands));
        }
        else if (leftSideCommand != null) {
            runCommand(leftSideCommand);
        }
    }

    private void rightSoftkeyPressed() {
        if (rightSideCommand != null) {
            runCommand(rightSideCommand);
        }
    }

    private void runCommand(Command c) {
        if (c == null) return;

        CommandListener lis = AppBase.getCurrentScreen().getCommandListener();
        if (lis == null) return;

        lis.commandAction(c, this);
    }

    void updateCommands() {
        Screen curr = AppBase.getCurrentScreen();

        // Remove canvas commands that do not belong to current screen
        for (int i = 0; i < canvasCommands.size(); ) {
            Command c = (Command) canvasCommands.elementAt(i);
            if (curr == null || curr.getCommands().indexOf(c) == -1) {
                canvasCommands.removeElementAt(i);
            }
            else i++;
        }

        if (curr == null) {
            setCommandListener(null);
            return;
        }

        if (curr.getCommandListener() != null) {
            setCommandListener(curr.getCommandListener());
        } else {
            setCommandListener(curr);
        }

        // Add current screen's commands that are not in canvas
        for (int i = 0; i < curr.getCommands().size(); i++) {
            Command c = (Command) curr.getCommands().elementAt(i);
            if (canvasCommands.indexOf(c) == -1) {
                canvasCommands.addElement(c);
            }
        }

        assignCommandPositions();
    }

    private final Vector remainingCommands = new Vector();

    private void assignCommandPositions() {
        // reset
        leftSideCommand = null;
        rightSideCommand = null;
        leftSoftkeyLabel = null;
        rightSoftkeyLabel = null;
        optionsCommandShown = false;

        if (canvasCommands.size() == 0) return;

        remainingCommands.setSize(0);

        for (int i = 0; i < canvasCommands.size(); i++) {
            remainingCommands.addElement(canvasCommands.elementAt(i));
        }

        // right softkey goes to the command with lowest priority number (and with command type BACK or EXIT if there are any)
        boolean seenBackCommand = false;

        for (int i = 0; i < remainingCommands.size(); i++) {
            Command c = (Command) remainingCommands.elementAt(i);

            boolean isBackCommand =
                (c.getCommandType() == Command.BACK || c.getCommandType() == Command.EXIT);

            if (seenBackCommand && !isBackCommand) {
                continue;
            }
            if (rightSideCommand == null || c.getPriority() < rightSideCommand.getPriority() || (!seenBackCommand && isBackCommand)) {
                rightSideCommand = c;
                if (isBackCommand) seenBackCommand = true;
            }
        }

        if (rightSideCommand != null) {
            remainingCommands.removeElement(rightSideCommand);
            rightSoftkeyLabel = rightSideCommand.getLabel();
        }

        // if that was the only command, then we're done
        if (remainingCommands.size() == 0) return;

        // if there is one command remaining, then it goes to left softkey
        if (remainingCommands.size() == 1) {
            leftSideCommand = (Command) remainingCommands.elementAt(0);
            leftSoftkeyLabel = leftSideCommand.getLabel();
            return;
        }

        // if there's more commands, show them in an options menu
        optionsCommandShown = true;
        leftSoftkeyLabel = "Options";
    }

    private static final String midletName = AppBase.instance.getAppProperty("MIDlet-Name");

    public void updateTitle() {
        Screen curr = AppBase.getCurrentScreen();
        if (curr == null) return;

        String title = curr.getTitle();

        // Set title shown in custom title bar
        if (title == null) {
            title = midletName;
        }

        int availableWidth = getWidth()
            - Fonts.boldHeight/4*2
            - Fonts.plain.stringWidth("88:88")
            - NetworkIndicator.width;

        currentTitle = Util.stringToWidth(title, Fonts.bold, availableWidth);
    }

    public void updateClock() {
        long now = new Date().getTime();
        currentClock = Util.formatTimestamp(now, use12hTime);

        if (instance != null) {
            AppBase.repaint();
        }
    }

    public static void setTimeFormat(boolean use12hTime) {
        instance.use12hTime = use12hTime;
        instance.updateClock();
    }
}