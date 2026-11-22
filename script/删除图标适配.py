#!/usr/bin/env python3
"""删除某个应用的图标适配。

用法：
    python3 script/删除图标适配.py com.example.app --dry-run
    python3 script/删除图标适配.py com.example.app

会清理：
    hdpi/<包名> 及 <包名>.<Activity> 素材目录
    allApps.xml / drawableMapping.xml / morph_icons_whitelist.xml
    night_fill.list / dedupe.list
    dedupe_blobs/ 中不再被引用的孤儿文件

并把删掉的目录名追加到 removed.list，模块安装/开机会据此清理
/data/oplus/uxicons 里遗留的旧图标（重新适配后会自动跳过）。
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ICON_ROOT = ROOT / "my_product" / "media" / "theme" / "uxicons" / "hdpi"
THEME = ROOT / "my_product" / "media" / "theme"
ALL_APPS = THEME / "allApps.xml"
DRAWABLE = THEME / "drawableMapping.xml"
MORPH = ICON_ROOT / "morph_icons_whitelist.xml"
NIGHT_FILL = ROOT / "my_product" / "media" / "theme" / "uxicons" / "night_fill.list"
DEDUPE_LIST = ROOT / "dedupe.list"
DEDUPE_BLOBS = ROOT / "dedupe_blobs"
REMOVED_LIST = ROOT / "removed.list"
OVERLAY_TARGETS = ROOT / "overlay" / "targets.txt"

REMOVED_HEADER = "# 删除适配后，安装/开机时从 /data/oplus/uxicons 清理的目录\n"


def read_lines(path):
    """读取文件行（保留换行）；文件不存在时返回空列表。"""
    try:
        return path.read_text(encoding="utf-8").splitlines(keepends=True)
    except OSError:
        return []


def write_lines(path, lines):
    path.write_text("".join(lines), encoding="utf-8")


def drop_contains(lines, needles):
    """删除包含任一 needle 的行。"""
    kept = []
    removed = 0
    for line in lines:
        if any(needle in line for needle in needles):
            removed += 1
        else:
            kept.append(line)
    return kept, removed


def drop_prefix(lines, prefixes):
    """删除以 prefix + '/' 开头的行。"""
    kept = []
    removed = 0
    for line in lines:
        text = line.strip()
        if any(text.startswith(prefix + "/") for prefix in prefixes):
            removed += 1
        else:
            kept.append(line)
    return kept, removed


def find_dirs(target):
    """返回 (可删除目录, 合并目录)。合并目录默认不动，避免误删另一个应用。"""
    exact = ICON_ROOT / target
    dirs = []
    if exact.is_dir():
        dirs.append(exact)
    for path in sorted(ICON_ROOT.glob(target + ".*")):
        if path.is_dir() and path not in dirs:
            dirs.append(path)

    merged = []
    if ICON_ROOT.is_dir():
        for path in sorted(ICON_ROOT.iterdir()):
            if path.is_dir() and "和" in path.name and target in path.name.split("和"):
                merged.append(path)
    return dirs, merged


def prune_blobs():
    """删除 dedupe.list 不再引用的 blob，返回删除数量。"""
    if not DEDUPE_LIST.is_file():
        return 0
    refs = set()
    for line in read_lines(DEDUPE_LIST):
        parts = line.split()
        if len(parts) == 2:
            refs.add(parts[1])
    removed = 0
    if DEDUPE_BLOBS.is_dir():
        for path in DEDUPE_BLOBS.glob("*.png"):
            if path.name not in refs:
                path.unlink()
                removed += 1
    return removed


def load_removed():
    entries = set()
    for line in read_lines(REMOVED_LIST):
        text = line.strip()
        if text and not text.startswith("#"):
            entries.add(text)
    return entries


def main() -> None:
    parser = argparse.ArgumentParser(description="删除某个应用的图标适配")
    parser.add_argument("target", help="包名，或 <包名>.<Activity> 目录名")
    parser.add_argument("--dry-run", action="store_true", help="只打印，不改文件")
    parser.add_argument("--force-merged", action="store_true",
                        help="同时删除 A和B 这类合并目录（会一起移除另一个应用的图标）")
    args = parser.parse_args()

    target = args.target.strip()
    if not target or "/" in target or ".." in target:
        raise SystemExit("目标名称不合法：%r" % args.target)
    if not ICON_ROOT.is_dir():
        raise SystemExit("图标目录不存在：%s" % ICON_ROOT)

    dirs, merged = find_dirs(target)
    delete_dirs = [p for p in dirs]
    if args.force_merged:
        delete_dirs += merged

    # 三个 XML：包名/目录名匹配（morph 白名单的标签不同，单独处理）
    xml_needles = {
        ALL_APPS: ['package="%s"' % target, 'name="%s"' % target],
        DRAWABLE: ['package="%s"' % target, 'name="%s"' % target],
        MORPH: ["<package_name>%s<" % target],
    }
    xml_changes = []
    for path, needles in xml_needles.items():
        if not path.is_file():
            continue
        lines, count = drop_contains(read_lines(path), needles)
        if count:
            xml_changes.append((path, lines, count))

    # 两份清单：按被删除目录的前缀匹配
    prefixes = [p.name for p in delete_dirs]
    if not prefixes:
        prefixes = [target]
    list_changes = []
    for path in (NIGHT_FILL, DEDUPE_LIST):
        if not path.is_file():
            continue
        lines, count = drop_prefix(read_lines(path), prefixes)
        if count:
            list_changes.append((path, lines, count))

    removed_names = [p.name for p in delete_dirs]

    print("目标        : %s" % target)
    for path in delete_dirs:
        print("素材目录    : 删除 %s" % path.relative_to(ROOT))
    for path in merged:
        if path in delete_dirs:
            print("合并目录    : 删除 %s" % path.relative_to(ROOT))
        else:
            print("合并目录    : 跳过 %s（仍被另一个应用使用；如两边都不需要，加 --force-merged）"
                  % path.relative_to(ROOT))
    for path, _, count in xml_changes:
        print("XML         : %s 删除 %d 行" % (path.relative_to(ROOT), count))
    for path, _, count in list_changes:
        print("清单        : %s 删除 %d 行" % (path.relative_to(ROOT), count))
    if removed_names:
        print("removed.list: 追加 %s" % ", ".join(removed_names))

    # 有记录但没找到目录时给出提示
    if not delete_dirs and (xml_changes or list_changes):
        print("提示        : 未找到素材目录，可能已被手动删除；"
              "如需清理设备缓存，请手动把 %s 写入 removed.list" % target)

    if OVERLAY_TARGETS.is_file():
        for line in read_lines(OVERLAY_TARGETS):
            parts = line.split()
            if len(parts) >= 2 and target in parts[1:]:
                print("! overlay   : targets.txt 仍有 %s，若不再需要请手动删除对应 RRO" % target)

    if args.dry_run:
        print("（dry-run，未改动任何文件）")
        return

    for path in delete_dirs:
        shutil.rmtree(path)
    for path, lines, _ in xml_changes:
        write_lines(path, lines)
    for path, lines, _ in list_changes:
        write_lines(path, lines)

    blobs = prune_blobs()
    if blobs:
        print("blob        : 删除孤儿 %d 个" % blobs)

    if removed_names:
        entries = load_removed() | set(removed_names)
        write_lines(REMOVED_LIST, [REMOVED_HEADER]
                    + ["%s\n" % name for name in sorted(entries)])
        print("removed.list: 已更新，共 %d 条" % len(entries))


if __name__ == "__main__":
    main()
