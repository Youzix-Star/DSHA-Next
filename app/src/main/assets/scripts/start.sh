#!/data/data/com.termux/files/usr/bin/bash
# 启动 DSH Web 服务（后台常驻，不拉起浏览器——由 DSHA 的 WebView 承载）
set -u

PORT=3080
BASE="$HOME/dsh"
LOG="$BASE/storage/dsh.log"
mkdir -p "$BASE/storage"
umask 077

# Android/Termux 无 bwrap/landlock，需放开沙箱权限模式才能执行 bash 工具
export DSH_PERMISSION_MODE=danger-full-access

if curl -s -o /dev/null --max-time 3 "http://127.0.0.1:$PORT"; then
  echo "[dsh] 服务已在运行"
  exit 0
fi

if ! command -v dsh >/dev/null 2>&1; then
  echo "[dsh] 未找到 dsh 命令，请先完成安装"
  exit 1
fi

# 有 Termux:API 时申请唤醒锁，降低服务被系统休眠杀掉的概率
if command -v termux-wake-lock >/dev/null 2>&1; then
  termux-wake-lock 2>/dev/null || true
fi

# setsid 让 dsh 脱离当前会话，避免 RUN_COMMAND 的 app-shell 退出时被一并带走
setsid nohup dsh web >>"$LOG" 2>&1 </dev/null &
echo "[dsh] 已启动 (pid $!)，日志：$LOG"

for _ in $(seq 1 45); do
  if curl -s -o /dev/null --max-time 2 "http://127.0.0.1:$PORT"; then
    echo "[dsh] 已就绪：http://127.0.0.1:$PORT"
    exit 0
  fi
  sleep 1
done

echo "[dsh] 启动超时，最近日志："
tail -20 "$LOG" 2>/dev/null
echo "[dsh] 若提示缺少模型密钥，请在 Web UI 的 Models 页面配置 DeepSeek API Key"
exit 1
