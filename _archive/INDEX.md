# GameShell 归档索引（_archive/）

> 归档区按治理规范 §12.1 使用仓根 `_archive/`（下划线前缀＝非活动区，§10 审计自动豁免）。
> 归档只移动文件、不改写正文；`docs/README.md` 与 `docs/engine-equivalence-ui-landed.md` 的引用已改新路径，`CHANGELOG.md` 属历史发布记录保留原文（例外已登记）。

| 原路径 | 归档后路径 | 归档日 | 类别 | 一句话结论 | 是否仍被引用 |
|---|---|---|---|---|---|
| `docs/plan-engine-equivalence-ui.md` | `_archive/执行记录/2026-11/plan-engine-equivalence-ui.md` | 2026-11-08 | 执行记录（已完成前置简报） | 2026-10-05 的两项功能前置简报；文首已自标「本简报已被取代，结果见 landed 篇」 | 是：`docs/README.md`、`docs/engine-equivalence-ui-landed.md`（已改新路径） |
| `docs/robustness-assessment-2026-10.md` | `_archive/执行记录/2026-11/robustness-assessment-2026-10.md` | 2026-11-08 | 执行记录（一次性评估） | 导入事故触发的健壮性完整性评估（真机取证 + JVM 回归 + CI 三部门禁） | 是：`docs/README.md`（已改新路径）；`CHANGELOG.md` 保留历史原文（例外） |

## 未归档（活动文档，判定理由）

| 文档 | 保留理由 |
|---|---|
| `docs/engine-equivalence-ui-landed.md` | 落地记录含 §5/§8「真机复测手册」，被 `tools/smoke_e2e_adb.sh` 与 `docs/tech-debt-register.md` 引用，仍有行动价值 |
| `docs/real-device-verification.md` | 真机验证证据基线（22 处引用，含 README 中英双语、源码注释、tech-debt 各条证据链）；归档会大范围断链且无收益 |
| `docs/save-recovery-drill.md` | 门禁 M10 清零证据，被 `tools/run_detect_test.sh` 引用（2026-11-05/06 最近一轮） |
| `docs/fullscreen-fix-retrospective.md` | 经验复盘，被源码注释锚定（`GalgameLaunchHelper.java`）且结论可复用 |
| `docs/ARCHITECTURE-REVIEW.md` | 评审模板与 AR 记录载体，仍在追加使用（非历史快照） |
| `docs/box64-wine-launch-chain.md`、`docs/wine-dxwrapper-tuning.md`、`docs/native-engine-integration.md` | 技术机制与调参知识，长期有效 |
| `docs/decision-registry.md`、`docs/tech-debt-register.md`、`docs/component-versions.md` | 决策 / 技术债 / 组件版本台账 |
| `docs/CONTRIBUTING.md`、`docs/UPSTREAM-PR-CHECKLIST.md`、`docs/release-governance.md`、`docs/agent-contract.md`、`docs/README.md` | 协作规范与索引 |

## 本次未纳入提交的在途改动（如实登记）

- `docs/icon/export-snapshot-index.md` 为未跟踪文件（用户 WIP），本次未触碰、未提交。
