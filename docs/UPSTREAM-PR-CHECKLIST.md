# 上游回馈清单（UPSTREAM-PR-CHECKLIST）

> 对应 v2.9 评估《开源治理与合规_Flash报告》P1-1「只取不予」：把滞留 fork 的通用修复
> 整理成可向上游 `brunodev85/winlator-app` 提交的 PR 清单，逐项回馈、逐项销账。
> 证据口径：改动要点与 fork 提交号自 `local/README.internal.md` §5.1（该文件仅本地保留，
> 本表自带关键事实，不依赖其可读）。实际提交 PR 需 GitHub 账号与联网操作，由维护者执行；
> 提交后在本表登记 PR 链接与状态。

| # | 候选改动 | fork 提交 | 上游价值 | PR 适配前提 | 状态 |
|---|---|---|---|---|---|
| 1 | `core/FileUtils.java`：缺 `.update-timestamp` 时 NPE 修复 | 1e97eef | 通用健壮性（新装环境必触发） | 无依赖，单文件小 diff | draft PR https://github.com/brunodev85/winlator-app/pull/101 |
| 2 | `core/WineUtils.java`：同一 NPE 根因修复 | 1e97eef | 同上（与 #1 同根因，建议合并为一个 PR） | 同上 | draft PR https://github.com/brunodev85/winlator-app/pull/101（与 #1 合并） |
| 3 | `core/ProcessHelper.java`：wine 输出落盘 `wine_exec.log`（原 /dev/null） | b7243c8 | 可诊断性（排障必需） | 上游可能倾向开关化：PR 描述给出默认关闭选项 | draft PR https://github.com/brunodev85/winlator-app/pull/102 |
| 4 | `core/LocaleHelper.java`：`supportedLocales` 增 `zh_CN` | bb810ca | 简体中文界面 | 属上游文件（本仓 A7 登记例外）：先开 issue 讨论，上游接受后本仓改动随 rebase 回退 | issue https://github.com/brunodev85/winlator-app/issues/100 |
| 5 | `xenvironment/components/GuestProgramLauncherComponent.java`：libmpshim 条件 LD_PRELOAD | b7243c8 / 45b76b6 | 特定设备（exec-mprotect 限制）启动链修复 | 需说明适用面（39-bit VA 设备），上游可能要求设备探测开关 | 评估中 |

**销账规则**：每项 PR 被上游合并（或明确拒绝并记录理由）后，把「状态」改为
`已合并 <PR 链接>` / `已拒绝：<理由>`；上游吸收后本仓对应改动随 rebase 自然消解。

## 2026-11-10 执行结果

- **#1+#2** → draft PR https://github.com/brunodev85/winlator-app/pull/101（2 文件 +7/−3）
- **#3** → draft PR https://github.com/brunodev85/winlator-app/pull/102（1 文件 +6/−1）
- **#4** → issue https://github.com/brunodev85/winlator-app/issues/100（仅开 issue，不提 PR）
- **#5** → 保持「评估中」，本轮不动
- 复现路径：`origin`（ReSerendipity/GameShell）不是上游 fork，跨仓 PR 被 GitHub 拒绝；已新建 fork `ReSerendipity/winlator-app` 并将两条分支推至该 fork，再由 fork 向上游发起 draft PR。
- 未做：未转 ready、未合并、未删分支、未改本仓 `main`、未 force push、未 cherry-pick（补丁由 `git diff` 生成后 `git apply`）。
- 待办：等上游维护者反馈；如需开关化 #3 或调整注释语言，按 PR 评论跟进。

## 2026-11-09 决策

### 上游 PR 本轮不提交

- **结论**：本轮不提交上游 PR；清单中的 5 项候选改动全部保留，状态标注为「待所有者执行」。
- **理由**：提交需要维护者账号与联网操作条件，当前不具备由本流程代为提交的条件。
- **触发条件**：由所有者具备账号与网络条件后，按清单逐项评估、提交并回填 PR 链接与状态。
- **责任人**：Doro。

## 2026-11-09 执行回填（WO-B30）

- **#4**：已开讨论 issue https://github.com/brunodev85/winlator-app/issues/100（未发 PR，等上游表态）。
- **#1+#2、#3**：分支与补丁已就绪并已推到 `origin`（`upstream/fix-fileutils-wineutils-npe` = `0d078d0`、
  `upstream/feat-wine-exec-log` = `3528add`），但 **draft PR 未能创建**：`origin`（ReSerendipity/GameShell）
  在 GitHub 上**不是** `brunodev85/winlator-app` 的 fork（`gh api repos/ReSerendipity/GameShell` 返回
  `fork=false`、`parent` 为空），而跨仓 PR 要求 head 仓库是被 base 仓的 fork 且同名 `winlator-app`。
  报错：`Head repository can't be blank / No commits between brunodev85:main and ReSerendipity:upstream/...`。
  故 #1–#3 状态保持「待提交」，等所有者决定是否新建 `ReSerendipity/winlator-app` fork 后再建 PR。
- **#5**：未动作，等待上游反馈后再评估。
