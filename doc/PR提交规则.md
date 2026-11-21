# PR 提交规则

本文档适用于所有新增或更新应用图标的 Pull Request。提交前请逐条核对，不符合规则的 PR 会被要求修改。

---

## 1. 合并方式：只允许 Rebase

- PR **只允许使用 Rebase 方式合并**，禁止 Merge commit（创建合并提交）、Squash（压缩成一个提交）等其他合并方式；
- 提交前请先把分支 rebase 到最新 `main`，保证历史是一条直线：

  ```bash
  git fetch origin
  git rebase origin/main
  git push --force-with-lease
  ```

- 如果分支里已经出现 Merge commit，请先 rebase 整理掉再提交 PR。

---

## 2. 提交粒度：一个 commit 只适配一个应用

- **一个 commit 只能包含一个应用**的适配内容：该应用的图标目录（`recbg.png`、`recfg.png`、`rec_night.png` 等）以及对应的 `allApps.xml`、`drawableMapping.xml`（如涉及大图标还有 `morph_icons_whitelist.xml`）修改。
- **一个 PR 可以包含多个 commit**，每个 commit 对应一个应用，便于逐个审查和回滚。
- 不要把多个应用的图标、XML 修改混在同一个 commit 里。
- 新增适配软件时，commit message 固定使用以下格式（冒号为英文冒号，后跟一个空格）：

  ```text
  新增适配: 软件名（软件包名）
  新增适配: 微信（com.tencent.mm）
  更新适配: QQ（com.tencent.mobileqq）
  ```

- `A和B` 这种一个目录覆盖两个包名的情况，按一个应用处理。

---

## 3. 必须通过适配检查脚本

在仓库根目录执行：

```bash
# 检查所有目录的 XML 登记情况，警告也视为失败
python3 script/检查图标适配.py --strict

# 检查本次新增应用的三件套素材
python3 script/检查图标适配.py --pkg <包名关键词> --assets --strict -v
```

要求：

- 两条命令都必须 **退出码为 0**；
- 输出中不能出现 `[错误]`，也不能出现 `[警告]`；
- 建议把第二条命令的完整输出贴到 PR 描述里。

说明：

- `--strict` 会让警告也导致失败，对应「没有 warning 也没有 error」的要求；
- `--assets` 会检查 `recbg.png`、`recfg.png`、`rec_night.png` 是否齐全；`rec_night.png` 可以与 `recfg.png` 相同（亮暗只差系统底板时这是正常做法）；
- 全量执行 `--assets` 会暴露仓库里历史素材的问题，因此素材检查只针对本次新增或修改的应用（`--pkg`）。

---

## 4. 必须适配 1×1，亮色与暗色都要有

- 每个新增应用必须提供 1×1 的三件套：

  | 文件 | 作用 |
  | --- | --- |
  | `recbg.png` | 亮色底板 |
  | `recfg.png` | 亮色前景 |
  | `rec_night.png` | 暗色前景 |

- 亮色和暗色**必须同时适配**，不能只交亮色；
- `rec_night.png` 必须存在；如果亮暗只差系统底板，它可以与 `recfg.png` 相同；如果前景本身需要变色，则提供对应的暗色前景；
- 1×2 / 2×1 / 2×2 大图标、异形文件夹、`monochrome` 单色图标均为**可选**，不做强制要求；
- 尺寸、透明通道、目录命名等细节见 [图标规范](图标规范.md) 的 4.1 节。

---

## 5. 图标质量要求

- **禁止牛皮癣**：图标内不得出现广告、二维码、网址、联系方式、促销文字、水印、角标、边框等与品牌无关的内容。
- **保持简洁**：图形居中、留白合理，不堆叠无关元素，缩小后仍能辨认。
- **保留原始主题色**：以应用官方图标或品牌主色为基础，不得随意改色、换色或做成与官方差异过大的版本。
- 前景图形四角透明，不贴边、不被裁切；底板铺满画布。
- 与官方图标明显不符、明显是二次创作或带有推广信息的素材，审核会直接拒绝。

---

## 6. 提交前自检清单

- [ ] PR 分支已 rebase 到最新 `main`，历史中没有 Merge commit（合并时只用 Rebase）
- [ ] commit message 使用 `新增适配: 软件名（软件包名）` 格式
- [ ] 每个 commit 只包含一个应用
- [ ] 已补齐 `recbg.png`、`recfg.png`、`rec_night.png`
- [ ] `rec_night.png` 已提供；若前景需要变色，暗色下清晰可辨
- [ ] 已写入 `allApps.xml` 和 `drawableMapping.xml`
- [ ] 提供大图标素材时，已写入 `morph_icons_whitelist.xml`
- [ ] `python3 script/检查图标适配.py --strict` 通过，无错误、无警告
- [ ] `python3 script/检查图标适配.py --pkg <包名> --assets --strict -v` 通过
- [ ] 图标无广告、无水印、无二维码，简洁且保留品牌主题色
- [ ] 目录名与包名完全一致（区分大小写）

新增应用需要改哪些文件、如何验证，见 [新增适配软件](新增适配软件.md)；如何判断一个应用是否已适配，见 [适配状态判断](适配状态判断.md)。
