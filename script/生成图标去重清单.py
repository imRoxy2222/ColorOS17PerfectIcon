#!/usr/bin/env python3
"""收敛 hdpi 下的重复图标：rec_night 走 night_fill，其余走 dedupe_blobs。

用法：
    python3 script/生成图标去重清单.py --dry-run   # 只看统计，不改文件
    python3 script/生成图标去重清单.py             # 实际删除并生成清单

处理规则：
    rec_night*.png 与同目录 recfg*.png 完全相同 -> 记入 night_fill.list，
        删除 rec_night，安装时由 recfg 复制还原（不额外占空间）；
    其余逐字节相同的 PNG -> 收敛到 dedupe_blobs/<md5>.png，
        映射写入 dedupe.list，安装时按清单还原。

脚本可重复执行：已有清单和 blob 会保留，新出现的重复项会被追加。
"""

from __future__ import annotations

import argparse
import hashlib
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ICON_ROOT = ROOT / "my_product" / "media" / "theme" / "uxicons" / "hdpi"
NIGHT_LIST = ROOT / "my_product" / "media" / "theme" / "uxicons" / "night_fill.list"
BLOB_DIR = ROOT / "dedupe_blobs"
MANIFEST = ROOT / "dedupe.list"

# 与 fill_night_icons 保持一致：暗色前景 -> 同目录的亮色前景
NIGHT_PAIRS = {
    "rec_night.png": "recfg.png",
    "rec_night_1x2.png": "recfg_1x2.png",
    "rec_night_2x1.png": "recfg_2x1.png",
    "rec_night_2x2.png": "recfg_2x2.png",
}


def load_lines(path):
    """读取按行存储的清单，返回集合。"""
    try:
        return {line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()}
    except OSError:
        return set()


def load_manifest():
    """读取已有去重清单，返回 {相对路径: blob 文件名}。"""
    entries = {}
    try:
        lines = MANIFEST.read_text(encoding="utf-8").splitlines()
    except OSError:
        return entries
    for line in lines:
        parts = line.split()
        if len(parts) == 2:
            entries[parts[0]] = parts[1]
    return entries


def main() -> None:
    parser = argparse.ArgumentParser(description="生成图标去重清单")
    parser.add_argument("--dry-run", action="store_true", help="只统计，不删除、不写入")
    args = parser.parse_args()

    if not ICON_ROOT.is_dir():
        raise SystemExit("图标目录不存在：%s" % ICON_ROOT)

    entries = load_manifest()
    night = load_lines(NIGHT_LIST)
    before_entries, before_night = len(entries), len(night)

    all_pngs = sorted(ICON_ROOT.rglob("*.png"))

    # 第一轮：rec_night 与同目录 recfg 完全相同的，交给 night_fill 处理
    night_now = set()
    night_bytes = 0
    for path in all_pngs:
        fg_name = NIGHT_PAIRS.get(path.name)
        if not fg_name:
            continue
        fg = path.with_name(fg_name)
        if not fg.is_file():
            continue
        if path.stat().st_size != fg.stat().st_size:
            continue
        if path.read_bytes() != fg.read_bytes():
            continue

        rel = path.relative_to(ICON_ROOT).as_posix()
        night.add(rel)
        entries.pop(rel, None)  # 同一个文件不再由 dedupe 负责
        night_now.add(path)
        night_bytes += path.stat().st_size
        if not args.dry_run:
            path.unlink()

    # 第二轮：其余 PNG 按内容分组；已有 blob 也参与比对
    blobs = {p.stem: p for p in BLOB_DIR.glob("*.png")} if BLOB_DIR.is_dir() else {}
    groups = defaultdict(list)
    for path in all_pngs:
        if path in night_now:
            continue
        digest = hashlib.md5(path.read_bytes()).hexdigest()
        groups[digest].append(path)

    # 两个以上文件相同，或单个文件与已有 blob 相同，都收敛
    dups = {
        digest: paths
        for digest, paths in groups.items()
        if len(paths) > 1 or digest in blobs
    }
    removed = 0
    removed_bytes = 0
    blob_bytes = 0

    for digest, paths in sorted(dups.items()):
        blob = BLOB_DIR / ("%s.png" % digest)
        new_blob = digest not in blobs
        if not args.dry_run:
            BLOB_DIR.mkdir(parents=True, exist_ok=True)
            if new_blob:
                blob.write_bytes(paths[0].read_bytes())
        if new_blob:
            blob_bytes += paths[0].stat().st_size
        for path in paths:
            rel = path.relative_to(ICON_ROOT).as_posix()
            entries[rel] = blob.name
            removed += 1
            removed_bytes += path.stat().st_size
            if not args.dry_run:
                path.unlink()

    if not args.dry_run:
        NIGHT_LIST.write_text(
            "".join("%s\n" % rel for rel in sorted(night)),
            encoding="utf-8",
        )
        MANIFEST.write_text(
            "".join("%s %s\n" % (rel, entries[rel]) for rel in sorted(entries)),
            encoding="utf-8",
        )

    print("night_fill  : 本次 %d 个（%.2f MiB），清单 %d -> %d"
          % (len(night_now), night_bytes / 1048576, before_night, len(night)))
    print("重复组      : %d" % len(dups))
    print("删除文件    : %d（%.2f MiB）" % (removed, removed_bytes / 1048576))
    print("新增 blob   : %.2f MiB" % (blob_bytes / 1048576))
    print("净回收      : %.2f MiB"
          % ((night_bytes + removed_bytes - blob_bytes) / 1048576))
    print("清单条目    : dedupe %d -> %d，night_fill %d -> %d"
          % (before_entries, len(entries), before_night, len(night)))
    if args.dry_run:
        print("（dry-run，未改动任何文件）")


if __name__ == "__main__":
    main()
