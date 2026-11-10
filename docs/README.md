# GameShell 文档索引

> 治理规范：[ReSerendipity/.github/governance](https://github.com/ReSerendipity/.github/tree/main/governance)

GameShell（galgame 安卓运行器，内嵌 Winlator 源组件）的 docs/ 全量索引。
全量逐文件清单（含路径 / 用途 / 状态 / 核对日期 2026-11-10）见 [`INDEX.md`](INDEX.md)；两者冲突时以 `INDEX.md` 为准。
总入口见根 [README.md](../README.md)；CI 内部约定在 [`ci/README.md`](../ci/README.md)，
上游组件零改例外与冲突面清单在 [`ci/zero-mod-exceptions.md`](../ci/zero-mod-exceptions.md)。

## 治理与协作

| 文档 | 内容 |
|---|---|
| [agent-contract.md](agent-contract.md) | Agent 契约（v2，与 governance 模板逐字节一致） |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 贡献指南：本地门禁、DCO 签署、提交约定、许可证双同步约定 |
| [release-governance.md](release-governance.md) | 发布 / 回滚规范：版本位 `app/build.gradle` versionName/versionCode、tag 驱动发布链、回滚一律前滚、校准记录（2026-10-18） |

## 记录与台账

| 文档 | 内容 |
|---|---|
| [ARCHITECTURE-REVIEW.md](ARCHITECTURE-REVIEW.md) | 架构评审记录（AR-NNN 模板 + 记录）：重大变更先评审后落地 |
| [decision-registry.md](decision-registry.md) | 决策与风险编号登记表（A/AC/RK）：让代码/文档引用的决策编号仓内可解析 |
| [tech-debt-register.md](tech-debt-register.md) | 技术债登记表（TD-01~25）：编号/登记日/偿还计划/状态，可被追债 |
| [component-versions.md](component-versions.md) | 组件版本登记表：Ren'Py / Kirikiroid2 / Winlator 三组件内嵌版本 vs 上游、核查节奏 |
| [UPSTREAM-PR-CHECKLIST.md](UPSTREAM-PR-CHECKLIST.md) | 上游回馈清单：滞留 fork 的通用修复向上游 winlator-app 提 PR 的逐项销账表 |

## 计划与落地（按时间线）

| 文档 | 内容 |
|---|---|
| [engine-equivalence-ui-landed.md](engine-equivalence-ui-landed.md) | 引擎等价化 + UI 个性化落地记录：勘误、提交清单、真机复测手册 |
| [native-engine-integration.md](native-engine-integration.md) | 原生引擎内嵌方案：Ren'Py / Kirikiroid2 端到端跑通的事实记录 |
| [box64-wine-launch-chain.md](box64-wine-launch-chain.md) | box64 / wine 启动链修复记录：39-bit VA 设备六条件、DirectShow 挂起定位 |
| [wine-dxwrapper-tuning.md](wine-dxwrapper-tuning.md) | Wine / dxwrapper 兼容性调优：任务背景、实施与真机取证 |
| [fullscreen-fix-retrospective.md](fullscreen-fix-retrospective.md) | 全屏修复复盘（PR #36）：根因两层叠加与可复用判断 |
| [real-device-verification.md](real-device-verification.md) | 真机验证报告（2026-10-22~24 三轮）：只写有证据的结论 |
| [save-recovery-drill.md](save-recovery-drill.md) | 存档备份/恢复演练记录（M10 止血）：自动备份调用点 + 导出→清空→恢复→校验 JVM 实测 |
