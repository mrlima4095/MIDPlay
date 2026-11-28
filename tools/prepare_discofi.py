#!/usr/bin/env python3
"""Prepare the vendored Discofi sources for embedding in MIDPlay.

The output is the standard Discofi build with only Bluetooth removed: the UI
and HTTP tethering code are preprocessed away, but FileConnection features
(such as importing a token from a file) stay enabled. Packages, resources and
RMS stores are isolated from the legacy Discord J2ME client, and a small host
API is added so MIDPlay can open/close Discofi, save notes, open the player and
import the legacy Discord settings on first launch.

The shared cc.nnproject.json library in src/ is used instead of Discofi's own
copy, so the dependency is packaged only once.
"""
import re
import shutil
import sys
from pathlib import Path

# Standard build minus Bluetooth. FileConnection is intentionally left enabled.
DEFINES = {"NO_BLUETOOTH", "NO_HTTP_BLUETOOTH_SUPPORT"}

# Longest prefixes first so nested packages are not renamed twice.
RENAMES = [
    ("tech.alicesworld.ModernConnector", "discofi.modern"),
    ("fi.gtrxac.bluewap", "discofi.bluewap"),
    ("fi.gtrxac.tinytls", "discofi.tinytls"),
    ("fi.gtrxac.discord", "discofi"),
    ("tube42.lib.imagelib", "discofi.imagelib"),
    ("com.gtrxac.discord", "discofi.http"),
    ("util.Base64", "discofi.utilbase.Base64"),
]


def preprocess(lines):
    result, stack, active = [], [], True
    for line in lines:
        stripped = line.strip()
        match = re.match(r"^//#(ifdef|ifndef)\s+(\S+)\s*$", stripped)
        if match:
            condition = (match.group(2) in DEFINES) == (match.group(1) == "ifdef")
            stack.append(active)
            active = active and condition
            result.append("")
            continue
        if stripped.startswith("//#else"):
            if stack:
                active = stack[-1] and not active
            result.append("")
            continue
        if stripped.startswith("//#endif"):
            if stack:
                active = stack.pop()
            result.append("")
            continue
        result.append(line if active else "")
    return result


def rename(text):
    for old, new in RENAMES:
        text = text.replace(old, new)
    text = text.replace("package util;", "package discofi.utilbase;")
    text = re.sub(r"^import util\.", "import discofi.utilbase.", text, flags=re.MULTILINE)
    for name in ("i.png", "l.png", "o.png"):
        text = text.replace('"/%s"' % name, '"/assets/discofi/%s"' % name)
    text = text.replace('RecordIO.save("a", instance)', 'RecordIO.save("discofi.a", instance)')
    text = text.replace('RecordIO.load("a", instance)', 'RecordIO.load("discofi.a", instance)')
    text = text.replace('RecordIO.load("b", this)', 'RecordIO.load("discofi.b", this)')
    text = text.replace('RecordIO.save("b", this)', 'RecordIO.save("discofi.b", this)')
    # This is the standard build with Bluetooth disabled, not the Lite one.
    text = text.replace(
        '"Bluetooth tethering is unavailable in Discofi Lite. To use tethering, install the standard version of Discofi."',
        '"Bluetooth tethering is disabled in this MIDPlay build."',
    )
    # MIDlet.getAppProperty() is final; route host lookups through AppBase.
    text = re.sub(
        r"(?:App|AppBase)\.instance\.getAppProperty\(",
        "AppBase.appProperty(",
        text,
    )
    # Quit in an embedded client must return to the host, not destroy a MIDlet.
    text = text.replace("App.instance.notifyDestroyed()", "App.exit()")
    return text


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit("Discofi patch failed (%s): pattern not found" % label)
    return text.replace(old, new, 1)


def add_before_class_end(text, block, label):
    marker = text.rfind("\n}")
    if marker == -1:
        raise SystemExit("Discofi patch failed (%s): class end not found" % label)
    return text[:marker] + "\n" + block + text[marker:]


