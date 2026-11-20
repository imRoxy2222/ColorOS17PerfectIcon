# 发布流程（GitHub Actions）

本仓库采用单分支（`master`）结构：仓库根目录是 Magisk 模块，`code/` 是配套 Android 应用。
打一个 `v*` tag 即可由 GitHub Actions 自动产出 **APK + 模块 zip** 并创建 Release。

```
ColorOS17PerfectIcon/
├── .github/workflows/release.yml   # 发布流水线
├── module.prop                     # 模块信息（version 决定模块 zip 名）
├── build.py                        # 模块打包脚本（自动排除 code/、doc/、dist/）
├── my_product/                     # 模块素材
└── code/                           # Android 应用（Gradle 工程）
```

---

## 1. 一次性准备：签名密钥与 Secrets

APK 由 Actions 构建并签名，密钥保存在仓库 Secrets 中。本机已经生成好一套，位于：

```
~/c17sig/c17-release.jks     # 密钥库（PKCS12，RSA 2048，有效期 10000 天，别名 coloros17）
~/c17sig/SECRETS.txt         # 4 个值的明文，直接逐条复制到 GitHub
```

打开仓库 → **Settings → Secrets and variables → Actions → New repository secret**，逐个添加：

| Secret 名称 | 对应 `SECRETS.txt` 中的行 | 说明 |
| --- | --- | --- |
| `C17_KEYSTORE_B64` | `C17_KEYSTORE_B64=` 后面那一整行 | 密钥库的 Base64，单行，不要换行 |
| `C17_STORE_PASSWORD` | `C17_STORE_PASSWORD=` | 密钥库密码 |
| `C17_KEY_ALIAS` | `C17_KEY_ALIAS=` | 固定为 `coloros17` |
| `C17_KEY_PASSWORD` | `C17_KEY_PASSWORD=` | 私钥密码（与库密码相同） |

> 密钥库文件本身不要提交到仓库，也不要贴到 Issue / PR。丢失后无法覆盖升级已安装的应用，
> 请把 `~/c17sig` 另外备份一份。

工作流读取的环境变量名是 `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，
由 workflow 从上面 4 个 secret 映射过去，见 `.github/workflows/release.yml` 的 `env:` 段。

---

## 2. 日常发版

1. **版本号规则**（都不用手改）：
   - APP 的 `versionName` 固定为 `1.0`，`versionCode` 自动取 `git rev-list --count HEAD`；
   - 模块 `module.prop` 的 `version` / `versioncode` 在打包时由 `build.py` 改写成同一个 commit 数，
     仓库里的文件保留原值即可。
2. 提交并推送到 `master`。
3. 打 tag。tag 是**纯数字**，从 `1` 开始依次递增（`1`、`2`、`3` …），不是 `v16.2.9` 这种：

   ```bash
   git tag 6
   git push origin master
   git push origin 6
   ```

   推送 tag 后会自动触发 `.github/workflows/release.yml`。流水线会先校验：
   - tag 必须是数字且 ≥ 1；
   - 打 tag `N` 时，仓库里必须已经存在 tag `N-1`（例如打 `6` 必须先有 `5`），否则直接失败，不会创建 Release。

4. 打开仓库 **Actions → Release** 查看进度；完成后 **Releases** 页面会出现新版本，包含：

   | 产物 | 说明 |
   | --- | --- |
   | `完美图标.apk` | 已用 `coloros17` 密钥签名的 release APK |
   | `完美图标模块_<commit数>_适配<数量>.zip` | 可直接刷入的 Magisk 模块 |
   | `update.json` | Magisk 管理器的更新清单，固定通过 `releases/latest/download/update.json` 访问 |

   文件名规则：
   - `commit数`：`git rev-list --count HEAD`，与 APP 的 `versionCode`、模块的 `version` 同源，随提交数递增；
   - `数量`：`my_product/media/theme/uxicons/hdpi/` 下实际覆盖的包名数。统计时会自动
     跳过 `temp_*`、`plus.*`、`icons-*`、`.trashed*`、`*.bak`、无点号的杂项目录，
     把“包名.Activity”目录归并到包名，`A和B` 这种目录按 2 个应用计数。
   - `update.json` 里 `version` / `versionCode` 同样是 commit 数，`zipUrl` 指向本次发布的模块 zip，
     `changelog` 指向本次 Release 页面。`module.prop` 中的 `updateJson` 永远指向 latest，
     所以每次发版后 Magisk 管理器都能检测到新版本。

   没有打 tag 时（手动 `Run workflow`）只上传 Actions Artifacts，不会创建 Release。

---

## 3. 流水线做了什么

1. `actions/checkout` 且 **`fetch-depth: 0`**（并拉取全部 tag）—— versionCode 依赖完整提交历史，浅克隆会算成 1。
2. 校验 tag：必须是数字，且必须存在上一个 tag（`N-1`）。
3. 安装 JDK 17、Android SDK（`platforms;android-36`、`build-tools;36.0.0`）。
4. 把 `C17_KEYSTORE_B64` 解码到 `$RUNNER_TEMP/c17-release.jks`。
5. `./gradlew :app:assembleRelease`，签名配置读取环境变量（见 `code/app/build.gradle.kts` 的 `signingConfigs`）。
6. `apksigner verify` 校验签名，并把 APK 重命名为 `完美图标.apk`。
7. 统计 `my_product/media/theme/uxicons/hdpi/` 覆盖的包名数，用 `python3 build.py`
   打包模块 zip（自动排除 `code/`、`doc/`、`dist/`、`.github/`，并把模块版本写成 commit 数）。
8. 生成 `update.json`：`version` / `versionCode` 为 commit 数，`zipUrl` 指向本次模块 zip。
9. 用 `softprops/action-gh-release` 创建 Release 并附加三个文件。

---

## 4. 本地等价构建

需要签名时先把环境变量指向本地密钥库：

```bash
export KEYSTORE_FILE="$HOME/c17sig/c17-release.jks"
export KEYSTORE_PASSWORD="$(grep '^C17_STORE_PASSWORD=' "$HOME/c17sig/SECRETS.txt" | cut -d= -f2)"
export KEY_ALIAS=coloros17
export KEY_PASSWORD="$KEYSTORE_PASSWORD"

cd code && ./gradlew :app:assembleRelease
# 产物：code/app/build/outputs/apk/release/app-release.apk

cd .. && python3 build.py            # 产物：dist/ColorOSart-<version>.zip
```

---

## 5. 常见问题

- **versionCode 变成 1**：workflow 的 checkout 没有 `fetch-depth: 0`，或本地是浅克隆（`git fetch --unshallow`）。
- **Gradle 下载慢/失败**：wrapper 使用腾讯镜像，依赖走阿里云镜像（`code/settings.gradle.kts`）。
  若镜像不可用，可把 `distributionUrl` 换成 `https://services.gradle.org/distributions/...`。
- **`SigningConfig` 找不到 / APK 未签名**：确认 4 个 secret 都已添加，且名字与 workflow 中一致。
- **Release 没有生成**：确认推送的是数字 tag（如 `6`）而不是普通分支提交；并确认上一个 tag
  （`5`）已经存在，版本号必须连续。
- **模块 zip 里混入了多余文件**：检查 `build.py` 的 `EXCLUDE_DIRS` / `EXCLUDE_FILES`，
  不要把它们放在需要打包的目录里；新增顶层目录时记得同步排除。
