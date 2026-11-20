#!/system/bin/sh
# 图标同步公共逻辑：只处理本机已安装应用对应的图标目录。
# 由 customize.sh（安装时，full）与 service.sh（开机后，incremental）source 使用。
#
# 用法：
#   . icon_sync.sh
#   sync_icons <源目录> <目标目录> [full|incremental]

_sync_msg() {
  if type ui_print >/dev/null 2>&1; then
    ui_print "$1"
  else
    echo "$1"
  fi
}

# 输出已安装应用的包名（每行一个）
_sync_packages() {
  if command -v pm >/dev/null 2>&1; then
    pm list packages 2>/dev/null
  elif [ -x /system/bin/pm ]; then
    /system/bin/pm list packages 2>/dev/null
  fi | sed 's/^package://' | tr -d '\r' | sort -u
}

_sync_do_copy() {
  mkdir -p "$2" && cp -rf "$1/." "$2/"
}

# 无法取得包名列表时（仅 full 模式）退回全量复制
_sync_copy_all() {
  for _d in "$1"/*; do
    [ -d "$_d" ] || continue
    _n=${_d##*/}
    case "$_n" in
      temp_*|.*|*.bak) continue ;;
    esac
    _sync_do_copy "$_d" "$2/$_n"
  done
}

sync_icons() {
  _src=$1
  _dst=$2
  _mode=${3:-full}
  _copied=0
  _kept=0
  _hash=0

  if [ ! -d "$_src" ]; then
    _sync_msg "! 图标源目录不存在：$_src"
    return 1
  fi
  mkdir -p "$_dst" || { _sync_msg "! 无法创建目录：$_dst"; return 1; }

  _pkgfile="$_dst/.pm-list.$$"
  _sync_packages > "$_pkgfile"

  # 开机阶段 pm 可能还没就绪，incremental 模式重试几次
  if [ "$_mode" = incremental ]; then
    _retry=0
    while [ ! -s "$_pkgfile" ] && [ "$_retry" -lt 3 ]; do
      sleep 5
      _sync_packages > "$_pkgfile"
      _retry=$((_retry + 1))
    done
  fi

  if [ ! -s "$_pkgfile" ]; then
    rm -f "$_pkgfile"
    if [ "$_mode" = incremental ]; then
      _sync_msg "! 暂未获取到应用列表，跳过本次同步"
      return 0
    fi
    _sync_msg "! 获取应用列表失败，回退为复制全部图标"
    _sync_copy_all "$_src" "$_dst"
    chmod -R 0777 "$_dst" 2>/dev/null
    return 0
  fi

  while IFS= read -r _pkg; do
    [ -n "$_pkg" ] || continue

    # 1) 目录名就是包名
    if [ -d "$_src/$_pkg" ]; then
      if [ "$_mode" = incremental ] && [ -d "$_dst/$_pkg" ]; then
        _kept=$((_kept + 1))
      else
        _sync_do_copy "$_src/$_pkg" "$_dst/$_pkg" && _copied=$((_copied + 1))
      fi
    fi

    # 2) 目录名是“包名 + Activity”，例如 com.xt.retouch.uilauncher.LaunchActivity
    for _d in "$_src/$_pkg".*; do
      [ -d "$_d" ] || continue
      _n=${_d##*/}
      if [ "$_mode" = incremental ] && [ -d "$_dst/$_n" ]; then
        _kept=$((_kept + 1))
      else
        _sync_do_copy "$_d" "$_dst/$_n" && _copied=$((_copied + 1))
      fi
    done
  done < "$_pkgfile"

  # 3) 一个目录同时对应多个包名，例如 A和B
  for _d in "$_src"/*和*; do
    [ -d "$_d" ] || continue
    _n=${_d##*/}
    _a=${_n%%和*}
    _b=${_n#*和}
    if grep -qxF "$_a" "$_pkgfile" || grep -qxF "$_b" "$_pkgfile"; then
      if [ "$_mode" = incremental ] && [ -d "$_dst/$_n" ]; then
        _kept=$((_kept + 1))
      else
        _sync_do_copy "$_d" "$_dst/$_n" && _copied=$((_copied + 1))
      fi
    fi
  done

  # 4) plus.H* 是主题商店资源 ID，无法映射到包名，跳过
  for _d in "$_src"/plus.*; do
    [ -d "$_d" ] && _hash=$((_hash + 1))
  done

  rm -f "$_pkgfile"
  [ "$_copied" -gt 0 ] && chmod -R 0777 "$_dst" 2>/dev/null

  _sync_msg "- 图标同步完成：新增 $_copied 个，已存在 $_kept 个，跳过哈希目录 $_hash 个"
}
