//#ifndef NO_FILECONNECTION
package fi.gtrxac.bluewap.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.io.file.*;
import javax.microedition.io.*;
import java.util.*;
import java.io.*;

public abstract class FilePickerScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;
    public static final int CMD_CLOSE = 2;
    
    private String currentPath;

    public FilePickerScreen(String title) {
        this(title, "file:///");
    }

    protected FilePickerScreen(String title, String currentPath) {
        super();
        setTitle(title);

        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(new Command("Close", Command.BACK, CMD_CLOSE));
        setCommandListener(this);

        this.currentPath = currentPath;

        addItem(new BlankItem(0));
        listFiles();
    }

    /**
     * Determine if a file of a given name should be shown in the file list.
     * To be overridden by subclass (optional)
     */
    protected boolean fileFilter(String fileName) {
        // show all files by default
        return true;
    }

    /**
     * To be overridden by subclass (recommended)
     */
    protected void handleException(Exception e) {
        e.printStackTrace();
    }

    /**
     * Should be implemented like:
     * App.pushScreen(new YourSubclass(selectedPath));
     */
    protected abstract void directorySelected(String selectedPath);

    protected abstract void fileSelected(FileConnection fc, String selected);

    protected void close() {
        while (AppBase.getCurrentScreen() instanceof FilePickerScreen) {
            AppBase.popScreen();
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getPriority() == CMD_BACK) {
            if (!currentPath.equals("file:///")) {
                int lastSlashIndex = currentPath.lastIndexOf('/', currentPath.length() - 2);

                if (lastSlashIndex != -1) {
                    AppBase.popScreen();
                }
            }
            else {
                close();
            }
        }
        else if (c.getPriority() == CMD_CLOSE) {
            close();
        }
    }

    protected void itemSelected(Item i) {
        ListItem highlighted = (ListItem) getHighlightedItem();
        if (highlighted == null) return;

        String selected = highlighted.text;
        String selectedPath = currentPath + selected;

        try {
            if (selected.endsWith("/")) {
                directorySelected(selectedPath);
            }
            else {
                FileConnection fc = (FileConnection) Connector.open(selectedPath, Connector.READ);
                fileSelected(fc, selected);
            }
        }
        catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Get the alphabetically first string from the vector and remove it from the vector.
     */
    private static String getFirstString(Vector v) {
        String result = null;
        for (int i = 0; i < v.size(); i++) {
            String cur = (String) v.elementAt(i);
            if (result == null || cur.compareTo(result) < 0) {
                result = cur;
            }
        }
        v.removeElement(result);
        return result;
    }

    private void listFiles() {
        try {
            if (currentPath.equals("file:///")) {
                Enumeration roots = FileSystemRegistry.listRoots();

                while (roots.hasMoreElements()) {
                    String root = (String) roots.nextElement();
                    addItem(new ListItem(root));
                }
            } else {
                FileConnection fc = (FileConnection) Connector.open(currentPath, Connector.READ);
                Enumeration list = fc.list();

                // Add items in alphabetical order, directories first
                Vector dirs = new Vector();
                Vector files = new Vector();

                while (list.hasMoreElements()) {
                    String fileName = (String) list.nextElement();

                    if (fileName.endsWith("/")) {
                        dirs.addElement(fileName);
                    }
                    else if (fileFilter(fileName)) {
                        files.addElement(fileName);
                    }
                }

                while (!dirs.isEmpty()) {
                    addItem(new ListItem(getFirstString(dirs)));
                }

                while (!files.isEmpty()) {
                    addItem(new ListItem(getFirstString(files)));
                }

                fc.close();
            }
        }
        catch (IOException e) {
            handleException(e);
        }
    }
}
//#endif