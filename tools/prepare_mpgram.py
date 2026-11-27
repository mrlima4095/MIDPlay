#!/usr/bin/env python3
"""Prepare the generic MPGram source for inclusion in the MIDPlay JAR."""

import json
import os
import shutil
import sys
from pathlib import Path


SYMBOLS = {
    "NO_NOKIAUI",
}

# Strings for the account menu in MPGram's main screen. They are appended to the
# generated language table so no upstream locale file has to be edited.
ACCOUNT_MESSAGES = {
    "MPGramAccounts": "Accounts",
    "MPGramAddAccount": "Add account",
    "MPGramSwitchAccount": "Switch account",
    "MPGramRemoveAccount": "Remove account",
    "MPGramAccountActive": "active",
    "MPGramNoAccounts": "No saved accounts",
    "MPGramAccountRemoved": "Account removed",
    "MPGramAccountRemoveLast": "Cannot remove the last account",
    "MPGramRenameAccount": "Name account",
    "MPGramAccountName": "Account name",
    "MPGramAccountNamePrompt": "Enter a name for this account",
    "MPGramAccountNameSaved": "Account name saved",
    "MPGramYes": "Yes",
    "MPGramNo": "No",
}

ACCOUNT_LOCALES = {
    "pt": {
        "MPGramAccounts": "Contas",
        "MPGramAddAccount": "Adicionar conta",
        "MPGramSwitchAccount": "Trocar conta",
        "MPGramRemoveAccount": "Remover conta",
        "MPGramAccountActive": "ativa",
        "MPGramNoAccounts": "Nenhuma conta salva",
        "MPGramAccountRemoved": "Conta removida",
        "MPGramAccountRemoveLast": "Não é possível remover a última conta",
        "MPGramRenameAccount": "Nomear conta",
        "MPGramAccountName": "Nome da conta",
        "MPGramAccountNamePrompt": "Digite um nome para esta conta",
        "MPGramAccountNameSaved": "Nome da conta salvo",
        "MPGramYes": "Sim",
        "MPGramNo": "Não",
    },
    "tr": {
        "MPGramAccounts": "Hesaplar",
        "MPGramAddAccount": "Hesap ekle",
        "MPGramSwitchAccount": "Hesabı değiştir",
        "MPGramRemoveAccount": "Hesabı kaldır",
        "MPGramAccountActive": "etkin",
        "MPGramNoAccounts": "Kayıtlı hesap yok",
        "MPGramAccountRemoved": "Hesap kaldırıldı",
        "MPGramAccountRemoveLast": "Son hesap kaldırılamaz",
        "MPGramRenameAccount": "Hesaba ad ver",
        "MPGramAccountName": "Hesap adı",
        "MPGramAccountNamePrompt": "Bu hesap için bir ad girin",
        "MPGramAccountNameSaved": "Hesap adı kaydedildi",
        "MPGramYes": "Evet",
        "MPGramNo": "Hayır",
    },
}


def enabled(directive):
    parts = directive.split()
    if parts[0] == "ifdef":
        return parts[1] in SYMBOLS
    if parts[0] == "ifndef":
        return parts[1] not in SYMBOLS
    if parts[0] == "if":
        return directive[3:].strip() == '""==""'
    raise ValueError("Unsupported MPGram preprocessor directive: " + directive)


def preprocess(source):
    output = []
    active = [True]
    for line in source.splitlines(True):
        stripped = line.strip()
        if not stripped.startswith("//#"):
            if active[-1]:
                output.append(line)
            continue
        directive = stripped[3:].strip()
        if directive.startswith(("ifdef ", "ifndef ", "if ")):
            active.append(active[-1] and enabled(directive))
        elif directive == "else":
            if len(active) == 1:
                raise ValueError("Unexpected #else")
            parent = active[-2]
            active[-1] = parent and not active[-1]
        elif directive == "endif":
            if len(active) == 1:
                raise ValueError("Unexpected #endif")
            active.pop()
        elif active[-1]:
            marker = line.index("//#")
            output.append(line[:marker] + line[marker + 3:])
    if len(active) != 1:
        raise ValueError("Unclosed MPGram preprocessor directive")
    return "".join(output)


def load_jsonc(path):
    lines = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped.startswith("//"):
            continue
        lines.append(line.split(" // ", 1)[0])
    return json.loads("\n".join(lines))


def escape_locale(value):
    return str(value).replace("\\", "\\\\").replace("\n", "\\n")


def language_constants(mpgram, destination, resources):
    messages = load_jsonc(mpgram / "langs" / "en.jsonc")
    messages.update(ACCOUNT_MESSAGES)
    constants = ["package mpgram;", "", "public interface LangConstants {"]
    for index, key in enumerate(messages, 1):
        constants.append("  int L%s = %d;" % (key, index))
    constants.append("  int LLocaleStrings = %d;" % len(messages))
    constants.append("  int Lmpgram = 0;")
    constants.append("}")
    (destination / "LangConstants.java").write_text("\n".join(constants) + "\n", encoding="utf-8")
    locale_dir = resources / "l"
    locale_dir.mkdir(parents=True, exist_ok=True)
    for locale_file in (mpgram / "langs").glob("*.jsonc"):
        if os.environ.get("MPGRAM_FORCE_ENGLISH") == "1" and locale_file.stem != "en":
            continue
        locale = load_jsonc(locale_file)
        output = []
        base = locale.get("LocaleBase")
        overrides = ACCOUNT_LOCALES.get(locale_file.stem, {})
        if base:
            output.append("/l/" + base)
            for index, key in enumerate(messages, 1):
                if key in locale:
                    output.append(str(index) + ":" + escape_locale(locale[key]))
                elif key in ACCOUNT_MESSAGES:
                    output.append(
                        str(index)
                        + ":"
                        + escape_locale(overrides.get(key, ACCOUNT_MESSAGES[key]))
                    )
        else:
            for key, value in messages.items():
                output.append(escape_locale(overrides.get(key, locale.get(key, value))))
        locale_dir.joinpath(locale_file.stem).write_text("\n".join(output) + "\n", encoding="utf-8")


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit("MPGram patch anchor not found: " + label)
    return text.replace(old, new, 1)


