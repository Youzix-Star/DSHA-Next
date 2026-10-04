# DSHA-Next

[DSHA-Next Shell](../DSHA-Next-Shell) 的 Android 客户端：用手机上的 Termux 装好并跑起 DeepSeek Harness，
再用一个应用把「安装 / 启动 / 关闭」变成按钮。

**当前版本只有界面外壳。** 遥控功能（走 Termux 的 `RUN_COMMAND` 权限）在后续版本接入，这一版交付的是
可安装、可点、可切换的完整 UI。

## 现在有什么

四个页签：

| 页签 | 现状 |
|---|---|
| **首页** | 品牌卡与版本信息 |
| **网页** | 系统 WebView 浏览器：地址栏、前进/后退/刷新；裸主机名补 `https://`，`localhost` 与 `127.0.0.1` 补 `http://` |
| **终端** | 占位界面，尚无任何执行能力 |
| **关于** | 设置与关于合并成一页：外观（主题模式 / 预见式返回 / 液态玻璃底栏）、引擎切换、源代码、开源许可、崩溃日志 |

- **两套界面引擎**：Miuix（液态玻璃底栏、大标题）与 Material Design，整套实现各自独立，
  「关于 → 引擎」里切换后立即生效并记住。
- **调试模式**：在「关于」页连续点七下 `Ciallo～(∠・ω c)⌒★`，出现「调试」分组（模拟崩溃）。
  页面上没有任何提示，这就是要点。
- **崩溃报告**：崩溃时由独立进程 `:crash` 弹出报告页，可复制/分享；报告落在
  `Android/data/top.youzix.dsha/files/crash/latest.txt`。

包名 `top.youzix.dsha`，minSdk 33（液态玻璃依赖 miuix-blur 的 RenderEffect），targetSdk 35。

## 构建

**不在本地构建**，全部依赖 GitHub Actions（`.github/workflows/android.yml`）：

- JDK 21 → `platforms;android-37.0` / `build-tools;37.0.0` → 从 Secrets 还原签名库 →
  `testDebugUnitTest` → `assembleRelease` → 上传 APK 与 R8 mapping；推送 `v*` 标签时额外创建 Release。
- 需要的 Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`（已配置）。
  本地构建则读根目录 `keystore.properties`（已 gitignore，签名库不在仓库里）。

## 来源与许可

界面框架来自 [NekoPlus](https://github.com/Youzix-Star/NekoPlus)（AGPL-3.0）：本仓库保留了它的双引擎外壳、
液态玻璃底栏、预测性返回、崩溃报告与版式，删掉了它的全部业务功能（AI 改写、文本替换、悬浮窗、无障碍、
新手引导、更新检查）。预编译的框架出处（miuix、AndroidX、InstallerX-Revived 的分页状态、AndroidLiquidGlass）
记在应用内「开源许可」页。

[AGPL-3.0](LICENSE)