def patch_app_base(text):
    # A MIDlet constructor and lifecycle can only be owned by the AMS. Discofi
    # is embedded, so it must be an ordinary class driven by the host MIDlet.
    text = replace_once(
        text,
        "public abstract class AppBase extends MIDlet {",
        "public abstract class AppBase {",
        "AppBase class declaration",
    )
    text = replace_once(
        text,
        "    public static AppBase instance;",
        """    public static AppBase instance;

    /** MIDlet that owns this embedded client (MIDPlay). */
    private static MIDlet host;

    /** True while Discofi owns the display; suppresses background repaints. */
    public static volatile boolean active;

    public static void setHost(MIDlet app) {
        host = app;
    }

    /** Reads a JAD/manifest property from the host MIDlet when embedded. */
    public static String appProperty(String key) {
        return host == null ? null : host.getAppProperty(key);
    }

    /** Opens an external URL through the host MIDlet. */
    public boolean platformRequest(String url)
            throws javax.microedition.io.ConnectionNotFoundException {
        return host != null && host.platformRequest(url);
    }

    public static void resetScreens() {
        screens.setSize(0);
    }""",
        "AppBase host",
    )
    text = replace_once(
        text,
        "        disp = Display.getDisplay(this);",
        "        disp = Display.getDisplay(host);",
        "AppBase display",
    )
    text = replace_once(
        text,
        """    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {}
""",
        "",
        "AppBase lifecycle removal",
    )
    text = replace_once(
        text,
        "    public void startApp() {",
        "    public void startEmbedded() {",
        "AppBase embedded start",
    )
    text = replace_once(
        text,
        "    public void startEmbedded() {",
        """    public void startEmbedded() {
        startEmbedded(true);
    }

    public void startEmbedded(boolean show) {""",
        "AppBase silent start overload",
    )
    text = replace_once(
        text,
        """        disp.setCurrent(AppCanvas.instance);
    }
""",
        """        if (show) disp.setCurrent(AppCanvas.instance);
    }
""",
        "AppBase optional display",
    )
    text = replace_once(
        text,
        """    public static void repaint() {
        AppCanvas.instance.repaint();
    }""",
        """    public static void repaint() {
        if (active) AppCanvas.instance.repaint();
    }""",
        "AppBase repaint guard",
    )
    return text


def patch_app(text):
    text = replace_once(
        text,
        "import discofi.bluewap.ui.*;",
        "import discofi.bluewap.ui.*;\nimport discofi.bluewap.Util;",
        "App Util import",
    )
    text = add_before_class_end(
        text,
        """    // _____________________________________________________________________
    //
    //  MIDPlay host integration
    // _____________________________________________________________________
    //
    private static MIDlet host;
    private static Runnable exitHandler;
    private static boolean initialized;
    public static HostBridge hostBridge;

    /** Registers host hooks (notes, player, translated command labels). */
    public static void setHostBridge(HostBridge bridge) {
        hostBridge = bridge;
    }

    /** Applies a token chosen from a MIDPlay note to the pending account. */
    public static void importTokenFromText(String text) {
        String token = text == null ? "" : text.trim();
        if (token.length() == 0) {
            App.pushScreen(new Dialog(midplay.util.Lang.tr("discofi.import_token_empty")));
            return;
        }
        if (pendingAccount == null) pendingAccount = new Account();
        pendingAccount.token = Util.stringToLength(token, 200);
        App.replaceScreen(new LoginScreen());
    }

    /** Opens Discofi inside the host MIDlet. */
    public static void open(MIDlet app, Runnable onExit) {
        host = app;
        exitHandler = onExit;
        AppBase.active = true;
        AppBase.setHost(app);
        Settings.importLegacyIfNeeded();
        Settings.load();
        if (!initialized) {
            initialized = true;
            new App().startEmbedded();
        } else {
            AppBase.recalcAllScreens();
        }
        Display.getDisplay(app).setCurrent(AppCanvas.instance);
        AppBase.repaint();
    }

    /**
     * Loads Discofi during host startup without taking over the display. The
     * host's automatic Discord preparation setting uses this for both clients.
     */
    public static void warmUp(MIDlet app) {
        host = app;
        AppBase.setHost(app);
        Settings.importLegacyIfNeeded();
        Settings.load();
        if (!initialized) {
            initialized = true;
            new App().startEmbedded(false);
        } else {
            AppBase.recalcAllScreens();
        }
    }

    /** True once Discofi has been initialized in this process. */
    public static boolean isRunning() {
        return initialized;
    }

    /**
     * Temporarily gives the display to the host (for example MIDPlay's player)
     * while keeping the internal screen stack and the host exit handler intact.
     */
    public static Displayable suspend() {
        if (!AppBase.active) return null;
        AppBase.active = false;
        return Display.getDisplay(host).getCurrent();
    }

    /** Takes the display back after a temporary host screen. */
    public static void resume() {
        if (!initialized || host == null) return;
        AppBase.active = true;
        AppBase.recalcAllScreens();
        Display.getDisplay(host).setCurrent(AppCanvas.instance);
        AppBase.repaint();
    }

    /** Leaves Discofi and restores the host screen. */
    public static void exit() {
        if (!AppBase.active) return;
        AppBase.active = false;
        Runnable handler = exitHandler;
        exitHandler = null;
        if (handler != null) handler.run();
    }

    /** Stops Discofi and releases its in-memory state (hidden htop screen). */
    public static void kill() {
        if (!initialized) return;
        try { Settings.save(); } catch (Throwable ignored) {}
        try { IconManager.iconCache.save(); } catch (Throwable ignored) {}
        AppBase.active = false;
        initialized = false;
        host = null;
        exitHandler = null;
        AppBase.resetScreens();
    }
""",
        "App host API",
    )
    return text


