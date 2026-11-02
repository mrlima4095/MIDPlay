#!/usr/bin/env python3
"""Package GH2ME source and locale assets in MIDPlay-private namespaces."""

import json
import re
import shutil
import sys
from pathlib import Path


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


def patch_gh(text):
    """Add the htop lifecycle hooks used by MIDPlay's subapp manager."""
    anchor = "\tprivate void start() {"
    if anchor not in text:
        raise SystemExit("GH start() changed upstream")

    hooks = """\t/** True when the embedded GitHub client is alive in this process. */
\tpublic static boolean isRunning() {
\t\treturn midlet != null;
\t}

\t/** Stops background work and releases the GitHub client. Settings stay saved. */
\tpublic static void kill() {
\t\tif (midlet == null) return;
\t\tif (needWriteConfig) {
\t\t\ttry { writeConfig(); } catch (Throwable ignored) {}
\t\t}
\t\tneedWriteConfig = false;
\t\ttry { stopOauthServer(); } catch (Throwable ignored) {}
\t\toauthSocket = null;
\t\toauthStarted = false;
\t\toauthThread = null;
\t\toauthMode = 0;
\t\toauthUrl = null;
\t\tgithubAccessToken = null;
\t\tgithubAccessTokenTime = 0;
\t\tgiteaAccessToken = null;
\t\tgiteaRefreshToken = null;
\t\tgiteaAccessTokenTime = 0;
\t\tgiteaRefreshTokenTime = 0;
\t\tlogin = null;
\t\trun = 0;
\t\trunParam = null;
\t\tconfirmationTask = 0;
\t\tconfirmationParam = null;
\t\tformHistory.removeAllElements();
\t\tthumbsToLoad.removeAllElements();
\t\tbookmarks = null;
\t\tfileImg = null;
\t\tfolderImg = null;
\t\trootsList = null;
\t\tdownloadReturn = null;
\t\tdownloadFile = null;
\t\tdownloadCurrentPath = null;
\t\tdownloadedPath = null;
\t\ttokenImportReturn = null;
\t\trepo = null;
\t\tref = null;
\t\tinSourceBrowser = false;
\t\tdiscardSettingsPrompt = false;
\t\tmainForm = null;
\t\tsettingsForm = null;
\t\tsearchForm = null;
\t\tbookmarksList = null;
\t\tloadingForm = null;
\t\tmainField = null;
\t\tproxyField = null;
\t\tbrowseProxyField = null;
\t\tproxyChoice = null;
\t\tmodeChoice = null;
\t\tlegacyUIChoice = null;
\t\tcanvasFullscreenChoice = null;
\t\tcustomApiField = null;
\t\tnetworkChoice = null;
\t\tdownloadMethodChoice = null;
\t\tdownloadPathField = null;
\t\tsearchField = null;
\t\tsearchChoice = null;
\t\tauthField = null;
\t\tclientIdField = null;
\t\tclientSecretField = null;
\t\tcurrent = null;
\t\tmidlet = null;
\t\thost = null;
\t\texitHandler = null;
\t\tdisplay = null;
\t\tL = null;
\t}

"""
    return text.replace(anchor, hooks + anchor, 1)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: prepare_github_j2me.py <source-dir> <output-dir>")
    root = Path(sys.argv[1])
    output = Path(sys.argv[2])
    source = root / "src"
    shutil.rmtree(output, ignore_errors=True)
    output.mkdir(parents=True)

    for java_file in source.rglob("*.java"):
        relative = java_file.relative_to(source)
        text = java_file.read_text(encoding="utf-8")
        if relative.parts[:3] == ("cc", "nnproject", "json"):
            # Reuse MIDPlay's JSON object model. GH2ME contributes only its
            # streaming reader, adapted to the shared parser's sentinels/API.
            if java_file.name != "JSONStream.java":
                continue
            text = text.replace("JSONObject.json_null", "JSON.json_null")
            text = text.replace("JSONObject.TRUE", "JSON.TRUE")
            text = text.replace("JSONObject.FALSE", "JSON.FALSE")
            target = output / "JSONStream.java"
        else:
            if java_file.name != "CanvasForm.java":
                text = re.sub(
                    r"^(\s*)(\w+)\.setDefaultCommand\((.+)\);$",
                    r"\1CanvasForm.bindDefaultCommand(\2, \3);",
                    text,
                    flags=re.MULTILINE,
                )
                text = text.replace("new ChoiceGroup(", "CanvasForm.choiceGroup(")
            text = text.replace("/file.png", "/gh2me-file.png")
            text = text.replace("/folder.png", "/gh2me-folder.png")
            text = text.replace('getResourceAsStream("/l/"', 'getResourceAsStream("/gh2me/l/"')
            text = "package githubj2me;\n\n" + text
            if java_file.name == "GH.java":
                text = patch_gh(text)
            target = output / java_file.name
        target.write_text(text, encoding="utf-8")

    messages = load_jsonc(root / "langs" / "en.jsonc")
    constants = ["package githubj2me;", "", "public interface LangConstants {"]
    for index, key in enumerate(messages, 1):
        constants.append("  int L%s = %d;" % (key, index))
    constants.extend(["  int LLocaleStrings = %d;" % len(messages), "  int Lgh2me = 0;", "}"])
    output.joinpath("LangConstants.java").write_text("\n".join(constants) + "\n", encoding="utf-8")

    locale_dir = output / "gh2me" / "l"
    locale_dir.mkdir(parents=True, exist_ok=True)
    for locale_file in (root / "langs").glob("*.jsonc"):
        locale = load_jsonc(locale_file)
        values = [escape_locale(locale.get(key, value)) for key, value in messages.items()]
        locale_dir.joinpath(locale_file.stem).write_text("\n".join(values) + "\n", encoding="utf-8")

    for asset, target in (
        ("g.png", "g.png"),
        ("g.svg", "g.svg"),
        ("file.png", "gh2me-file.png"),
        ("folder.png", "gh2me-folder.png"),
    ):
        shutil.copy2(root / "res" / asset, output / target)


if __name__ == "__main__":
    main()
