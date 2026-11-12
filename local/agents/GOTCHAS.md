# GOTCHAS（坑点台账）

> 本文件由自进化协议维护：每次踩坑 / 修 bug 后追加到表尾，不删不改历史行。
> 路径优先 `local/agents/GOTCHAS.md`；存量 `docs/agents/GOTCHAS.md` 视为历史分片。
> 不得写密钥 / token / 口令 / 连接串；限制级内容只写指路，不摘正文。

| # | 坑点标题 | 触发场景 | 现象/报错 | 正确做法 | 首次发现日期 |
|---|---|---|---|---|---|
| 1 | 示例：把「探测不到」写成「缺失即故障」 | 干净检出 / CI 里读本地文件 | 报「文件缺失」，实际是设计上不入库 | 先查 `.gitignore` 与 `git ls-files`，再判定 | YYYY-MM-DD |
| 2 | G34O 图标只保留 anydpi 矢量时，旧图标矢量仍被 splash 引用 | 删除密度 PNG / 旧图标矢量 | 若先删 galgame_launcher_foreground 而未同步 splash 与 galgame_splash_dark，会资源缺失 | 图标批次只删 15 个密度 PNG；旧矢量与 splash/暗档一起随 UI 批次处理 | 2026-11-02 |
| 3 | zstd-jni 1.5.7-24 的 AAR metadata 要求 compileSdk 37 | Dependabot 单独升级 zstd-jni | checkDebugAarMetadata 失败：requires compileSdk 37 vs 35 | 回退到 1.5.2-3，并在 dependabot.yml ignore；升级需与 AGP/compileSdk 同批 | 2026-11-02 |
| 4 | zero-mod-allow 登记块写错会被守卫按「登记损坏」拒绝（exit 2），而非放行 | 编辑 ci/zero-mod-exceptions.md 机器可读块 | 推送被 fail-closed 拦下，报出无法解析的行 | 改块后先跑 ZERO_MOD_BASE=<基线> bash ci/check-zero-mod.sh 自测放行/拦截两侧 | 2026-11-02 |
| 5 | zero-mod-allow 登记只能在 PR 里改，直推 main 会被守卫拒绝 | owner 直推修改例外表 | push 事件且 HEAD 非 PR 合并提交 → exit 1 | 例外登记走 PR；直推只改说明/脚本，不改零改放行块 | 2026-11-02 |
| 6 | `ci` 门禁不覆盖 xserver/xconnector 子树，改它们等于裸改 | 修改 `com.winlator.xserver` / `xenvironment` 里的类 | `ci` 三步只编 `com.winlator.galgame` + 7 个热点文件，xserver 侧改错也能「门禁全绿」 | 用 `.\gradlew.bat :app:compileDebugJavaWithJavac --offline` 补一次全量 javac（本 fork 不做 APK 打包，但不影响编译任务；已实测 rc=0 可用） | 2026-11-07 |
| 7 | 运行时探针用 `ci/stubs` 时，`com/winlator/core/FileUtils` 的桩会遮蔽真实实现 | 以 `stubs` 为 classpath 跑 JVM 探针验证文件类逻辑 | `isComplete()` 之类的判定全返回假：桩版 `readString` 拿不到真实文件内容，探针假失败 | 运行时类路径必须 `probe;gradleClasses;stubs;android.jar`（我们的类在前、桩在后），缺 `android.jar` 又会 `NoClassDefFoundError: android/system/ErrnoException` | 2026-11-07 |

| 8 | 提交前手工预跑 ledger-gate，会把基线推进到当前行数，随后真推送反被闸判红 | 提交已就绪、想先自检一次闸，于是在有未推提交的工作树里手动跑 `check-ledger-append.ps1 -Enforce` | 预跑当时 `upstream..HEAD` 无差异 ⇒ `codeChanged=false` ⇒ 判通过；但通过分支会把 `.ledger-baseline.json` 的 `gotchasLines` 重写成当前行数。真推送时 `codeChanged=true`，而「当前行数 > 基线」不再成立 ⇒ `ledger-gate FAIL：代码/配置/脚本变更，但 GOTCHAS 未新增行`（GameShell 这次是先过 DCO、类型门禁，最后卡在这一道） | 预跑只用来验证脚本本身（用临时复制的仓库副本跑），**不要**在有未推提交的工作树上跑 `-Enforce`；已踩则补一条**真坑**（新行数高于基线），而不是改 baseline 往回退或用 `[skip-ledger]` 绕过 | 2026-11-08 |
| 9 | 手工改容器 `.container` 的顶层 `dxwrapper` 会被 `extraData.dxwrapper` 缓存串静默跳过，看起来换了后端其实没换 | 想做 A/B，直接改 `rootfs/home/xuser-<id>/.container` 的 `dxwrapper` 再启动 | `XServerDisplayActivity` 用 `wined3d-<ver>-vkd3d-<ver>-<ddraw>` 生成 cacheId 并与 `container.getExtra("dxwrapper")` 比对，相等就 `return false` 跳过整套 dll 替换；回滚旧 JSON 时该串与「实际装着的 dll」不一致，于是 dll 仍是 DXVK、配置写着 wined3d，日志里连 `wined3d_dll_init` 都没有 | 手工 A/B 必须同时清掉 `extraData.dxwrapper`；启动后先核对 `system32/d3d11.dll` 字节数（内建 442,368 / DXVK 4,055,054）再读日志 | 2026-11-08 |
| 10 | 引擎预设的 dxwrapper 依据必须绑定「当次构建 + 当次驱动」，供给一变旧结论会整条反转 | 沿用上一轮「advhd 用 wined3d 就有窗口」把 advhd 继续钉在 wined3d | 同一台机、同一包升到 gladio 1.1 后，wined3d 从「有窗口 + 引擎报错」退化成「GL 上下文挂死、应用永久停启动页」，FL 也从可用掉到 9_3；旧记录没人重测就会被当成现状引用 | 改预设前对目标引擎跑一次真机复现，结论入文档时带「设备 / 包版本 / 驱动三元组」 | 2026-11-08 |
| 11 | PowerShell 双引号里变量紧跟冒号，会被当成 `$i:` 作用域语法而报 `Variable reference is not valid. ':' was not followed by a valid variable name character` | 循环里拼「行$i: 内容」这类带行号前缀的调试输出 | 整段脚本**一行都不执行**（不是那一行输出被吞），很容易误判成命令本身有问题，白改一轮命令 | 用 `"行${i}: "` 或 `"行" + $i + ": "` | 2026-11-08 |
| 12 | 台账被父目录级 ignore 挡住，`!` 反包含写了不生效 | 本仓 `.gitignore` 原为 `/local/`，按 ledger-gate 要求往台账补行后执行 `git add` | `git check-ignore -v` 仍命中 `/local/`，`git add -n` 报 paths are ignored；台账长期只存在于本机，远端无该文件 | 按 ADR-0019 改四段式：`/local/*` → `!/local/agents/` → `/local/agents/*` → `!/local/agents/GOTCHAS.md` → `!/local/agents/REVISION_LOG.md`；验收以 `git add -n` 成功为准，且同目录 `.ledger-baseline.json` 等内部件必须仍被忽略 | 2026-11-12 |
