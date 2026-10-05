# AGENTS.md —— 给下一个接手的人（和 agent）

## 0. 这是什么

`Youzix-Star/DSHA-Next`，包名 `top.youzix.dsha`：**DSHA-Next Shell 的 Android 客户端**。

它是 `Youzix-Star/NekoPlus` 的**挖空版**：只留 UI 框架与部分设计，业务功能全删。
遥控 Termux（准备 / 安装 / 启动 / 停止 dsh、跑一条命令）已经接上，走 `RUN_COMMAND`；
App 自己仍然不执行任何命令，也没有终端模拟器（终端页是一条命令一次调用，没有 PTY）。

- 两套 UI 引擎：miuix（`ui/miuix/**`）与 Material 3（`ui/material3/**`），**互不共享 Composable**，
  同一批页签写两遍，切换在「关于 → 引擎」。改一个页签**必须两个引擎一起改**，否则切过去就少东西。
- 共享层：`AppState`（进程级 UI 状态）、`ui/UiEngine.kt`（`UiEngine` + `UiEnginePrefs`）、
  `ui/AppIcons.kt`、`ui/web/WebViewHost.kt`（`BrowserState` + WebView，两个引擎共用）、
  `ui/terminal/TerminalConsole.kt`（终端输出面板，两个引擎共用）、`ui/predictiveback/**`、`ui/crash/**`。
- 四个页签常量：`TAB_HOME=0 / TAB_WEB=1 / TAB_TERMINAL=2 / TAB_ABOUT=3`。
- **遥控 Termux**：`termux/TermuxBridge.kt`（RUN_COMMAND 协议 + 写进 `~/.dsha/run.sh` 的脚本）、
  `termux/TermuxController.kt`（进程级状态与收发）、`termux/TermuxUi.kt`（两个引擎共用的按钮与文案，
  纯函数、可在 CI 里测）。App 自己**不执行任何命令**，全部经 Termux 的 `RUN_COMMAND` 服务。

## 1. 铁律

1. **不在本地构建。** 这台设备没有 Android SDK，也**不要**去装。唯一验证手段是 GitHub Actions，
   一次 3～6 分钟。所以：能在本地读代码解决的问题，别丢给 CI。
2. **不要凭记忆写 API。** Compose / miuix 的签名从 AAR 里读（`unzip -p <aar> classes.jar`，
   或 `strings <X>Kt.class | grep -E '^[a-z][A-Za-z]{2,22}$'` 拿参数名）。参数名猜错就是一轮 CI。
   源码 jar 更快：`https://repo1.maven.org/maven2/top/yukonga/miuix/kmp/miuix-ui-android/<版本>/miuix-ui-android-<版本>-sources.jar`。
   Termux 那一侧同理 —— `RUN_COMMAND` 的 extra 名、`Runner` 取值、结果 bundle 的 key
   全部来自 `~/DSHA/termux-app/termux-shared/.../TermuxConstants.java`，不要猜。
3. **一次构建改一批事。** 每版只带一个改动会把用户磨没。
4. **不要宣称没验证过的事。** 没验证就写"未验证"。
5. **仓库里只放仓库的东西。** 日志、APK、AAR、签名库、临时解包目录一律放 `~/scratch/`。
   签名库**永远不要提交**（`*.jks`、`keystore.properties` 已在 `.gitignore`）。
6. 合并/改名后 `grep -rn "^<<<<<<<"` 与全仓 `grep -rn "love\.miao\.yun"` 各跑一次。
7. **`com.termux.permission.RUN_COMMAND` 是 Termux 声明的权限，不是本应用的。** 它必须在
   `AndroidManifest.xml` 里 `<uses-permission>`，由用户在 Termux 的应用信息页里授予；不要试图
   自己弹权限框，也不要在没有它的情况下假装修好了。`<queries><package android:name="com.termux"/>`
   少了，`isInstalled()` 在 Android 11+ 上永远是 false。

## 2. CI

```bash
TOK=$(cat ~/.dsha-secrets/gh-token)
SHA=$(git rev-parse HEAD)
curl -s -H "Authorization: token $TOK" \
  "https://api.github.com/repos/Youzix-Star/DSHA-Next/actions/runs?head_sha=$SHA&per_page=1"
# 取 run id → jobs → 日志
curl -sL -H "Authorization: token $TOK" \
  "https://api.github.com/repos/Youzix-Star/DSHA-Next/actions/jobs/$JID/logs" -o ~/scratch/ci.log
grep -oE "(e|error): [^ ]*(java|kt):[0-9]+:[0-9]+ .{0,90}" ~/scratch/ci.log | head -30
```

- 工作流 `.github/workflows/android.yml`：`main` push / PR 触发构建，`v*` tag 额外发 Release
  （资产名 `DSHA-Next-<tag>.apk`）。
- 签名 Secrets 已配好：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。
- R8 mapping 作为 artifact `r8-mapping` 上传，混淆堆栈靠它翻译回真实类名。
- 本地跑不过的测试，CI 里也跑不过：`testDebugUnitTest` 有「测试数必须 > 0」的闸门，
  删测试要连着删那个 step。

## 2.5 没有编译器时先跑这三样

CI 一轮 3～6 分钟，而下面三个脚本能在 20 秒内挡掉大多数编译错误：

```bash
python3 scripts/klex.py <改过的 .kt>...   # 词法 + 括号平衡 + 未使用 import（不改的文件不查 import）
python3 scripts/runscript.py              # 把 TermuxBridge 里的 run.sh 抽出来交给 sh -n
python3 scripts/kcheck.py <file.kt>       # 只查括号
```

`runscript.py --dump <文件>` 会把脚本真的落盘，可以拿一个假 HOME 手跑一遍各动作
（`probe` / `start` / `stop` / `log` / `shell`），不需要手机也不需要 App。

这三个都是文本检查，**看不见类型**。已经踩过两次的坑，改完文件请自己过一眼：

- **整文件重写会丢东西。** 这台机器上 `write` 工具建不了硬链接（Android 禁 `link(2)`），
  所以文件是用 `cat > file <<'EOF'` 整份覆盖写的。`TermuxController.kt` 第二次重写时把
  `refreshEnvironment()` 的定义丢了、留下两个调用点，只有 CI 发现。重写超过 100 行的文件后，
  把函数名列表拉出来对一遍：
  `grep -n "fun \|private val" <文件>`
- **raw string 里没有转义。** Kotlin 对 raw string 和普通字符串一视同仁地展开 `$name` 与 `${…}`，
  引号只是文本。`run.sh` 因此把每个 `$` 写成 `§DOLLAR§`，由 `runnerScript` 末尾换回来 ——
  这不是风格，是唯一能既保留 shell 写法又不被编译器吃掉的办法。`klex.py` 会拒绝裸 `$name`。

## 3. 这个用户怎么协作

- **他要结果，不要流程。** 能自己决定的小事就决定，把"做了什么、为什么、代价是什么"讲清楚。
- **他会核实你说的话。** 结论必须带证据（sha256、日志原文、字节码里的签名）。
- **他讨厌冗余入口与啰嗦文案**：界面文案一行一句，不放营销话。
- **他看不到代码，只看 APK。** 涉及观感的事要给可安装的 release 链接，并说清"要你看哪几点"。
- 出错就直说"这是我的判断错误"并立刻修，别解释环境。
