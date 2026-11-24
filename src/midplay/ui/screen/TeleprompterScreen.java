package midplay.ui.screen;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import midplay.MIDPlay;
import midplay.ui.Commands;
import midplay.ui.Navigator;
import midplay.ui.PlayerNavHelper;
import midplay.util.Lang;

public final class TeleprompterScreen extends Canvas implements CommandListener {
    private final Navigator navigator;
    private String text = "";
    private int scrollY = 0;
    private int lineHeight;
    private int bottomMargin;
    private int visibleLines;
    private int charWidth;
    private boolean editing = false;
    private TextBox editor;
    private final Command editCommand;
    private final Command saveCommand;
    private final Command clearCommand;
    private final Font font;

    public TeleprompterScreen(Navigator navigator) {
        this.navigator = navigator;
        setFullScreenMode(true);

        font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        lineHeight = font.getHeight() + 2;
        bottomMargin = lineHeight;
        charWidth = font.charWidth('W');
        visibleLines = getHeight() / lineHeight;

        editCommand = new Command(Lang.tr("teleprompter.edit"), Command.SCREEN, 1);
        saveCommand = new Command(Lang.tr("teleprompter.save"), Command.SCREEN, 1);
        clearCommand = new Command(Lang.tr("teleprompter.clear"), Command.SCREEN, 2);

        addCommand(editCommand);
        addCommand(Commands.playerNowPlaying());
        addCommand(Commands.back());
        setCommandListener(this);
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        scrollY = 0;
        repaint();
    }

    public String getText() {
        return text;
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);

        if (text.length() == 0) {
            g.setColor(0x666666);
            Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(small);
            String msg = Lang.tr("teleprompter.empty");
            g.drawString(msg, w / 2, h / 2, Graphics.HCENTER | Graphics.VCENTER);
            return;
        }

        g.setFont(font);
        g.setColor(0xFFFFFF);

        visibleLines = Math.max(1, (h - 20) / lineHeight);
        String[] lines = wrapText(text, w - 20);
        int totalHeight = lines.length * lineHeight;

        if (scrollY < 0) scrollY = 0;
        if (scrollY > totalHeight + bottomMargin - h) scrollY = totalHeight + bottomMargin - h;
        if (scrollY < 0) scrollY = 0;

        // Short texts are centered vertically instead of being pinned to the top.
        int top = 10;
        if (totalHeight < h - 20) {
            top = 10 + (h - 20 - totalHeight) / 2;
        }

        int startLine = scrollY / lineHeight;
        int endLine = Math.min(lines.length, startLine + visibleLines + 1);

        for (int i = startLine; i < endLine; i++) {
            int y = i * lineHeight - scrollY + top;
            if (y + lineHeight > 10 && y < h) {
                g.drawString(lines[i], w / 2, y, Graphics.HCENTER | Graphics.TOP);
            }
        }

        if (lines.length > visibleLines) {
            drawScrollbar(g, w, h, lines.length);
        }
    }

    private void drawScrollbar(Graphics g, int w, int h, int totalLines) {
        int barH = h - 20;
        int thumbH = Math.max(10, (visibleLines * barH) / totalLines);
        int thumbY = 10 + (scrollY * (barH - thumbH)) / Math.max(1, totalLines * lineHeight - h);
        g.setColor(0x444444);
        g.fillRect(w - 8, 10, 6, barH);
        g.setColor(0xAAAAAA);
        g.fillRect(w - 8, thumbY, 6, thumbH);
    }

    private String[] wrapText(String text, int maxWidth) {
        java.util.Vector v = new java.util.Vector();
        String[] paragraphs = splitParagraphs(text);
        for (int p = 0; p < paragraphs.length; p++) {
            String para = paragraphs[p].trim();
            if (para.length() == 0) {
                v.addElement("");
                continue;
            }
            String[] words = splitWords(para);
            StringBuffer line = new StringBuffer();
            for (int i = 0; i < words.length; i++) {
                String test = line.length() == 0 ? words[i] : line.toString() + " " + words[i];
                if (font.stringWidth(test) <= maxWidth) {
                    line = new StringBuffer(test);
                } else {
                    if (line.length() > 0) {
                        v.addElement(line.toString());
                    }
                    line = new StringBuffer(words[i]);
                }
            }
            if (line.length() > 0) {
                v.addElement(line.toString());
            }
        }
        String[] result = new String[v.size()];
        v.copyInto(result);
        return result;
    }

    private String[] splitParagraphs(String text) {
        java.util.Vector v = new java.util.Vector();
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            v.addElement(text.substring(start, end));
            start = end + 1;
        }
        String[] result = new String[v.size()];
        v.copyInto(result);
        return result;
    }

    private String[] splitWords(String text) {
        java.util.Vector v = new java.util.Vector();
        int start = 0;
        while (start < text.length()) {
            while (start < text.length() && text.charAt(start) == ' ') start++;
            if (start >= text.length()) break;
            int end = start;
            while (end < text.length() && text.charAt(end) != ' ') end++;
            v.addElement(text.substring(start, end));
            start = end;
        }
        String[] result = new String[v.size()];
        v.copyInto(result);
        return result;
    }

    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        if (action == Canvas.UP) {
            scrollY -= lineHeight;
            if (scrollY < 0) scrollY = 0;
            repaint();
        } else if (action == Canvas.DOWN) {
            scrollY += lineHeight;
            repaint();
        } else if (action == Canvas.LEFT) {
            scrollY = 0;
            repaint();
        } else if (action == Canvas.RIGHT) {
            String[] lines = wrapText(text, getWidth() - 20);
            int totalHeight = lines.length * lineHeight;
            scrollY = Math.max(0, totalHeight - getHeight());
            repaint();
        } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE) {
            openEditor();
        }
    }

    protected void keyRepeated(int keyCode) {
        keyPressed(keyCode);
    }

    private void openEditor() {
        editor = new TextBox(Lang.tr("teleprompter.edit_title"), text, 10000, TextField.ANY);
        editor.addCommand(clearCommand);
        editor.addCommand(saveCommand);
        editor.addCommand(Commands.back());
        editor.setCommandListener(this);
        Display.getDisplay(MIDPlay.getInstance()).setCurrent(editor);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == Commands.back()) {
            if (displayable == editor) {
                Display.getDisplay(MIDPlay.getInstance()).setCurrent(this);
            } else {
                navigator.back();
            }
        } else if (command == Commands.playerNowPlaying()) {
            PlayerNavHelper.showNowPlaying(navigator);
        } else if (command == editCommand) {
            openEditor();
        } else if (command == saveCommand) {
            text = editor.getString();
            editor = null;
            Display.getDisplay(MIDPlay.getInstance()).setCurrent(this);
            repaint();
        } else if (command == clearCommand) {
            editor.setString("");
        }
    }
}