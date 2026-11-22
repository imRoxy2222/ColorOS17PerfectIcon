# ColorOS17 完美图标

为 ColorOS 补充第三方应用图标的 Magisk 模块，附带一个 Android 应用，导入模块 zip 后
即可查看哪些应用已适配 / 未适配，并把手机上现有的图标素材导出成 zip，方便补做缺失的图标。

图标来源：上游模块 ColorOS16 完美图标 V16.2.8（作者：酷安 @眩晕丶 / @ZuyQA），
本项目在其基础上继续维护，新增适配应用、重做了配套应用，并做了体积收敛。

## 功能

- **模块**：把图标素材同步到 `/data/oplus/uxicons/`，让桌面用 ART+ 重绘的图标生效，
  包含 1×1 与 1×2 / 2×1 / 2×2 大图标、暗色前景、单色图标。
- **仅经典风格**：只保留经典风格素材（`recbg` / `recfg` / `rec_night` 及大图标、
  `monochrome`、`art_off` 等）；`nsd` / `day` / `peb` / `mat` / `outline` 等整图风格已移除。
- **按需同步**：安装和开机只处理本机已安装的应用，不把全部素材写入 `/data`。
- **重复素材收敛**：`rec_night` 与 `recfg` 相同时由脚本在安装/开机时复制；
  其他逐字节相同的 PNG 收敛到 `dedupe_blobs/`，通过 `dedupe.list` 还原。
- **按需 RRO**：`overlay/` 里的静态 RRO 只在目标应用已安装时注入。
- **应用（`ColorOS17PerfectIcon.apk`）**：导入打包好的模块 zip，按 zip 内是否包含该包名标记
  「已适配 / 未适配」，支持筛选、搜索、多选导出（含分层素材、原始 APK 素材和一份
  说明生成暗色图标的 `README.md`）。

两种状态的含义：

| 状态 | 含义 |
| --- | --- |
| 已适配 | 导入的模块 zip 里有该包名的图标目录，暗色下桌面会换成模块里的素材 |
| 未适配 | 模块 zip 里没有该包名，暗色下仍是原来的图标 |

判定细节见 [适配状态判断](doc/适配状态判断.md)。

## 仓库结构

```text
ColorOS17PerfectIcon/
├── module.prop                 # 模块信息
├── customize.sh                # 安装：解压已安装应用图标 → 还原共享素材 → 同步 → 清理已删除适配
├── post-fs-data.sh             # 开机早期：绑定主题目录，按需注入静态 RRO
├── service.sh                  # 开机：增量同步新安装的应用，并清理 removed.list
├── uninstall.sh                # 卸载：用 uxicons.zip 重置 /data/oplus/uxicons
├── icon_sync.sh                # 安装/开机共用的同步、还原、清理函数
├── action.sh                   # 模块操作按钮：打开项目主页
├── dedupe.list                 # 共享图标清单：目标路径 → dedupe_blobs/<md5>.png
├── dedupe_blobs/               # 去重后保留的唯一 PNG
├── removed.list                # 已删除适配的目录名，安装/开机时从设备清理
├── uxicons.zip                 # 卸载时恢复 /data/oplus/uxicons 的备份，只在卸载时用
├── overlay/                    # 静态 RRO（activity-alias 等场景）
│   ├── targets.txt             # RRO 名 → 目标包名
│   └── *.apk.bin
├── script/                     # Python 脚本
│   ├── 打包成magisk模块.py      # 打包模块 zip（内部把 hdpi 压成 icons.zip）
│   ├── 生成图标去重清单.py      # 收敛重复 PNG，维护 dedupe.list / night_fill.list
│   ├── 删除图标适配.py          # 删除某个应用的适配并登记到 removed.list
│   ├── 检查图标适配.py          # 检查素材目录是否已登记到各 XML
│   └── 推送到手机.py            # 打包后通过 adb 推送到手机 Download
├── my_product/media/theme/     # 模块主体
│   ├── allApps.xml             # 图标名 ↔ 包名 映射
│   ├── drawableMapping.xml     # 参与重绘的包名清单
│   ├── icon_version.xml        # uxicons 资源版本
│   └── uxicons/
│       ├── night_fill.list     # rec_night ← 同目录 recfg 的还原清单
│       └── hdpi/               # 图标素材，一个包名一个目录
│           ├── morph_icons_whitelist.xml
│           └── <包名>/{recbg,recfg,rec_night,monochrome,...}.png
├── code/                       # Android 应用（Kotlin + Compose + Miuix）
└── doc/                        # 文档
```

