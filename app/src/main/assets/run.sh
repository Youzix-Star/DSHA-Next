# DSHA-Next —— 由 App 直接交给 Termux 执行的小脚本。
#
# 用法: run.sh <动作> [参数…]
#   probe                输出 status/version/running/port/url 六行
#   start [端口]          后台启动 dsh web，日志写到 ~/.dsha/web.log
#   stop                 停止 dsh web
#   log [行数]            打印 web.log 的尾部
#   install [版本]        运行 ~/.dsha/install-dsh.sh
#   shell <命令>          在 $PREFIX/bin 的 PATH 下执行一条命令
#
# 这个文件跟 APK 一起走：App 每次把它的内容作为 bash 的参数发给 Termux，
# 不在 Termux 的目录里留副本，所以升级 App 就是升级这个脚本。
# 只有 ~/.dsha/install-dsh.sh（安装脚本）需要由 App 写一次 —— 那一次是必须落盘的。

DSHA_DIR="${HOME:-/data/data/com.termux/files/home}/.dsha"
LOG="$DSHA_DIR/web.log"
PORT_FILE="$DSHA_DIR/web.port"
PREFIX_DIR="${PREFIX:-/data/data/com.termux/files/usr}"
export PATH="$PREFIX_DIR/bin:$PATH"
export HOME="${HOME:-/data/data/com.termux/files/home}"

mkdir -p "$DSHA_DIR" 2>/dev/null

# dsh web 的进程：用完整命令行匹配，避免误伤别的 node 进程
web_pids() {
    pgrep -f "lib/bin.js web" 2>/dev/null
}

read_port() {
    if [ -f "$PORT_FILE" ]; then cat "$PORT_FILE"; else echo 3080; fi
}

# dsh 打印的带 token 地址
read_url() {
    [ -f "$LOG" ] || return 0
    grep -a -o 'http://[0-9A-Za-z._:-]*/?token=[A-Za-z0-9._~-]*' "$LOG" | tail -n 1
}

case "${1:-probe}" in
  probe)
    if command -v dsh >/dev/null 2>&1; then
        echo "status=installed"
    else
        echo "status=missing-dsh"
    fi
    echo "version=$(dsh --version 2>/dev/null | tail -n 1)"
    echo "running=$([ -n "$(web_pids)" ] && echo yes || echo no)"
    echo "port=$(read_port)"
    echo "url=$(read_url)"
    ;;
  start)
    if [ "${2:-}" != "" ]; then printf '%s' "$2" > "$PORT_FILE"; fi
    PORT="$(read_port)"
    if [ -n "$(web_pids)" ]; then
        echo "已经在运行"
        read_url
        exit 0
    fi
    # 项目自己的建议：先锁唤醒，否则 Android 会回收实例
    if command -v termux-wake-lock >/dev/null 2>&1; then
        termux-wake-lock >/dev/null 2>&1
    fi
    : > "$LOG"
    # 脱离本次调用：命令要立刻返回，dsh 要继续活着
    if command -v setsid >/dev/null 2>&1; then
        setsid dsh web --no-open --port "$PORT" >> "$LOG" 2>&1 &
    else
        nohup dsh web --no-open --port "$PORT" >> "$LOG" 2>&1 &
    fi
    # 等 token 地址出现（最多 40 秒）
    i=0
    while [ "$i" -lt 80 ]; do
        U="$(read_url)"
        if [ -n "$U" ]; then echo "$U"; exit 0; fi
        if [ -z "$(web_pids)" ]; then
            echo "dsh web 退出了，日志尾部：" >&2
            tail -n 20 "$LOG" >&2
            exit 1
        fi
        sleep 0.5
        i=$((i + 1))
    done
    echo "已启动，但 40 秒内没有拿到 token 地址；日志尾部：" >&2
    tail -n 20 "$LOG" >&2
    exit 1
    ;;
  stop)
    PIDS="$(web_pids)"
    if [ -z "$PIDS" ]; then echo "没有在运行"; exit 0; fi
    kill $PIDS 2>/dev/null
    i=0
    while [ "$i" -lt 20 ] && [ -n "$(web_pids)" ]; do sleep 0.25; i=$((i + 1)); done
    if [ -n "$(web_pids)" ]; then kill -9 $(web_pids) 2>/dev/null; fi
    echo "已停止"
    ;;
  log)
    if [ -f "$LOG" ]; then tail -n "${2:-40}" "$LOG"; else echo "还没有 $LOG"; fi
    ;;
  install)
    if [ ! -x "$DSHA_DIR/install-dsh.sh" ]; then
        echo "缺少 $DSHA_DIR/install-dsh.sh，请先在 App 首页点一次「准备」" >&2
        exit 1
    fi
    if [ "${2:-}" != "" ]; then "$DSHA_DIR/install-dsh.sh" "$2"; else "$DSHA_DIR/install-dsh.sh"; fi
    ;;
  shell)
    shift
    exec sh -c "$*"
    ;;
  *)
    echo "未知动作: $1" >&2
    exit 2
    ;;
esac
