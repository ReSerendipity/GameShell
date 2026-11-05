#!/usr/bin/env bash
# 纯 JVM 回归验证入口（无需 Android SDK / 设备 / 模拟器）
#
# 为什么存在：被测对象都是纯 java 逻辑（无 Android 依赖），
# 因此可以直接用 JDK 编译运行。2026-10-05 修复「Ren'Py 标准布局漏判」时建立
# EngineDetector 回归；v2.9 评估整改（任务 GameShell-v29-remediate，
# 对应《测试_Flash报告》P1-1）扩展 GalgameLanguageDetector 回归。
#
# 用法： bash tools/run_detect_test.sh     （退出码非 0 表示有用例失败）
#
# M10 整改（2026-11-05）新增第四块：GalgameSaveManager 存档备份/恢复演练
# （tools/save-drill，含自动备份标记与滚动清理断言，演练留痕见 docs/save-recovery-drill.md）。
set -euo pipefail

# 根路径由 git 推导，不写死用户主目录，换机器/换克隆位置依旧可跑
R="$(git rev-parse --show-toplevel)"
SRC="$R/app/src/main/java/com/winlator/galgame"
T="$R/tools/detect-test"

# javac/java 查找口径与 ci/pre-push:17-21 一致：先查 PATH，再回退 $JAVA_HOME/bin
JAVAC=""
JAVA=""
if command -v javac >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
    JAVAC="javac"
    JAVA="java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
fi

if [ -z "$JAVAC" ]; then
    echo "[run_detect_test] 未找到 javac/java（PATH 与 JAVA_HOME 都没有），无法运行测试" >&2
    exit 1
fi

L="$R/tools/lang-detect-test"

rm -rf "$T/src" "$T/classes"
mkdir -p "$T/src/com/winlator/galgame" "$T/classes"

# 始终从真实源码复制，保证测的是当前代码（不是快照）
cp "$SRC/EngineDetector.java" "$T/src/com/winlator/galgame/"
cp "$SRC/GalgameLanguageDetector.java" "$T/src/com/winlator/galgame/"

echo "== EngineDetector 回归（DetectTest）=="
"$JAVAC" -encoding UTF-8 -nowarn \
  -d "$T/classes" \
  "$T/src/com/winlator/galgame/EngineDetector.java" \
  "$T/DetectTest.java"
"$JAVA" -Dfile.encoding=UTF-8 -cp "$T/classes" DetectTest

echo "== GalgameLanguageDetector 回归（LanguageDetectorTest）=="
"$JAVAC" -encoding UTF-8 -nowarn \
  -d "$T/classes" \
  "$T/src/com/winlator/galgame/GalgameLanguageDetector.java" \
  "$L/LanguageDetectorTest.java"
"$JAVA" -Dfile.encoding=UTF-8 -cp "$T/classes" LanguageDetectorTest

echo "== 分类 / ID / 图标编解码纯逻辑（FilterTest）=="
"$JAVAC" -encoding UTF-8 -nowarn \
  -d "$T/classes" \
  "$SRC/GalgameIds.java" \
  "$SRC/GalgameFilter.java" \
  "$SRC/IconCodec.java" \
  "$T/FilterTest.java"
"$JAVA" -Dfile.encoding=UTF-8 -cp "$T/classes" FilterTest

echo "== 存档备份/恢复演练（SaveBackupDrillTest，M10）=="
D="$R/tools/save-drill"
rm -rf "$D/src" "$D/classes"
mkdir -p "$D/src/com/winlator/galgame" "$D/classes"

# 始终从真实源码复制，保证测的是当前代码（不是快照）。
# GalgameSaveManager 依赖 android.util.Log / com.winlator.core.{AppUtils,FileUtils,
# WineRegistryEditor} / com.winlator.container.Container：取 CI 桩做类型与签名来源；
# 唯一例外是 core/FileUtils —— CI 桩的 delete 是空操作，演练的滚动清理步骤需要一个
# 真实递归删除，故用 tools/save-drill/stubs 下的同名同签名实现顶替（见其头注）。
cp "$SRC/GalgameSaveManager.java" "$D/src/com/winlator/galgame/"
"$JAVAC" -encoding UTF-8 -nowarn \
  -d "$D/classes" \
  $(find "$R/ci/stubs" -name '*.java' ! -path '*core/FileUtils.java') \
  "$D/stubs/com/winlator/core/FileUtils.java" \
  "$D/src/com/winlator/galgame/GalgameSaveManager.java" \
  "$D/SaveBackupDrillTest.java"
"$JAVA" -Dfile.encoding=UTF-8 -cp "$D/classes" com.winlator.galgame.SaveBackupDrillTest
