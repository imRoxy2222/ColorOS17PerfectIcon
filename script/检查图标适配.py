#!/usr/bin/env python3
"""检查 uxicons 图标目录是否已登记到各 XML 配置。

用途：新增适配应用后，确认 my_product/media/theme/uxicons/hdpi 下的每个
应用目录都写进了 allApps.xml、drawableMapping.xml；如果目录里提供了大图标
素材，还应写进 morph_icons_whitelist.xml。

用法：
    python3 script/检查图标适配.py                 # 检查全部目录
    python3 script/检查图标适配.py --pkg 关键词     # 只检查名字包含关键词的目录
    python3 script/检查图标适配.py --assets        # 额外检查 1×1 三件套是否齐全
    python3 script/检查图标适配.py --strict        # 警告也视为失败

退出码：0 = 通过；1 = 有错误（--strict 时警告也算错误）。
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEME = ROOT / "my_product" / "media" / "theme"
HDPI = THEME / "uxicons" / "hdpi"
NIGHT_FILL = THEME / "uxicons" / "night_fill.list"
ALL_APPS = THEME / "allApps.xml"
DRAWABLE = THEME / "drawableMapping.xml"
MORPH = HDPI / "morph_icons_whitelist.xml"

# 新增适配的最低素材要求：1×1 的亮色底板、亮色前景、暗色前景
REQUIRED_ICONS = ("recbg.png", "recfg.png", "rec_night.png")
# 只要出现其中任意一张，就当作该应用提供了大图标素材
LARGE_ICONS = (
    "recbg_1x2.png", "recfg_1x2.png",
    "recbg_2x1.png", "recfg_2x1.png",
    "recbg_2x2.png", "recfg_2x2.png",
)


def load_packages(path, tag):
    """读取 XML 里指定标签的 package 属性集合。"""
    try:
        root = ET.parse(path).getroot()
    except OSError as exc:
        raise SystemExit("无法读取 %s：%s" % (path, exc))
    except ET.ParseError as exc:
        raise SystemExit("XML 解析失败 %s：%s" % (path, exc))

    packages = set()
    for element in root.iter(tag):
        value = element.get("package")
        if value:
            packages.add(value.strip())
    return packages


def load_night_fill(path):
    """读取瘦身清单：这些 rec_night 会在安装时由 recfg 复制生成。"""
    try:
        return {line.strip() for line in path.read_text().splitlines() if line.strip()}
    except OSError:
        return set()


def load_whitelist(path):
    """读取 morph 白名单里的 package_name 文本。"""
    try:
        root = ET.parse(path).getroot()
    except OSError as exc:
        raise SystemExit("无法读取 %s：%s" % (path, exc))
    except ET.ParseError as exc:
        raise SystemExit("XML 解析失败 %s：%s" % (path, exc))

    packages = set()
    for element in root.iter("package_name"):
        if element.text:
            packages.add(element.text.strip())
    return packages


def is_ignored(name):
    """主题商店/临时/备份目录不参与检查。"""
    return (
        name.startswith(".")
        or name.startswith("plus.")
        or name.startswith("temp_")
        or name.endswith(".bak")
    )


def is_covered(name, packages):
    """判断目录名是否已登记。

    支持三种目录名：
    - 纯包名：com.example.app
    - 包名 + Activity：com.example.app.ui.LaunchActivity（按去掉最后一段的包名匹配）
    - 多包名合并：A和B（要求两侧都登记）
    """
    if name in packages:
        return True

    if "和" in name:
        return all(is_covered(part, packages) for part in name.split("和"))

    head, _, last = name.rpartition(".")
    if head and last[:1].isupper() and head in packages:
        return True

    return False


def covered_by(name, packages, label):
    """返回覆盖情况说明；未覆盖时返回错误文案。"""
    if is_covered(name, packages):
        return None
    return "未写入 %s" % label


def main():
    parser = argparse.ArgumentParser(
        description="检查 uxicons 图标目录是否已登记到各 XML 配置",
    )
    parser.add_argument(
        "--pkg",
        help="只检查目录名包含该关键词的应用（例如 com.example）",
    )
    parser.add_argument(
        "--assets",
        action="store_true",
        help="额外检查每个目录是否有 recbg / recfg / rec_night 三件套",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="把警告也视为错误（退出码为 1）",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="列出每条警告的详情（默认只显示数量）",
    )
    args = parser.parse_args()

    for path in (ALL_APPS, DRAWABLE, MORPH):
        if not path.is_file():
            raise SystemExit("缺少配置文件：%s" % path)
    if not HDPI.is_dir():
        raise SystemExit("缺少图标目录：%s" % HDPI)

    all_apps = load_packages(ALL_APPS, "icon")
    drawable = load_packages(DRAWABLE, "item")
    whitelist = load_whitelist(MORPH)
    night_fill = load_night_fill(NIGHT_FILL)

    directories = sorted(
        (p for p in HDPI.iterdir() if p.is_dir() and not is_ignored(p.name)),
        key=lambda p: p.name,
    )
    if args.pkg:
        directories = [p for p in directories if args.pkg in p.name]
    if not directories:
        print("没有找到需要检查的应用目录")
        return 1

    errors = {}
    warnings = {}

    for path in directories:
        name = path.name

        problems = []
        missing = covered_by(name, all_apps, "allApps.xml")
        if missing:
            problems.append(missing)
        missing = covered_by(name, drawable, "drawableMapping.xml")
        if missing:
            problems.append(missing)
        if problems:
            errors[name] = problems

        if args.assets:
            missing_icons = []
            for f in REQUIRED_ICONS:
                if (path / f).is_file():
                    continue
                # 与 recfg 逐字节相同的 rec_night 已从素材中省略，安装时会自动补回
                if f == "rec_night.png" and "%s/%s" % (name, f) in night_fill:
                    continue
                missing_icons.append(f)
            if missing_icons:
                errors.setdefault(name, []).append(
                    "缺少 1×1 素材：" + "、".join(missing_icons)
                )

        has_large = any((path / f).is_file() for f in LARGE_ICONS)
        if has_large and not is_covered(name, whitelist) and name not in errors:
            warnings.setdefault(name, []).append(
                "有大图标素材，但未写入 morph_icons_whitelist.xml"
            )

    for name in sorted(errors):
        print("[错误] %s" % name)
        for problem in errors[name]:
            print("  - %s" % problem)

    if args.verbose or (args.strict and warnings):
        for name in sorted(warnings):
            if name in errors:
                continue
            print("[警告] %s" % name)
            for problem in warnings[name]:
                print("  - %s" % problem)

    print()
    print("配置文件：allApps.xml %d 条，drawableMapping.xml %d 条，morph 白名单 %d 条"
          % (len(all_apps), len(drawable), len(whitelist)))
    print("检查目录：%d 个" % len(directories))
    print("结果：错误 %d 个，警告 %d 个" % (len(errors), len(warnings)))
    if warnings and not args.verbose and not args.strict:
        print("警告详情可加 -v 查看")

    failed = bool(errors) or (args.strict and bool(warnings))
    if failed:
        print("检查未通过")
        return 1
    print("检查通过")
    return 0


if __name__ == "__main__":
    # Windows 控制台默认 GBK，避免中文输出报错
    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    sys.exit(main())
