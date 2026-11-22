# 闲鱼（com.taobao.idlefish）桌面图标不生效的原因与解决

## 现象

模块里已经有 `com.taobao.idlefish` 的整套素材（`recbg` / `recfg` / `rec_night` 等），ColorOS 设置中的应用图标显示正常，
但桌面图标始终是原版，重启桌面、重启系统界面都无效。

## 原因

闲鱼的桌面入口不是普通 Activity，而是一个 `activity-alias`：

- `AndroidManifest.xml` 里共有 22 个 `activity-alias`（`AppIcon_Skin_Cosmic`、`Fur`、`Default_Badge` 等），
  用到 21 个独立图标资源（`appicon_skin_*` / `appicon_fisher_vip`），每个 alias 都带自己的 `android:icon`。
- 这些 alias 全部指向 `com.taobao.fleamarket.home.activity.InitActivity`，而 `InitActivity` 自己也声明了
  MAIN/LAUNCHER，图标是应用默认图标 `drawable/idlefish_ic_launcher`。
- 当前桌面实际使用的入口是 `com.taobao.idlefish/com.taobao.fleamarket.home.activity.AppIcon_Skin_Cosmic`。

ColorOS 的 uxicons 文件管线是按「包名 / 普通 Activity」解析 `/data/oplus/uxicons` 下的素材的，alias 入口不会走这条管线。
真机验证结果：ART+ 已开启（`artPlusOn : true`）、闲鱼也命中 morph 图标白名单（`morph_icons_whitelist.xml` 里
`<morph>1</morph>`），但 launcher 重启后完全没有 uxicons / morph 的文件读取日志，说明它根本没去读图片文件。

因此下面这些写法对闲鱼全部无效（均已真机验证）：

| 尝试 | 结果 |
| --- | --- |
| `uxicons/hdpi/com.taobao.idlefish#com.taobao.fleamarket.home.activity.AppIcon_Skin_Cosmic/` | 桌面图标不变 |
| 包名目录下 `<Activity>_recbg.png` + `ux_component.png` | 桌面图标不变 |
| `/data/oplus/uxicons/choose/com.taobao.idlefish.png` 自定义图标 | 桌面图标不变（该机制还需要配套 `.cfg` 指向真实图标包） |
| `pm install` 普通 RRO 后 `cmd overlay enable` | 被系统拒绝 |

普通 RRO 被拒的报错是 Android 12+ 的安全策略：

```text
Overlay ... and target ... signed with different certificates, and the overlay lacks <overlay android:targetName>
```

闲鱼没有声明 `<overlayable>`，overlay 又不可能用闲鱼的签名，所以「安装型 RRO」这条路走不通。

## 解决：预装静态 RRO

系统自带的 overlay（例如 `/product/overlay/GmsConfigOverlayCommonCN`）同样是跨签名、没有 `targetName`，但因为是
**预装静态 overlay**，扫描时不受上面那条限制。我们照着这个机制给闲鱼单独做一个静态 RRO：

- `android:isStatic="true"`，覆盖闲鱼全部 22 个图标资源：
  `appicon_fisher_vip`、`appicon_skin_*`（20 个）以及应用默认图标 `idlefish_ic_launcher`。
- 资源类型必须与目标一致（都是 `drawable`），文件名必须完全同名。

`AndroidManifest.xml`：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.color17.idlefish.iconoverlay"
    android:versionCode="1"
    android:versionName="1.0">

    <overlay
        android:targetPackage="com.taobao.idlefish"
        android:priority="1"
        android:isStatic="true" />

    <application android:hasCode="false" />
</manifest>
```

> `targetSdkVersion` 必须 ≥ 29，否则扫描时会报 `must target Q or later`。

### 注入方式

`/product/overlay` 在开机早期就已经被挂成只读 overlayfs（lowerdir 包含 `/my_product/overlay` 等），
之后无论往 `/my_product/overlay` 还是 `/product` 里加文件都不会出现在它里面。因此我们在 `post-fs-data.sh`
里**再叠一层 overlayfs**，把 RRO 注入进去：

```sh
OV_ROOT=/data/adb/ColorOSPerfectIcon/overlay
mkdir -p "$OV_ROOT/upper" "$OV_ROOT/work"
cp -f "$MODDIR/overlay/IdlefishIconOverlay.apk.bin" "$OV_ROOT/upper/IdlefishIconOverlay.apk"
chmod 644 "$OV_ROOT/upper/IdlefishIconOverlay.apk"
chcon u:object_r:system_file:s0 "$OV_ROOT/upper/IdlefishIconOverlay.apk"
mount -t overlay overlay \
  -o "lowerdir=/product/overlay,upperdir=$OV_ROOT/upper,workdir=$OV_ROOT/work" \
  /product/overlay
