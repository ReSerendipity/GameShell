# 文档索引（docs/INDEX）

> 最后核对：2026-11-10　｜　维护：文档整理工单 WO-DOC-B01
> 本文件是 GameShell `docs/` 全量 tracked Markdown 的机器可读索引（16 篇），并登记 `docs/` 之外的同体系文档（仓根、`ci/`、`_archive/`、`local/`、`app/`、`.workbuddy/`）。
> [`README.md`](README.md) 是面向读者的人工导航（治理 / 台账 / 时间线三组表）；两者冲突时以本文件为准。
> 治理规范上游：[ReSerendipity/.github/governance](https://github.com/ReSerendipity/.github/tree/main/governance)。

## 状态口径

| 状态 | 含义 |
|---|---|
| 现行 | 仍在指导当前工作，或仍是某项规则、清单、登记表的有效来源 |
| 历史 | 时点记录（评审、复盘、真机验证、演练），只作追溯 |
| 废弃 | 已被后继文档取代，或文件自身声明作废 |
| WIP | 文件自述未定稿，或尚未入库 |
| 本地 | 仅本地保留、不入库 |

## 一、仓根入口文档

| 文档 | 用途 | 状态 | 核对日期 |
|---|---|---|---|
| [`README.md`](../README.md) | 项目总入口：能力、截图、构建与安装、组件版本、已知限制 | 现行 | 2026-11-10 |
| [`ARCHITECTURE.md`](../ARCHITECTURE.md) | 架构说明：应用壳 / 内嵌引擎 / JNI 边界 | 现行 | 2026-11-10 |
| [`CHANGELOG.md`](../CHANGELOG.md) | 版本变更记录（Keep a Changelog 体例） | 现行 | 2026-11-10 |
| [`AGENTS.md`](../AGENTS.md) | 编码代理契约（仓根入口版） | 现行 | 2026-11-10 |
| [`SECURITY.md`](../SECURITY.md) | 安全策略与漏洞披露 | 现行 | 2026-11-10 |
| [`CODE_OF_CONDUCT.md`](../CODE_OF_CONDUCT.md) | 行为准则 | 现行 | 2026-11-10 |

## 二、docs/ 治理与协作

| 文档 | 用途 | 状态 | 核对日期 |
|---|---|---|---|
| [`README.md`](README.md) | docs 人工导航：治理三件 + 台账五项 + 计划落地时间线 | 现行 | 2026-11-10 |
| [`agent-contract.md`](agent-contract.md) | Agent 契约 v2（与 governance 模板逐字节一致） | 现行 | 2026-11-10 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 贡献指南：本地门禁、DCO 签署、提交约定、许可证双同步约定 | 现行 | 2026-11-10 |
| [`release-governance.md`](release-governance.md) | 发布 / 回滚规范：版本位取 `app/build.gradle`、tag 驱动发布链、回滚一律前滚（校准 2026-10-18） | 现行 | 2026-11-10 |

## 三、docs/ 记录与台账

| 文档 | 用途 | 状态 | 核对日期 |
|---|---|---|---|
| [`ARCHITECTURE-REVIEW.md`](ARCHITECTURE-REVIEW.md) | 架构评审记录（AR-NNN 模板 + 实例）：重大变更先评审后落地 | 现行 | 2026-11-10 |
| [`decision-registry.md`](decision-registry.md) | 决策与风险编号登记表（A / AC / RK）：让代码与文档引用的编号仓内可解析 | 现行 | 2026-11-10 |
| [`tech-debt-register.md`](tech-debt-register.md) | 技术债登记表（TD-01~25）：编号 / 登记日 / 偿还计划 / 状态 | 现行 | 2026-11-10 |
| [`component-versions.md`](component-versions.md) | 内嵌组件版本登记：Ren'Py / Kirikiroid2 / Winlator 的仓内版本 vs 上游、核查节奏 | 现行 | 2026-11-10 |
| [`UPSTREAM-PR-CHECKLIST.md`](UPSTREAM-PR-CHECKLIST.md) | 上游回馈清单：滞留 fork 的通用修复向 `winlator-app` 提 PR 的逐项销账表 | 现行 | 2026-11-10 |

## 四、docs/ 计划与落地记录（按时间线）

| 文档 | 用途 | 状态 | 核对日期 |
|---|---|---|---|
| [`engine-equivalence-ui-landed.md`](engine-equivalence-ui-landed.md) | 引擎等价化 + UI 个性化落地记录：勘误、提交清单、真机复测手册 | 历史 | 2026-11-10 |
| [`native-engine-integration.md`](native-engine-integration.md) | 原生引擎内嵌方案：Ren'Py / Kirikiroid2 端到端跑通的事实记录 | 历史 | 2026-11-10 |
| [`box64-wine-launch-chain.md`](box64-wine-launch-chain.md) | box64 / wine 启动链修复记录：39-bit VA 设备六条件、DirectShow 挂起定位 | 历史 | 2026-11-10 |
| [`wine-dxwrapper-tuning.md`](wine-dxwrapper-tuning.md) | Wine / dxwrapper 兼容调优：背景、实施与真机取证 | 历史 | 2026-11-10 |
| [`fullscreen-fix-retrospective.md`](fullscreen-fix-retrospective.md) | 全屏修复复盘（PR #36）：根因两层叠加与可复用判断 | 历史 | 2026-11-10 |
| [`real-device-verification.md`](real-device-verification.md) | 真机验证报告（2026-10-22~24 三轮）：只写有证据的结论 | 历史 | 2026-11-10 |
| [`save-recovery-drill.md`](save-recovery-drill.md) | 存档备份/恢复演练（M10 止血）：备份调用点 + 导出→清空→恢复→校验 JVM 实测 | 历史 | 2026-11-10 |

> 上述记录类文档引用到的两份前置/评估材料已在 `_archive/`：`plan-engine-equivalence-ui.md`（2026-10-05 前置简报）与 `robustness-assessment-2026-10.md`（2026-10-26 健壮性评估），索引见 [`_archive/INDEX.md`](../_archive/INDEX.md)。

## 五、docs/ 内的资产

| 项 | 用途 | 状态 | 核对日期 |
|---|---|---|---|
| `assets/` | README 展示图与架构图（`architecture.svg`、`readme-galgameshell-*`，无 Markdown） | 现行 | 2026-11-10 |
| `icon/` | 启动图标快照：`ic_launcher-512.png` + [`export-snapshot-index.md`](icon/export-snapshot-index.md)（**未跟踪 WIP**，2026-11-05 由仓外 `_app-icons/` 迁入，本轮只登记不改） | WIP | 2026-11-10 |

## 六、docs/ 之外的同体系文档

| 区域 | 用途 | 状态 | 核对日期 |
|---|---|---|---|
| [`ci/README.md`](../ci/README.md) | CI 内部约定（本地门禁与远端 workflow 对照） | 现行 | 2026-11-10 |
| [`ci/zero-mod-exceptions.md`](../ci/zero-mod-exceptions.md) | 上游组件零改例外与冲突面清单 | 现行 | 2026-11-10 |
| [`_archive/`](../_archive/) | 项目级归档 3 篇（`INDEX.md` + `执行记录/2026-11/` 两份材料） | 历史 | 2026-11-10 |
| `local/`（3 篇） | `README.internal.md`、`agents/GOTCHAS.md`、`agents/REVISION_LOG.md`：**仅本地保留、不入库** | 本地 | 2026-11-10 |
| `app/src/main/cpp/libadrenotools/`（6 篇） | 内嵌上游组件自带 README（非本仓撰写，随上游版本更新，**不修改**） | 现行 | 2026-11-10 |
| `.workbuddy/memory/`（14 篇） | 本地 AI 工作记忆（2026-09-22 ~ 2026-10-05 日档 + `MEMORY.md` / `learning-projects.md`），**未随仓库分发** | 本地 | 2026-11-10 |

## 七、索引维护规则

1. **新增文档**：进 `docs/` 对应分组（治理 / 台账 / 记录）并在本文件相应节补一行；同时更新 [`README.md`](README.md) 的三组表。
2. **记录类文档到点归档**：计划/复盘/验证报告在对应任务关闭后移入 `_archive/执行记录/<YYYY-MM>/`（保留文件名），`状态` 改 `历史`，引用处同步改路径；**不删除**。
3. **编号类台账**（`decision-registry.md`、`tech-debt-register.md`、`UPSTREAM-PR-CHECKLIST.md`）只增不删，关闭项在行内标状态。
4. **上游自带文档**（`app/src/main/cpp/libadrenotools/**` 6 篇）只做目录级登记，随上游同步，不由本仓改写。
5. **核对日期**：整仓复核时统一刷新。
