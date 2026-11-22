#!/system/bin/sh
# 图标同步公共逻辑：只处理本机已安装应用对应的图标目录。
# 由 customize.sh（安装时，full）与 service.sh（开机后，incremental）source 使用。
#
# 用法：
#   . icon_sync.sh
#   extract_icons <图标归档> <目标目录> [full|incremental] [包名列表文件]
#   sync_icons    <源目录>   <目标目录> [full|incremental] [包名列表文件]
#
# 说明：包名列表建议由调用方只取一次后传入，避免在 ColorOS 上反复调用 pm。

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

# 只给源目录里实际存在的应用设置权限，避免每次遍历目标目录里历史遗留的几千个旧目录
_sync_set_perm() {
  _s=$1
  _t=$2
  set --
  for _d in "$_s"/*; do
    [ -d "$_d" ] || continue
    set -- "$@" "$_t/${_d##*/}"
  done
  [ $# -gt 0 ] && chmod -R 0777 "$@" 2>/dev/null
  return 0
}

# 仓库为瘦身省略了与 recfg 完全相同的 rec_night，这里按清单复制还原，
# 保证桌面在 /my_product/media/theme 与 /data/oplus/uxicons 中看到的仍是完整三件套。
fill_night_icons() {
  _fn_src=$1
  _fn_list=$2

  if [ ! -f "$_fn_list" ]; then
    _sync_msg "! 缺少暗色前景清单：$_fn_list"
    return 0
  fi

  _fn_n=0
  while IFS= read -r _fn_rel; do
    [ -n "$_fn_rel" ] || continue
    _fn_dst="$_fn_src/$_fn_rel"
    [ -f "$_fn_dst" ] && continue

    _fn_dir=${_fn_dst%/*}
    _fn_base=${_fn_dst##*/}
    case $_fn_base in
      rec_night.png)     _fn_fg="$_fn_dir/recfg.png" ;;
      rec_night_1x2.png) _fn_fg="$_fn_dir/recfg_1x2.png" ;;
      rec_night_2x1.png) _fn_fg="$_fn_dir/recfg_2x1.png" ;;
      rec_night_2x2.png) _fn_fg="$_fn_dir/recfg_2x2.png" ;;
      *) continue ;;
    esac

    [ -f "$_fn_fg" ] || continue
    cp -f "$_fn_fg" "$_fn_dst" 2>/dev/null && _fn_n=$((_fn_n + 1))
  done < "$_fn_list"

  [ "$_fn_n" -gt 0 ] && _sync_msg "- 已补回暗色前景 $_fn_n 个"
  return 0
}

# 仓库把逐字节相同的图标收敛到了 dedupe_blobs/，这里按清单复制回各应用目录。
# 只处理目标目录已存在的应用（解压阶段已按已安装应用过滤）。
fill_dedupe_icons() {
  _fd_src=$1
  _fd_list=$2
  _fd_blobs=$3

  if [ ! -f "$_fd_list" ]; then
    _sync_msg "! 缺少去重清单：$_fd_list"
    return 0
  fi
  if [ ! -d "$_fd_blobs" ]; then
    _sync_msg "! 缺少去重 blob 目录：$_fd_blobs"
    return 0
  fi

  _fd_n=0
  while read -r _fd_rel _fd_blob; do
    [ -n "$_fd_rel" ] || continue
    _fd_dst="$_fd_src/$_fd_rel"
    [ -f "$_fd_dst" ] && continue
    [ -d "${_fd_dst%/*}" ] || continue
    [ -f "$_fd_blobs/$_fd_blob" ] || continue
    cp -f "$_fd_blobs/$_fd_blob" "$_fd_dst" 2>/dev/null && _fd_n=$((_fd_n + 1))
  done < "$_fd_list"

  [ "$_fd_n" -gt 0 ] && _sync_msg "- 已补回共享图标 $_fd_n 个"
  return 0
}

# 找到可用的 unzip（安装阶段在安装器 PATH 里，开机阶段回退到 KernelSU 自带 busybox）
_sync_unzip() {
  if command -v unzip >/dev/null 2>&1; then
    echo unzip
    return 0
  fi
  for _bb in /data/adb/ksu/bin/busybox /data/adb/magisk/busybox; do
    if [ -x "$_bb" ]; then
      echo "$_bb unzip"
      return 0
    fi
  done
  return 1
}

# 解析包名列表：优先使用调用方缓存的列表文件；否则自己取（incremental 重试等待 pm 就绪）
# 结果：_pkgfile 为列表文件，_own_pkgfile=1 表示需要调用方负责清理
_sync_resolve_pkgs() {
  _given=$1
  _r_dst=$2
  _r_mode=$3

  if [ -n "$_given" ] && [ -e "$_given" ]; then
    _pkgfile=$_given
    _own_pkgfile=0
    return 0
  fi

  _pkgfile="$_r_dst/.pm-list.$$"
  _own_pkgfile=1
  _sync_packages > "$_pkgfile"

  if [ "$_r_mode" = incremental ]; then
    _r_retry=0
    while [ ! -s "$_pkgfile" ] && [ "$_r_retry" -lt 3 ]; do
      sleep 5
      _sync_packages > "$_pkgfile"
      _r_retry=$((_r_retry + 1))
    done
  fi

  return 0
}