def patch_settings(text):
    text = replace_once(
        text,
        "import discofi.bluewap.ui.*;",
        "import discofi.bluewap.ui.*;\nimport discofi.bluewap.Util;\nimport cc.nnproject.json.*;",
        "Settings imports",
    )
    text = replace_once(
        text,
        "public class Settings implements RecordIOImpl {",
        """public class Settings implements RecordIOImpl {
    /** MIDPlay-hosted proxy; unlike the old public proxy it serves the inbox route. */
    public static final String MIDPLAY_PROXY_URL = "http://72.60.138.179:8091";
    private static final String LEGACY_PROXY_URL = "http://146.59.80.3";""",
        "Settings proxy constants",
    )
    text = replace_once(
        text,
        """    public static void load() {
        RecordIO.load("discofi.a", instance);
        refresh();
    }""",
        """    public static void load() {
        RecordIO.load("discofi.a", instance);
        upgradeLegacyProxy();
        refresh();
    }

    /** Moves accounts imported from the legacy app to the MIDPlay-hosted proxy. */
    private static void upgradeLegacyProxy() {
        if (Accounts.list == null) return;
        boolean changed = false;
        for (int i = 0; i < Accounts.list.size(); i++) {
            Account acc = (Account) Accounts.list.elementAt(i);
            if (acc != null && LEGACY_PROXY_URL.equals(acc.apiUrl)) {
                acc.apiUrl = MIDPLAY_PROXY_URL;
                changed = true;
            }
        }
        if (changed) {
            try { RecordIO.save("discofi.a", instance); } catch (Throwable ignored) {}
        }
    }""",
        "Settings legacy proxy upgrade",
    )
    text = add_before_class_end(
        text,
        """    /**
     * First-launch migration from the legacy Discord J2ME client. The old
     * settings live in the positional JSON array in RMS "discord.settings";
     * Discofi uses its own binary store ("discofi.a"). Only the essentials
     * and the settings visible in Discofi are carried over.
     */
    public static boolean importLegacyIfNeeded() {
        try {
            String[] stores = RecordStore.listRecordStores();
            if (stores != null) {
                for (int i = 0; i < stores.length; i++) {
                    if ("discofi.a".equals(stores[i])) return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }

        RecordStore rms = null;
        try {
            rms = RecordStore.openRecordStore("discord.settings", false);
            if (rms.getNumRecords() < 1) return false;
            JSONArray data = JSON.getArray(Util.bytesToString(rms.getRecord(1)));

            String token = data.getString(1, "");
            if (token == null || token.trim().length() == 0) return false;

            String api = data.getString(0, "");
            String cdn = data.getString(11, "");
            boolean proxyless = data.getInt(49, 0) != 0;
            int theme = data.getInt(2, 0);
            boolean use12h = data.getInt(6, 0) != 0;
            int messageCount = data.getInt(7, 20);
            boolean typing = data.getInt(47, 1) != 0;

            Accounts.list = new Vector();
            Account acc = new Account();
            acc.token = token.trim();
            // Use the MIDPlay-hosted proxy: the old public proxy lacks the
            // mentions route used by the Discofi inbox.
            if (api != null && api.length() > 0) acc.apiUrl = MIDPLAY_PROXY_URL;
            if (cdn != null && cdn.length() > 0) acc.cdnUrl = cdn;
            acc.connectionMode = proxyless
                ? Account.CONNECTION_MODE_DIRECT
                : Account.CONNECTION_MODE_PROXY;
            Accounts.list.addElement(acc);
            Accounts.setActive(0);

            lightTheme = theme == 1;
            use12hTime = use12h;
            if (messageCount > 0) messageLoadCount = Math.min(messageCount, 255);
            sendTypingIndicator = typing;
            save();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (rms != null) {
                try { rms.closeRecordStore(); } catch (Throwable ignored) {}
            }
        }
    }
""",
        "Settings legacy import",
    )
    return text


