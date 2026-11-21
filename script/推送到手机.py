#!/usr/bin/env python3
"""打包 Magisk 模块并推送到手机 Download 目录。

流程：打包模块 zip → 检查 adb 是否已有设备 → 没有则连接到下面配置的地址 →
推送 zip 到手机 Download 目录。

用法：
    python3 script/推送到手机.py
    python3 script/推送到手机.py -q   # 打包时不打印文件清单

改手机地址 / 推送目录时，直接改下面的全局变量。
"""

from __future__ import annotations

import argparse
import importlib.util
import subprocess
import sys
from pathlib import Path

# ===== 按需修改 =====
ADB_ADDRESS = "192.168.2.140:33445"
REMOTE_DIR = "/sdcard/Download"
# ====================

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_DIR = ROOT / "script"


def load_packer():
    """载入同目录的打包脚本，复用它的命名和打包逻辑。"""
    path = SCRIPT_DIR / "打包成magisk模块.py"
    spec = importlib.util.spec_from_file_location("magisk_packer", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def default_output(packer) -> Path:
    """与打包脚本命令行默认输出保持一致。"""
    props = packer.parse_prop(packer.ROOT / "module.prop")
    commit_count = packer.git_commit_count()
    version = commit_count if commit_count is not None else props.get("version", "unknown")
    return packer.ROOT / "dist" / f"{props.get('id', 'module')}-{version}.zip"


def adb(*args: str, capture: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["adb", *args],
        capture_output=capture,
        text=True,
    )


def list_devices() -> list[str]:
    """当前处于 device 状态的设备序列号。"""
    result = adb("devices")
    if result.returncode != 0:
        raise SystemExit("执行 adb devices 失败：" + (result.stderr or "").strip())
    devices = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

    parser = argparse.ArgumentParser(description="打包 Magisk 模块并推送到手机")
    parser.add_argument("-q", "--quiet", action="store_true", help="打包时不打印文件清单")
    args = parser.parse_args()

    # 1. 打包模块
    print("== 打包模块 ==")
    packer = load_packer()
    zip_path = default_output(packer)
    packer.build(zip_path, quiet=args.quiet)
    print(f"模块包：{zip_path}（{zip_path.stat().st_size / 1024 / 1024:.2f} MiB）")
    print()

    # 2. 确认 adb 设备
    print("== 检查 adb 设备 ==")
    if not list_devices():
        print(f"没有在线设备，尝试连接 {ADB_ADDRESS} ...")
        result = adb("connect", ADB_ADDRESS)
        print((result.stdout or "").strip() or (result.stderr or "").strip())
    devices = list_devices()
    if not devices:
        raise SystemExit("仍然没有可用设备，请确认手机已开启无线调试、地址和端口正确。")
    print("在线设备：" + "、".join(devices))
    print()

    # 3. 推送
    print("== 推送到手机 ==")
    remote = f"{REMOTE_DIR.rstrip('/')}/{zip_path.name}"
    result = adb("push", str(zip_path), remote)
    if result.returncode != 0:
        raise SystemExit("推送失败：" + (result.stderr or result.stdout or "").strip())
    print(f"已推送：{remote}")


if __name__ == "__main__":
    main()
