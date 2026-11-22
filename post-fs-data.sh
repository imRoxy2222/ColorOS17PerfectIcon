MODDIR=${0%/*}

# 1) 图标主题目录
mount --bind "$MODDIR/my_product/media/theme/" /my_product/media/theme/

# 2) 静态 RRO（activity-alias 等主题图标覆盖不到的应用）
# /product/overlay 在开机早期就已经挂成只读 overlayfs（lowerdir 含 /my_product/overlay 等），
# 之后往里写文件不会被它看到，所以只能再叠一层 overlayfs 把 APK 注入进去。
# system_server 扫描 overlay 目录发生在 post-fs-data 之后，因此这里挂载是有效的。
OV_ROOT="$MODDIR/run/overlay"
OV_TARGETS="$MODDIR/overlay/targets.txt"

# 从 targets.txt 读取 RRO 对应的目标包名，格式：<RRO 名> <目标包名>，# 开头为注释。
# 未登记的 RRO 返回非 0，调用方保持“始终注入”的旧行为。
_ov_target_of() {
  OV_PKG=
  [ -f "$OV_TARGETS" ] || return 1
  while read -r _name _pkg; do
    case "$_name" in
      ''|'#'*) continue ;;
    esac
    if [ "$_name" = "$1" ]; then
      OV_PKG=$_pkg
      return 0
    fi
  done < "$OV_TARGETS"
  return 1
}

# 判断目标包是否已安装。post-fs-data 阶段 system_server 尚未启动，不能用 pm，
# 只能按包名扫描 /data/app 和各分区的安装目录。
_ov_installed() {
  # 预装应用的目录名不一定等于包名，先查 packages.xml（带右引号，避免前缀误匹配）
  if [ -f /data/system/packages.xml ] && grep -F -q "name=\"$1\"" /data/system/packages.xml; then
    return 0
  fi
  # /data/app 下目录名形如 <包名>-<hash>==
  for _q in /data/app/*/"$1"-*/base.apk /data/app/"$1"-*/base.apk; do
    [ -f "$_q" ] && return 0
  done
  # 各分区 app / priv-app 下通常是 <包名> 目录或 <包名>.apk
  for _d in /system/app /system/priv-app \
            /system_ext/app /system_ext/priv-app \
            /product/app /product/priv-app \
            /vendor/app /vendor/priv-app \
            /odm/app /odm/priv-app \
            /my_*/app /my_*/priv-app; do
    [ -e "$_d/$1" ] && return 0
    [ -e "$_d/$1.apk" ] && return 0
    for _q in "$_d/$1"-*; do
      [ -e "$_q" ] && return 0
    done
  done
  return 1
}

if [ -d /product/overlay ]; then
  rm -rf "$OV_ROOT"
  mkdir -p "$OV_ROOT/upper" "$OV_ROOT/work"
  injected=0
  for OV_BIN in "$MODDIR"/overlay/*.apk.bin; do
    [ -f "$OV_BIN" ] || continue
    OV_NAME=$(basename "$OV_BIN" .apk.bin)
    # targets.txt 里登记了目标包名才判断；未登记则保持始终注入
    if _ov_target_of "$OV_NAME" && [ -n "$OV_PKG" ] && ! _ov_installed "$OV_PKG"; then
      continue
    fi
    cp -f "$OV_BIN" "$OV_ROOT/upper/$OV_NAME.apk"
    chmod 644 "$OV_ROOT/upper/$OV_NAME.apk"
    chcon u:object_r:system_file:s0 "$OV_ROOT/upper/$OV_NAME.apk" 2>/dev/null
    injected=1
  done
  if [ "$injected" = 1 ]; then
    mount -t overlay overlay -o "lowerdir=/product/overlay,upperdir=$OV_ROOT/upper,workdir=$OV_ROOT/work" /product/overlay
  fi
fi