def patch_mp(text):
    """Add multi-account and lifecycle hooks used by MIDPlay's subapp manager."""
    anchor = "\tprivate void closeHost() {"
    if anchor not in text:
        raise SystemExit("MPGram closeHost() changed upstream")

    hooks = """\t/** Materializes an account into the static fields used by all MPGram requests. */
\tstatic void applyAccountSnapshot(String u, int st, String ph,
\t\t\tString url, String instPass, String sid) {
\t\tuser = u;
\t\tuserState = st;
\t\tphone = ph;
\t\tinstanceUrl = url == null || url.length() == 0 ? DEFAULT_INSTANCE_URL : url;
\t\tinstancePassword = instPass;
\t\tselfId = sid;
\t\tphoneCodeHash = null;
\t}

\t/** Rebuilds MPGram's account menu from the persisted account list. */
\tprivate static void showAccountsList() {
\t\tMPGramAccounts.ensureLoaded();
\t\taccountsList = new List(L[LMPGramAccounts], Choice.IMPLICIT);
\t\tint count = MPGramAccounts.count();
\t\tint active = MPGramAccounts.activeIndex();
\t\taccountListIndex = new int[count];
\t\tfor (int i = 0; i < count; i++) {
\t\t\taccountListIndex[i] = i;
\t\t\tString label = MPGramAccounts.label(i);
\t\t\tif (i == active) label += " (" + L[LMPGramAccountActive] + ")";
\t\t\taccountsList.append(label, null);
\t\t}
\t\taccountsList.addCommand(backCmd);
\t\taccountsList.addCommand(addAccountCmd);
\t\taccountsList.addCommand(importSessionCmd);
\t\taccountsList.addCommand(viewSessionCodeCmd);
\t\taccountsList.addCommand(renameAccountCmd);
\t\tif (count > 1) {
\t\t\taccountsList.addCommand(removeAccountCmd);
\t\t}
\t\taccountsList.setSelectCommand(List.SELECT_COMMAND);
\t\taccountsList.setCommandListener(midlet);
\t\tdisplay(accountsList);
\t}

\t/** Lets the account store copy MP's current credentials without exposing private fields. */
\tstatic void commitCurrentAccountState() {
\t\tMPGramAccounts.commitCurrent(user, userState, phone, instanceUrl,
\t\t\t\tinstancePassword, selfId);
\t}

\t/** True when the embedded MPGram instance is alive in this process. */
\tpublic static boolean isRunning() {
\t\treturn midlet != null;
\t}

\t/**
\t * Stops background work and releases the MPGram instance. The saved session
\t * stays in RMS, so the next open starts a fresh instance.
\t */
\tpublic static void kill() {
\t\tMP app = midlet;
\t\tif (app == null) return;
\t\tuser = null;
\t\tkeepAliveRunning = false;
\t\tauthenticationCancelled = true;
\t\tif (updatesThread != null) {
\t\t\ttry { app.cancel(updatesThread, true); } catch (Throwable ignored) {}
\t\t\tupdatesThread = null;
\t\t}
\t\ttry { closePlayer(); } catch (Throwable ignored) {}
\t\ttry { closeVoicePlayer(); } catch (Throwable ignored) {}
\t\ttry { closeVoiceRecorder(); } catch (Throwable ignored) {}
\t\tif (notificationPlayer != null) {
\t\t\ttry { notificationPlayer.stop(); } catch (Throwable ignored) {}
\t\t\ttry { notificationPlayer.close(); } catch (Throwable ignored) {}
\t\t\tnotificationPlayer = null;
\t\t}
\t\tif (needWriteConfig) {
\t\t\ttry { writeConfig(); } catch (Throwable ignored) {}
\t\t}
\t\tneedWriteConfig = false;
\t\tupdatesThread = null;
\t\tupdatesThreadCopy = null;
\t\tupdatesRunning = false;
\t\tupdatesSleeping = false;
\t\tsending = false;
\t\tdownloading = false;
\t\trun = 0;
\t\trunParam = null;
\t\tconfirmationTask = 0;
\t\tconfirmationParam = null;
\t\tusersCache.clear();
\t\tchatsCache.clear();
\t\timagesCache.clear();
\t\tformHistory.removeAllElements();
\t\timagesToLoad.removeAllElements();
\t\tthreadConnections.clear();
\t\tclosingConnections.removeAllElements();
\t\tpendingShareText = null;
\t\tuser = null;
\t\tinstancePassword = null;
\t\tselfId = null;
\t\tphone = null;
\t\tphoneCodeHash = null;
\t\tuserState = 0;
\t\tuserDefaultImg = null;
\t\tchatDefaultImg = null;
\t\tfileImg = null;
\t\tfolderImg = null;
\t\trootsList = null;
\t\tauthorizingAlert = null;
\t\tnotificationOpenCmd = null;
\t\tnotificationCloseCmd = null;
\t\tnotificationAlertPeerId = null;
\t\tnotificationReturnScreen = null;
\t\tsessionImportReturn = null;
\t\tsessionCodeBox = null;
\t\tsessionFileNameBox = null;
\t\tsessionImportAlert = null;
\t\tsessionExportAlert = null;
\t\tsessionExportDirectory = null;
\t\tsessionExportText = null;
\t\trichTextUrl = null;
\t\twriteTo = null;
\t\treplyTo = null;
\t\tsendFile = null;
\t\tedit = null;
\t\tfwdPeer = null;
\t\tfwdMsg = null;
\t\tupdateUrl = null;
\t\tlastType = 0;
\t\tdownloadMessage = null;
\t\tdownloadCurrentPath = null;
\t\tdownloadedPath = null;
\t\tplayerState = 0;
\t\tplaylist = null;
\t\tplaylistIndex = 0;
\t\tplaylistSize = 0;
\t\tplaylistOffset = 0;
\t\tplaylistPeer = null;
\t\tcurrentMusic = null;
\t\tcurrentPlayer = null;
\t\tvoicePeer = null;
\t\tvoiceFrom = null;
\t\tvoiceMessageId = 0;
\t\tvoiceState = 0;
\t\tvoiceDuration = 0;
\t\tvoicePlayer = null;
\t\trecordPlayer = null;
\t\trecordControl = null;
\t\trecordPath = null;
\t\tfileMode = 0;
\t\tmainDisplayable = null;
\t\tstartupForm = null;
\t\tstartupBackCmd = null;
\t\tchatsList = null;
\t\tfoldersList = null;
\t\tloadingForm = null;
\t\tsettingsForm = null;
\t\tauthForm = null;
\t\taccountsList = null;
\t\taccountListIndex = null;
\t\tauthFormReturn = null;
\t\tsessionCodeReturn = null;
\t\taccountNameBox = null;
\t\taccountRemoveAlert = null;
\t\ttokenAccountIndex = -2;
\t\twriteForm = null;
\t\tplayerForm = null;
\t\tplaylistList = null;
\t\tsettingsUiForm = null;
\t\tsettingsNotifForm = null;
\t\tsettingsBehForm = null;
\t\tsettingsAdvForm = null;
\t\tinstanceField = null;
\t\tinstancePasswordField = null;
\t\timagesChoice = null;
\t\tavaCacheChoice = null;
\t\tuiChoice = null;
\t\tbehChoice = null;
\t\tlangChoice = null;
\t\tchatsFontSizeCoice = null;
\t\tnetworkChoice = null;
\t\tplayMethodChoice = null;
\t\tplayerCreateMethodChoice = null;
\t\tadvChoice = null;
\t\tlegacyUiChoice = null;
\t\tavaCacheGauge = null;
\t\tphotoSizeGauge = null;
\t\tprofileCacheGauge = null;
\t\tchatsGauge = null;
\t\tmsgsGauge = null;
\t\tupdateTimeoutGauge = null;
\t\tstickerPreviewSizeGauge = null;
\t\tnotifyChoice = null;
\t\tnotifyMethodChoice = null;
\t\tpushIntervalGauge = null;
\t\tpushBgIntervalGauge = null;
\t\tnotificationVolumeGauge = null;
\t\tdownloadMethodChoice = null;
\t\tdownloadPathField = null;
\t\ttextMethodChoice = null;
\t\tthemeChoice = null;
\t\twallpaperPathField = null;
\t\tmessageField = null;
\t\tsendChoice = null;
\t\tfileLabel = null;
\t\tplayerTitleLabel = null;
\t\tplayerArtistLabel = null;
\t\tplayerProgress = null;
\t\tplayerVolumeGauge = null;
\t\tplayerPlaypauseBtn = null;
\t\tplayerCover = null;
\t\tvoiceAlert = null;
\t\tvoiceProgress = null;
\t\trecordAlert = null;
\t\tpendingShareText = null;
\t\tmidlet = null;
\t\thost = null;
\t\texitHandler = null;
\t\tcurrent = null;
\t\tdisplay = null;
\t\tL = null;
\t}

\tpublic static int getAccountCount() {
\t\tMPGramAccounts.ensureLoaded();
\t\treturn MPGramAccounts.count();
\t}

\tpublic static String getAccountLabel(int index) {
\t\tMPGramAccounts.ensureLoaded();
\t\treturn MPGramAccounts.label(index);
\t}

\tpublic static int getActiveAccountIndex() {
\t\tMPGramAccounts.ensureLoaded();
\t\treturn MPGramAccounts.activeIndex();
\t}

\t/** Switches the active account and opens MPGram with it. */
\tpublic static boolean switchAccount(int index, MIDlet app, Runnable onExit) {
\t\tMPGramAccounts.ensureLoaded();
\t\tif (index < 0 || index >= MPGramAccounts.count()) return false;
\t\tif (isRunning()) kill();
\t\tMPGramAccounts.select(index);
\t\tMPGramAccounts.applyActive();
\t\twriteAuth();
\t\tif (app != null) open(app, onExit);
\t\treturn true;
\t}

\t/** Starts a fresh login and stores it as an additional account on success. */
\tpublic static boolean addAccount(MIDlet app, Runnable onExit) {
\t\tif (isRunning()) kill();
\t\tMPGramAccounts.ensureLoaded();
\t\tMPGramAccounts.prepareNew();
\t\tMPGramAccounts.applyBlank();
\t\twriteAuth();
\t\tif (app != null) open(app, onExit);
\t\treturn true;
\t}

\t/** Removes one stored account. The remaining active account becomes the mirror in mp4user. */
\tpublic static boolean removeAccount(int index) {
\t\tMPGramAccounts.ensureLoaded();
\t\tif (!MPGramAccounts.remove(index)) return false;
\t\tif (isRunning()) kill();
\t\tMPGramAccounts.applyActive();
\t\twriteAuth();
\t\treturn true;
\t}

"""
    text = text.replace(anchor, hooks + anchor, 1)

    text = replace_once(
        text,
        "\tprivate static Command logoutCmd;",
        "\tprivate static Command logoutCmd;\n"
        "\tprivate static Command accountsCmd;\n"
        "\tprivate static Command addAccountCmd;\n"
        "\tprivate static Command importSessionCmd;\n"
        "\tprivate static Command viewSessionCodeCmd;\n"
        "\tprivate static Command removeAccountCmd;\n"
        "\tprivate static Command renameAccountCmd;\n"
        "\tprivate static Command accountNameSaveCmd;\n"
        "\tprivate static Command accountRemoveYesCmd;\n"
        "\tprivate static Command accountRemoveNoCmd;\n"
        "\tprivate static List accountsList;\n"
        "\tprivate static int[] accountListIndex;\n"
        "\tprivate static Displayable authFormReturn;\n"
        "\tprivate static Displayable sessionCodeReturn;\n"
        "\tprivate static TextBox accountNameBox;\n"
        "\tprivate static Alert accountRemoveAlert;\n"
        "\tprivate static int tokenAccountIndex = -2;",
        "account fields",
    )

    text = replace_once(
        text,
        """\tpublic static boolean hasSavedSession() {
\t\ttry {
\t\t\tRecordStore store = RecordStore.openRecordStore(AUTH_RECORD_NAME, false);
\t\t\ttry {
\t\t\t\tJSONObject session = parseObject(new String(store.getRecord(1), "UTF-8"));
\t\t\t\treturn session.getString("user", null) != null && session.getInt("state", 0) >= 3;
\t\t\t} finally {
\t\t\t\tstore.closeRecordStore();
\t\t\t}
\t\t} catch (Exception e) {
\t\t\treturn false;
\t\t}
\t}
""",
        """\tpublic static boolean hasSavedSession() {
\t\treturn MPGramAccounts.hasSavedSession();
\t}
""",
        "hasSavedSession",
    )

    text = replace_once(
        text,
        "\t\tlogoutCmd = new Command(L[LLogout], Command.ITEM, 1);",
        "\t\tlogoutCmd = new Command(L[LLogout], Command.ITEM, 1);\n"
        "\t\taccountsCmd = new Command(L[LMPGramAccounts], Command.SCREEN, 22);\n"
        "\t\taddAccountCmd = new Command(L[LMPGramAddAccount], Command.ITEM, 1);\n"
        "\t\timportSessionCmd = new Command(L[LImportSession], Command.ITEM, 2);\n"
        "\t\tviewSessionCodeCmd = new Command(L[LShowSessionCode], Command.ITEM, 3);\n"
        "\t\tremoveAccountCmd = new Command(L[LMPGramRemoveAccount], Command.ITEM, 4);\n"
        "\t\trenameAccountCmd = new Command(L[LMPGramRenameAccount], Command.ITEM, 5);\n"
        "\t\taccountNameSaveCmd = new Command(L[LOk], Command.OK, 1);\n"
        "\t\taccountRemoveYesCmd = new Command(L[LMPGramYes], Command.OK, 1);\n"
        "\t\taccountRemoveNoCmd = new Command(L[LMPGramNo], Command.CANCEL, 1);",
        "account commands",
    )

    text = replace_once(
        text,
        """\t\tf = new Form(L[LAuth_Title]);
\t\tf.addCommand(exitCmd);
\t\tf.addCommand(aboutCmd);
\t\tf.addCommand(settingsCmd);
\t\tf.setCommandListener(midlet);
""",
        """\t\tf = new Form(L[LAuth_Title]);
\t\tf.addCommand(exitCmd);
\t\tf.addCommand(aboutCmd);
\t\tf.addCommand(settingsCmd);
\t\tf.addCommand(backCmd);
\t\tf.setCommandListener(midlet);
""",
        "back command on auth form",
    )

    text = replace_once(
        text,
        "\t\t} catch (Exception ignored) {}\n\n\t\tif (user == null) {",
        "\t\t} catch (Exception ignored) {}\n\n"
        "\t\tMPGramAccounts.ensureLoaded();\n"
        "\t\tMPGramAccounts.applyActive();\n\n"
        "\t\tif (user == null) {",
        "constructor account load",
    )

    text = replace_once(
        text,
        """\t\t\t\tselfId = ((JSONObject) api("me&status=1")).getString("id");
\t\t\t\tif (authenticationCancelled) break;
\t\t\t\tuserState = 4;
""",
        """\t\t\t\tselfId = ((JSONObject) api("me&status=1")).getString("id");
\t\t\t\tif (authenticationCancelled) break;
\t\t\t\tuserState = 4;
\t\t\t\tMPGramAccounts.commitCurrent(user, userState, phone, instanceUrl,
\t\t\t\t\t\tinstancePassword, selfId);
\t\t\t\tauthFormReturn = null;
""",
        "validation commit",
    )

    text = replace_once(
        text,
        """\t\t\tif (user != null && selfId != null) {
\t\t\t\ttry {
\t\t\t\t\tMP.api("logout");
\t\t\t\t} catch (Throwable ignored) {}
\t\t\t}
\t\t\tuserState = 0;
""",
        """\t\t\tif (user != null && selfId != null) {
\t\t\t\ttry {
\t\t\t\t\tMP.api("logout");
\t\t\t\t} catch (Throwable ignored) {}
\t\t\t}
\t\t\tMPGramAccounts.removeActive();
\t\t\tuserState = 0;
""",
        "logout account removal",
    )

    text = replace_once(
        text,
        "\t\t\t\t\tif (AUTH_RECORD_NAME.equals(s[i])) continue;",
        "\t\t\t\t\tif (AUTH_RECORD_NAME.equals(s[i])\n"
        "\t\t\t\t\t\t\t|| MPGramAccounts.RECORD_NAME.equals(s[i])) continue;",
        "reset preserves accounts",
    )

    text = replace_once(
        text,
        """\t\t\t\t\t// authorization

\t\t\t\t\ts = new StringItem(null, L[LAuthorization]);
\t\t\t\t\ts.setLayout(Item.LAYOUT_CENTER | Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER);
\t\t\t\t\ts.setFont(medBoldFont);
\t\t\t\t\tf.append(new Spacer(8, 8));
\t\t\t\t\tf.append(s);

\t\t\t\t\ts = new StringItem(null, L[LShowSessionCode], Item.BUTTON);
\t\t\t\t\ts.setDefaultCommand(exportSessionCmd);
\t\t\t\t\ts.setItemCommandListener(this);
\t\t\t\t\ts.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER);
\t\t\t\t\tf.append(s);

\t\t\t\t\ts = new StringItem(null, L[LLogout], Item.BUTTON);
\t\t\t\t\ts.setDefaultCommand(logoutCmd);
\t\t\t\t\ts.setItemCommandListener(this);
\t\t\t\t\ts.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER);
\t\t\t\t\tf.append(s);

\t\t\t\t\tf.append(new Spacer(8, 8));
""",
        """\t\t\t\t\tf.append(new Spacer(8, 8));
""",
        "remove authorization from settings",
    )

    text = replace_once(
        text,
        "\t\tl.addCommand(settingsCmd);\n\t\tl.addCommand(contactsCmd);",
        "\t\tl.addCommand(settingsCmd);\n"
        "\t\tl.addCommand(accountsCmd);\n"
        "\t\tl.addCommand(contactsCmd);",
        "accounts command in main menu",
    )

    text = replace_once(
        text,
        "\t\t\tif (c == authImportSessionCmd || c == authNewSessionCmd || c == authQrCmd) {",
        "\t\t\tif (c == authImportSessionCmd || c == authNewSessionCmd || c == authQrCmd) {\n"
        "\t\t\t\tMPGramAccounts.prepareNew();",
        "new login pending",
    )

    text = replace_once(
        text,
        """\t\tuser = code;
\t\twriteAuth();
\t\tdisplay(returnTo);
""",
        """\t\tMPGramAccounts.prepareNew();
\t\tuser = code;
\t\twriteAuth();
\t\tdisplay(returnTo);
""",
        "import session pending",
    )

    text = replace_once(
        text,
        """\t\t\tif (d == sessionImportAlert && c == authImportSessionNoteCmd) {
\t\t\t\tsessionImportReturn = sessionCodeBox;
""",
        """\t\t\tif (d == sessionImportAlert && c == authImportSessionNoteCmd) {
\t\t\t\tsessionImportReturn = sessionCodeReturn == null ? sessionCodeBox : sessionCodeReturn;
""",
        "session import note returns to accounts",
    )

    text = replace_once(
        text,
        """\t\t\tif (d == sessionImportAlert && c == authImportSessionFileCmd) {
\t\t\t\tsessionImportReturn = sessionCodeBox;
""",
        """\t\t\tif (d == sessionImportAlert && c == authImportSessionFileCmd) {
\t\t\t\tsessionImportReturn = sessionCodeReturn == null ? sessionCodeBox : sessionCodeReturn;
""",
        "session import file returns to accounts",
    )

    text = replace_once(
        text,
        """\t\t\tif (d == sessionCodeBox && (c == backCmd || c == cancelCmd)) {
\t\t\t\tdisplay(settingsForm);
\t\t\t\treturn;
\t\t\t}
""",
        """\t\t\tif (d == sessionCodeBox && (c == backCmd || c == cancelCmd)) {
\t\t\t\tDisplayable back = sessionCodeReturn == null ? settingsForm : sessionCodeReturn;
\t\t\t\tsessionCodeReturn = null;
\t\t\t\ttokenAccountIndex = -2;
\t\t\t\tdisplay(back);
\t\t\t\treturn;
\t\t\t}
""",
        "session code back to accounts",
    )

    text = replace_once(
        text,
        """\t\t\tif (d == sessionExportAlert && c == exportSessionFileCmd) {
\t\t\t\tsessionExportText = sessionExportData(((TextBox) sessionCodeBox).getString());
""",
        """\t\t\tif (d == sessionExportAlert && c == exportSessionFileCmd) {
\t\t\t\tsessionExportText = tokenAccountIndex >= 0
\t\t\t\t\t\t? MPGramAccounts.exportData(tokenAccountIndex)
\t\t\t\t\t\t: sessionExportData(((TextBox) sessionCodeBox).getString());
""",
        "export stored account to file",
    )

    text = replace_once(
        text,
        """\t\t\tif (d == sessionExportAlert && c == exportSessionNoteCmd) {
\t\t\t\tsessionExportText = sessionExportData(((TextBox) sessionCodeBox).getString());
""",
        """\t\t\tif (d == sessionExportAlert && c == exportSessionNoteCmd) {
\t\t\t\tsessionExportText = tokenAccountIndex >= 0
\t\t\t\t\t\t? MPGramAccounts.exportData(tokenAccountIndex)
\t\t\t\t\t\t: sessionExportData(((TextBox) sessionCodeBox).getString());
""",
        "export stored account to note",
    )

    text = replace_once(
        text,
        """\t\t\t\tif (e.code == 401) {
\t\t\t\t\tif (param == null) {
""",
        """\t\t\t\tif (e.code == 401) {
\t\t\t\t\tMPGramAccounts.cancelPending();
\t\t\t\t\twriteAuth();
\t\t\t\t\tif (param == null) {
""",
        "failed validation restores account 401",
    )

    text = replace_once(
        text,
        """\t\t\tif (param == null) {
\t\t\t\tmainDisplayable = returnTo;
\t\t\t}
\t\t\talert.addCommand(authRetryCmd);
""",
        """\t\t\tMPGramAccounts.cancelPending();
\t\t\twriteAuth();
\t\t\tif (param == null) {
\t\t\t\tmainDisplayable = returnTo;
\t\t\t}
\t\t\talert.addCommand(authRetryCmd);
""",
        "failed validation restores account",
    )

    text = replace_once(
        text,
        """\t\t\tauthenticationCancelled = true;
\t\t\tbackgroundStart = true;
\t\t\tdestroyApp(true);
""",
        """\t\t\tMPGramAccounts.cancelPending();
\t\t\tauthenticationCancelled = true;
\t\t\tbackgroundStart = true;
\t\t\tdestroyApp(true);
""",
        "startup back restores pending login",
    )

    text = replace_once(
        text,
        """\t\t\tMPGramAccounts.cancelPending();
\t\t\tauthenticationCancelled = true;
\t\t\tbackgroundStart = true;
\t\t\tdestroyApp(true);
\t\t\treturn;
\t\t}
""",
        """\t\t\tMPGramAccounts.cancelPending();
\t\t\tauthenticationCancelled = true;
\t\t\tbackgroundStart = true;
\t\t\tdestroyApp(true);
\t\t\treturn;
\t\t}
\t\tif (d == authForm && c == backCmd) {
\t\t\tMPGramAccounts.cancelPending();
\t\t\tDisplayable back = authFormReturn;
\t\t\tauthFormReturn = null;
\t\t\tif (back == null) {
\t\t\t\tdestroyApp(true);
\t\t\t} else {
\t\t\t\tdisplay(back);
\t\t\t}
\t\t\treturn;
\t\t}
\t\tif (d == accountNameBox && c == accountNameSaveCmd) {
\t\t\tString name = accountNameBox.getString().trim();
\t\t\tMPGramAccounts.setName(MPGramAccounts.activeIndex(), name.length() == 0 ? null : name);
\t\t\taccountNameBox = null;
\t\t\tshowAccountsList();
\t\t\tdisplay(infoAlert(L[LMPGramAccountNameSaved]), accountsList);
\t\t\treturn;
\t\t}
\t\tif (d == accountNameBox && c == cancelCmd) {
\t\t\taccountNameBox = null;
\t\t\tdisplay(accountsList);
\t\t\treturn;
\t\t}
\t\tif (d == accountRemoveAlert) {
\t\t\tif (c == accountRemoveNoCmd) {
\t\t\t\taccountRemoveAlert = null;
\t\t\t\tdisplay(accountsList);
\t\t\t\treturn;
\t\t\t}
\t\t\tif (c == accountRemoveYesCmd) {
\t\t\t\taccountRemoveAlert = null;
\t\t\t\tMPGramAccounts.remove(MPGramAccounts.activeIndex());
\t\t\t\tMPGramAccounts.applyActive();
\t\t\t\twriteAuth();
\t\t\t\tshowAccountsList();
\t\t\t\treturn;
\t\t\t}
\t\t}
\t\tif (c == accountsCmd) {
\t\t\tMPGramAccounts.cancelPending();
\t\t\tshowAccountsList();
\t\t\treturn;
\t\t}
\t\tif (d == accountsList) {
\t\t\tif (c == backCmd) {
\t\t\t\taccountsList = null;
\t\t\t\taccountListIndex = null;
\t\t\t\tdisplay(mainDisplayable == null ? authForm : mainDisplayable);
\t\t\t\treturn;
\t\t\t}
\t\t\tif (c == addAccountCmd) {
\t\t\t\tMPGramAccounts.prepareNew();
\t\t\t\tMPGramAccounts.applyBlank();
\t\t\t\tauthFormReturn = accountsList;
\t\t\t\tif (instanceField != null) instanceField.setString(instanceUrl);
\t\t\t\tif (instancePasswordField != null) {
\t\t\t\t\tinstancePasswordField.setString(instancePassword == null ? "" : instancePassword);
\t\t\t\t}
\t\t\t\tdisplay(authForm);
\t\t\t\treturn;
\t\t\t}
\t\t\tif (c == importSessionCmd) {
\t\t\t\tMPGramAccounts.prepareNew();
\t\t\t\tsessionCodeBox = null;
\t\t\t\tsessionCodeReturn = accountsList;
\t\t\t\ttokenAccountIndex = -2;
\t\t\t\tshowSessionImportPrompt();
\t\t\t\treturn;
\t\t\t}
\t\t\tif (c == viewSessionCodeCmd) {
\t\t\t\tint index = MPGramAccounts.activeIndex();
\t\t\t\ttokenAccountIndex = index;
\t\t\t\tsessionCodeReturn = accountsList;
\t\t\t\tTextBox t = new TextBox(L[LShowSessionCode],
\t\t\t\t\t\tMPGramAccounts.sessionCode(index), 1024, TextField.NON_PREDICTIVE);
\t\t\t\tt.addCommand(backCmd);
\t\t\t\tt.addCommand(sessionCodeImportCmd);
\t\t\t\tt.addCommand(sessionCodeExportCmd);
\t\t\t\tt.setCommandListener(this);
\t\t\t\tsessionCodeBox = t;
\t\t\t\tdisplay(t);
\t\t\t\treturn;
\t\t\t}
\t\t\tif (c == renameAccountCmd) {
\t\t\t\tint index = MPGramAccounts.activeIndex();
\t\t\t\tString name = MPGramAccounts.name(index);
\t\t\t\taccountNameBox = new TextBox(L[LMPGramAccountName],
\t\t\t\t\t\tname == null ? "" : name, 64, TextField.ANY);
\t\t\t\taccountNameBox.addCommand(cancelCmd);
\t\t\t\taccountNameBox.addCommand(accountNameSaveCmd);
\t\t\t\taccountNameBox.setCommandListener(this);
\t\t\t\tdisplay(accountNameBox);
\t\t\t\treturn;
\t\t\t}
\t\t\tif (c == removeAccountCmd) {
\t\t\t\tif (MPGramAccounts.count() <= 1) {
\t\t\t\t\tdisplay(infoAlert(L[LMPGramAccountRemoveLast]), accountsList);
\t\t\t\t\treturn;
\t\t\t\t}
\t\t\t\tString label = MPGramAccounts.label(MPGramAccounts.activeIndex());
\t\t\t\taccountRemoveAlert = alert(L[LMPGramRemoveAccount],
\t\t\t\t\t\tlocalizeFormatted(LRemove_Alert, label), AlertType.WARNING);
\t\t\t\taccountRemoveAlert.setTimeout(Alert.FOREVER);
\t\t\t\taccountRemoveAlert.addCommand(accountRemoveYesCmd);
\t\t\t\taccountRemoveAlert.addCommand(accountRemoveNoCmd);
\t\t\t\taccountRemoveAlert.setCommandListener(this);
\t\t\t\tdisplay(accountRemoveAlert, accountsList);
\t\t\t\treturn;
\t\t\t}
\t\t\tif (c == List.SELECT_COMMAND) {
\t\t\t\tint pos = accountsList.getSelectedIndex();
\t\t\t\tint index = accountListIndex == null ? -1
\t\t\t\t\t\t: (pos >= 0 && pos < accountListIndex.length ? accountListIndex[pos] : -1);
\t\t\t\tif (index < 0) return;
\t\t\t\tif (index == MPGramAccounts.activeIndex()) {
\t\t\t\t\taccountsList = null;
\t\t\t\t\taccountListIndex = null;
\t\t\t\t\tdisplay(mainDisplayable == null ? authForm : mainDisplayable);
\t\t\t\t} else {
\t\t\t\t\tswitchAccount(index, host, exitHandler);
\t\t\t\t}
\t\t\t}
\t\t}
""",
        "accounts command handling",
    )

    text = replace_once(
        text,
        """\t\tif (c == exitCmd) {
\t\t\tdestroyApp(true);
\t\t}
""",
        """\t\tif (c == exitCmd) {
\t\t\tMPGramAccounts.cancelPending();
\t\t\twriteAuth();
\t\t\tdestroyApp(true);
\t\t}
""",
        "exit cancels pending account",
    )

    text = replace_once(
        text,
        """\t\t} catch (Exception ignored) {}
\t}

\tprivate static void writeConfig() throws Exception {
""",
        """\t\t} catch (Exception ignored) {}
\t\tMPGramAccounts.syncFromMP();
\t}

\tprivate static void writeConfig() throws Exception {
""",
        "writeAuth account sync",
    )

    text = replace_once(
        text,
        """\t\ttry {
\t\t\tRecordStore.deleteRecordStore(AUTH_RECORD_NAME);
\t\t} catch (Exception ignored) {}
""",
        """\t\ttry {
\t\t\tRecordStore.deleteRecordStore(AUTH_RECORD_NAME);
\t\t} catch (Throwable ignored) {}
""",
        "writeAuth delete tolerates security errors",
    )

    text = replace_once(
        text,
        """\t\t\tRecordStore r = RecordStore.openRecordStore(AUTH_RECORD_NAME, true);
\t\t\ttry {
\t\t\t\tr.addRecord(b, 0, b.length);
\t\t\t} finally {
\t\t\t\tr.closeRecordStore();
\t\t\t}
\t\t} catch (Exception ignored) {}
""",
        """\t\t\tRecordStore r = RecordStore.openRecordStore(AUTH_RECORD_NAME, true);
\t\t\ttry {
\t\t\t\tr.addRecord(b, 0, b.length);
\t\t\t} finally {
\t\t\t\tr.closeRecordStore();
\t\t\t}
\t\t} catch (Throwable ignored) {}
""",
        "writeAuth store tolerates security errors",
    )

    return text