```

要点：

- 必须放在 `post-fs-data` 阶段：`system_server` 扫描 overlay 目录发生在这之后，放到 `service.sh` 就太晚了。
- upperdir 里的 APK 要 `chcon` 成 `u:object_r:system_file:s0`（与 `/product/overlay` 里现有 overlay 文件一致），
  否则 `system_server` 可能没有读取权限。
- 完整实现见仓库根目录的 `post-fs-data.sh`。

### 仓库中的落地文件

| 文件 | 作用 |
| --- | --- |
| `overlay/*.apk.bin` | 静态 RRO。打包脚本会排除 `.apk`，所以加 `.bin` 后缀随模块一起发布 |
| `post-fs-data.sh` | 开机时遍历 `overlay/*.apk.bin`，用 overlayfs 把 APK 注入 `/product/overlay` |

当前已落地的 RRO：

| 文件 | 目标应用 | 覆盖的资源 |
| --- | --- | --- |
| `IdlefishIconOverlay.apk.bin` | 闲鱼 `com.taobao.idlefish` | 22 个 alias 图标 + 默认图标 |

### 构建与验证

构建：

```sh
aapt2 compile --dir res -o compiled.zip
aapt2 link -o unsigned.apk -I android.jar --manifest AndroidManifest.xml \
  --min-sdk-version 29 --target-sdk-version 36 compiled.zip
zipalign -f 4 unsigned.apk aligned.apk
apksigner sign --ks <keystore> --out IdlefishIconOverlay.apk aligned.apk
```

`res/drawable-xxhdpi/` 下放 22 个与闲鱼资源同名的 PNG 即可。

重启后验证：

```sh
ls -lZ /product/overlay/IdlefishIconOverlay.apk
cmd overlay list | grep -i -A2 idlefish
```

正常应看到文件存在，且 `cmd overlay list` 里出现：

```text
com.taobao.idlefish
[x] com.color17.idlefish.iconoverlay
```

## 其他应用遇到同样问题时怎么判断和复用

判断是否属于这类问题：

1. 模块素材齐全、设置里的应用图标正常，但桌面图标不变；
2. 桌面入口是 `activity-alias`，且 alias 自带 `android:icon`；
3. launcher 重启后抓不到 uxicons / morph 的文件读取日志。

满足以上条件时，按下面步骤复用：

1. 用 `aapt2 dump xmltree base.apk --file AndroidManifest.xml` 找到实际入口 alias，以及它的 `android:icon` 资源 ID；
2. 用 `aapt2 dump resources base.apk` 查出该 ID 对应的资源名，把所有 alias 图标和应用默认图标都列出来；
3. 为每个资源名准备一张同名 PNG，放到 `res/drawable-xxhdpi/`；
4. 用上面的命令构建、签名；
5. 把 APK 放进 `overlay/` 并改名为 `*.apk.bin`，避免被打包脚本排除；
6. `post-fs-data.sh` 会自动遍历 `overlay/*.apk.bin` 注入，无需再改脚本；
7. 重启模块，用上面的命令验证。

注意事项：

- 每个应用需要一个独立的 RRO，资源名必须和目标 APK 完全一致，类型也必须是 `drawable`。
- 静态 overlay 不能禁用，预装后常开；要撤销只能删掉注入逻辑并重启。
- `my_product/media/theme/` 下那些没有扩展名的 `com.taobao.taobao`、`com.oppo.store` 不是 overlay，
  而是主题商店的单应用图标资源包（zip 容器，没有 `AndroidManifest.xml`），由主题管线直接读取，不要和 RRO 混淆。
