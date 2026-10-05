#!/data/data/com.termux/files/usr/bin/bash
# 停止 DSH Web 服务
if pkill -f "deepseek-ai/dsh/lib/bin.js" 2>/dev/null; then
  echo "[dsh] 已停止"
else
  echo "[dsh] 未发现运行中的 dsh"
fi
if command -v termux-wake-unlock >/dev/null 2>&1; then
  termux-wake-unlock 2>/dev/null || true
fi