ACCOUNTS_CLASS = """package mpgram;

import java.util.Vector;
import javax.microedition.rms.RecordStore;

/**
 * Persistent MPGram account list. Only one account is materialized in MP's static
 * fields at a time; mp4user stays as a mirror of the active account for old builds.
 */
final class MPGramAccounts {
    static final String RECORD_NAME = "mp4accounts";

    private static final Vector accounts = new Vector();
    private static int activeIndex;
    private static boolean loaded;
    private static boolean pendingNew;

    private MPGramAccounts() {}

    static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            RecordStore store = RecordStore.openRecordStore(RECORD_NAME, false);
            try {
                if (store.getNumRecords() > 0) {
                    JSONObject root = MP.parseObject(new String(store.getRecord(1), "UTF-8"));
                    JSONArray saved = root.getArray("accounts", null);
                    if (saved != null) {
                        for (int i = 0; i < saved.size(); i++) {
                            accounts.addElement(saved.getObject(i, new JSONObject()));
                        }
                    }
                    activeIndex = root.getInt("active", 0);
                }
            } finally {
                store.closeRecordStore();
            }
        } catch (Throwable ignored) {}

        if (accounts.size() == 0) {
            migrateLegacySession();
        }
        if (activeIndex < 0 || activeIndex >= accounts.size()) {
            activeIndex = 0;
        }
    }

    /** Converts the old single-session mp4user record into the first account. */
    private static void migrateLegacySession() {
        try {
            RecordStore store = RecordStore.openRecordStore(MP.AUTH_RECORD_NAME, false);
            try {
                if (store.getNumRecords() == 0) return;
                JSONObject session = MP.parseObject(new String(store.getRecord(1), "UTF-8"));
                String user = session.getNullableString("user");
                if (user == null || user.length() == 0) return;
                JSONObject account = new JSONObject();
                account.put("user", user);
                account.put("state", session.getInt("state", 0));
                account.put("phone", session.getNullableString("phone"));
                account.put("url", session.getNullableString("url"));
                account.put("instPass", session.getNullableString("instPass"));
                account.put("selfId", session.getNullableString("selfId"));
                accounts.addElement(account);
                activeIndex = 0;
                save();
            } finally {
                store.closeRecordStore();
            }
        } catch (Throwable ignored) {}
    }

    static int count() {
        return accounts.size();
    }

    static int activeIndex() {
        ensureLoaded();
        return activeIndex;
    }

    static JSONObject active() {
        ensureLoaded();
        if (accounts.size() == 0) return null;
        if (activeIndex < 0 || activeIndex >= accounts.size()) activeIndex = 0;
        return (JSONObject) accounts.elementAt(activeIndex);
    }

    static boolean hasSavedSession() {
        JSONObject account = active();
        if (account == null) return false;
        String user = account.getNullableString("user");
        return user != null && user.length() > 0 && account.getInt("state", 0) >= 3;
    }

    static String label(int index) {
        ensureLoaded();
        if (index < 0 || index >= accounts.size()) return "";
        JSONObject account = (JSONObject) accounts.elementAt(index);
        String name = account.getNullableString("name");
        String phone = account.getNullableString("phone");
        String user = account.getNullableString("user");
        String label = name != null && name.length() > 0 ? name
                : (phone != null && phone.length() > 0 ? phone : user);
        if (label == null || label.length() == 0) {
            label = "MPGram " + (index + 1);
        } else if (label.length() > 40) {
            label = label.substring(0, 40) + "...";
        }
        return label;
    }

    /** User-defined display name for an account. */
    static String name(int index) {
        ensureLoaded();
        if (index < 0 || index >= accounts.size()) index = activeIndex;
        JSONObject account = index < 0 || index >= accounts.size()
                ? null : (JSONObject) accounts.elementAt(index);
        return account == null ? null : account.getNullableString("name");
    }

    static void setName(int index, String name) {
        ensureLoaded();
        if (index < 0 || index >= accounts.size()) index = activeIndex;
        JSONObject account = index < 0 || index >= accounts.size()
                ? null : (JSONObject) accounts.elementAt(index);
        if (account == null) return;
        account.put("name", name);
        save();
    }

    /** Session token stored for an account, without making it active. */
    static String sessionCode(int index) {
        ensureLoaded();
        if (index < 0 || index >= accounts.size()) index = activeIndex;
        JSONObject account = index < 0 || index >= accounts.size()
                ? null : (JSONObject) accounts.elementAt(index);
        return account == null ? "" : account.getNullableString("user");
    }

    /** Exportable session bundle for an account without switching to it. */
    static String exportData(int index) {
        ensureLoaded();
        if (index < 0 || index >= accounts.size()) index = activeIndex;
        JSONObject account = index < 0 || index >= accounts.size()
                ? null : (JSONObject) accounts.elementAt(index);
        JSONObject exported = new JSONObject();
        exported.put("format", "MPGram-session-v1");
        exported.put("user", account == null ? "" : account.getNullableString("user"));
        exported.put("url", account == null ? null : account.getNullableString("url"));
        exported.put("instPass", account == null ? null : account.getNullableString("instPass"));
        return exported.toString();
    }

    static boolean select(int index) {
        ensureLoaded();
        if (index < 0 || index >= accounts.size()) return false;
        pendingNew = false;
        activeIndex = index;
        save();
        return true;
    }

    /** Applies the active account to MP's request state. */
    static void applyActive() {
        if (pendingNew) {
            applyBlank();
            return;
        }
        JSONObject account = active();
        if (account == null) {
            applyBlank();
            return;
        }
        MP.applyAccountSnapshot(
                account.getNullableString("user"),
                account.getInt("state", 0),
                account.getNullableString("phone"),
                account.getNullableString("url"),
                account.getNullableString("instPass"),
                account.getNullableString("selfId"));
    }

    static void applyBlank() {
        MP.applyAccountSnapshot(null, 0, null, MP.DEFAULT_INSTANCE_URL, null, null);
    }

    /** Marks the next successful validation as a new account instead of updating the active one. */
    static void prepareNew() {
        ensureLoaded();
        pendingNew = true;
    }

    static void cancelPending() {
        ensureLoaded();
        if (!pendingNew) return;
        pendingNew = false;
        applyActive();
    }

    /** Saves whatever MP currently holds into the active account entry. */
    static void syncFromMP() {
        ensureLoaded();
        if (pendingNew) return;
        MP.commitCurrentAccountState();
    }

    static void commitCurrent(String user, int state, String phone, String url,
            String instPass, String selfId) {
        ensureLoaded();
        if (user == null || user.length() == 0) return;
        int match = find(user, url);
        String name = match < 0 ? null
                : ((JSONObject) accounts.elementAt(match)).getNullableString("name");
        JSONObject account = new JSONObject();
        account.put("user", user);
        account.put("state", state);
        account.put("phone", phone);
        account.put("url", url);
        account.put("instPass", instPass);
        account.put("selfId", selfId);
        account.put("name", (String) name);

        if (match < 0) {
            accounts.addElement(account);
            activeIndex = accounts.size() - 1;
        } else {
            accounts.setElementAt(account, match);
            activeIndex = match;
        }
        pendingNew = false;
        save();
    }

    /** Clears the active account's credentials, used by MPGram's Log out command. */
    static void removeActive() {
        ensureLoaded();
        if (accounts.size() > 1) {
            remove(activeIndex);
            return;
        }
        JSONObject account = active();
        if (account == null) return;
        account.put("user", (String) null);
        account.put("state", 0);
        account.put("phone", (String) null);
        account.put("selfId", (String) null);
        save();
    }

    static boolean remove(int index) {
        ensureLoaded();
        if (accounts.size() <= 1 || index < 0 || index >= accounts.size()) return false;
        accounts.removeElementAt(index);
        if (activeIndex >= accounts.size()) activeIndex = accounts.size() - 1;
        else if (activeIndex > index) activeIndex--;
        save();
        return true;
    }

    private static int find(String user, String url) {
        for (int i = 0; i < accounts.size(); i++) {
            JSONObject account = (JSONObject) accounts.elementAt(i);
            String savedUser = account.getNullableString("user");
            String savedUrl = account.getNullableString("url");
            if (same(user, savedUser) && same(url, savedUrl)) return i;
        }
        return -1;
    }

    private static boolean same(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    static synchronized void save() {
        try {
            JSONObject root = new JSONObject();
            root.put("v", 1);
            root.put("active", activeIndex);
            JSONArray array = new JSONArray(accounts.size());
            for (int i = 0; i < accounts.size(); i++) {
                array.add((JSONObject) accounts.elementAt(i));
            }
            root.put("accounts", array);
            byte[] data = root.toString().getBytes("UTF-8");
            try {
                RecordStore.deleteRecordStore(RECORD_NAME);
            } catch (Throwable ignored) {}
            RecordStore store = RecordStore.openRecordStore(RECORD_NAME, true);
            try {
                store.addRecord(data, 0, data.length);
            } finally {
                store.closeRecordStore();
            }
        } catch (Throwable ignored) {}
    }
}

"""
def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: prepare_mpgram.py <mpgram-dir> <output-dir>")
    mpgram = Path(sys.argv[1])
    destination = Path(sys.argv[2])
    resources = destination.parent / "mpgram-res"
    source_dir = mpgram / "src"
    if not (source_dir / "MP.java").is_file():
        raise SystemExit("MPGram source is missing. Initialize third_party/mpgram first.")
    shutil.rmtree(destination, ignore_errors=True)
    shutil.rmtree(resources, ignore_errors=True)
    destination.mkdir(parents=True)
    shutil.copytree(mpgram / "res", resources)
    for source_file in source_dir.rglob("*.java"):
        prepared = preprocess(source_file.read_text(encoding="utf-8"))
        if source_file.name == "MP.java":
            prepared = patch_mp(prepared)
        target = destination / source_file.relative_to(source_dir)
        target.parent.mkdir(parents=True, exist_ok=True)
        package = "" if source_file.parent.name == "zip" else "package mpgram;\n\n"
        target.write_text(package + prepared, encoding="utf-8")
    language_constants(mpgram, destination, resources)
    (destination / "MPGramAccounts.java").write_text(ACCOUNTS_CLASS, encoding="utf-8")


if __name__ == "__main__":
    main()
