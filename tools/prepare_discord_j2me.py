#!/usr/bin/env python3
"""Package the Discord J2ME sources for the MIDPlay build.

The upstream project is a standalone MIDlet built with a small preprocessor.
MIDPlay embeds it as a subapp, so this script:

* applies the preprocessor directives for a MIDP 2.0 / CLDC 1.1 build,
* moves the code into MIDPlay-private packages,
* turns the MIDlet lifecycle into a host-driven open/exit bridge,
* keeps Discord's RMS records under a ``discord.`` prefix,
* copies the resources under ``/discord/`` so they cannot clash with MIDPlay.
"""

import json
import re
import shutil
import sys
from pathlib import Path

DEFINES = {
    "MIDP2_ALT",  # generic MIDP 2.0 build
    "OVER_100KB",
    "TOUCH_SUPPORT",
    "EMOJI_SUPPORT",
    "PROXYLESS_SUPPORT",
    "TRANSITION_SCREEN",
    "INLINE_ATTACHMENTS",
    "PASSWORD_LOGIN",
    "NO_BLUETOOTH",
    "NO_HTTP_BLUETOOTH_SUPPORT",
    "EMBEDDED_HOST",
}

STORE_NAMES = (
    "u|theme|login|settings|notifsound|insound|outsound|bgimage|emoji|emoji2|guild|favguild|lang"
)

EXCLUDED_FILES = {
    # Bluetooth transport is only used by the standalone build on devices with JSR-82.
    "BluetoothSocketConnection.java",
    "BluetoothHTTPConnection.java",
}

# The upstream sources use Nokia/Symbian direct fonts outside preprocessor guards.
# MIDPlay builds without the Nokia UI API, so a small fallback keeps the same call sites.
DIRECT_FONT_STUB = """package discordj2me;

import javax.microedition.lcdui.Font;

public final class DirectFontUtil {
  public static final boolean supported = false;

  public static Font getFont(int face, int style, int height, int size) {
    return Font.getFont(face, style, size);
  }

  private DirectFontUtil() {}
}
"""

RESOURCE_NAMES = (
    "icon.png",
    "sparkle.png",
    "selector.png",
    "play.png",
    "file.png",
    "bar.png",
    "overlay.png",
    "icons.png",
    "l.png",
    "notify.mid",
    "en.json",
    "en-compact.json",
    "pt-BR.json",
)

# Lets the login screen import a token from the MIDPlay notepad. It is written
# next to the upstream sources so it can reuse Discord's ListScreen and Dialog.
TOKEN_NOTE_PICKER = """package discordj2me;

import javax.microedition.lcdui.*;
import midplay.store.NotesManager;
import midplay.store.NotesManager.Note;
import midplay.util.Lang;

/** Imports a Discord token from a saved MIDPlay note. */
public class TokenNotePicker extends ListScreen implements CommandListener {
    private final Note[] notes;
    private final Object returnTo;

    private TokenNotePicker(Note[] notes, Object returnTo) {
        super(Lang.tr("discord.import_token_note"), true, false, false);
        this.notes = notes;
        this.returnTo = returnTo;
        for (int i = 0; i < notes.length; i++) {
            String title = notes[i].title == null ? "" : notes[i].title;
            append(title, null, null, null);
        }
        setCommandListener(this);
    }

    public static void open(Object returnTo) {
        Note[] notes = NotesManager.getInstance().getNotes();
        if (notes.length == 0) {
            App.error(Lang.tr("discord.no_notes"), returnTo);
            return;
        }
        if (returnTo == null) returnTo = MainMenu.get(false);
        App.disp.setCurrent(new TokenNotePicker(notes, returnTo));
    }

    public void commandAction(Command c, Displayable d) {
        if (c == SELECT_COMMAND) {
            int index = getSelectedIndex();
            if (index >= 0 && index < notes.length) {
                App.importTokenFromText(notes[index].text, returnTo);
            }
        } else if (c == BACK_COMMAND) {
            App.disp.setCurrent(returnTo);
        }
    }
}
"""

