#!/usr/bin/env python3
"""在没有手机的情况下校验 Termux 侧那六个脚本。

`app/src/main/assets/scripts/*.sh` 随 APK 走，运行时以 `bash -lc <脚本内容>` 的形式整份交给
Termux —— 这是整个仓库最容易坏、而 CI 完全看不见的一块：脚本语法错、少一个 key、少了那个
标记串，编译一样通过，装到手机上才发现。

这里做三件事：`sh -n` 过一遍语法、逐个脚本核对它必须输出的东西、打印字节摘要。

用法:
    python3 scripts/runscript.py

退出码 0 表示：六个脚本都在、语法都过、每个该有的输出都在。
"""

import hashlib
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SCRIPTS = REPO / "app/src/main/assets/scripts"

# DSHA 的命名，一个不少。probe/status 是首页的眼睛，start/stop/logs/install 是终端的手。
REQUIRED = ["probe.sh", "status.sh", "start.sh", "stop.sh", "logs.sh", "install.sh"]

# 每个脚本必须出现的东西。少了任何一条，对应的功能就是静默失效：
#   probe.sh   那个标记串是 TermuxBridge 判定「桥通了」的唯一依据
#   status.sh  六个 key 全被 TermuxFacts.from 读
#   start.sh   写日志到 ~/dsh/storage/dsh.log（logs.sh 读同一个路径），并且脱离会话
#   stop.sh    杀的是 dsh 那只进程
#   logs.sh    同上那个日志路径
#   install.sh 走我们自己的安装脚本
MUST_CONTAIN = {
    "probe.sh": ["dsha-bridge-ok"],
    "status.sh": ["repo=", "dsh_bin=", "dsh_version=", "install_dir=", "node=", "server="],
    "start.sh": ["dsh web", "setsid", "storage/dsh.log", "termux-wake-lock", "3080"],
    "stop.sh": ["deepseek-ai/dsh/lib/bin.js"],
    "logs.sh": ["storage/dsh.log"],
    "install.sh": ["install-dsh.sh"],
}

failures = []


def fail(message):
    failures.append(message)
    print(f"  FAIL {message}")


def ok(message):
    print(f"  ok   {message}")


def main():
    if not SCRIPTS.is_dir():
        print(f"missing {SCRIPTS}")
        return 1

    for name in REQUIRED:
        path = SCRIPTS / name
        if not path.is_file():
            fail(f"{name} 不存在")
            continue

        source = path.read_text(encoding="utf-8")
        print(f"{name}: {len(source.splitlines())} 行, sha256 {hashlib.sha256(source.encode()).hexdigest()[:16]}")

        if not source.startswith("#!"):
            fail(f"{name} 没有 shebang")
        elif "com.termux" not in source.splitlines()[0]:
            fail(f"{name} 的 shebang 不是 Termux 路径: {source.splitlines()[0]}")

        result = subprocess.run(["sh", "-n"], input=source, text=True, capture_output=True)
        if result.returncode != 0:
            fail(f"{name} 没通过 sh -n:\n{result.stderr.strip()}")
        else:
            ok(f"{name} 语法通过")

        for needle in MUST_CONTAIN[name]:
            if needle in source:
                ok(f"{name} 含 {needle}")
            else:
                fail(f"{name} 缺少 {needle}")

    print("RUNSCRIPT PASSED" if not failures else f"RUNSCRIPT FAILED ({len(failures)})")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
