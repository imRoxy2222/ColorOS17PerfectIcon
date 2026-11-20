#!/system/bin/sh
# 开机后补同步：只复制本次新增（目标目录不存在）的应用图标，避免每次开机重复写入。
# KernelSU 会在 late_start 阶段执行本脚本。

MODDIR=${0%/*}

. "$MODDIR/icon_sync.sh"
sync_icons "$MODDIR/my_product/media/theme/uxicons/hdpi" "/data/oplus/uxicons" incremental