NEW_DM_FORM = """package discordj2me;

import javax.microedition.lcdui.*;
import midplay.util.Lang;

/** Asks for a Discord user ID and opens (or creates) that direct message. */
public class NewDMForm extends Form implements CommandListener {
    private final TextField idField;
    private final Command okCommand;
    private final Command backCommand;

    public NewDMForm() {
        super(Lang.tr("discord.new_dm"));
        idField = new TextField(Lang.tr("discord.new_dm_id"), "", 32, TextField.ANY);
        idField.setInitialInputMode("MIDP_LOWERCASE_LATIN");
        okCommand = new Command(Lang.tr("action.ok"), Command.OK, 0);
        backCommand = new Command(Lang.tr("action.back"), Command.BACK, 1);
        append(idField);
        addCommand(okCommand);
        addCommand(backCommand);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCommand) {
            App.openDMSelector(false, false);
        } else if (c == okCommand) {
            String value = idField.getString().trim();
            if (value.length() == 0) {
                App.error(Lang.tr("discord.new_dm_invalid"), this);
                return;
            }
            if (isNumeric(value)) {
                if (!isValidId(value)) {
                    App.error(Lang.tr("discord.new_dm_invalid"), this);
                    return;
                }
                HTTPThread h = new HTTPThread(HTTPThread.OPEN_DM_BY_ID);
                h.dmUserId = value;
                h.start();
            } else {
                HTTPThread h = new HTTPThread(HTTPThread.OPEN_DM_BY_USERNAME);
                h.dmUsername = value;
                h.start();
            }
        }
    }

    private static boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    private static boolean isValidId(String id) {
        if (id.length() < 15 || id.length() > 25) return false;
        for (int i = 0; i < id.length(); i++) {
            char ch = id.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }
}
"""

HOST_FIELDS = """    private static MIDlet host;
    private static Runnable exitHandler;

    /** Language selected in MIDPlay; embedded Discord follows it by default. */
    static String hostLanguage;

    public static void setHostLanguage(String code) {
        if (code == null) {
            hostLanguage = null;
            return;
        }
        if (code.toLowerCase().startsWith("pt")) {
            hostLanguage = "pt-BR";
        } else if ("en".equals(code)) {
            hostLanguage = "en-US";
        } else {
            hostLanguage = code;
        }
        if (instance != null && Settings.languageFollowsHost
                && !hostLanguage.equals(Settings.language)) {
            Settings.language = hostLanguage;
            Locale.setLanguage();
        }
    }

    /** True while Discord owns the screen. Background threads must not override the host. */
    static volatile boolean active = false;

    /** Screen that was visible when Discord was last closed. */
    private static Object lastScreen;

    private boolean started = false;

    /** Opens Discord inside the host MIDlet, returning to it when the user quits. */
    public static void open(MIDlet app, Runnable onExit) {
        host = app;
        exitHandler = onExit;
        active = true;
        if (instance == null) {
            new App().startApp();
        } else {
            if (Settings.useGateway && !gatewayActive()) startGateway();
            if (lastScreen != null) {
                disp.restore(lastScreen);
            } else if (Settings.token == null || Settings.token.trim().length() == 0) {
                disp.setCurrent(new LoginForm());
            } else {
                disp.setCurrent(MainMenu.get(false));
            }
        }
    }

    /**
     * Loads Discord settings, theme, fonts and the saved session during host
     * boot without taking over the host display.
     */
    public static void warmUp(MIDlet app) {
        host = app;
        if (instance == null) new App().startApp();
    }

    /** Opens the channel where a host-side notification occurred. */
    public static void openNotification(MIDlet app, Runnable onExit, String guildID, String channelID) {
        open(app, onExit);
        if (Settings.token != null && Settings.token.trim().length() != 0) {
            viewNotification(guildID, channelID);
        }
    }

    /** Leaves Discord and shows the host MIDlet again. */
    public static void exit() {
        if (!active) return;
        Object screen = disp.getCurrent();
        // Do not restore a dialog that asked to leave the app; show what was behind it.
        if (screen instanceof Dialog && ((Dialog) screen).lastScreen != null) {
            screen = ((Dialog) screen).lastScreen;
        }
        lastScreen = screen;
        active = false;
        // Normally keep the gateway alive so Discord notifications can still
        // reach the host. Economy mode must not leave anything in background.
        if (economyMode) stopGateway();
        if (exitHandler != null) {
            Runnable handler = exitHandler;
            exitHandler = null;
            handler.run();
        }
    }

    public String getAppProperty(String key) {
        return host == null ? null : host.getAppProperty(key);
    }

    public boolean platformRequest(String url)
            throws javax.microedition.io.ConnectionNotFoundException {
        return host != null && host.platformRequest(url);
    }

    public void notifyDestroyed() {
        exit();
    }

    /** Imports a token from a MIDPlay note and returns to the login form. */
    public static void importTokenFromText(String text, Object returnTo) {
        String token = text == null ? "" : text.trim();
        if (token.length() == 0) {
            disp.setCurrent(new Dialog(
                midplay.util.Lang.tr("discord.import_token_title"),
                midplay.util.Lang.tr("discord.import_token_empty"),
                returnTo
            ));
            return;
        }
        Settings.token = Util.stringToLength(token, 200);
        disp.setCurrent(new Dialog(
            midplay.util.Lang.tr("discord.import_token_title"),
            midplay.util.Lang.tr("discord.import_token_done"),
            returnTo
        ));
    }

    /** True when the Discord client has been started in this process. */
    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * Stops background work and releases cached Discord data. The host screen is
     * left untouched; the next open builds a fresh client from the saved settings.
     */
    public static void kill() {
        if (instance == null) return;
        HTTPQueue.releaseHtopState();
        KeyRepeatThread.releaseHtopState();
        stopGateway();
        GatewayThread.releaseHtopState();
        WrapperCanvas.releaseHtopState();
        MyCanvas.releaseHtopState();
        ChannelView.releaseHtopState();
        ChannelViewItem.releaseHtopState();
        Dialog.releaseHtopState();
        ListScreen.releaseHtopState();
        LoadingScreen.releaseHtopState();
        FormattedStringPartEmoji.releaseHtopState();
        FormattedStringParser.releaseHtopState();
        MainMenu.releaseHtopState();
        SettingsSectionScreen.releaseHtopState();
        SoundSettingsScreen.releaseHtopState();
        Settings.releaseHtopState();
        Settings.hasFetchedProxylessEmojis = false;
        try { Settings.save(); } catch (Throwable ignored) {}
        Locale.releaseHtopState();
        active = false;
        lastScreen = null;
        host = null;
        exitHandler = null;
        guilds = null;
        selectedGuild = null;
        guildSelector = null;
        subscribedGuilds.removeAllElements();
        unsortedGuilds = null;
        channels = null;
        selectedChannel = null;
        channelSelector = null;
        channelIsOpen = false;
        threads = null;
        threadSelector = null;
        selectedChannelForThreads = null;
        messages = null;
        channelView = null;
        typingUsers = null;
        typingUserIDs = null;
        attachmentView = null;
        isDM = false;
        dmChannels = null;
        selectedDmChannel = null;
        dmSelector = null;
        uploadToken = null;
        myUserId = null;
        authorFont = null;
        timestampFont = null;
        messageFont = null;
        titleFont = null;
        ic = null;
        IconCache.init();
        NameColorCache.init();
        disp = null;
        instance = null;
    }
"""