def patch_chat_screen(text):
    text = replace_once(
        text,
        "import javax.microedition.lcdui.TextBox;",
        "import javax.microedition.lcdui.TextBox;\nimport midplay.util.Lang;",
        "ChatScreen Lang import",
    )
    text = replace_once(
        text,
        "    public static final int CMD_DELETE = 5;",
        "    public static final int CMD_DELETE = 5;\n    public static final int CMD_SAVE_NOTE = 6;",
        "ChatScreen command id",
    )
    text = replace_once(
        text,
        "    public Message editDraftTarget;",
        "    public Message editDraftTarget;\n\n    private final Command saveNoteCommand;",
        "ChatScreen command field",
    )
    text = replace_once(
        text,
        """        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));""",
        """        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        saveNoteCommand = new Command(
            App.hostBridge == null ? "Save note" : App.hostBridge.saveNoteLabel(),
            Command.ITEM, CMD_SAVE_NOTE);""",
        "ChatScreen command label",
    )
    text = replace_once(
        text,
        "        removeCommand(refreshCommand);",
        "        removeCommand(refreshCommand);\n        removeCommand(saveNoteCommand);",
        "ChatScreen command remove",
    )
    text = replace_once(
        text,
        """        if (ownMessageHighlighted) {
            addCommand(editCommand);
            addCommand(deleteCommand);
        }
        else {
            removeCommand(editCommand);
            removeCommand(deleteCommand);
        }""",
        """        if (ownMessageHighlighted) {
            addCommand(editCommand);
            addCommand(deleteCommand);
        } else {
            removeCommand(editCommand);
            removeCommand(deleteCommand);
        }

        if (messageHighlighted && App.hostBridge != null) {
            addCommand(saveNoteCommand);
        } else {
            removeCommand(saveNoteCommand);
        }""",
        "ChatScreen command visibility",
    )
    text = replace_once(
        text,
        "            case CMD_REFRESH: {",
        """            case CMD_SAVE_NOTE: {
                if (App.hostBridge == null) break;
                Item selected = getHighlightedItem();
                if (selected instanceof MessageItem) {
                    boolean saved = App.hostBridge.saveNote(
                        ((MessageItem) selected).message.content);
                    App.pushScreen(new Dialog(Lang.tr(
                        saved ? "discofi.save_note_done" : "discofi.save_note_error")));
                }
                break;
            }
            case CMD_REFRESH: {""",
        "ChatScreen command action",
    )
    return text


def patch_main_screen(text):
    # AppCanvas already exposes the host player command on every Discofi screen.
    # Adding another one here produced two identical "Player" buttons in the menu.
    text = replace_once(
        text,
        """            case CMD_QUIT: {
                promptQuit();
                break;
            }""",
        """            case CMD_QUIT: {
                App.exit();
                break;
            }""",
        "MainScreen direct quit",
    )
    marker = text.find("\n    private void promptQuit() {")
    if marker == -1:
        raise SystemExit("Discofi patch failed (MainScreen quit dialog): pattern not found")
    end = text.find("\n    }\n", marker)
    if end == -1:
        raise SystemExit("Discofi patch failed (MainScreen quit dialog): method end not found")
    text = text[:marker] + text[end + len("\n    }\n") :]
    return text


