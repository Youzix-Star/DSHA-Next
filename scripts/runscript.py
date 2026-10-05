#!/usr/bin/env python3
"""Check the Termux runner script without a phone and without a build.

`TermuxBridge.runnerScript` is a Kotlin raw string that is base64'd into the APK and written into
Termux's home at runtime — the single most failure-prone artefact in this repo, and the one CI
cannot see, because a broken script is still a compiling Kotlin file.

This pulls the string back out of the Kotlin source, undoes `trimIndent()` the way the Kotlin
compiler would, and hands the result to `sh -n`. It also prints the sha256 of the exact script the
app will install, so a claimed "installed and it works" can be checked against a build.

Usage:
    python3 scripts/runscript.py [--dump <file>]

Exit code 0 means: the string is extractable, `sh -n` accepts it, and every action the app can
send (`probe`, `start`, `stop`, `log`, `install`, `shell`) appears in the case table.
"""

import argparse
import hashlib
import re
import subprocess
import sys
import textwrap
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SOURCE = REPO / "app/src/main/java/top/youzix/dsha/termux/TermuxBridge.kt"

# Every action TermuxController can hand to the script. A missing one is a runtime "未知动作".
ACTIONS = ["version", "probe", "start", "stop", "log", "install", "shell"]

failures = []


def fail(message):
    failures.append(message)
    print(f"  FAIL {message}")


def ok(message):
    print(f"  ok   {message}")


def extract(source: str):
    """Return the runner script as the Kotlin compiler would see it."""
    anchor = source.index("val runnerScript")
    start = source.index('"""', anchor) + 3
    end = source.index('"""', start)
    raw = source[start:end]
    # `trimIndent()`: drop the first line if it only exists because of the opening quotes, remove
    # the longest common leading whitespace of the remaining non-blank lines, and drop the blank
    # lines at both ends.
    lines = raw.split("\n")
    if lines and lines[0].strip() == "":
        lines = lines[1:]
    while lines and lines[-1].strip() == "":
        lines.pop()
    return textwrap.dedent("\n".join(lines)) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dump", help="write the extracted script here for inspection")
    args = parser.parse_args()

    source = SOURCE.read_text(encoding="utf-8")
    try:
        script = extract(source)
    except ValueError as error:
        print(f"cannot extract runnerScript: {error}")
        return 1

    if args.dump:
        Path(args.dump).write_text(script, encoding="utf-8")

    print(f"runner script: {len(script.splitlines())} lines, "
          f"sha256 {hashlib.sha256(script.encode()).hexdigest()[:16]}")

    if script.startswith("#!/") is False:
        fail("script does not start with a shebang")
    else:
        shebang = script.splitlines()[0]
        if not shebang.startswith("#!/data/data/com.termux/files/usr/bin/"):
            fail(f"shebang is not a Termux path: {shebang}")
        else:
            ok(f"shebang {shebang}")

    # `sh -n` is the real check: the app runs this file with Termux's sh, so any syntax it rejects
    # is a script that fails on the device and nowhere else.
    result = subprocess.run(["sh", "-n"], input=script, text=True, capture_output=True)
    if result.returncode != 0:
        fail(f"sh -n rejected the script:\n{result.stderr.strip()}")
    else:
        ok("sh -n accepts the script")

    case_block = script.split("case ", 1)[-1]
    for action in ACTIONS:
        if re.search(rf"^\s*{action}\)", case_block, re.MULTILINE):
            ok(f"action {action} present")
        else:
            fail(f"action {action} missing from the case table")

    # The base64 install path writes these two names; a rename in one place and not the other is a
    # 准备 that reports success and leaves nothing behind.
    for name in ("run.sh", "install-dsh.sh"):
        if name in script:
            ok(f"mentions {name}")
        else:
            fail(f"{name} never mentioned")

    print("RUNSCRIPT PASSED" if not failures else f"RUNSCRIPT FAILED ({len(failures)})")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
