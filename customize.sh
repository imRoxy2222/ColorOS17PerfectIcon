SKIPUNZIP=true
SKIPMOUNT=false
PROPFILE=true
POSTFSDATA=true
LATESTARTSERVICE=true

unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2

ui_print "正在写入oplus目录"
SOURCE_ICONS="$MODPATH/my_product/media/theme/uxicons/hdpi"
TARGET_ICONS="/data/oplus/uxicons"

if [ ! -d "$TARGET_ICONS" ]; then
  mkdir -p "$TARGET_ICONS"
fi

if [ -d "$SOURCE_ICONS" ]; then
  cp -rf "$SOURCE_ICONS"/* "$TARGET_ICONS/"
  chmod -R 0777 "$TARGET_ICONS"
else
  ui_print "提示：压缩包内未找到 hdpi 文件夹，跳过覆盖"
fi

set_perm_recursive $MODPATH 0 0 0777 0777

CommonPath=$MODPATH/common

if [ -d "${CommonPath}" ]; then
  mv ${CommonPath}/* $MODPATH
  rm -rf ${CommonPath}
fi

ui_print "- 写入完成！"
ui_print "- 加入Q群一起交流吧~ "

Testing=`pm list package | grep -w 'com.tencent.mobileqq'`
if [[ "$Testing" != "" ]];then
  am start -d 'mqqapi://card/show_pslcard?src_type=internal&version=1&uin=1070892396&card_type=group&source=qrcode' >/dev/null 2>&1
fi