def patch_account(text):
    # The legacy proxy does not implement the mentions inbox route. Try the
    # configured API first, then fall back to Discord's HTTPS endpoint.
    old = """    public Vector getInbox(boolean refresh) throws DiscordException {
        if (!refresh && inboxAvailable()) return inbox;

        JSONArray inboxJson = (JSONArray) fetchJSON(
            "/users/@me/mentions?limit=25&roles=true&everyone=true");

        for (int i = 0; i < inboxJson.size(); i++) {
            JSONObject mentionJson = inboxJson.getObject(i);
            inbox.addElement(new Message(mentionJson));
        }

        return inbox;
    }
"""
    new = """    /** Fetches the inbox directly when the configured proxy lacks the route. */
    private AbstractJSON fetchInboxDirect(String route) throws DiscordException {
        try {
            HTTP h = HTTP.createRequest("GET", "https://discord.com/api/v9" + route);
            h.setHeader("Authorization", token);
            h.setHeader("X-Super-Properties", SuperProperties.get());
            h.setHeader("X-Discord-Locale", "en-US");
            return handleJSONResponse(h.getResponseCode(), h.getResponseString());
        } catch (DiscordException e) {
            throw e;
        } catch (Exception e) {
            throw new DiscordException(e);
        }
    }

    public Vector getInbox(boolean refresh) throws DiscordException {
        if (!refresh && inboxAvailable()) return inbox;

        String route = "/users/@me/mentions?limit=25&roles=true&everyone=true";
        AbstractJSON parsed;
        try {
            parsed = fetchJSON(route);
        } catch (DiscordException e) {
            if (e.message == null || e.message.indexOf("404") == -1) throw e;
            try {
                parsed = fetchInboxDirect(route);
            } catch (DiscordException directError) {
                Vector tips = new Vector();
                tips.addElement("switch to a direct connection in account settings");
                tips.addElement("or use a proxy that supports the mentions inbox");
                throw new DiscordException("Inbox unavailable", directError.message, tips);
            }
        }

        JSONArray inboxJson = (JSONArray) parsed;
        inbox.setSize(0);
        for (int i = 0; i < inboxJson.size(); i++) {
            JSONObject mentionJson = inboxJson.getObject(i);
            inbox.addElement(new Message(mentionJson));
        }

        return inbox;
    }
"""
    return replace_once(text, old, new, "Account inbox fallback")


def patch_account_list(text):
    text = replace_once(
        text,
        "    public static final int CMD_SETTINGS = 5;",
        "    public static final int CMD_SETTINGS = 5;\n    public static final int CMD_IMPORT_LEGACY = 6;",
        "AccountListScreen command id",
    )
    text = replace_once(
        text,
        '        addCommand(new Command("Settings", Command.SCREEN, CMD_SETTINGS));',
        """        addCommand(new Command("Settings", Command.SCREEN, CMD_SETTINGS));
        addCommand(new Command(
            App.hostBridge == null ? "Import Discord settings" : App.hostBridge.importLabel(),
            Command.SCREEN, CMD_IMPORT_LEGACY));""",
        "AccountListScreen import command",
    )
    text = replace_once(
        text,
        "            case CMD_DELETE: {",
        """            case CMD_IMPORT_LEGACY: {
                Settings.importLegacyIfNeeded();
                Settings.load();
                App.replaceAllScreens(new AccountListScreen());
                break;
            }
            case CMD_DELETE: {""",
        "AccountListScreen import action",
    )
    return text


def patch_message_box(text):
    text = replace_once(
        text,
        "import javax.microedition.lcdui.*;",
        "import javax.microedition.lcdui.*;\nimport midplay.util.Lang;",
        "MessageBox Lang import",
    )
    # Upstream 1.1.0+ formats channel names through getNameForTitle().
    text = text.replace(
        '"Message " + screen.channel.name',
        '"Message " + screen.channel.getNameForTitle()',
    )
    text = replace_once(
        text,
        "import discofi.bluewap.ui.AppCanvas;",
        "import discofi.bluewap.ui.AppCanvas;\nimport discofi.bluewap.ui.Dialog;",
        "MessageBox Dialog import",
    )
    text = replace_once(
        text,
        "    public static final int CMD_BACK = 1;",
        "    public static final int CMD_BACK = 1;\n    public static final int CMD_SAVE_NOTE = 2;",
        "MessageBox command id",
    )
    text = replace_once(
        text,
        "    private ChatScreen screen;",
        "    private ChatScreen screen;\n    private Command saveNoteCommand;",
        "MessageBox command field",
    )
    text = replace_once(
        text,
        "        setCommandListener(this);",
        """        setCommandListener(this);
        if (App.hostBridge != null) {
            saveNoteCommand = new Command(
                App.hostBridge.saveNoteLabel(), Command.SCREEN, CMD_SAVE_NOTE);
            addCommand(saveNoteCommand);
        }""",
        "MessageBox save command",
    )
    text = replace_once(
        text,
        "        else {\n            screen.",
        """        else if (c.getPriority() == CMD_SAVE_NOTE) {
            saveNote();
        }
        else {
            screen.""",
        "MessageBox save action",
    )
    text = add_before_class_end(
        text,
        """    private void saveNote() {
        boolean saved = App.hostBridge != null && App.hostBridge.saveNote(getString());
        App.pushScreen(new Dialog(Lang.tr(
            saved ? "discofi.save_note_done" : "discofi.save_note_error")));
    }
""",
        "MessageBox save method",
    )
    return text


