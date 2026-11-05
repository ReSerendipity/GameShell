# 存档备份与恢复演练记录（M10 止血，2026-11-05/06）

> 门禁 M10「存档与经济数据恢复演练」（⛔P0 绝对）此前判 `[ ]`：GalgameSaveManager
> 有导出/恢复函数但**无自动备份调用点**、**无恢复实测记录**。本记录 + 随附演练夹具
> （`tools/save-drill/`，已并入 `tools/run_detect_test.sh` 第四块）为清零证据。
> 形态注记（评估报告 §12.1 原文口径）：存档为用户本地可控文件、无服务端经济资产。

## 1. 本轮代码改动（演练的对象）

| 改动 | 位置 |
|---|---|
| 自动备份调用点：`autoBackupAsync(gameId)` | `app/src/main/java/com/winlator/galgame/GalgameSaveManager.java` |
| 调用点接线：两处游戏启动成功后各一行 | `com/winlator/galgame/ui/GalgameLibraryActivity.java`（launchGame）、`com/winlator/GalgameDetailActivity.java`（launch） |
| 自动快照标记 `.autobackup` + 滚动清理（每游戏保留最新 5 份，只清带标记目录，手动导出永不清理） | 同上 GalgameSaveManager |
| JVM 测试缝隙：包内可见构造器注入 S: 盘根 | 同上 GalgameSaveManager |

自动备份语义：启动成功即异步快照 S: 盘到 `Downloads/galgame-backup/<gameId>-<ts>/`，
与手动导出同目录同名规范（恢复对话框零改动即可列出自动快照），失败静默不阻断启动。

## 2. 演练环境与命令原文

- 方式：纯 JVM 演练（无需 Android 设备/模拟器/SDK），复用本仓「自建 JVM 回归」惯例。
- 时间：2026-11-06T00:18:16+08:00（本地时区 +08:00）。
- 工作树：HEAD `3fe13fe1` + 本轮未提交改动（演练对象即本轮改动本体，随本记录同笔提交）。
- 演练临时工作区：`%TEMP%/save-drill-5707587304219333819`（java.io.tmpdir 下，用户名部分已打码；不在仓库内）。

命令原文（仓库根执行）：

```bash
bash tools/run_detect_test.sh
```

夹具唯一环境偏离（已在脚本头注声明）：CI 桩 `ci/stubs/com/winlator/core/FileUtils.java`
的 `delete` 是空操作，演练的滚动清理步骤改用 `tools/save-drill/stubs` 下同名同签名
的真实递归删除实现；其余桩（Log/AppUtils/WineRegistryEditor/Container）与 CI 同源。

## 3. 演练样本

- gameId：`gs_drill_game`
- 样本数：**5 个文件 / 7,725 字节**，覆盖 S: 盘三类布局：
  - 便携存档 `Save/save.dat`(4096B) `Save/global.dat`(1024B) + 嵌套子目录 `Save/deep/level2/entry.bin`(512B)
  - Shell Folder 重定向类 `Documents/config.ini`(文本)
  - `SavedGames/progress.sav`(2048B)
- 内容为确定性字节图案（`(i*mul) mod m`），非真实玩家存档（不引入 PII）。

## 4. 对平判据与逐断言结果（退出码 0）

| 步骤 | 动作 | 对平判据 | 结果 |
|---|---|---|---|
| 样本 | 造 5 文件/7725B 样本树 | 文件计数 == 5 | PASS |
| 导出 | `exportBackup(backupRoot, auto=true)` | 退出码 true；快照目录存在且唯一 | PASS |
| 导出 | 自动备份标记 | `.autobackup` 标记文件存在 | PASS |
| 导出 | **对平①**：快照 vs S: 盘 | 相对文件集合一致（跳过标记）且**逐文件字节相等** | PASS |
| 损坏 | 删 `Save/save.dat`、改写 `Save/global.dat`、混入 `stray.tmp` | 三项损坏落地可验 | PASS |
| 恢复 | `restoreBackup(快照)` | 退出码 true | PASS |
| 恢复 | **对平②**：恢复后 vs 已知样本字节 | 5 个样本文件**逐一与导出前字节相等** | PASS |
| 恢复 | 合并语义 | 覆盖式合并：陌生文件 `stray.tmp` 按语义保留 | PASS |
| 清理 | 8 份自动快照 → `pruneAutoBackups` | 恰剪 3 份（保留最新 5） | PASS |
| 清理 | 最老 3 份已删 | `1000000..1000002` 不存在 | PASS |
| 清理 | 最新自动快照存活 | `1000006` 与真实快照仍在 | PASS |
| 清理 | 手动导出保护 | 无标记目录（`999999`）永不被自动清理 | PASS |

汇总：**断言 13 通过 / 0 失败；`DRILL RESULT: PASS`；脚本退出码 0**。
同脚本前三块（EngineDetector / LanguageDetector / Filter 回归）同步全绿。

## 5. 覆盖范围声明（如实）

- 本演练验证**应用同源代码路径**（GalgameSaveManager 真实源码复制编译）的
  导出→清空/损坏→恢复→内容校验与自动备份清理逻辑，属 JVM 级实测。
- 真机级（Android 文件系统、S: 盘经 wine 重定向、Downloads 目录真实落盘）行为
  以 `real-device-verification.md` 的既有真机记录为准，本轮未在真机重复执行；
  门禁 M10 的形态注记（用户本地文件、无经济资产）下，止血目标为「备份机制 +
  一次可复核恢复实测」，本记录即该实测。
- 复跑方式：`bash tools/run_detect_test.sh`（任何克隆、任何机器，无需设备）。
