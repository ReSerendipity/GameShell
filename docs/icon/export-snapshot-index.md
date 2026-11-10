# GameShell 启动图标（当前构建版）

> **迁移说明（2026-11-05）**：本索引原存于仓库外 `C:\Users\Doro\APP\repository\_app-icons\GameShell\`，现已迁入仓库 `docs/icon/`。
> 迁移时经 `git hash-object` 复验，6 个图标文件与仓库内正式位置字节一致，仓库外的冗余副本已删除。下表「导出文件」一列仅为历史快照命名记录，磁盘上已无对应文件。

- 仓库：`C:\Users\Doro\APP\repository\GameShell`
- 索引位置：`C:\Users\Doro\APP\repository\GameShell\docs\icon\export-snapshot-index.md`
- 当前 HEAD：`34ed0dbd`（2026-11-02，`docs(governance): 登记 GameShell 改名的 app_name 同步`）
- 仓库改名提交：`b78ce0a8`（2026-11-02，`chore(repo): 仓库改名 GalgameShell → GameShell 并同步内部引用`）
- 图标来源：**工作区未提交快照**（G34O 定稿，文件时间 2026-11-01）；这 6 个图标文件不在上述提交内
- 整理规则：按用户 2026-11-02 指令，仅保留当前工作区这一版（本地实际构建使用的版本），历史版本导出文件已全部删除

## 保留清单（6 个图标文件）

| 导出文件 | 原始路径 | 类型 | 尺寸/格式 | 说明 |
| --- | --- | --- | --- | --- |
| `20261101_mipmap-anydpi-v26_ic_launcher.xml` | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive Icon XML | 433 B | `android:icon` 指向的自适应图标；背景 + G34O 前景 + monochrome |
| `20261101_mipmap-anydpi-v26_ic_launcher_round.xml` | `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Adaptive Icon XML | 433 B | 与方形 XML 内容相同；manifest 未声明 `android:roundIcon`，不是独立入口 |
| `20261101_values_ic_launcher_background.xml` | `app/src/main/res/values/ic_launcher_background.xml` | Color Resource XML | 332 B | 背景色 `#FFF5F7FA`（G34O 浅灰白，日夜一致） |
| `20261101_drawable_ic_launcher_foreground.xml` | `app/src/main/res/drawable/ic_launcher_foreground.xml` | Vector Drawable XML | 108dp 画布 | G34O「偏心窥圆/九弦」前景：雾墨三级 + 电光蓝强调弦 + 朱砂红眼 |
| `20261101_drawable_ic_launcher_monochrome.xml` | `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Vector Drawable XML | 108dp 画布 | 单色轮廓层，供系统主题图标着色（targetSdk 30 暂不消费） |
| `20261101_docs-icon_ic_launcher-512.png` | `docs/icon/ic_launcher-512.png` | PNG | 512×512 | 文档/商店预览用归档图，非运行时资源 |

## 当前生效链路

- `app/src/main/AndroidManifest.xml`：`android:icon="@mipmap/ic_launcher"`，无 `android:roundIcon`。
- `minSdkVersion 30`：API 26+ 恒定命中 `mipmap-anydpi-v26/ic_launcher.xml`，密度 PNG 不参与运行时渲染。
- 工作区当前图标 = `#FFF5F7FA` 背景 + `drawable/ic_launcher_foreground.xml`（G34O）+ `drawable/ic_launcher_monochrome.xml`。
- 仓库已改名 `GameShell`，但图标文件本身仍是未提交改动；若工作区内容被覆盖，需要重新生成或从 DraftPeek G34O 源文件恢复。

## 为什么不再保留历史版本

- 历史导出曾包含 2 代已提交图标（初始 Winlator 版 `5c199d5d`、品牌紫版 `1bf00116`/`0987d590` 及其并行同树提交 `47296012`/`00e806c0`）和品牌图 `5a7ab1c0`，共 39 个文件。
- 这些历史版本仍可从 Git 历史恢复：`git log --all -- '*ic_launcher*' '*launcher_foreground*' 'logo.png'`，或按上述提交号 `git show <sha>:<path>`。
- 当前只保留会进入实际构建的这一版，避免导出目录里混入已废弃的紫色图标、密度 PNG 与旧品牌图。

## 校验

- 6/6 文件与当前工作区源文件内容一致（重新复制后按字节校验；XML 因仓库改名注释由 `GalgameShell` 改为 `GameShell`，体积比旧快照各少 3 B）。
- 本目录无任何历史版本文件，无旧仓名文件；仅有 6 个图标 + 本 `index.md`。
