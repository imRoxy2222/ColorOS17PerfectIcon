SKIPUNZIP=1
SKIPMOUNT=false
PROPFILE=true
POSTFSDATA=true
LATESTARTSERVICE=true

unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2

. "$MODPATH/icon_sync.sh"

ICON_ARCHIVE="$MODPATH/icons.zip"
SOURCE_ICONS="$MODPATH/my_product/media/theme/uxicons/hdpi"
NIGHT_FILL="$MODPATH/my_product/media/theme/uxicons/night_fill.list"
TARGET_ICONS="/data/oplus/uxicons"

# 包列表只取一次，解压和同步都复用，避免反复调用 pm
PKG_LIST="$MODPATH/.pkg-list"
_sync_packages > "$PKG_LIST"

ui_print "- 安装耗时约1-5分钟,请耐心等待"

if [ -f "$ICON_ARCHIVE" ]; then
  ui_print "- 正在解压本机已安装应用的图标"
  extract_icons "$ICON_ARCHIVE" "$SOURCE_ICONS" full "$PKG_LIST"
else
  ui_print "! 压缩包内未找到图标归档，跳过图标解压"
fi

if [ -d "$SOURCE_ICONS" ]; then
  fill_night_icons "$SOURCE_ICONS" "$NIGHT_FILL"
fi

if [ -d "$SOURCE_ICONS" ]; then
  ui_print "- 正在同步图标到 $TARGET_ICONS"
  sync_icons "$SOURCE_ICONS" "$TARGET_ICONS" full "$PKG_LIST"
else
  ui_print "! 未找到图标文件夹，跳过图标同步"
fi

# 图标保持 unzip 解压时的 0644/0755 即可（只需要可读）；
# 这里只补模块脚本的可执行位，避免逐文件 fork 拖慢安装。
chmod 0755 "$MODPATH"/*.sh

CommonPath=$MODPATH/common

if [ -d "${CommonPath}" ]; then
  mv ${CommonPath}/* $MODPATH
  rm -rf ${CommonPath}
fi

ui_print "- 写入完成！"

rm -f "$PKG_LIST"
