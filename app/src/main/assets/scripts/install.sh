#!/data/data/com.termux/files/usr/bin/bash
# 安装 / 更新 DSH 运行环境。
#
# 与 DSHA 的同名脚本只有一处不同：它克隆 FunnelCakes/deepseek-harness-android 再跑那个仓库的
# setup.sh，我们用自己那份 DSHA-Next-Shell 的 install-dsh.sh（APK 里带着，进首页时会写到
# ~/.dsha/install-dsh.sh）。两边的产物是同一个东西 —— $PREFIX/bin/dsh 加上能跑 dsh web 的
# 运行环境 —— 只是引导脚本不同；其余流程（先装 git 类依赖、失败给出可读原因、结束时打印下一步）
# 保持与 DSHA 相同的形状。
set -uo pipefail

INSTALLER="$HOME/.dsha/install-dsh.sh"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*"; }

if [ ! -x "$INSTALLER" ]; then
  warn "未找到安装脚本 $INSTALLER"
  warn "请回到 App 首页点一次「安装 dsh」—— 那一步会把它写进 Termux"
  exit 1
fi

if [ "${1:-}" != "" ]; then
  info "开始安装 @deepseek-ai/dsh@$1（首次需下载依赖并做原生编译，约 5~15 分钟）"
  bash "$INSTALLER" "$1"
else
  info "开始安装 @deepseek-ai/dsh（首次需下载依赖并做原生编译，约 5~15 分钟）"
  bash "$INSTALLER"
fi