# 从 icons.zip 里按已安装应用的包名解出图标目录
extract_icons() {
  _arc=$1
  _dst=$2
  _mode=${3:-full}
  _given=${4:-}
  # 本次实际解压的应用数，供调用方判断是否需要补回 rec_night
  _sync_extracted=0

  if [ ! -f "$_arc" ]; then
    _sync_msg "! 图标归档不存在：$_arc"
    return 1
  fi
  mkdir -p "$_dst" || { _sync_msg "! 无法创建目录：$_dst"; return 1; }

  _uz=$(_sync_unzip)
  if [ -z "$_uz" ]; then
    _sync_msg "! 未找到 unzip，无法解压图标归档"
    return 1
  fi

  _sync_resolve_pkgs "$_given" "$_dst" "$_mode"

  if [ ! -s "$_pkgfile" ]; then
    [ "$_own_pkgfile" = 1 ] && rm -f "$_pkgfile"
    if [ "$_mode" = incremental ]; then
      _sync_msg "! 暂未获取到应用列表，跳过本次图标解压"
      return 0
    fi
    _sync_msg "! 获取应用列表失败，回退为解压全部图标"
    $_uz -o -d "$_dst" "$_arc" >/dev/null 2>&1
    _sync_extracted=1
    return 0
  fi

  _cnt=0
  set --
  while IFS= read -r _pkg; do
    [ -n "$_pkg" ] || continue

    if [ "$_mode" = incremental ]; then
      # 已有对应目录的应用不再重复解压
      _has=
      for _e in "$_dst/$_pkg" "$_dst/$_pkg".* "$_dst/$_pkg"#*; do
        [ -e "$_e" ] && { _has=1; break; }
      done
      [ -n "$_has" ] && continue
    fi

    # 一个模式覆盖：包名目录、包名.Activity 目录、包名#Activity 目录，
    # 以及 “包名A和包名B” 这类合并目录（两侧任一半安装都能命中）
    set -- "$@" "*$_pkg*/*"
    _cnt=$((_cnt + 1))
  done < "$_pkgfile"

  [ "$_own_pkgfile" = 1 ] && rm -f "$_pkgfile"

  if [ "$_cnt" -eq 0 ]; then
    _sync_msg "- 没有需要解压图标的应用"
    return 0
  fi

  # 一次 unzip 解出全部命中的目录，避免逐个应用起进程
  $_uz -o -d "$_dst" "$_arc" "$@" >/dev/null 2>&1
  _sync_extracted=$_cnt

  _sync_msg "- 已为 $_cnt 个应用解压图标"
}

sync_icons() {
  _src=$1
  _dst=$2
  _mode=${3:-full}
  _given=${4:-}

  if [ ! -d "$_src" ]; then
    _sync_msg "! 图标源目录不存在：$_src"
    return 1
  fi
  mkdir -p "$_dst" || { _sync_msg "! 无法创建目录：$_dst"; return 1; }

  # 全量模式：源目录在解压阶段已按已安装应用过滤，整目录一次复制即可
  if [ "$_mode" = full ]; then
    _n=0
    for _d in "$_src"/*; do
      [ -d "$_d" ] && _n=$((_n + 1))
    done
    _sync_do_copy "$_src" "$_dst"
    _sync_set_perm "$_src" "$_dst"
    _sync_msg "- 图标同步完成：新增 $_n 个"
    return 0
  fi

  _copied=0
  _kept=0
  _hash=0
  _newdirs=

  _sync_resolve_pkgs "$_given" "$_dst" "$_mode"

  if [ ! -s "$_pkgfile" ]; then
    [ "$_own_pkgfile" = 1 ] && rm -f "$_pkgfile"
    _sync_msg "! 暂未获取到应用列表，跳过本次同步"
    return 0
  fi

  while IFS= read -r _pkg; do
    [ -n "$_pkg" ] || continue

    # 1) 目录名就是包名
    if [ -d "$_src/$_pkg" ]; then
      if [ -d "$_dst/$_pkg" ]; then
        _kept=$((_kept + 1))
      else
        _sync_do_copy "$_src/$_pkg" "$_dst/$_pkg" && {
          _copied=$((_copied + 1))
          _newdirs="$_newdirs $_dst/$_pkg"
        }
      fi
    fi

    # 2) 目录名是“包名 + Activity”，例如 com.xt.retouch.uilauncher.LaunchActivity
    for _d in "$_src/$_pkg".*; do
      [ -d "$_d" ] || continue
      _n=${_d##*/}
      if [ -d "$_dst/$_n" ]; then
        _kept=$((_kept + 1))
      else
        _sync_do_copy "$_d" "$_dst/$_n" && {
          _copied=$((_copied + 1))
          _newdirs="$_newdirs $_dst/$_n"
        }
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
      if [ -d "$_dst/$_n" ]; then
        _kept=$((_kept + 1))
      else
        _sync_do_copy "$_d" "$_dst/$_n" && {
          _copied=$((_copied + 1))
          _newdirs="$_newdirs $_dst/$_n"
        }
      fi
    fi
  done

  # 4) plus.H* 是主题商店资源 ID，无法映射到包名，跳过
  for _d in "$_src"/plus.*; do
    [ -d "$_d" ] && _hash=$((_hash + 1))
  done

  [ "$_own_pkgfile" = 1 ] && rm -f "$_pkgfile"

  # 只给本次新增的目录设置权限
  [ -n "$_newdirs" ] && chmod -R 0777 $_newdirs 2>/dev/null

  _sync_msg "- 图标同步完成：新增 $_copied 个，已存在 $_kept 个，跳过哈希目录 $_hash 个"
}