`dist/`、`build/` 是构建产物，不参与版本管理。

## 安装模块

从 [Releases](../../releases) 下载 `ColorOS17PerfectIcon_<提交数>_apps<应用数>.zip`，
在 Magisk / KernelSU 里刷入后重启。首次安装约 1-5 分钟，安装脚本只把**手机上已安装应用**
的图标复制到 `/data/oplus/uxicons/`，开机时会再增量同步一次。当前模块 zip 约 170 MB。

- 覆盖安装即可升级，无需先卸载；`removed.list` 中的旧适配会在安装/开机时清理。
- `uxicons.zip` 是卸载备份，不要删除；卸载模块时会用它重置 `/data/oplus/uxicons`。
- 桌面仍显示旧图标时，重启桌面或手机；确认系统桌面已获得 ROOT 权限、图标风格为「经典」、
  ART+ 图标已开启。

模块内置 `updateJson`（指向 `releases/latest/download/update.json`），发版后可以直接在
Magisk 管理器里检测并更新，不需要手动下载 zip。

## 文档

| 文档 | 内容 |
| --- | --- |
| [新增适配软件](doc/新增适配软件.md) | 给一个新应用补图标：需要放哪些 PNG、要同步修改哪些 XML、如何收敛重复素材与验证 |
| [删除适配](doc/删除适配.md) | 删除某个应用的适配：仓库清理、设备清理、`removed.list` 与重新适配 |
| [利用配套软件快速适配](doc/利用配套软件快速适配部分应用.md) | 在配套软件里导出未适配应用的原始素材，交给 AI 快速补图标 |
| [PR 提交规则](doc/PR提交规则.md) | 提交粒度、检查脚本、1×1 亮暗素材要求与图标质量规范 |
| [适配状态判断](doc/适配状态判断.md) | 如何从导入的模块 zip 判断「已适配 / 未适配」、支持的打包布局、已知局限 |
| [图标规范](doc/图标规范.md) | `recbg` / `recfg` / `rec_night` / `monochrome` 及各尺寸的完整说明 |
| [闲鱼图标特例与静态 RRO 方案](doc/闲鱼图标特例与静态RRO方案.md) | `activity-alias` 入口导致常规素材无效的原因，以及按需注入静态 RRO 的做法与复用步骤 |
| [发布流程](doc/发布流程.md) | 打数字 tag 自动构建 APK 并发布模块、签名密钥与 Secrets 配置 |

## 构建

应用（需要 JDK 17 + Android SDK 36）：

```bash
cd code
./gradlew :app:assembleRelease     # 无签名配置时产出未签名包，CI 里会自动签名
```

模块：

```bash
python3 script/打包成magisk模块.py              # 输出 dist/ColorOSPerfectIcon-<commit数>.zip
python3 script/打包成magisk模块.py -o out.zip   # 指定输出
python3 script/推送到手机.py                    # 打包并 adb 推送到手机 Download
```

新增或修改图标后，先收敛重复素材并检查登记：

```bash
python3 script/生成图标去重清单.py --dry-run
python3 script/生成图标去重清单.py
python3 script/检查图标适配.py --assets --pkg <包名关键词>
```

Release 产物名与本地打包不同，统一为 `ColorOS17PerfectIcon_<提交数>_apps<应用数>.zip`，
由 [发布流程](doc/发布流程.md) 中的 GitHub Actions 生成。

## 许可证

[MIT](LICENSE)
