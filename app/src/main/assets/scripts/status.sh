#!/data/data/com.termux/files/usr/bin/bash
# 输出 KEY=VALUE 形式的状态，供 DSHA 安装器解析
echo "home=$HOME"
echo "repo=$([ -d "$HOME/deepseek-harness-android/.git" ] && echo yes || echo no)"
echo "dsh_bin=$(command -v dsh >/dev/null 2>&1 && echo yes || echo no)"
echo "dsh_version=$(dsh --version 2>/dev/null | head -1 || echo -)"
echo "install_dir=$([ -f "$HOME/dsh/start_dsh.sh" ] && echo yes || echo no)"
echo "node=$(node -v 2>/dev/null || echo -)"
echo "wake_lock=$(command -v termux-wake-lock >/dev/null 2>&1 && echo yes || echo no)"
if curl -s -o /dev/null --max-time 3 "http://127.0.0.1:3080"; then
  echo "server=running"
else
  echo "server=stopped"
fi
