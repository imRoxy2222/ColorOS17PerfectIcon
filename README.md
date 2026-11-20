# ColorOS17 完美图标

为 ColorOS 补充第三方应用图标的 Magisk 模块，附带一个 Android 应用，用于查看哪些应用
已适配 / 未适配，并把手机上现有的图标素材导出成 zip，方便补做缺失的图标。

图标来源：上游模块 ColorOS16 完美图标 V16.2.8（作者：酷安 @眩晕丶 / @ZuyQA），
本项目在其基础上继续维护，新增适配应用、重做了配套应用。

## 功能

- **模块**：把图标素材同步到 `/data/oplus/uxicons/`，让桌面用 ART+ 重绘的图标生效，
  包含 1×1 与 1×2 / 2×1 / 2×2 大图标、暗色前景、单色图标。
- **应用（`完美图标.apk`）**：扫描手机桌面应用，标记「已适配 / 未适配」，支持筛选、搜索、
  多选导出（含分层素材、原始 APK 素材和一份说明生成暗色图标的 `README.md`）。

「已适配」的判定：图标包目录存在，且 `rec_night.png` 与 `recfg.png` 内容不同
（只复制一份前景当暗色素材不算适配）。

## 仓库结构

```
ColorOS17PerfectIcon/
├── module.prop                 # 模块信息
├── customize.sh                # 安装时同步图标（icon_sync.sh）
├── service.sh                  # 开机增量同步
├── build.py                    # 打包模块 zip（排除 code/、doc/、dist/）
├── my_product/media/theme/     # 模块主体
│   ├── allApps.xml             # 图标名 ↔ 包名 映射
│   ├── drawableMapping.xml     # 参与重绘的包名清单
│   ├── icon_version.xml        # uxicons 资源版本
│   └── uxicons/hdpi/           # 图标素材，一个包名一个目录
│       ├── morph_icons_whitelist.xml
│       └── <包名>/{recbg,recfg,rec_night,monochrome,...}.png
├── code/                       # Android 应用（Kotlin + Compose + Miuix）
└── doc/                        # 文档
```

## 安装模块

从 [Releases](../../releases) 下载 `完美图标模块_*.zip`，在 Magisk / KernelSU 里刷入后重启。
安装脚本只把**手机上已安装应用**的图标复制到 `/data/oplus/uxicons/`，开机时会再增量同步一次。

模块内置 `updateJson`（指向 `releases/latest/download/update.json`），发版后可以直接在
Magisk 管理器里检测并更新，不需要手动下载 zip。

## 文档

| 文档 | 内容 |
| --- | --- |
| [新增适配软件](doc/add-app.md) | 给一个新应用补图标：需要放哪些 PNG、要同步修改哪些 XML、如何验证 |
| [图标规范](doc/icon-spec.md) | `recbg` / `recfg` / `rec_night` / `monochrome` 及各尺寸的完整说明 |
| [发布流程](doc/release.md) | 打 tag 自动构建 APK 与模块、签名密钥与 Secrets 配置 |

## 构建

应用（需要 JDK 17 + Android SDK 36）：

```bash
cd code
./gradlew :app:assembleRelease     # 无签名配置时产出未签名包，CI 里会自动签名
```

模块：

```bash
python3 build.py                   # 输出 dist/ColorOSart-<version>.zip
python3 build.py -o out.zip        # 指定输出
```

## 许可证

[MIT](LICENSE)
