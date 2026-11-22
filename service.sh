#!/system/bin/sh
# 开机后补同步：先从 icons.zip 为本次新增的应用解压图标，再只复制目标目录里还没有的图标，
# 避免每次开机重复写入。KernelSU 会在 late_start 阶段执行本脚本。

MODDIR=${0%/*}
SOURCE_ICONS="$MODDIR/my_product/media/theme/uxicons/hdpi"
NIGHT_FILL="$MODDIR/my_product/media/theme/uxicons/night_fill.list"

. "$MODDIR/icon_sync.sh"

# 包列表只取一次，pm 没就绪时重试几次
PKG_LIST="$MODDIR/.pkg-list.$$"
_sync_packages > "$PKG_LIST"
_retry=0
while [ ! -s "$PKG_LIST" ] && [ "$_retry" -lt 3 ]; do
  sleep 5
  _sync_packages > "$PKG_LIST"
  _retry=$((_retry + 1))
done

extract_icons "$MODDIR/icons.zip" "$SOURCE_ICONS" incremental "$PKG_LIST"
fill_night_icons "$SOURCE_ICONS" "$NIGHT_FILL"
sync_icons "$SOURCE_ICONS" "/data/oplus/uxicons" incremental "$PKG_LIST"

rm -f "$PKG_LIST"