RESET_METHODS = {
    "WrapperCanvas.java": """    static void releaseHtopState() {
        WrapperCanvas old = instance;
        instance = null;
        if (old != null) {
            old.current = null;
            if (old.commands != null) old.commands.removeAllElements();
        }
        ashaPlaceholderCommand = null;
        isKeyPressed = false;
        beginRepeatTime = 0;
        isDraggingBack = false;
    }""",
    "MyCanvas.java": """    static void releaseHtopState() {
        backgroundImageOrig = null;
        backgroundImage = null;
        backgroundImageWidth = 0;
        backgroundImageHeight = 0;
    }""",
    "ChannelView.java": """    static void releaseHtopState() {
        draftMessage = null;
        messageBarLeft = null;
        messageBarCenter = null;
        messageBarRight = null;
        messageBarAttachmentIcon = null;
        messageBarEmojiIcon = null;
        messageBarIconHash = 0;
        messageBarHash = 0;
    }""",
    "ChannelViewItem.java": """    static void releaseHtopState() {
        playButton = null;
        fileIcon = null;
        smallFont = null;
        smallBoldFont = null;
        unreadIndicatorImage = null;
        smallFontSize = 0;
    }""",
    "Dialog.java": """    static void releaseHtopState() {
        okLabel = null;
        okLabelLong = null;
        overlay = null;
    }""",
    "ListScreen.java": """    static void releaseHtopState() {
        indicatorImage = null;
    }""",
    "LoadingScreen.java": """    static void releaseHtopState() {
        phoneFrames = null;
        bubbleFrames = null;
        scale = 0;
    }""",
    "FormattedStringPartEmoji.java": """    static void releaseHtopState() {
        emojiTable = null;
        loadedEmoji = null;
        loadedEmojiNames = null;
        loadedSheet = null;
        loadedSheetId = -1;
    }""",
    "FormattedStringParser.java": """    static void releaseHtopState() {
        unicodeEmojis = null;
        unicodeEmojiNames = null;
    }""",
    "Locale.java": """    static void releaseHtopState() {
        strs = null;
        defaultStrs = null;
        version = 0;
    }""",
    "MainMenu.java": """    static void releaseHtopState() {
        instance = null;
        hasFavorites = false;
    }""",
    "SettingsSectionScreen.java": """    static void releaseHtopState() {
        instance = null;
        settings = null;
    }""",
    "SoundSettingsScreen.java": """    static void releaseHtopState() {
        instance = null;
    }""",
    "Settings.java": """    static void releaseHtopState() {
        if (loginRms != null) {
            try { loginRms.closeRecordStore(); } catch (Throwable ignored) {}
        }
        loginRms = null;
        loginData = null;
        index = 0;
    }""",
    "GatewayThread.java": """    static void releaseHtopState() {
        reconnectAttempts = 0;
        if (player != null) {
            try { player.stop(); } catch (Throwable ignored) {}
            try { player.close(); } catch (Throwable ignored) {}
            player = null;
        }
    }""",
    "KeyRepeatThread.java": """    static void releaseHtopState() {
        enabled = false;
        activeKey = 0;
        KeyRepeatThread old = instance;
        instance = null;
        if (old != null) old.interrupt();
    }""",
    "HTTPQueue.java": """    static void releaseHtopState() {
        Vector items = new Vector();
        synchronized (LOCK) {
            for (int i = 0; i < queueItems.size(); i++) {
                items.addElement(queueItems.elementAt(i));
            }
        }
        for (int i = 0; i < items.size(); i++) {
            HTTPQueue item = (HTTPQueue) items.elementAt(i);
            item.canceled = true;
            item.close();
        }
        synchronized (LOCK) {
            queueItems.removeAllElements();
            activeByThread.clear();
        }
    }""",
}


