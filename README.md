# DSHA-Next

[DSHA-Next Shell](https://github.com/Youzix-Star/DSHA-Next-Shell) 的 Android 客户端：用手机上的 Termux
装好并跑起 DeepSeek Harness，再用一个应用把「准备 / 安装 / 启动 / 停止」变成按钮。

## 现在有什么

四个页签：

| 页签 | 现状 |
|---|---|
| **首页** | 遥控面板：彩色主状态块（点一下执行）＋ 两张前置条件卡 ＋ 三格统计 ＋ 概览 |
| **网页** | 系统 WebView 浏览器；dsh 在跑时直接给出带 token 的入口 |
| **终端** | 一条命令一次 Termux 调用：快捷命令、输出、复制、清空、web 日志 |
| **关于** | 设置与关于合并成一页：外观（主题模式 / 预见式返回 / 液态玻璃底栏）、引擎切换、源代码、开源许可、崩溃日志 |

- **两套界面引擎**：Miuix（液态玻璃底栏、大标题）与 Material Design，整套实现各自独立，
  「关于 → 引擎」里切换后立即生效并记住。**新增页面必须两边一起写**。
- **调试模式**：在「关于」页连续点七下 `Ciallo～(∠・ω c)⌒★`，出现「调试」分组（模拟崩溃）。
  页面上没有任何提示，这就是要点。
- **崩溃报告**：崩溃时由独立进程 `:crash` 弹出报告页，可复制/分享。

包名 `top.youzix.dsha`，minSdk 33，targetSdk 35。

## 遥控是怎么接上的

App 自己不执行任何命令。它把指令发给 Termux 的 `RUN_COMMAND` 服务，由 Termux 以它自己的身份执行。

```
App ──RUN_COMMAND intent──▶ Termux RunCommandService
                              └─▶ /system/bin/sh -c 'sh ~/.dsha/run.sh <动作>'
                                    ├─ probe   读状态（装没装 / 跑没跑 / 端口 / token 地址）
                                    ├─ start   后台拉起 dsh web，日志写 ~/.dsha/web.log
                                    ├─ stop    结束 dsh web
                                    ├─ install 跑打包在 APK 里的 install-dsh.sh
                                    └─ shell   执行终端页里输入的一条命令
```

- 结果通过附加在 intent 上的 `PendingIntent` 回传，所以 App 能拿到 stdout / stderr / 退出码。
  **这个 PendingIntent 必须是 `FLAG_MUTABLE`**：Termux 用 `send(..., resultIntent)` 把结果 bundle
  挂在它自己的 Intent 上，平台只在 `Intent.fillIn` 里合并进 PendingIntent，而
  `PendingIntentRecord.sendInner` 在 `FLAG_IMMUTABLE` 时**整段跳过**这次合并 —— 结果永远收不到。
- 回包由一个**清单里声明的、`exported="false"` 的接收器**收（`TermuxResultReceiver`），不是运行时
  注册的：命令可能在 Activity 已经不在了才回（长安装、旋转、切去 Termux 再回来），运行时注册的
  接收器会跟着 Activity 一起注销。
- **运行脚本随 APK 走，不需要任何「准备」步骤。** `assets/run.sh` 的内容每次作为 `bash -lc`
  的参数整份发给 Termux，所以终端和首页的按钮开箱即用，升级 App 就是升级脚本。它同时消掉了
  一整类问题：Kotlin 字符串会展开 `$name` 与 `${…}`，脚本放在 Kotlin 字符串里时每个 `$` 都得
  转义，`scripts/runscript.py` 现在会拒绝那种占位符写法。
- **App 写不进 Termux 的私有目录**，唯一需要落盘的是 18 KB 的安装脚本 `~/.dsha/install-dsh.sh`：
  首次进入首页时由 App 默默写入（base64 经 `sh -c`），失败只在用户真的点「安装」时报出来。
- 安装走 `terminal-session`：要编译原生模块，2～10 分钟，进度在 Termux 窗口里看，App 只负责交出去。

### 授权（三步，App 只能代劳其中一步）

| 前提 | 谁来做 | App 怎么知道 |
| :-- | :-- | :-- |
| Termux 已安装（F-Droid 版） | 用户 | `PackageManager` 查询 `com.termux`（靠清单里的 `<queries>`） |
| `com.termux.permission.RUN_COMMAND` | **点「请求权限」**，系统弹框 | `checkSelfPermission` |
| `allow-external-apps = true` | **只能在 Termux 里执行一行命令** | Termux 回的错误文本里含 `allow-external-apps` |

第三条无法代劳：那个开关在 Termux 的私有目录里，任何别的应用都写不进去。被拒时首页会把这行
命令原样显示出来：

```bash
echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings
```

三件事没做全时命令不会有任何回音，而「没回音」和「超时」的区别在于：`allow-external-apps`
被拒时 Termux **会**回一条错误（App 据此给出上面那行命令），权限没给时才是一声不响。
- 预设端口 3080，一次只跑一个 `dsh web`；`start` 会把带 token 的地址取回来，网页页签直接可用。

界面与流程参照了 [DSHA](https://github.com/Youzix-Star/DSHA)（同一位作者的图形化安装器）：
首页的分块与内边距、`allow-external-apps` 的判定、`FLAG_MUTABLE` 的选择、以及「脚本随命令走」
都来自它 —— 它在这几处都踩过一遍。`Card` 的 `insideMargin` 尤其要注意：miuix 默认是 **0**，
卡里放裸 `Text` 必须自己给，不然文字直接贴边。

细节在 `app/src/main/java/top/youzix/dsha/termux/`：`TermuxBridge`（协议与脚本）、
`TermuxController`（状态与收发）、`TermuxUi`（两个引擎共用的按钮与文案）、
`TermuxResultReceiver`（清单里的回包接收器）。

授权这三步的做法参考了 [DSHA](https://github.com/Youzix-Star/DSHA)（同一位作者的图形化安装器）：
它的五步引导、`allow-external-apps` 的判定方式与 `FLAG_MUTABLE` 的选择都在这上面踩过一遍。

## 构建

**不在本地构建**，全部依赖 GitHub Actions（`.github/workflows/android.yml`）：

- JDK 21 → `platforms;android-37.0` / `build-tools;37.0.0` → 从 Secrets 还原签名库 →
  `testDebugUnitTest` → `assembleRelease` → 上传 APK 与 R8 mapping；推送 `v*` 标签时额外创建 Release。
- 需要的 Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`（已配置）。

本地能在没有 SDK 的情况下做的检查（`scripts/`）：

```bash
python3 scripts/klex.py app/src/main/java/.../TermuxBridge.kt   # 词法/结构/未使用 import
python3 scripts/runscript.py                                    # 抽出 run.sh 交给 sh -n
python3 scripts/kcheck.py <file.kt>                             # 括号平衡
```

## 来源与许可

界面框架来自 [NekoPlus](https://github.com/Youzix-Star/NekoPlus)（AGPL-3.0）：本仓库保留了它的双引擎外壳、
液态玻璃底栏、预测性返回、崩溃报告与版式，删掉了它的全部业务功能（AI 改写、文本替换、悬浮窗、无障碍、
新手引导、更新检查）。`app/src/main/assets/install-dsh.sh` 来自 DSHA-Next-Shell（AGPL-3.0），
字节一致。预编译的框架出处（miuix、AndroidX、InstallerX-Revived 的分页状态、AndroidLiquidGlass）
记在应用内「开源许可」页。

[AGPL-3.0](LICENSE)
