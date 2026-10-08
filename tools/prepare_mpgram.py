#!/usr/bin/env python3
"""Prepare the generic MPGram source for inclusion in the MIDPlay JAR."""

import json
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
    locale_dir.mkdir(parents=True)
    for locale_file in (mpgram / "langs").glob("*.jsonc"):
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


def adapt_midlet(source):
    source = source.replace("public class MP extends MIDlet", "public class MP")
    source = source.replace("static MP midlet;", "static MP midlet;\n\tprivate static MIDlet host;\n\tprivate static Runnable exitHandler;")
    source = source.replace(
        "static boolean paused;",
        "static boolean paused;\n\tprivate static boolean authenticationCancelled;\n\tprivate static Alert authorizingAlert;",
    )
    source = source.replace(
        "\t// region MIDlet\n",
        "\tpublic static void open(MIDlet app, Runnable onExit) {\n"
        "\t\thost = app;\n\t\texitHandler = onExit;\n"
        "\t\tif (midlet == null) new MP().start();\n"
        "\t\telse display.setCurrent(current == null ? mainDisplayable : current);\n"
        "\t}\n\n\t// region MIDlet\n",
        1,
    )
    source = source.replace("\t\tnotifyDestroyed();", "\t\tif (exitHandler != null) exitHandler.run();")
    source = source.replace("\tprotected void startApp()  {", "\tprivate void start()  {")
    source = source.replace("getAppProperty(", "host.getAppProperty(")
    source = source.replace("Display.getDisplay(this)", "Display.getDisplay(host)")
    source = source.replace("midlet.host.getAppProperty(", "host.getAppProperty(")
    source = source.replace("platformRequest(url)", "host.platformRequest(url)")
    source = source.replace("PlayerListener.STOPPED_AT_TIME", '"stoppedAtTime"')
    source = source.replace(
        "\t\t// sanity check\n\t\tif (!\"nnproject\".equals(host.getAppProperty(\"MIDlet-Vendor\"))\n"
        "\t\t\t\t|| checkClass(\"javay.microedition.lcdui.Canvas\"))\n\t\t\tthrow new RuntimeException();\n\n",
        "",
    )
    source = source.replace(
        "\t\tcase RUN_VALIDATE_AUTH: {\n\t\t\tString s = instanceUrl;",
        "\t\tcase RUN_VALIDATE_AUTH: {\n\t\t\tauthenticationCancelled = false;\n\t\t\tString s = instanceUrl;",
    )
    source = source.replace(
        "\t\t\tAlert alert = loadingAlert(L[LAuthorizing]);\n\t\t\tif (param == null) {\n\t\t\t\talert.addCommand(exitCmd);",
        "\t\t\tAlert alert = loadingAlert(L[LAuthorizing]);\n\t\t\tif (param == null) {\n"
        "\t\t\t\tauthorizingAlert = alert;\n\t\t\t\talert.addCommand(exitCmd);\n"
        "\t\t\t\talert.addCommand(logoutCmd);",
    )
    source = source.replace(
        "\t\t\t\tselfId = ((JSONObject) api(\"me&status=1\")).getString(\"id\");",
        "\t\t\t\tselfId = ((JSONObject) api(\"me&status=1\")).getString(\"id\");\n"
        "\t\t\t\tif (authenticationCancelled) break;",
    )
    source = source.replace(
        "\t\t\t} catch (APIException e) {\n\t\t\t\tif (e.code == 401) {",
        "\t\t\t} catch (APIException e) {\n\t\t\t\tif (authenticationCancelled) break;\n\t\t\t\tif (e.code == 401) {",
    )
    source = source.replace(
        "\t\t\t} catch (Exception e) {\n\t\t\t\talert = errorAlert(e);\n\t\t\t}",
        "\t\t\t} catch (Exception e) {\n\t\t\t\tif (authenticationCancelled) break;\n\t\t\t\talert = errorAlert(e);\n\t\t\t}",
        1,
    )
    source = source.replace(
        "\tpublic void commandAction(Command c, Displayable d) {",
        "\tpublic void commandAction(Command c, Displayable d) {\n"
        "\t\tif (c == logoutCmd && d == authorizingAlert) {\n"
        "\t\t\tauthenticationCancelled = true;\n"
        "\t\t\tuserState = 0;\n\t\t\tuser = phone = selfId = null;\n"
        "\t\t\twriteAuth();\n\t\t\tcurrent = mainDisplayable = authForm;\n"
        "\t\t\tdestroyApp(true);\n\t\t\treturn;\n\t\t}\n",
        1,
    )
    return source


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
            prepared = adapt_midlet(prepared)
        target = destination / source_file.relative_to(source_dir)
        target.parent.mkdir(parents=True, exist_ok=True)
        package = "" if source_file.parent.name == "zip" else "package mpgram;\n\n"
        target.write_text(package + prepared, encoding="utf-8")
    language_constants(mpgram, destination, resources)


if __name__ == "__main__":
    main()