def patch_login_screen(text):
    text = replace_once(
        text,
        "    private ButtonItem tokenJarImportButton;",
        "    private ButtonItem tokenJarImportButton;\n    private ButtonItem tokenNoteImportButton;",
        "LoginScreen note field",
    )
    text = replace_once(
        text,
        '        addItem(tokenJarImportButton = new ButtonItem("Import from JAR"));',
        """        addItem(tokenJarImportButton = new ButtonItem("Import from JAR"));
        addItem(tokenNoteImportButton = new ButtonItem(
            App.hostBridge == null ? "Import from note" : App.hostBridge.importTokenNoteLabel()));""",
        "LoginScreen note button",
    )
    text = replace_once(
        text,
        "        else if (i == tokenJarImportButton) {",
        """        else if (i == tokenNoteImportButton) {
            TokenNotePicker.open();
        }
        else if (i == tokenJarImportButton) {""",
        "LoginScreen note action",
    )
    return text


def patch_app_canvas(text):
    text = replace_once(
        text,
        "import discofi.bluewap.*;",
        "import discofi.bluewap.*;\nimport discofi.App;",
        "AppCanvas App import",
    )
    text = replace_once(
        text,
        "    private final Vector canvasCommands = new Vector();",
        """    private final Vector canvasCommands = new Vector();

    /** Host's now-playing command, available on every Discofi screen. */
    private Command nowPlayingCommand;""",
        "AppCanvas host command field",
    )
    text = replace_once(
        text,
        """    private void runCommand(Command c) {
        if (c == null) return;

        CommandListener lis = AppBase.getCurrentScreen().getCommandListener();
        if (lis == null) return;

        lis.commandAction(c, this);
    }""",
        """    private void runCommand(Command c) {
        if (c == null) return;

        if (c == nowPlayingCommand) {
            if (App.hostBridge != null) App.hostBridge.openNowPlaying();
            return;
        }

        CommandListener lis = AppBase.getCurrentScreen().getCommandListener();
        if (lis == null) return;

        lis.commandAction(c, this);
    }""",
        "AppCanvas host command action",
    )
    text = replace_once(
        text,
        """            if (curr == null || curr.getCommands().indexOf(c) == -1) {""",
        """            if (c != nowPlayingCommand &&
                    (curr == null || curr.getCommands().indexOf(c) == -1)) {""",
        "AppCanvas keep host command",
    )
    text = replace_once(
        text,
        """        assignCommandPositions();
    }

    private final Vector remainingCommands = new Vector();""",
        """        updateHostCommands();
        assignCommandPositions();
    }

    /** Adds/refreshes the host player command without touching screen commands. */
    private void updateHostCommands() {
        String label = App.hostBridge == null ? null : App.hostBridge.playerLabel();
        if (label == null || label.length() == 0) {
            if (nowPlayingCommand != null) {
                canvasCommands.removeElement(nowPlayingCommand);
                nowPlayingCommand = null;
            }
            return;
        }
        if (nowPlayingCommand != null && label.equals(nowPlayingCommand.getLabel())) return;
        if (nowPlayingCommand != null) canvasCommands.removeElement(nowPlayingCommand);
        nowPlayingCommand = new Command(label, Command.SCREEN, 0);
        canvasCommands.addElement(nowPlayingCommand);
    }

    private final Vector remainingCommands = new Vector();""",
        "AppCanvas host command refresh",
    )
    return text