def append_release_method(text, method):
    marker = text.rfind("\n}")
    if marker == -1:
        raise SystemExit("Could not append Discord cache release method")
    return text[:marker] + "\n\n" + method + text[marker:]


def preprocess(text):
    """Apply //#ifdef, //#ifndef, //#else and //#endif directives."""
    output = []
    stack = []  # parent active state for each open block
    active = True
    for line in text.splitlines():
        stripped = line.strip()
        match = re.match(r"^//#(ifdef|ifndef)\s+(\S+)\s*$", stripped)
        if match:
            condition = (match.group(2) in DEFINES) == (match.group(1) == "ifdef")
            stack.append(active)
            active = active and condition
            output.append("")
            continue
        if stripped.startswith("//#else"):
            if stack:
                active = stack[-1] and not active
            output.append("")
            continue
        if stripped.startswith("//#endif"):
            if stack:
                active = stack.pop()
            output.append("")
            continue
        output.append(line if active else "")
    return "\n".join(output) + "\n"


def strip_jsonc(text):
    """Remove // comments outside strings, then parse the JSONC document."""
    result = []
    index = 0
    in_string = False
    escaped = False
    while index < len(text):
        char = text[index]
        if in_string:
            result.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == '"':
            in_string = True
            result.append(char)
            index += 1
            continue
        if char == "/" and index + 1 < len(text) and text[index + 1] == "/":
            while index < len(text) and text[index] not in "\r\n":
                index += 1
            continue
        result.append(char)
        index += 1
    return json.loads("".join(result))


def rewrite_resource_paths(text):
    for name in RESOURCE_NAMES:
        text = text.replace('"/%s"' % name, '"/discord/%s"' % name)
    return text


def rewrite_rms_names(text):
    text = re.sub(
        r"(openRecordStore|deleteRecordStore)\(\"(" + STORE_NAMES + r")\"",
        r'\1("discord.\2"',
        text,
    )
    text = re.sub(
        r'(loadEmojiJson)\(\"(' + STORE_NAMES + r')\"',
        r'\1("discord.\2"',
        text,
    )
    text = re.sub(
        r'(addItem\([^,]+,\s*)\"(' + STORE_NAMES + r')\"',
        r'\1"discord.\2"',
        text,
    )
    text = re.sub(
        r'\"(' + STORE_NAMES + r')\"\.equals\(rmsName\)',
        r'"discord.\1".equals(rmsName)',
        text,
    )
    text = text.replace(
        '"notifsound", "insound", "outsound"',
        '"discord.notifsound", "discord.insound", "discord.outsound"',
    )
    return text


def patch_app(text):
    old_class = "public class App extends MIDlet implements Strings {"
    if old_class not in text:
        raise SystemExit("Discord App class declaration changed upstream")
    text = text.replace(old_class, "public class App implements Strings {")

    old_fields = "    private boolean started = false;\n"
    if old_fields not in text:
        raise SystemExit("Discord App field block changed upstream")
    text = text.replace(old_fields, HOST_FIELDS, 1)

    old_display = "disp = new MyDisplay(Display.getDisplay(this));"
    if old_display not in text:
        raise SystemExit("Discord App display setup changed upstream")
    text = text.replace(old_display, "disp = new MyDisplay(Display.getDisplay(host));")
    return text


