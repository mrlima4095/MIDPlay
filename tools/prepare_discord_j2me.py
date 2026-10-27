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

HOST_FIELDS = """    public static App instance;

    private static MIDlet host;
    private static Runnable exitHandler;

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
            } else {
                disp.setCurrent(MainMenu.get(false));
            }
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
        if (gateway != null) gateway.stop = true;
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
"""


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

    old_fields = "    public static App instance;\n\n    private boolean started = false;\n"
    if old_fields not in text:
        raise SystemExit("Discord App field block changed upstream")
    text = text.replace(old_fields, HOST_FIELDS)

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
        (app_out / java_file.name).write_text(text, encoding="utf-8")

    (app_out / "DirectFontUtil.java").write_text(DIRECT_FONT_STUB, encoding="utf-8")
    (app_out / "TokenNotePicker.java").write_text(TOKEN_NOTE_PICKER, encoding="utf-8")

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

    locale = strip_jsonc((root / "language" / "en.jsonc").read_text(encoding="utf-8"))
    (res_out / "en.json").write_text(
        json.dumps(locale, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )


if __name__ == "__main__":
    main()
