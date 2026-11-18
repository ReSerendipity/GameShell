# 组件版本登记表（Component Version Registry）

> 定位：三个引擎/基座组件「内嵌版本 vs 上游最新稳定版」的**单一事实源**。
> 三个组件的升级路径完全不同：前两条是**换预编译产物**（与仓库内其他代码零冲突），
> 第三条是**按需逐笔移植**（冲突面见 [`ci/zero-mod-exceptions.md`](../ci/zero-mod-exceptions.md)）。
>
> 核查节奏：每次发版（CHANGELOG 版本号变更）前核查一次；结果更新本表「最近核查」列
> 并在 CHANGELOG 记一笔；升级操作单独开批次提交。

## 登记表

| 组件 | 进入本仓的形态 | 内嵌版本 | 上游仓库 | 上游最新稳定版 | 最近核查 | 升级路径 |
|---|---|---|---|---|---|---|
| Ren'Py 原生引擎 | 预编译产物 `jniLibs/arm64-v8a/librenpython.so` + `assets/renpy/**`（引擎源码不在仓库） | **8.5.3**（RAPT 构建：CPython 3.12.8 + Ren'Py 8.5.3 + pygame_sdl2/SDL2 静态合并为单一 so） | [renpy/renpy](https://github.com/renpy/renpy)（构建系统 [renpy/renpy-build](https://github.com/renpy/renpy-build)） | **8.5.3**（2026-05-15 "We Can Go to the Moon"）＝内嵌版，无需动作；master 活跃（nightly） | 2026-10-25 | 官方 RAPT 重跑构建，`librenpython.so` + `assets/renpy/**` **一起换**（`__pycache__/*.cpython-312.pyc` 必须与 so 的 CPython 同源）；Java 宿主依赖的 JNI 契约（`PythonSDLActivity` 接口、`start_python()` 固定执行 `getFilesDir()/main.py`）上游多年未变，大版本变更时才需动壳 |
| Kirikiroid2 | 预编译产物 `jniLibs/arm64-v8a/libgame.so`（+libffmpeg）+ 改写自上游的 `org/tvp/kirikiri2/KR2Activity.java`（唯一有上游血缘的 Java 文件，BSD-3） | master 快照（上游不给发行 tag，无法对齐版本号） | [zeas2/Kirikiroid2](https://github.com/zeas2/Kirikiroid2) | **无发行节奏**：master 最后推送 **2024-06-05**，最新 tag 1.3.9，HEAD d1c2b12 | 2026-10-25 | 上游已停更 ~16 个月，**无同步对象**（本仓实际自维护这条线）；若换新版 so，对照其 JNI 表核对 KR2Activity 的 native 声明——类名被 so 硬编码（`org/tvp/kirikiri2/KR2Activity`），不可改 |
| Winlator 源组件 | **源码级内嵌**（决策 A8），本仓直接改上游文件 | 嵌入点 **a030f55**（2026-09-14，versionCode 33）；上游基线 **8077f37**（2026-10-16） | [brunodev85/winlator-app](https://github.com/brunodev85/winlator-app) | **无 tag/release**（作者站外分发，以 main HEAD 计）：8077f37 | 2026-10-25（`git ls-remote` 复核：自 10-17 起 8 天无新提交） | **按需逐笔移植**，当前落后 5 笔：vsync 默认关（4f539f0）、native_resolution（34df94f，strings 冲突已预登记）、外接鼠标焦点修复（a544910）、local ref 泄漏修复（8077f37）。移植前核对 `ci/zero-mod-exceptions.md` 冲突面清单 |

容器运行时附属件（随源组件/资产分发，不单独立跟踪）：**Wine 10.10**（rootfs 内，随 Winlator
rootfs 更新）、**box64 0.4.5**（本仓自建 `assets/box64/box64-*.tzst`，版本真源是
`core/DefaultVersion.java` 的 `BOX64` 常量，属零改例外登记文件，见
`ci/zero-mod-exceptions.md` 受守卫路径表）。

## 核查方法（复验命令）

```bash
# Winlator：对比登记的 HEAD（8077f37）
git ls-remote https://github.com/brunodev85/winlator-app HEAD

# Kirikiroid2：tag 列表 + HEAD（tag 排序取最新）
git ls-remote --tags --refs https://github.com/zeas2/Kirikiroid2 | tail -5
git ls-remote https://github.com/zeas2/Kirikiroid2 HEAD

# Ren'Py：仓库 tags 不标发行版，稳定版以官网为准
#   https://www.renpy.org/latest.html   （最新稳定版）
#   https://www.renpy.org/release_list.html （全历史）
# 仓库活跃度（当日有无提交）：
#   https://github.com/renpy/renpy/commits/master.atom
```

## 登记要求

1. **发版前必查**：更新「最近核查」列；若任一组件落后于最新稳定版，在 CHANGELOG
   `Changed/Upgraded` 里明确写「落后于 X，本版未跟进」或跟进的动作。
2. **升级即换件**（前两条路线）：替换产物后必须真机跑一轮
   [`real-device-verification.md`](real-device-verification.md) 对应引擎的冒烟用例。
3. **不追 nightly**：只跟官方稳定版；Winlator 组件只按需移植（修复型提交优先，
   功能型提交评估后再定）。

## Wincomponents 资产登记：VC++ 2015-2022（`vcrun2019`）

> 位置：`app/src/main/assets/wincomponents/vcrun2019.tzst`；清单条目在
> `app/src/main/assets/wincomponents/wincomponents.json`。该目录的 `.tzst` 按
> `.gitignore:27` 默认不跟踪，构建 APK 时由本地资产直接打包。

| 项目 | 登记值 |
|---|---|
| 组件键 | `vcrun2019`（`dlnames` 26 项，`restoreBuiltinDlls=true`） |
| 内容 | 官方 VC++ 2015-2022 x64/x86 CRT + MFC：`system32/` 26 个 DLL、`syswow64/` 25 个 DLL（x86 无 `vcruntime140_1.dll`） |
| 官方安装包版本 | `14.44.35211.0`（ProductName `Microsoft Visual C++ 2015-2022 Redistributable (x64/x86) - 14.44.35211`） |
| x64 安装包 | 25,635,768 B；SHA-256 `CC0FF0EB1DC3F5188AE6300FAEF32BF5BEEBA4BDD6E8E445A9184072096B713B` |
| x86 安装包 | 13,953,392 B；SHA-256 `0C09F2611660441084CE0DF425C51C11E147E6447963C3690F97E0B25C55ED64` |
| x64 下载 | `https://download.visualstudio.microsoft.com/download/pr/e9604e21-4b79-4096-a4b3-1b63d4b55b4d/CC0FF0EB1DC3F5188AE6300FAEF32BF5BEEBA4BDD6E8E445A9184072096B713B/VC_redist.x64.exe` |
| x86 下载 | `https://download.visualstudio.microsoft.com/download/pr/e9604e21-4b79-4096-a4b3-1b63d4b55b4d/0C09F2611660441084CE0DF425C51C11E147E6447963C3690F97E0B25C55ED64/VC_redist.x86.exe` |
| 本仓 tzst | 11,405,839 B；SHA-256 `77398B03CA00358CC0F4FD08B54C3A8D34F0D5EC7EECB8D5C2DFDA6578005084`；tar+zstd，根为 `./` + `system32/` + `syswow64/`，与 `vcrun2010.tzst` 同构 |
| 提取方式 | 官方 bootstrapper 的 PE 叠加层内 `MSCF` CAB（非 `/layout`；`/layout` 只复制 bootstrapper，不能产出 MSI/CAB）；CAB 内 MSI/CAB 再解出 DLL，未修改二进制 |
| 未纳入 | 官方安装包中的 Windows 7/8/8.1 UCRT 更新 CAB（KB2999226 等）；本组件面向 Wine prefix，UCRT 由 Wine 提供，不安装宿主机更新 |
| 再分发依据 | Microsoft Visual C++ 可再发行组件；仅持有有效 Visual Studio 许可证的开发者可按 Microsoft 软件许可条款再分发**未修改**文件。条款/可分发代码清单：<https://learn.microsoft.com/zh-cn/cpp/windows/redistributing-visual-cpp-files?view=msvc-170>、<https://learn.microsoft.com/en-us/visualstudio/releases/2022/redistribution?view=visualstudio> |
| 安装链路 | `XServerDisplayActivity.extractWinComponentFiles()` → `TarCompressorUtils.extract(Type.ZSTD, ..., "wincomponents/vcrun2019.tzst", windowsDir)`；`WineUtils.overrideWinComponentDlls()` 按 `dlnames` 写 `Software\\Wine\\DllOverrides=native,builtin` |
| 兼容接线 | Winlator 的 `Container.checkObsoleteOrMissingProperties()` 只保留 `DEFAULT_WINCOMPONENTS` 中列出的键；新键会被裁掉。已在 `Container.DEFAULT_WINCOMPONENTS` 与 `FALLBACK_WINCOMPONENTS` **末尾新增** `vcrun2019=0`，既有条目与顺序未改。 |
| 真机验证（2026-11-15） | 设备 RMX5010 / Android 16 / `com.winlator` 11.5；容器 `xuser-4` 配置 `wincomponents=...,vcrun2019=1`。冷启动 `GalgameShortcutActivity` 后：`system32/` 26 个、`syswow64/` 25 个 VC DLL 落盘（mtime 15:50 刷新）；`user.reg` 的 `Software\\Wine\\DllOverrides` 有 26 个 `*140*` 键，均为 `native,builtin`；`msvcp140.dll` / `vcruntime140.dll` / `msvcp140_1.dll` / `mfc140.dll` 的 SHA-256 与官方 staging 逐字节一致。 |

