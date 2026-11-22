#!/usr/bin/env python3
"""把 hdpi 下逐字节相同的 PNG 收敛成共享 blob，并生成 dedupe.list。

用法：
    python3 script/生成图标去重清单.py --dry-run   # 只看统计，不改文件
    python3 script/生成图标去重清单.py             # 实际删除重复文件并生成清单

产物：
    dedupe_blobs/<md5>.png   每个重复组保留一份
    dedupe.list              每行“相对路径 blob 文件名”

安装时 customize.sh / service.sh 会按清单把 blob 复制回已安装应用的目录，
因此素材目录里可以安全地省略这些重复文件。脚本可重复执行：已有清单会保留，
只处理新出现的重复组。
"""

from __future__ import annotations

import argparse
import hashlib
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ICON_ROOT = ROOT / "my_product" / "media" / "theme" / "uxicons" / "hdpi"
BLOB_DIR = ROOT / "dedupe_blobs"
MANIFEST = ROOT / "dedupe.list"


def load_manifest():
    """读取已有清单，返回 {相对路径: blob 文件名}。"""
    entries = {}
    if not MANIFEST.is_file():
        return entries
    for line in MANIFEST.read_text(encoding="utf-8").splitlines():
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
    before = len(entries)

    groups = defaultdict(list)
    for path in sorted(ICON_ROOT.rglob("*.png")):
        digest = hashlib.md5(path.read_bytes()).hexdigest()
        groups[digest].append(path)

    dups = {digest: paths for digest, paths in groups.items() if len(paths) > 1}
    removed = 0
    removed_bytes = 0
    blob_bytes = 0

    for digest, paths in sorted(dups.items()):
        blob = BLOB_DIR / ("%s.png" % digest)
        new_blob = not blob.is_file()
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
        MANIFEST.write_text(
            "".join("%s %s\n" % (rel, entries[rel]) for rel in sorted(entries)),
            encoding="utf-8",
        )

    print("重复组      : %d" % len(dups))
    print("删除文件    : %d（%.2f MiB）" % (removed, removed_bytes / 1048576))
    print("新增 blob   : %.2f MiB" % (blob_bytes / 1048576))
    print("净回收      : %.2f MiB" % ((removed_bytes - blob_bytes) / 1048576))
    print("清单条目    : %d -> %d" % (before, len(entries)))
    if args.dry_run:
        print("（dry-run，未改动任何文件）")


if __name__ == "__main__":
    main()
