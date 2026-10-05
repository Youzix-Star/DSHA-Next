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

    # A backtick-quoted name is a *name*, not code: Kotlin lets a test read
    # `` `Termux's own settings` `` and every character inside is opaque. Scanning them as code is
    # how `'` in an English possessive turns into an "unterminated character literal".
    ticks = set()
    for hit in re.finditer(r"`[^`\n]*`", source):
        ticks.update(range(hit.start(), hit.end()))

    while i < n:
        c = source[i]

        if i in ticks:
            i += 1
            continue

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

    # A raw string has no backslash escapes, so every `$` in one is either interpolation or
    # literal — and the difference is decided by the character after it. `$name` and `${expr}` are
    # interpolation; `$ `, `$/`, `$)`, `$1` are literal, which is why shell text mostly survives.
    # The trap is shell's own `${VAR:-default}`: it looks like a Kotlin template, and Kotlin reads
    # it as one. That exact mistake cost a CI round trip, so it is checked here by name.
    for start, end in raw_ranges:
        chunk = source[start:end]
        for match in re.finditer(r"\$", chunk):
            j = match.end()
            if j >= len(chunk):
                problems.append(f"{path.name}: raw 字符串以 '$' 结尾")
                break
            if chunk[j] != "{":
                continue
            # `${'$'}{` is the escape. Anything else opening a brace is Kotlin interpolation.
            if chunk[j:j + 6] == "{'$'}{":
                continue
            problems.append(
                f"{path.name}: raw 字符串里出现了 ${{…}} —— Kotlin 会当成模板插值。"
                f"shell 的 ${{VAR:-默认}} 必须写成 ${{'$'}}{{VAR:-默认}}",
            )
            break
        # `$NAME` is the other half of the trap: it compiles, then fails with "Unresolved
        # reference 'NAME'" at that very column. Shell text is where it happens, because a shell
        # variable and a Kotlin one look identical.
        #
        # Only *some* spellings are a problem, and the difference is the character before the `$`:
        # Kotlin expands `$` + a letter or `_` after an identifier character, a `.`, or nothing —
        # so `` `$NAME` `` and `foo$NAME` are interpolations, while `"$NAME"` and `[$NAME]` are
        # literal dollars that were always fine. Reporting the safe spellings too is how a checker
        # gets ignored, so the safe ones are skipped by name here.
        # Kotlin expands `$name` and `${…}` in a raw string exactly as it does in a quoted one —
        # the quotes around it are just text. That is why a shell script living in a raw string
        # produces a page of "Unresolved reference 'PATH'" rather than a syntax error: the compiler
        # read each one as a variable. No spelling of a literal dollar is both readable and safe, so
        # this repo writes `§DOLLAR§` and replaces it once in `runnerScript`; this check is what
        # makes the placeholder mandatory rather than a convention.
        #
        # A name that *is* declared in the same file is a deliberate interpolation — `Lens.kt`'s
        # GLSL does exactly that — so it is left alone. Anything else is a dollar that was meant to
        # be literal, which is the mistake this exists to catch.
        for match in re.finditer(r"\$([A-Za-z_]\w*)", chunk):
            name = match.group(1)
            if re.search(r"\b" + re.escape(name) + r"\b\s*(?::|=|\))", source):
                continue
            problems.append(
                f"{path.name}: raw 字符串里的 ${name} 在本文件里没有声明，"
                f"Kotlin 会当成变量插值并报 Unresolved reference。"
                f"多行 shell/脚本请把 '$' 写成占位符 §DOLLAR§",
            )

    # An import line with no name after it (`import x\nimport y`) is a syntax error, and the
    # compiler points at whatever follows — usually a line that looks perfectly fine.
    for match in re.finditer(r"(?m)^import\s*$", source):
        line = source.count("\n", 0, match.start()) + 1
        problems.append(f"{path.name}:{line}: import 后面没有名字")

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
