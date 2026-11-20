#!/system/bin/sh
# KernelSU 模块操作按钮：打开项目 GitHub 仓库

URL="https://github.com/imRoxy2222/ColorOS17PerfectIcon"

am start -a android.intent.action.VIEW -d "$URL" >/dev/null 2>&1 \
  || am start --user 0 -a android.intent.action.VIEW -d "$URL" >/dev/null 2>&1

echo "已打开项目主页：$URL"
exit 0
