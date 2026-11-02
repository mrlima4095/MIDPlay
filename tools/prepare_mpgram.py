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
        if base:
            output.append("/l/" + base)
            for index, key in enumerate(messages, 1):
                if key in locale:
                    output.append(str(index) + ":" + escape_locale(locale[key]))
        else:
            for key, value in messages.items():
                output.append(escape_locale(locale.get(key, value)))
        locale_dir.joinpath(locale_file.stem).write_text("\n".join(output) + "\n", encoding="utf-8")


def patch_mp(text):
    """Add the htop lifecycle hooks used by MIDPlay's subapp manager."""
    anchor = "\tprivate void closeHost() {"
    if anchor not in text:
        raise SystemExit("MPGram closeHost() changed upstream")

    hooks = """\t/** True when the embedded MPGram instance is alive in this process. */
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

"""
    return text.replace(anchor, hooks + anchor, 1)


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


if __name__ == "__main__":
    main()