TOKEN_NOTE_PICKER = """package discofi;

import discofi.bluewap.ui.*;
import midplay.store.NotesManager;
import midplay.store.NotesManager.Note;
import midplay.util.Lang;

/** Selects a MIDPlay note and imports its text as a Discord token. */
public class TokenNotePicker extends ListScreen {
    private final Note[] notes;

    private TokenNotePicker(Note[] notes) {
        super();
        setTitle(Lang.tr("discofi.import_token_note"));
        this.notes = notes;
        addItem(new BlankItem(0));
        for (int i = 0; i < notes.length; i++) {
            String title = notes[i].title == null ? "" : notes[i].title;
            if (title.length() == 0) title = Lang.tr("discofi.note") + " " + (i + 1);
            addItem(new ListItem(title, notes[i]));
        }
    }

    public static void open() {
        Note[] notes = NotesManager.getInstance().getNotes();
        if (notes.length == 0) {
            App.pushScreen(new Dialog(Lang.tr("discofi.no_notes")));
            return;
        }
        App.pushScreen(new TokenNotePicker(notes));
    }

    protected void itemSelected(Item item) {
        if (item instanceof ListItem && ((ListItem) item).object instanceof Note) {
            App.importTokenFromText(((Note) ((ListItem) item).object).text);
        }
    }
}
"""


HOST_BRIDGE = """package discofi;

/** Hooks supplied by the MIDPlay host for the embedded Discofi client. */
public interface HostBridge {
    boolean saveNote(String text);

    void openNowPlaying();

    String saveNoteLabel();

    String playerLabel();

    String importLabel();

    String importTokenNoteLabel();
}
"""


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: prepare_discofi.py <source-dir> <output-dir>")
    source = Path(sys.argv[1])
    output = Path(sys.argv[2])
    java_out = output / "src"
    res_out = output / "res" / "assets" / "discofi"

    shutil.rmtree(output, ignore_errors=True)
    java_out.mkdir(parents=True)
    res_out.mkdir(parents=True)

    files = {}
    destinations = {}
    total = 0
    for f in sorted(source.rglob("*.java")):
        relative = f.relative_to(source).as_posix()
        # cc.nnproject.json is shared with the host (see src/cc/nnproject/json).
        if relative.startswith("cc/nnproject/json/"):
            continue
        text = rename("\n".join(preprocess(f.read_text(encoding="utf-8").splitlines())) + "\n")
        renamed_path = relative
        for old, new in RENAMES:
            renamed_path = renamed_path.replace(old.replace(".", "/"), new.replace(".", "/"))
        dest = java_out / renamed_path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(text, encoding="utf-8")
        files[relative] = text
        destinations[relative] = dest
        total += 1

    patches = {
        "fi/gtrxac/bluewap/ui/AppBase.java": patch_app_base,
        "fi/gtrxac/discord/App.java": patch_app,
        "fi/gtrxac/discord/Settings.java": patch_settings,
        "fi/gtrxac/discord/Account.java": patch_account,
        "fi/gtrxac/discord/ChatScreen.java": patch_chat_screen,
        "fi/gtrxac/discord/MainScreen.java": patch_main_screen,
        "fi/gtrxac/discord/AccountListScreen.java": patch_account_list,
        "fi/gtrxac/discord/LoginScreen.java": patch_login_screen,
        "fi/gtrxac/discord/MessageBox.java": patch_message_box,
        "fi/gtrxac/discord/MessageEditBox.java": patch_message_box,
        "fi/gtrxac/bluewap/ui/AppCanvas.java": patch_app_canvas,
    }
    for relative, patch in patches.items():
        if relative not in files:
            raise SystemExit("Discofi patch target missing: %s" % relative)
        destinations[relative].write_text(patch(files[relative]), encoding="utf-8")

    (java_out / "discofi" / "HostBridge.java").write_text(HOST_BRIDGE, encoding="utf-8")
    (java_out / "discofi" / "TokenNotePicker.java").write_text(
        TOKEN_NOTE_PICKER, encoding="utf-8")

    for name in ("i.png", "l.png", "o.png"):
        asset = source.parent / "res" / name
        if not asset.exists():
            asset = Path(sys.argv[1]).parent / "res" / name
        shutil.copy2(asset, res_out / name)

    print("prepared %d Discofi sources (Bluetooth removed; + HostBridge)" % total)


if __name__ == "__main__":
    main()
