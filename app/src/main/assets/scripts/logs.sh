#!/data/data/com.termux/files/usr/bin/bash
# 输出 DSH 服务的最近日志
LOG="$HOME/dsh/storage/dsh.log"
if [ -f "$LOG" ]; then
  tail -n 200 "$LOG"
else
  echo "(暂无日志：$LOG 不存在)"
fi