def patch_login_form(text):
    """Offer file and MIDPlay-note sources when importing a login token."""
    field = "    private Command importTokenCommand;\n"
    if field not in text:
        raise SystemExit("Discord LoginForm import token field changed upstream")
    text = text.replace(
        field,
        field
        + "    private Command importTokenFilesCommand;\n"
        + "    private Command importTokenNotesCommand;\n"
        + "    private Command importTokenCancelCommand;\n",
        1,
    )

    # The import button was only created when JSR-75 was present. Notes live in
    # RMS, so keep the button available on every device.
    start_marker = (
        "        if (Util.supportsFileConn) {\n"
        "            importTokenCommand = Locale.createCommand(IMPORT_TOKEN, Command.ITEM, 3);"
    )
    start = text.find(start_marker)
    if start == -1:
        raise SystemExit("Discord LoginForm import token setup changed upstream")
    end = text.find("\n        }\n", start)
    if end == -1:
        raise SystemExit("Discord LoginForm import token setup has no end")
    end += len("\n        }\n")
    block = text[start:end]
    inner = block.split("\n", 1)[1].rsplit("\n        }\n", 1)[0]
    dedented = "\n".join(line[4:] if line.startswith("    ") else line for line in inner.splitlines())
    text = text[:start] + dedented + "\n" + text[end:]

    old_help = (
        "        if (Util.supportsFileConn) {\n"
        "            append(Locale.get(TOKEN_IMPORT_HELP_WITH_FC));\n"
        "            append(importTokenButton);\n"
        "        } else {\n"
        "            append(Locale.get(TOKEN_IMPORT_HELP));\n"
        "        }\n"
    )
    new_help = (
        "        if (Util.supportsFileConn) {\n"
        "            append(Locale.get(TOKEN_IMPORT_HELP_WITH_FC));\n"
        "        } else {\n"
        "            append(Locale.get(TOKEN_IMPORT_HELP));\n"
        "        }\n"
        "        append(importTokenButton);\n"
    )
    if old_help not in text:
        raise SystemExit("Discord LoginForm import token help changed upstream")
    text = text.replace(old_help, new_help, 1)

    quit_handler = (
        "        else if (c == quitCommand) {\n"
        "            App.instance.notifyDestroyed();\n"
        "        }\n"
    )
    prompt_handlers = (
        quit_handler
        + "        else if (c == importTokenFilesCommand) {\n"
        + "            App.disp.setCurrent(new TokenFilePicker());\n"
        + "        }\n"
        + "        else if (c == importTokenNotesCommand) {\n"
        + "            TokenNotePicker.open(this);\n"
        + "        }\n"
        + "        else if (c == importTokenCancelCommand) {\n"
        + "            App.disp.setCurrent(this);\n"
        + "        }\n"
    )
    if quit_handler not in text:
        raise SystemExit("Discord LoginForm quit handler changed upstream")
    text = text.replace(quit_handler, prompt_handlers, 1)

    old_item_handler = (
        "        else {\n"
        "            // import token command\n"
        "            App.disp.setCurrent(new TokenFilePicker());\n"
        "        }\n"
    )
    new_item_handler = (
        "        else if (c == importTokenCommand) {\n"
        "            showImportTokenPrompt();\n"
        "        }\n"
    )
    if old_item_handler not in text:
        raise SystemExit("Discord LoginForm item handler changed upstream")
    text = text.replace(old_item_handler, new_item_handler, 1)

    anchor = "    private void showTokenEntry() {"
    if anchor not in text:
        raise SystemExit("Discord LoginForm token entry method changed upstream")
    prompt = """    private void showImportTokenPrompt() {
        Dialog d = new Dialog(
            midplay.util.Lang.tr("discord.import_token_title"),
            midplay.util.Lang.tr("discord.import_token_prompt")
        );
        if (Util.supportsFileConn) {
            importTokenFilesCommand = new Command(
                midplay.util.Lang.tr("discord.import_from_files"), Command.OK, 1
            );
            d.addCommand(importTokenFilesCommand);
        }
        importTokenNotesCommand = new Command(
            midplay.util.Lang.tr("discord.import_from_notes"), Command.OK, 2
        );
        importTokenCancelCommand = new Command(
            midplay.util.Lang.tr("action.cancel"), Command.BACK, 3
        );
        d.addCommand(importTokenNotesCommand);
        d.addCommand(importTokenCancelCommand);
        d.setCommandListener(this);
        App.disp.setCurrent(d);
    }

"""
    text = text.replace(anchor, prompt + anchor, 1)
    return text


def patch_my_display(text):
    """Keep background screens from taking over the host once Discord is closed."""
    anchor = "    public int getColor(int colorSpecifier) {"
    if anchor not in text:
        raise SystemExit("Discord MyDisplay layout changed upstream")
    helper = """    private void apply(Displayable d) {
        if (App.active) disp.setCurrent(d);
    }

    /** Restores a saved screen without disturbing the navigation history. */
    public synchronized void restore(Object d) {
        if (d instanceof MyCanvas) {
            WrapperCanvas.instance.setCurrent((MyCanvas) d);
            apply(WrapperCanvas.instance);
        } else if (d instanceof Displayable) {
            apply((Displayable) d);
        }
    }

"""
    text = text.replace(anchor, helper + anchor)

    old_plain = "        disp.setCurrent(d);\n    }\n\n    private boolean allowTransition"
    if old_plain not in text:
        raise SystemExit("Discord MyDisplay.setCurrent(Displayable) changed upstream")
    text = text.replace(old_plain, "        apply(d);\n    }\n\n    private boolean allowTransition")

    old_canvas = (
        "        disp.setCurrent(WrapperCanvas.instance);\n"
        "    }\n\n"
        "    public void setCurrent(MyCanvas d)"
    )
    if old_canvas not in text:
        raise SystemExit("Discord MyDisplay.setCurrent(MyCanvas) changed upstream")
    text = text.replace(old_canvas, "        apply(WrapperCanvas.instance);\n    }\n\n    public void setCurrent(MyCanvas d)")
    return text


