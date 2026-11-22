MODDIR=${0%/*}

# 1) 图标主题目录
mount --bind "$MODDIR/my_product/media/theme/" /my_product/media/theme/

# 2) 静态 RRO（activity-alias 等主题图标覆盖不到的应用）
# /product/overlay 在开机早期就已经挂成只读 overlayfs（lowerdir 含 /my_product/overlay 等），
# 之后往里写文件不会被它看到，所以只能再叠一层 overlayfs 把 APK 注入进去。
# system_server 扫描 overlay 目录发生在 post-fs-data 之后，因此这里挂载是有效的。
OV_ROOT="$MODDIR/run/overlay"
if [ -d /product/overlay ]; then
  rm -rf "$OV_ROOT"
  mkdir -p "$OV_ROOT/upper" "$OV_ROOT/work"
  injected=0
  for OV_BIN in "$MODDIR"/overlay/*.apk.bin; do
    [ -f "$OV_BIN" ] || continue
    OV_NAME=$(basename "$OV_BIN" .apk.bin)
    cp -f "$OV_BIN" "$OV_ROOT/upper/$OV_NAME.apk"
    chmod 644 "$OV_ROOT/upper/$OV_NAME.apk"
    chcon u:object_r:system_file:s0 "$OV_ROOT/upper/$OV_NAME.apk" 2>/dev/null
    injected=1
  done
  if [ "$injected" = 1 ]; then
    mount -t overlay overlay -o "lowerdir=/product/overlay,upperdir=$OV_ROOT/upper,workdir=$OV_ROOT/work" /product/overlay
  fi
fi
