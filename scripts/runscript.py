#!/usr/bin/env python3
"""Check the Termux runner script without a phone and without a build.

`app/src/main/assets/run.sh` travels inside the APK and is handed to Termux as the argument of every
command — the single most failure-prone artefact in this repo, and the one CI cannot see, because a
broken shell script is still a perfectly good asset.

This runs it through `sh -n`, checks that every action the app can send is handled, and prints the
sha256 of the exact bytes the app will hand over, so "installed and it works" can be checked against
a build.

Usage:
    python3 scripts/runscript.py [--dump <file>]

Exit code 0 means: the file exists, `sh -n` accepts it, and every action is in the case table.
"""

import argparse
import hashlib
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SCRIPT = REPO / "app/src/main/assets/run.sh"

# Every action TermuxController can hand to the script. A missing one is a runtime "未知动作".
ACTIONS = ["probe", "start", "stop", "log", "install", "shell"]

failures = []


def fail(message):
    failures.append(message)
    print(f"  FAIL {message}")


def ok(message):
    print(f"  ok   {message}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dump", help="write a copy here for hand-running")
    args = parser.parse_args()

    if not SCRIPT.is_file():
        print(f"missing {SCRIPT}")
        return 1
    script = SCRIPT.read_text(encoding="utf-8")

    if args.dump:
        Path(args.dump).write_text(script, encoding="utf-8")

    print(f"run.sh: {len(script.splitlines())} lines, sha256 {hashlib.sha256(script.encode()).hexdigest()[:16]}")

    result = subprocess.run(["sh", "-n"], input=script, text=True, capture_output=True)
    if result.returncode != 0:
        fail(f"sh -n rejected the script:\n{result.stderr.strip()}")
    else:
        ok("sh -n accepts the script")

    case_block = script.split("case ", 1)[-1] if "case " in script else ""
    for action in ACTIONS:
        if re.search(rf"^\s*{action}\)", case_block, re.MULTILINE):
            ok(f"action {action} present")
        else:
            fail(f"action {action} missing from the case table")

    # The bridge hands this file over verbatim and then calls `run.sh <action>`; both halves of that
    # arrangement are asserted here, because either one alone looks fine.
    if "run.sh" in script:
        ok("mentions run.sh")
    else:
        fail("never mentions run.sh")
    for name in ("install-dsh.sh", "web.log"):
        if name in script:
            ok(f"mentions {name}")
        else:
            fail(f"{name} never mentioned")

    # A Kotlin string used to hold this script, and every `$` in it had to be escaped; the file is an
    # asset now precisely so that stops being true. Catch a regression to the placeholder spelling.
    if "DOLLAR" in script:
        fail("script contains the §DOLLAR§ placeholder — it is an asset now, dollars must be plain")
    else:
        ok("no escaping leftovers")

    print("RUNSCRIPT PASSED" if not failures else f"RUNSCRIPT FAILED ({len(failures)})")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