def patch_settings(text):
    """Let the embedded client follow MIDPlay's language until the user overrides it."""
    field = "    static String language;\n"
    if field not in text:
        raise SystemExit("Discord Settings language field changed upstream")
    text = text.replace(field, field + "    static boolean languageFollowsHost = true;\n", 1)

    locale_load = "        Locale.setLanguage();\n    }\n"
    if locale_load not in text:
        raise SystemExit("Discord Settings language load changed upstream")
    text = text.replace(
        locale_load,
        "        languageFollowsHost = getBoolRecord(true);\n"
        "//#ifdef EMBEDDED_HOST\n"
        "        if (languageFollowsHost && App.hostLanguage != null) {\n"
        "            language = App.hostLanguage;\n"
        "        }\n"
        "        if (language != null) {\n"
        "            language = Util.replace(language, \"_\", \"-\");\n"
        "            if (language.toLowerCase().startsWith(\"pt\")) language = \"pt-BR\";\n"
        "        }\n"
        "//#endif\n"
        "        Locale.setLanguage();\n"
        "    }\n",
        1,
    )

    password_save = "        setStringRecord(passwordLoginLastEmail);\n"
    start = text.rfind(password_save)
    if start == -1:
        raise SystemExit("Discord Settings password save changed upstream")
    end = text.find("\n    }\n", start)
    if end == -1:
        raise SystemExit("Discord Settings save method has no end")
    text = (
        text[:end]
        + "\n        setBoolRecord(languageFollowsHost);"
        + text[end:]
    )
    return text


def patch_language_selector(text):
    """Remember when the user picks a language inside Discord."""
    select = (
        "        if (c == SELECT_COMMAND) {\n"
        "            Settings.language = Locale.langIds[getSelectedIndex()];\n"
    )
    if select not in text:
        raise SystemExit("Discord LanguageSelector select handler changed upstream")
    text = text.replace(
        select,
        select + "            Settings.languageFollowsHost = false;\n",
        1,
    )
    return text


def patch_dm_selector(text):
    """Add a "New DM" command that opens a conversation by user ID."""
    field = "    private Command searchCommand;\n"
    if field not in text:
        raise SystemExit("Discord DMSelector command fields changed upstream")
    text = text.replace(field, field + "    private Command newDmCommand;\n", 1)

    search_setup = '        searchCommand = Locale.createCommand(SEARCH, Command.SCREEN, 2);\n'
    if search_setup not in text:
        raise SystemExit("Discord DMSelector search command changed upstream")
    text = text.replace(
        search_setup,
        search_setup
        + '        newDmCommand = new Command(\n'
        + '            midplay.util.Lang.tr("discord.new_dm"), Command.SCREEN, 1\n'
        + '        );\n',
        1,
    )

    add_commands = "        addCommand(searchCommand);\n        addCommand(refreshCommand);\n"
    if add_commands not in text:
        raise SystemExit("Discord DMSelector command registration changed upstream")
    text = text.replace(
        add_commands,
        add_commands + "        addCommand(newDmCommand);\n",
        1,
    )

    back_handler = (
        "            App.disp.setCurrent(MainMenu.get(false));\n"
        "        }\n"
        "        if (c == searchCommand) {\n"
    )
    if back_handler not in text:
        raise SystemExit("Discord DMSelector back handler changed upstream")
    text = text.replace(
        back_handler,
        "            App.disp.setCurrent(MainMenu.get(false));\n"
        "        }\n"
        "        if (c == newDmCommand) {\n"
        "            App.disp.setCurrent(new NewDMForm());\n"
        "            return;\n"
        "        }\n"
        "        if (c == searchCommand) {\n",
        1,
    )
    return text


