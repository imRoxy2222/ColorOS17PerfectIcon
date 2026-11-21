#!/usr/bin/env python3
"""ColorOS 完美图标 Magisk 模块打包脚本。

Linux / macOS / Windows 通用，自动识别当前系统（其实只需要 Python 3）。
脚本会自动跳过 code/、doc/、script/、.git/ 等无关内容，在 dist/ 下生成可直接刷入的
Magisk 模块 zip。

用法：
    python3 script/打包成magisk模块.py             # 输出 dist/<id>-<commit数>.zip
    python3 script/打包成magisk模块.py -o out.zip  # 指定输出路径
    python3 script/打包成magisk模块.py -q          # 静默模式

Windows 上如果 python 不在 PATH，可尝试 `py script/打包成magisk模块.py`。
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 图标源目录：打包时不再散放这上万个文件，而是压成一个不压缩的内部归档，
# 安装脚本再按本机已安装的包名按需解出，避免刷写时逐个小文件写入。
ICON_ROOT = ROOT / "my_product" / "media" / "theme" / "uxicons" / "hdpi"
ICON_ARCHIVE_NAME = "icons.zip"

# 整个目录跳过（按目录名，任意层级匹配）
EXCLUDE_DIRS = {
    ".git", ".idea", ".vscode", ".trae", "__pycache__",
    "code", "doc", "dist", "script", "build",
}

# 指定文件名跳过
EXCLUDE_FILES = {
    ".gitignore", ".DS_Store", "Thumbs.db", "desktop.ini", "打包成magisk模块.py", "README.md",
    "update.json",
}

# 按扩展名跳过；uxicons.zip 例外（uninstall.sh 需要它）
EXCLUDE_SUFFIXES = {".zip", ".apk", ".pyc", ".pyo", ".log", ".swp"}
KEEP_ZIP = "uxicons.zip"

# 需要保留可执行权限的文件
EXECUTABLE_FILES = {
    "customize.sh", "post-fs-data.sh", "uninstall.sh", "service.sh",
    "icon_sync.sh", "action.sh", "update-binary",
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


def git_commit_count() -> int | None:
    """当前 HEAD 的 commit 数；不在 git 仓库时返回 None。"""
    try:
        result = subprocess.run(
            ["git", "rev-list", "--count", "HEAD"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=15,
        )
        if result.returncode != 0:
            return None
        return int(result.stdout.strip())
    except (OSError, ValueError, subprocess.SubprocessError):
        return None


def apply_commit_version(data: bytes, count: int) -> bytes:
    """把 module.prop 里的 version / versioncode 改写成 commit 数。"""
    lines = []
    for line in data.decode("utf-8").splitlines():
        key = line.split("=", 1)[0].strip() if "=" in line else ""
        if key in ("version", "versioncode"):
            line = f"{key}={count}"
        lines.append(line)
    return ("\n".join(lines) + "\n").encode("utf-8")


def iter_files(exclude_paths: set[Path]):
    """按稳定顺序遍历需要打包的文件。"""
    for dirpath, dirnames, filenames in os.walk(ROOT):
        current = Path(dirpath)
        # 图标目录单独打包成 icons.zip，不逐个写入模块 zip
        if current == ICON_ROOT or ICON_ROOT in current.parents:
            continue
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


def collect_icon_files() -> list[Path]:
    """hdpi 下的全部图标文件（按路径排序，保证产物稳定）。"""
    if not ICON_ROOT.is_dir():
        return []
    return sorted(p for p in ICON_ROOT.rglob("*") if p.is_file())


def write_icon_archive(dest: Path, icon_files: list[Path]) -> None:
    """把图标打成一个不压缩的 zip：安装时可按包名随机寻址，只读需要的条目。"""
    with zipfile.ZipFile(dest, "w", compression=zipfile.ZIP_STORED) as zf:
        for path in icon_files:
            info = zipfile.ZipInfo(path.relative_to(ICON_ROOT).as_posix())
            info.compress_type = zipfile.ZIP_STORED
            info.date_time = time.localtime(path.stat().st_mtime)[:6]
            info.external_attr = (0o100000 | 0o644) << 16
            zf.writestr(info, path.read_bytes())


def clean_dist(output: Path) -> int:
    """删除 dist 中除本次输出外的旧产物，返回删除数量。"""
    dist = ROOT / "dist"
    if not dist.is_dir():
        return 0
    removed = 0
    for entry in dist.iterdir():
        if entry.resolve() == output:
            continue
        if entry.is_dir() and not entry.is_symlink():
            shutil.rmtree(entry)
        else:
            entry.unlink()
        removed += 1
    return removed


def build(output: Path, quiet: bool = False) -> Path:
    missing = [f for f in REQUIRED_FILES if not (ROOT / f).is_file()]
    if missing:
        raise SystemExit("缺少必要文件：" + ", ".join(missing))

    props = parse_prop(ROOT / "module.prop")
    module_id = props.get("id", "module")
    version = props.get("version", "unknown")
    commit_count = git_commit_count()

    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    removed = clean_dist(output)
    if output.exists():
        output.unlink()
    exclude_paths = {output, ROOT / Path(__file__).name}

    files = list(iter_files(exclude_paths))
    icon_files = collect_icon_files()
    total_size = 0

    with tempfile.TemporaryDirectory(dir=output.parent) as tmpdir:
        icon_archive = Path(tmpdir) / ICON_ARCHIVE_NAME
        write_icon_archive(icon_archive, icon_files)
        icon_archive_size = icon_archive.stat().st_size

        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
            for path, rel in files:
                data = path.read_bytes()
                arcname = rel.as_posix()
                if arcname == "module.prop" and commit_count is not None:
                    data = apply_commit_version(data, commit_count)
                total_size += len(data)

                info = zipfile.ZipInfo(arcname)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.date_time = time.localtime(path.stat().st_mtime)[:6]
                mode = 0o755 if path.name in EXECUTABLE_FILES else 0o644
                info.external_attr = (0o100000 | mode) << 16  # 保留 Unix 权限位
                zf.writestr(info, data, compresslevel=9)

                if not quiet:
                    print(f"  + {arcname}")

            if icon_files:
                # 内部归档已是不压缩数据，外层也用 STORE，避免白耗 CPU 和解压时间
                zf.write(icon_archive, arcname=ICON_ARCHIVE_NAME, compress_type=zipfile.ZIP_STORED)
                total_size += icon_archive_size
                if not quiet:
                    print(f"  + {ICON_ARCHIVE_NAME}（{len(icon_files)} 个图标文件）")

    if not quiet:
        excluded = sorted(EXCLUDE_DIRS & {p.name for p in ROOT.iterdir() if p.is_dir()})
        print()
        print(f"系统      : {sys.platform}")
        print(f"模块      : {module_id} {commit_count if commit_count is not None else version}")
        print(f"文件数量  : {len(files)}（另有 {len(icon_files)} 个图标打进 {ICON_ARCHIVE_NAME}）")
        print(f"原始大小  : {total_size / 1024 / 1024:.2f} MiB")
        print(f"压缩后    : {output.stat().st_size / 1024 / 1024:.2f} MiB")
        print(f"输出      : {output}")
        if removed:
            print(f"已清理 dist: {removed} 项旧产物")
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
    parser.add_argument("-o", "--output", help="输出 zip 路径（默认 dist/<id>-<commit数>.zip）")
    parser.add_argument("-q", "--quiet", action="store_true", help="不打印文件清单")
    args = parser.parse_args()

    if args.output:
        output = Path(args.output)
    else:
        props = parse_prop(ROOT / "module.prop")
        commit_count = git_commit_count()
        version = commit_count if commit_count is not None else props.get("version", "unknown")
        name = f"{props.get('id', 'module')}-{version}.zip"
        output = ROOT / "dist" / name

    build(output, quiet=args.quiet)


if __name__ == "__main__":
    main()
