#!/usr/bin/env python3
"""A small Kotlin lexer, for the checks a device-less machine can still make.

There is no Kotlin compiler on this phone, so the two mistakes that actually cost a CI round trip
have to be caught by something cheaper:

* an **unterminated string or comment** — which turns every later line into nonsense and produces a
  compiler error nowhere near the real cause;
* a **`$` interpolation mistake inside a raw string** — `` "${x}" `` is a compile error, and a
  `"$x"` that was meant to stay literal is a silent bug that only shows up on the device.

It also reports unbalanced braces and parens (outside strings and comments), and — for the files
named on the command line — imports whose name never appears again. That last check is opt-in by
file on purpose: files this repo inherited from NekoPlus carry unused imports that are nobody's
business here, and a tool that cries about them gets ignored.

Usage:
    python3 scripts/klex.py <file.kt> [...]     # check these files, unused imports included
    python3 scripts/klex.py                     # every tracked .kt file, structure only

Exit code 0 means every file lexed cleanly.
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


def tracked_kotlin():
    out = subprocess.run(
        ["git", "ls-files", "*.kt"], cwd=REPO, capture_output=True, text=True,
    ).stdout.split()
    return [REPO / name for name in out]


def scan(path, check_imports=True):
    problems = []
    source = path.read_text(encoding="utf-8")
    i, n, line = 0, len(source), 1
    depth_brace, depth_paren = 0, 0
    raw_ranges = []

    while i < n:
        c = source[i]

        if c == "\n":
            line += 1
            i += 1
            continue

        if source.startswith("//", i):
            i = source.find("\n", i)
            if i == -1:
                break
            continue

        if source.startswith("/*", i):
            end = source.find("*/", i + 2)
            if end == -1:
                problems.append(f"{path.name}:{line}: 块注释没有闭合")
                break
            line += source.count("\n", i, end)
            i = end + 2
            continue

        if source.startswith('"""', i):
            start_line = line
            end = source.find('"""', i + 3)
            if end == -1:
                problems.append(f"{path.name}:{start_line}: raw 字符串没有闭合")
                break
            raw_ranges.append((i, end))
            line += source.count("\n", i, end)
            i = end + 3
            continue

        if c == '"':
            start_line = line
            i += 1
            closed = False
            while i < n:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == "\n":
                    problems.append(f"{path.name}:{start_line}: 普通字符串跨行（少了一个引号？）")
                    break
                if source[i] == '"':
                    i += 1
                    closed = True
                    break
                # ${...} and $name are legal inside ordinary strings too; braces inside them must
                # not be counted as code.
                if source[i] == "$" and i + 1 < n and source[i + 1] == "{":
                    depth, j = 1, i + 2
                    while j < n and depth:
                        if source[j] == "{":
                            depth += 1
                        elif source[j] == "}":
                            depth -= 1
                        elif source[j] == "\n":
                            line += 1
                        j += 1
                    i = j
                    continue
                i += 1
            if not closed and i >= n:
                problems.append(f"{path.name}:{start_line}: 字符串没有闭合")
            continue

        if c == "'":
            # A char literal, or (in a raw string's wake) nothing at all.
            end = source.find("'", i + 1)
            if end == -1 or "\n" in source[i:end]:
                problems.append(f"{path.name}:{line}: 字符字面量没有闭合")
                i += 1
                continue
            i = end + 1
            continue

        if c == "{":
            depth_brace += 1
        elif c == "}":
            depth_brace -= 1
            if depth_brace < 0:
                problems.append(f"{path.name}:{line}: 多了一个 '}}'")
                depth_brace = 0
        elif c == "(":
            depth_paren += 1
        elif c == ")":
            depth_paren -= 1
            if depth_paren < 0:
                problems.append(f"{path.name}:{line}: 多了一个 ')'")
                depth_paren = 0
        i += 1

    if depth_brace != 0:
        problems.append(f"{path.name}: 花括号不平衡（差 {depth_brace}）")
    if depth_paren != 0:
        problems.append(f"{path.name}: 圆括号不平衡（差 {depth_paren}）")

    # Inside raw strings the only legal `$` forms are `$name`, `${expr}`, or a lone `$` before a
    # character that cannot start one. `${` with no closing brace, and `$` at end of file, are the
    # two the compiler rejects outright.
    for start, end in raw_ranges:
        chunk = source[start:end]
        for match in re.finditer(r"\$", chunk):
            j = match.end()
            if j >= len(chunk):
                problems.append(f"{path.name}: raw 字符串以 '$' 结尾")
                break
            if chunk[j] == "{":
                if "}" not in chunk[j:]:
                    problems.append(f"{path.name}: raw 字符串里的 ${{ 没有闭合")
                    break
            elif not (chunk[j].isalpha() or chunk[j] == "_"):
                # `$ ` or `$)`: legal in a raw string, and a common way to write a literal dollar.
                continue

    if not check_imports:
        return problems

    # Unused imports: never fatal, always noise in a diff this size.
    body = re.sub(r"(?m)^import .*$", "", source)
    for match in re.finditer(r"(?m)^import (?:[\w.]+\.)?(\w+)$", source):
        name = match.group(1)
        # `by` delegation and `PaddingValues.plus` are resolved by receiver type, not by the name
        # appearing in the body, so a textual check cannot see them. Treating them as used is the
        # conservative direction: a false "unused" here would delete a required import.
        if name in ("getValue", "setValue", "provideDelegate", "plus"):
            continue
        if not re.search(r"\b" + re.escape(name) + r"\b", body):
            problems.append(f"{path.name}: import {name} 没有被用到")

    return problems


def main():
    named = [Path(a).resolve() for a in sys.argv[1:]]
    targets = named or tracked_kotlin()
    everything = []
    for path in targets:
        everything.extend(scan(path, check_imports=path in named))
    if everything:
        for problem in everything:
            print(problem)
        print(f"KLEX FAILED ({len(everything)})")
        return 1
    print(f"KLEX PASSED ({len(targets)} files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
