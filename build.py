#!/usr/bin/env python3
"""ColorOS 完美图标 Magisk 模块打包脚本。

Linux / macOS / Windows 通用，自动识别当前系统（其实只需要 Python 3）。
脚本会自动跳过 code/、doc/、.git/ 等无关内容，在 dist/ 下生成可直接刷入的
Magisk 模块 zip。

用法：
    python build.py                # 输出 dist/<id>-<version>.zip
    python build.py -o out.zip     # 指定输出路径
    python build.py -q             # 静默模式

Windows 上如果 python 不在 PATH，可尝试 `py build.py`。
"""

from __future__ import annotations

import argparse
import os
import sys
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# 整个目录跳过（按目录名，任意层级匹配）
EXCLUDE_DIRS = {
    ".git", ".idea", ".vscode", ".trae", "__pycache__",
    "code", "doc", "dist",
}

# 指定文件名跳过
EXCLUDE_FILES = {
    ".gitignore", ".DS_Store", "Thumbs.db", "desktop.ini", "build.py",
}

# 按扩展名跳过；uxicons.zip 例外（uninstall.sh 需要它）
EXCLUDE_SUFFIXES = {".zip", ".pyc", ".pyo", ".log", ".swp"}
KEEP_ZIP = "uxicons.zip"

# 需要保留可执行权限的文件
EXECUTABLE_FILES = {
    "customize.sh", "post-fs-data.sh", "uninstall.sh", "service.sh",
    "icon_sync.sh", "update-binary",
}

# 打包前必须存在的文件
REQUIRED_FILES = [
    "module.prop",
    "customize.sh",
    "META-INF/com/google/android/update-binary",
    "META-INF/com/google/android/updater-script",
]

# 隐藏文件默认跳过，但这些必须保留
KEEP_HIDDEN = {".nomedia"}


def parse_prop(path: Path) -> dict[str, str]:
    """读取 Magisk module.prop 的 key=value。"""
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def iter_files(exclude_paths: set[Path]):
    """按稳定顺序遍历需要打包的文件。"""
    for dirpath, dirnames, filenames in os.walk(ROOT):
        current = Path(dirpath)
        dirnames[:] = sorted(
            d for d in dirnames
            if d not in EXCLUDE_DIRS and not d.startswith(".")
        )
        for name in sorted(filenames):
            path = current / name
            if path.resolve() in exclude_paths:
                continue
            if name in EXCLUDE_FILES:
                continue
            if name.startswith(".") and name not in KEEP_HIDDEN:
                continue
            if path.suffix.lower() in EXCLUDE_SUFFIXES and name != KEEP_ZIP:
                continue
            yield path, path.relative_to(ROOT)


def build(output: Path, quiet: bool = False) -> Path:
    missing = [f for f in REQUIRED_FILES if not (ROOT / f).is_file()]
    if missing:
        raise SystemExit("缺少必要文件：" + ", ".join(missing))

    props = parse_prop(ROOT / "module.prop")
    module_id = props.get("id", "module")
    version = props.get("version", "unknown")

    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    exclude_paths = {output, ROOT / Path(__file__).name}

    files = list(iter_files(exclude_paths))
    total_size = 0

    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path, rel in files:
            data = path.read_bytes()
            total_size += len(data)

            arcname = rel.as_posix()
            info = zipfile.ZipInfo(arcname)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.date_time = time.localtime(path.stat().st_mtime)[:6]
            mode = 0o755 if path.name in EXECUTABLE_FILES else 0o644
            info.external_attr = (0o100000 | mode) << 16  # 保留 Unix 权限位
            zf.writestr(info, data, compresslevel=9)

            if not quiet:
                print(f"  + {arcname}")

    if not quiet:
        excluded = sorted(EXCLUDE_DIRS & {p.name for p in ROOT.iterdir() if p.is_dir()})
        print()
        print(f"系统      : {sys.platform}")
        print(f"模块      : {module_id} {version}")
        print(f"文件数量  : {len(files)}")
        print(f"原始大小  : {total_size / 1024 / 1024:.2f} MiB")
        print(f"压缩后    : {output.stat().st_size / 1024 / 1024:.2f} MiB")
        print(f"输出      : {output}")
        if excluded:
            print(f"已排除目录: {', '.join(excluded)}")

    return output


def main() -> None:
    # Windows 控制台默认 GBK，避免中文输出报错
    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

    parser = argparse.ArgumentParser(description="打包 ColorOS 完美图标 Magisk 模块")
    parser.add_argument("-o", "--output", help="输出 zip 路径（默认 dist/<id>-<version>.zip）")
    parser.add_argument("-q", "--quiet", action="store_true", help="不打印文件清单")
    args = parser.parse_args()

    if args.output:
        output = Path(args.output)
    else:
        props = parse_prop(ROOT / "module.prop")
        name = f"{props.get('id', 'module')}-{props.get('version', 'unknown')}.zip"
        output = ROOT / "dist" / name

    build(output, quiet=args.quiet)


if __name__ == "__main__":
    main()
