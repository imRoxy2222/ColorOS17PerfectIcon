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

1. **改版本号**（只有两处需要手动改）：
   - `module.prop` 的 `version=` 和 `versioncode=`，例如 `16.2.9`
   - `code/app/build.gradle.kts` 的 `versionName = "1.0"`（想跟随模块版本就改成 `16.2.9`）
   - `versionCode` **不要手改**，构建时自动取 `git rev-list --count HEAD`，随提交数递增。
2. 提交并推送到 `master`。
3. 打 tag，tag 名与模块版本保持一致（推荐 `v` + 版本号）：

   ```bash
   git tag v16.2.9
   git push origin master
   git push origin v16.2.9
   ```

   推送 tag 后会自动触发 `.github/workflows/release.yml`。

4. 打开仓库 **Actions → Release** 查看进度；完成后 **Releases** 页面会出现新版本，包含：

   | 产物 | 说明 |
   | --- | --- |
   | `ColorOS17PerfectIcon-<版本>-<versionCode>.apk` | 已用 `coloros17` 密钥签名的 release APK |
   | `ColorOSart-<版本>.zip` | 可直接刷入的 Magisk 模块 |

   没有打 tag 时（手动 `Run workflow`）只上传 Actions Artifacts，不会创建 Release。

---

## 3. 流水线做了什么

1. `actions/checkout` 且 **`fetch-depth: 0`** —— versionCode 依赖完整提交历史，浅克隆会算成 1。
2. 安装 JDK 17、Android SDK（`platforms;android-36`、`build-tools;36.0.0`）。
3. 把 `C17_KEYSTORE_B64` 解码到 `$RUNNER_TEMP/c17-release.jks`。
4. `./gradlew :app:assembleRelease`，签名配置读取环境变量（见 `code/app/build.gradle.kts` 的 `signingConfigs`）。
5. `apksigner verify` 校验签名，并把 APK 重命名为带版本号和 versionCode 的名字。
6. `python3 build.py` 打包模块 zip（自动排除 `code/`、`doc/`、`dist/`、`.github/`）。
7. 用 `softprops/action-gh-release` 创建 Release 并附加两个文件。

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
- **Release 没有生成**：确认推送的是 tag（`refs/tags/v*`）而不是普通分支提交。
- **模块 zip 里混入了多余文件**：检查 `build.py` 的 `EXCLUDE_DIRS` / `EXCLUDE_FILES`，
  不要把它们放在需要打包的目录里；新增顶层目录时记得同步排除。
