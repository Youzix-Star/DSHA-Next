#!/data/data/com.termux/files/usr/bin/bash
# 在没有手机、没有 App 的情况下，把 assets/scripts 里那几个脚本真跑一遍。
#
# 上一轮为了回答「终端到底能不能用」烧了三次 CI；这个沙箱让同一类问题 3 秒能答。
# 做法：造一个假的 HOME / PREFIX、假的 dsh 与 termux-wake-lock，然后执行脚本、断言输出。
#
# 只跑**不依赖真 dsh 也能判定**的部分：probe/status 的输出契约、start 的日志目录、
# stop 与 logs 在「什么都没有」时的可读输出。真正起服务需要真 dsh，那条只能真机验。
#
# 用法: bash tests/scripts-sandbox.sh
set -uo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPTS="$REPO/app/src/main/assets/scripts"

WORK="$(mktemp -d "${TMPDIR:-$PREFIX/tmp}/scripts-sandbox-XXXXXX")"
cleanup() {
  for p in $(ps -ef 2>/dev/null | grep "$WORK" | grep "dsh web" | grep -v grep | awk '{print $2}'); do
    kill -9 "$p" 2>/dev/null
  done
  rm -rf "$WORK"
}
trap cleanup EXIT

STUBS="$WORK/stubs"
PREFIX_DIR="$WORK/prefix"
HOME_DIR="$WORK/home"
mkdir -p "$STUBS" "$PREFIX_DIR/bin" "$HOME_DIR"

# 假 dsh：--version 与 web 都有输出；web 会一直活着
cat > "$PREFIX_DIR/bin/dsh" <<'EOF'
#!/bin/sh
case "$1" in
  --version) echo "0.2.0-rc.2"; exit 0 ;;
esac
echo "dsh-android-fixes: host half active"
echo "dsh web: http://127.0.0.1:3080/?token=FAKETOKEN"
while :; do sleep 5; done
EOF
printf '#!/bin/sh\nexit 0\n' > "$STUBS/termux-wake-lock"
printf '#!/bin/sh\nexec "$@"\n' > "$STUBS/setsid"
chmod +x "$STUBS"/* "$PREFIX_DIR/bin/dsh"

RUN() { env -i HOME="$HOME_DIR" PREFIX="$PREFIX_DIR" PATH="$PREFIX_DIR/bin:$STUBS:/system/bin" /system/bin/sh "$SCRIPTS/$1" "${@:2}"; }
RUN_PLAIN() { env -i HOME="$HOME_DIR" PREFIX="$PREFIX_DIR" PATH="$STUBS:/system/bin" /system/bin/sh "$SCRIPTS/$1" "${@:2}"; }

FAILURES=0
ok()   { printf '  ok   %s\n' "$1"; }
fail() { printf '  FAIL %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
contains() { case "$2" in *"$3"*) ok "$1" ;; *) fail "$1（[$2] 里没有 [$3]）" ;; esac; }

echo "沙箱: $WORK"
echo

# ---------------------------------------------------------------- probe.sh
echo "1) probe.sh —— 桥接标记串（TermuxBridge 判定「通了」的唯一依据）"
OUT="$(RUN probe.sh)"
contains "输出标记串" "$OUT" "dsha-bridge-ok"
contains "输出 home" "$OUT" "home="

# ---------------------------------------------------------------- status.sh
echo
echo "2) status.sh —— TermuxFacts 要读的六个 key"
OUT="$(RUN status.sh)"
for key in repo= dsh_bin= dsh_version= install_dir= node= server=; do
  contains "含 $key" "$OUT" "$key"
done
contains "dsh 在 PATH 上时为 yes" "$OUT" "dsh_bin=yes"
contains "假 dsh 的版本" "$OUT" "dsh_version=0.2.0-rc.2"
contains "服务未运行（3080 上没东西）" "$OUT" "server=stopped"

# ---------------------------------------------------------------- status.sh（没装 dsh）
echo
echo "3) status.sh —— 没有 dsh 时"
OUT="$(RUN_PLAIN status.sh)"
contains "dsh_bin=no" "$OUT" "dsh_bin=no"
contains "server=stopped" "$OUT" "server=stopped"

# ---------------------------------------------------------------- logs.sh
echo
echo "4) logs.sh —— 日志不存在时给可读输出，而不是报错"
OUT="$(RUN logs.sh 2>&1)"
contains "说明日志不存在" "$OUT" "暂无日志"

echo "   写入一段日志后再读"
mkdir -p "$HOME_DIR/dsh/storage"
printf 'hello from dsh\n' > "$HOME_DIR/dsh/storage/dsh.log"
OUT="$(RUN logs.sh)"
contains "读到日志内容" "$OUT" "hello from dsh"

# ---------------------------------------------------------------- stop.sh
echo
echo "5) stop.sh —— 没有在跑时给可读输出"
OUT="$(RUN stop.sh 2>&1)"
contains "说明没发现运行中的 dsh" "$OUT" "未发现运行中的 dsh"

# ---------------------------------------------------------------- start.sh 的前置检查
echo
echo "6) start.sh —— 没有 dsh 时明确报错（不静默失败）"
OUT="$(RUN_PLAIN start.sh 2>&1)"
contains "指出缺少 dsh" "$OUT" "未找到 dsh"

echo
echo "7) start.sh —— 有 dsh 时会创建日志目录并尝试起服务"
env -i HOME="$HOME_DIR" PREFIX="$PREFIX_DIR" PATH="$PREFIX_DIR/bin:$STUBS:/system/bin" /system/bin/sh "$SCRIPTS/start.sh" > "$WORK/start.out" 2>&1 || true
if [ -d "$HOME_DIR/dsh/storage" ]; then ok "创建了 ~/dsh/storage"; else fail "没有创建 ~/dsh/storage"; fi

echo
if [ "$FAILURES" = 0 ]; then
  echo "SCRIPTS SANDBOX PASSED"
else
  echo "SCRIPTS SANDBOX FAILED ($FAILURES)"
fi
exit $([ "$FAILURES" = 0 ] && echo 0 || echo 1)
