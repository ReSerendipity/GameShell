# 架构 — GameShell

> 治理定位：`delivery=tool`、`lifecycle=active`（`governance/desired-state.json`）。
> 本文按 2026-11-03 的实际目录结构生成；数据流与边界须由维护者在实现变更时同步复核。

## 1. 定位

**Galgame 的 Android 运行器 —— 导入即玩，自动识别引擎并路由到原生或模拟层。**

## 2. 组件与依赖方向

```text
应用层：app/ (2502)
工具层：tools/ (16)、scripts/ (4)
支撑层：docs/ (22)、.github/ (6)
其他层：ci/ (140)、gradle/ (3)
根级层：根级文件 (16)
```

| 组件 | 目录 | 文件数 | 代表文件 |
|---|---|---|---|
| `app` | `app/` | 2502 | `app/.gitignore`, `app/build.gradle`, `app/proguard-rules.pro` |
| `ci` | `ci/` | 140 | `ci/README.md`, `ci/check-dco.sh`, `ci/check-hotspot-sync.sh` |
| `docs` | `docs/` | 22 | `docs/ARCHITECTURE-REVIEW.md`, `docs/CONTRIBUTING.md`, `docs/UPSTREAM-PR-CHECKLIST.md` |
| 根级文件 | `/` | 16 | `.editorconfig`, `.gitattributes`, `.gitignore` |
| `tools` | `tools/` | 16 | `tools/box64/box64-galgameshell.patch`, `tools/box64/build_box64_android.sh`, `tools/box64/linkshim_glibc_atexit.c` |
| `scripts` | `scripts/` | 4 | `scripts/check_licenses.py`, `scripts/galgame-fix-prefix.sh`, `scripts/gen_galgame_stubs.py` |
| `gradle` | `gradle/` | 3 | `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` |

依赖方向：入口层（应用 / 页面）调用领域层（业务模块），领域层依赖基础设施层（脚本、配置、模型资产）；反向引用不允许。

## 3. 数据流

导入游戏包 → 引擎识别 → 路由到原生或模拟层 → 启动运行时。

## 4. 外部依赖

| 依赖 | 用途 | 不可用时 |
|---|---|---|
| Gradle / Android SDK | 构建 APK | 无法出包 |
| 内置模拟层运行时 | 路由与兼容 | 仅剩原生路径 |

## 5. 边界与非目标

- 不做长期在线服务；离线即可完成主要工作。
- 不引入与工具体积不匹配的重型运行时。
- 不入库：密钥 / token / 口令 / 连接串、本机绝对路径、`local/` 与 `AGENTS.md`、构建产物。
- 治理正文只在 `ReSerendipity/.github/governance`，本仓不复制规则正文。

## 6. 变更影响面

| 改什么 | 必须同步 | 由谁校验 |
|---|---|---|
| 目录结构 / 入口 | 本文「组件与依赖方向」与「数据流」 | 维护者 |
| CI 与门禁 | 本仓 `README.md` 的测试命令；治理仓 §20 远端载体口径 | `audit-drift.py` |
| 依赖清单 | 锁文件与 `.github/dependabot.yml` 的生态段 | Dependabot / `lock-consistency` |
| README 展示图 | §8.1 真实性红线（图必须来自真实运行） | 治理仓 `readme-image-provenance` |