def patch_http_thread(text):
    """Handle opening a direct message by user ID or by username."""
    constant = "    static final int HTTP_QR_LOGIN_CHECK = 18;\n"
    if constant not in text:
        raise SystemExit("Discord HTTPThread action constants changed upstream")
    text = text.replace(
        constant,
        constant
        + "    static final int OPEN_DM_BY_ID = 19;\n"
        + "    static final int OPEN_DM_BY_USERNAME = 20;\n",
        1,
    )

    field = "    String channelID;\n"
    if field not in text:
        raise SystemExit("Discord HTTPThread notification fields changed upstream")
    text = text.replace(
        field,
        field
        + "\n    // Parameters for OPEN_DM_BY_ID and OPEN_DM_BY_USERNAME\n"
        + "    String dmUserId;\n"
        + "    String dmUsername;\n",
        1,
    )

    form_anchor = "    private byte[] createFormPart("
    if form_anchor not in text:
        raise SystemExit("Discord HTTPThread form helper changed upstream")
    helpers = """    private static String normalizeUsername(String value) {
        String name = value == null ? "" : value.trim();
        if (name.startsWith("@")) name = name.substring(1);
        int hash = name.indexOf('#');
        if (hash != -1) name = name.substring(0, hash);
        return name.toLowerCase();
    }

    private static boolean usernameMatches(JSONObject user, String query) {
        if (user == null) return false;
        String username = user.getString("username", null);
        if (username != null && username.toLowerCase().equals(query)) return true;
        String global = user.getString("global_name", null);
        if (global != null && global.toLowerCase().equals(query)) return true;
        return false;
    }

    private static String encodeQuery(String value) {
        StringBuffer result = new StringBuffer();
        String hex = "0123456789ABCDEF";
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_'
                    || ch == '.' || ch == '~') {
                result.append(ch);
            } else if (ch == ' ') {
                result.append("%20");
            } else {
                result.append('%');
                result.append(hex.charAt((ch >> 4) & 0xF));
                result.append(hex.charAt(ch & 0xF));
            }
        }
        return result.toString();
    }

    private void openDMById(String userId) throws Exception {
        // Best effort: request the friendship straight from the user ID.
        // Being already friends or having requests disabled must not stop the
        // DM from opening.
        try {
            HTTP.apiRequest("PUT", "/users/@me/relationships/" + userId, "{}", false);
        } catch (Throwable ignored) {
        }

        JSONObject request = new JSONObject();
        request.put("recipient_id", userId);
        DMChannel dm = new DMChannel(
            JSON.getObject(HTTP.post("/users/@me/channels", request, false))
        );

        if (App.dmChannels == null) {
            App.dmChannels = new DMChannel[] { dm };
        } else {
            boolean found = false;
            for (int i = 0; i < App.dmChannels.length; i++) {
                if (App.dmChannels[i].id.equals(dm.id)) {
                    App.dmChannels[i] = dm;
                    found = true;
                    break;
                }
            }
            if (!found) {
                DMChannel[] grown = new DMChannel[App.dmChannels.length + 1];
                grown[0] = dm;
                System.arraycopy(App.dmChannels, 0, grown, 1, App.dmChannels.length);
                App.dmChannels = grown;
            }
        }

        // Reload the DM list from the server next time it opens, so the new
        // conversation also appears there.
        App.dmSelector = null;
        App.isDM = true;
        App.selectedDmChannel = dm;
        App.dontShowLoadScreen = true;
        new HTTPThread(HTTPThread.FETCH_MESSAGES).start();
    }

    private boolean searchGuildMembers(String guildID, String query) throws Exception {
        JSONArray members = JSON.getArray(HTTP.get(
            "/guilds/" + guildID + "/members/search?query=" + encodeQuery(query) + "&limit=10",
            false
        ));
        for (int i = 0; i < members.size(); i++) {
            JSONObject user = members.getObject(i).getObject("user", null);
            if (!usernameMatches(user, query)) continue;
            openDMById(user.getString("id"));
            return true;
        }
        return false;
    }

    private void openDMByUsername(String value) throws Exception {
        String query = normalizeUsername(value);
        if (query.length() == 0) {
            throw new Exception(midplay.util.Lang.tr("discord.new_dm_not_found"));
        }

        // If the conversation already exists, just open it.
        if (App.dmChannels != null) {
            for (int i = 0; i < App.dmChannels.length; i++) {
                DMChannel ch = App.dmChannels[i];
                if (ch.isGroup) continue;
                boolean match = ch.name != null && ch.name.toLowerCase().equals(query);
                if (!match && ch.username != null && ch.username.toLowerCase().equals(query)) {
                    match = true;
                }
                if (!match) continue;
                App.dmSelector = null;
                App.isDM = true;
                App.selectedDmChannel = ch;
                App.dontShowLoadScreen = true;
                new HTTPThread(HTTPThread.FETCH_MESSAGES).start();
                return;
            }
        }

        // Friends and pending requests include the user object inline.
        try {
            JSONArray relationships = JSON.getArray(HTTP.get("/users/@me/relationships", false));
            for (int i = 0; i < relationships.size(); i++) {
                JSONObject relationship = relationships.getObject(i);
                JSONObject user = relationship.getObject("user", null);
                if (!usernameMatches(user, query)) continue;
                openDMById(user.getString("id", relationship.getString("id", null)));
                return;
            }
        } catch (Throwable ignored) {
        }

        // Fall back to searching members of the servers we are in.
        if (App.guilds != null) {
            for (int i = 0; i < App.guilds.length; i++) {
                try {
                    if (searchGuildMembers(App.guilds[i].id, query)) return;
                } catch (Throwable ignored) {
                }
            }
        } else {
            try {
                JSONArray guilds = JSON.getArray(HTTP.get("/users/@me/guilds", false));
                for (int i = 0; i < guilds.size(); i++) {
                    try {
                        if (searchGuildMembers(guilds.getObject(i).getString("id"), query)) return;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        throw new Exception(midplay.util.Lang.tr("discord.new_dm_not_found"));
    }

"""
    text = text.replace(form_anchor, helpers + form_anchor, 1)

    qr_case = (
        "                    if (prevScreen instanceof QRLoginScreen) {\n"
        "                        ((QRLoginScreen) prevScreen).tokenCallback();\n"
        "                    }\n"
        "                    break;\n"
        "                }\n"
    )
    if qr_case not in text:
        raise SystemExit("Discord HTTPThread QR login case changed upstream")
    new_case = """                case OPEN_DM_BY_ID: {
                    openDMById(dmUserId);
                    break;
                }

                case OPEN_DM_BY_USERNAME: {
                    openDMByUsername(dmUsername);
                    break;
                }

"""
    text = text.replace(qr_case, qr_case + new_case, 1)
    return text


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: prepare_discord_j2me.py <source-dir> <output-dir>")
    root = Path(sys.argv[1])
    output = Path(sys.argv[2])
    shutil.rmtree(output, ignore_errors=True)
    app_out = output / "src" / "discordj2me"
    json_out = app_out / "json"
    app_out.mkdir(parents=True)
    json_out.mkdir(parents=True)

    app_src = root / "src" / "com" / "gtrxac" / "discord"
    for java_file in sorted(app_src.glob("*.java")):
        if java_file.name in EXCLUDED_FILES:
            continue
        text = preprocess(java_file.read_text(encoding="utf-8"))
        text = text.replace("package com.gtrxac.discord;", "package discordj2me;")
        text = text.replace("import cc.nnproject.json.", "import discordj2me.json.")
        text = re.sub(r"^\s*import jtube\.ui\.nokia\.[^;]+;\s*$", "", text, flags=re.MULTILINE)
        text = re.sub(
            r"^\s*import fi\.gtrxac\.bluewap\.http\.HTTP;\s*$", "", text, flags=re.MULTILINE
        )
        text = rewrite_resource_paths(text)
        text = rewrite_rms_names(text)
        if java_file.name == "App.java":
            text = patch_app(text)
        elif java_file.name == "LoginForm.java":
            text = patch_login_form(text)
        elif java_file.name == "MyDisplay.java":
            text = patch_my_display(text)
        elif java_file.name == "Settings.java":
            text = patch_settings(text)
        elif java_file.name == "LanguageSelector.java":
            text = patch_language_selector(text)
        elif java_file.name == "DMSelector.java":
            text = patch_dm_selector(text)
        elif java_file.name == "HTTPThread.java":
            text = patch_http_thread(text)
        reset_method = RESET_METHODS.get(java_file.name)
        if reset_method:
            text = append_release_method(text, reset_method)
        (app_out / java_file.name).write_text(text, encoding="utf-8")

    (app_out / "DirectFontUtil.java").write_text(DIRECT_FONT_STUB, encoding="utf-8")
    (app_out / "TokenNotePicker.java").write_text(TOKEN_NOTE_PICKER, encoding="utf-8")
    (app_out / "NewDMForm.java").write_text(NEW_DM_FORM, encoding="utf-8")

    json_src = root / "src" / "cc" / "nnproject" / "json"
    for java_file in sorted(json_src.glob("*.java")):
        text = java_file.read_text(encoding="utf-8")
        text = text.replace("package cc.nnproject.json;", "package discordj2me.json;")
        text = text.replace("import cc.nnproject.json.", "import discordj2me.json.")
        (json_out / java_file.name).write_text(text, encoding="utf-8")

    res_out = output / "res" / "discord"
    res_out.mkdir(parents=True)
    for asset in (root / "res").glob("*"):
        if asset.is_file():
            shutil.copy2(asset, res_out / asset.name)

    for language in ("en", "pt-BR"):
        locale = strip_jsonc(
            (root / "language" / (language + ".jsonc")).read_text(encoding="utf-8")
        )
        (res_out / (language + ".json")).write_text(
            json.dumps(locale, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
        )


if __name__ == "__main__":
    main()
