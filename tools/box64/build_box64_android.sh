#!/usr/bin/env bash
# 构建 GameShell 自带的 box64（glibc/arm64，供 Winlator 式 rootfs 使用）。
#
# 为什么要自建：官方 winlator-app 只带 box64-0.4.4.tzst；本机启动链依赖
# 上游 main 的 custommem 修复 + tools/box64/box64-galgameshell.patch 的三处私有改动
# （39-bit VA 死循环、dl* 自插环）。配方与背景见 docs/box64-wine-launch-chain.md。
#
# 用法：
#   BOX64_SRC=... NDK=... GLSYSROOT=... ./build_box64_android.sh <upstream-ref> <version-label>
# 例：
#   ... ./build_box64_android.sh abfb8c3b 0.4.6
#
# 产物：app/src/main/assets/box64/box64-<version-label>.tzst（内部 ./usr/local/bin/box64）
# 不覆盖既有 tzst，便于在应用里按容器回退版本。
set -euo pipefail
# Git Bash(MSYS2) 会把形如 /data/... 的参数「翻译」成 Windows 路径，于是
# -Wl,--dynamic-linker=/data/data/... 被改写成 C:/Program Files/Git/data/...，
# 产物 PT_INTERP 直接废掉（脚本自检当场就拦下来了）。关掉参数路径转换。
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

# ROOT 要 Windows 形式：上面关掉了 MSYS 参数路径转换，交给 git/ar/stat 这些原生程序的
# 路径必须是它们认得的盘符路径，/c/Users/... 形式会「找不到文件」。
ROOT="$(cd "$(dirname "$0")/../.." && pwd -W)"
: "${BOX64_SRC:?需要 BOX64_SRC=box64 源码 checkout 路径}"
: "${NDK:?需要 NDK=Android NDK 根目录（配方要求 27.0.12077973）}"
: "${GLSYSROOT:?需要 GLSYSROOT=Debian arm64 glibc sysroot 路径}"
# rootfs 内的动态链接器绝对路径（Android linker 解析不了 /lib/…，见下方链接段注释）
: "${BOX64_INTERP:=/data/data/com.winlator/files/rootfs/lib/ld-linux-aarch64.so.1}"
REF="${1:?需要上游 ref（commit/branch）作为构建基线}"
VER="${2:?需要版本号标签，如 0.4.6}"

CLANG="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
LLVM_STRIP="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe"
[ -x "$CLANG" ] || { echo "找不到 clang：$CLANG" >&2; exit 1; }
[ -d "$GLSYSROOT/lib" ] || { echo "sysroot 缺 lib/：$GLSYSROOT" >&2; exit 1; }
# Windows 上写进 cmake/原生程序的路径必须是它们认得的盘符路径 + 正斜杠：
# cmake 会把 `\U` `\t` 当转义序列直接报错（首跑就是这么挂的）；而上面关掉了 MSYS 参数
# 自动转换，/c/Users/... 这种 MSYS 形式喂给 cmake/ninja/clang 就是「系统找不到指定的文件」。
fwd() { printf '%s' "$1" | sed -e 's|\\|/|g'; }
winpath() {
  case "$1" in
    /?/*) printf '%s/%s' "$(cd "$(dirname "$1")" && pwd -W)" "$(basename "$1")" ;;
    *)    fwd "$1" ;;
  esac
}
CLANG_F="$(winpath "$CLANG")"
GLSYSROOT_F="$(winpath "$GLSYSROOT")"
# ninja 不一定在 PATH（本机只在 Android SDK 的 cmake 目录里），显式指给 cmake
if [ -z "${NINJA:-}" ]; then
  if command -v ninja >/dev/null 2>&1; then
    NINJA="$(command -v ninja)"
  elif command -v cmake >/dev/null 2>&1 && [ -x "$(dirname "$(command -v cmake)")/ninja.exe" ]; then
    NINJA="$(dirname "$(command -v cmake)")/ninja.exe"
  fi
fi
[ -n "${NINJA:-}" ] || { echo "找不到 ninja：请把它加进 PATH 或设 NINJA=…/ninja.exe" >&2; exit 1; }
NINJA_F="$(winpath "$NINJA")"

cd "$BOX64_SRC"
# 本脚本会 checkout/apply，绝不在带未提交改动的树上跑——那会吃掉别人的现场（补丁已另存于
# tools/box64/，但工作树里的其它改动没有）。要构建就指向一棵干净 checkout。
#
# 例外：脚本自己生成的产物（工具链文件 / build 目录 / 链接垫片目录）与「补丁已经打上」
# 这两种情况要放过——否则同一条命令第二次跑就被自己的残留挡住（首跑就是这么挂的）。
SELF_GEN='^\?\? \.(galgameshell-toolchain\.cmake|galgameshell-linkshim)/?|^\?\? build-'
DIRTY="$(git status --porcelain | grep -Ev "$SELF_GEN" || true)"
PATCH_APPLIED=0
if [ -n "$DIRTY" ]; then
  # 唯一允许的改动状态：私有补丁已经在树上（能反向还原）。否则停。
  if git apply --reverse --check "$ROOT/tools/box64/box64-galgameshell.patch" 2>/dev/null; then
    FILE_COUNT="$(printf '%s\n' "$DIRTY" | wc -l | tr -d ' ')"
    PATCH_FILES="$(grep -c '^diff --git' "$ROOT/tools/box64/box64-galgameshell.patch")"
    if [ "$FILE_COUNT" = "$PATCH_FILES" ]; then
      PATCH_APPLIED=1
      echo "工作树仅有本脚本私有补丁的改动（$FILE_COUNT 个文件），按「已打补丁」继续重放基线"
    fi
  fi
  if [ "$PATCH_APPLIED" = 0 ]; then
    echo "$BOX64_SRC 有未提交改动，已停止。请清理/换一棵干净 checkout 再构建。" >&2
    git status --short >&2
    exit 1
  fi
fi
git fetch --quiet origin
git checkout --quiet --detach "$REF"
if [ "$PATCH_APPLIED" = 1 ]; then
  # checkout 会带着改动走（同基线上就是原样保留），再校一次确实还在
  git apply --reverse --check "$ROOT/tools/box64/box64-galgameshell.patch" 2>/dev/null \
    || { echo "切到 $REF 后私有补丁缺失，已停止" >&2; exit 1; }
  echo "私有补丁随 checkout 保留，不重复重放"
else
  # 私有改动必须先落到干净树上；冲突就停，不要静默丢补丁
  if git apply --check "$ROOT/tools/box64/box64-galgameshell.patch" 2>/dev/null; then
    git apply "$ROOT/tools/box64/box64-galgameshell.patch"
    echo "已重放 box64-galgameshell.patch"
  else
    echo "补丁无法干净重放到 $REF —— 需要人工合并，已停止（不自动三方合并）" >&2
    exit 1
  fi
fi

# 工具链文件按环境生成，绝不把机器绝对路径写进仓库
TOOLCHAIN="$BOX64_SRC/.galgameshell-toolchain.cmake"
cat > "$TOOLCHAIN" <<EOF
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER "$CLANG_F")
set(CMAKE_C_COMPILER_TARGET aarch64-linux-gnu)
set(CMAKE_ASM_COMPILER "$CLANG_F")
set(CMAKE_ASM_COMPILER_TARGET aarch64-linux-gnu)
set(CMAKE_SYSROOT "$GLSYSROOT_F")
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
EOF

BUILD="$BOX64_SRC/build-$VER"
cmake -G Ninja -B "$BUILD" -S "$BOX64_SRC" \
  -DCMAKE_MAKE_PROGRAM="$NINJA_F" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DARM_DYNAREC=ON -DBOX32=ON -DWINLATOR_GLIBC=ON

# 只让 ninja 编对象，不让它链接：链接必须手工做，有两处官方配方给不了的坑
#   1) sysroot 里没有 gcc 的 crtbegin.o/crtend.o 与 libgcc —— clang 对
#      aarch64-linux-gnu 会把它们按**裸文件名**传给 lld（不受 -L 影响），
#      所以必须放进链接的工作目录；libgcc.a 用 compiler-rt builtins 顶替。
#   2) rootfs 的 glibc 不导出 atexit / pthread_atfork（只有 __cxa_atexit /
#      __register_atfork），box64 main 自 2026-09 起直接引用前者 —— 用
#      linkshim_glibc_atexit.c 转发补上，语义与 glibc 自身实现一致。
SHIM="$BOX64_SRC/.galgameshell-linkshim"
rm -rf "$SHIM"; mkdir -p "$SHIM"
"$LLVM_STRIP" --version >/dev/null   # 顺带确认 NDK 工具可用

# crt 对象与 libgcc：优先用 sysroot 里真实的 gcc 运行时（Debian libgcc-12-dev 的
# usr/lib/gcc/aarch64-linux-gnu/12/），拿不到才退回「空 crt + compiler-rt builtins」——
# 空 crtbegin.o 会丢掉 .init_array/.ctors，构造器不跑，产物能链上但不是可信复现。
GCC_RT="$(ls -d "$GLSYSROOT"/usr/lib/gcc/aarch64-linux-gnu/*/ 2>/dev/null | head -n1 || true)"
if [ -n "$GCC_RT" ] && [ -f "$GCC_RT/crtbegin.o" ] && [ -f "$GCC_RT/libgcc.a" ]; then
  cp "$GCC_RT/crtbegin.o" "$GCC_RT/crtend.o" "$GCC_RT/libgcc.a" "$SHIM/"
  [ -f "$GCC_RT/libgcc_eh.a" ] && cp "$GCC_RT/libgcc_eh.a" "$SHIM/" || true
  echo "crt/libgcc 取自 sysroot：$GCC_RT"
else
  printf 'static int _q;\n' > "$SHIM/empty.c"
  "$CLANG" --target=aarch64-linux-gnu -c "$SHIM/empty.c" -o "$SHIM/crtbegin.o"
  cp "$SHIM/crtbegin.o" "$SHIM/crtend.o"
  echo "sysroot 无 gcc 运行时，退回空 crt + compiler-rt（构造器会失效，仅用于链通调试）" >&2
  BUILTINS="$(ls "$NDK"/toolchains/llvm/prebuilt/*/lib/clang/*/lib/aarch64-unknown-linux-musl/libclang_rt.builtins.a \
             "$NDK"/lib/clang/*/lib/aarch64-unknown-linux-musl/libclang_rt.builtins.a 2>/dev/null | head -n1 || true)"
  [ -f "$BUILTINS" ] || { echo "找不到 compiler-rt builtins（NDK 下 aarch64-unknown-linux-musl）" >&2; exit 1; }
  cp "$BUILTINS" "$SHIM/libgcc.a"
fi
# libgcc_s.a 只用来满足 -lgcc_s 的搜索命中：**必须是空归档**。曾经把 crtend.o 塞进去，
# 结果和真正的 crtend.o 撞 `.tm_clone_table` 的 __TMC_END__（duplicate symbol）。
rm -f "$SHIM/libgcc_s.a"
"$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-ar.exe" rcs "$SHIM/libgcc_s.a"
"$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe" --target=aarch64-linux-gnu -O2 \
  -c "$ROOT/tools/box64/linkshim_glibc_atexit.c" -o "$SHIM/glibc_atexit_shim.o"

cmake --build "$BUILD" --target mainobj
cp "$SHIM/crtbegin.o" "$SHIM/crtend.o" "$BUILD/"

# 目标 box64 的链接这里必然是失败的（crt 裸文件名 + glibc 链接脚本里的绝对路径都对不上），
# 但它会在失败前写出 CMakeFiles/box64.rsp —— 那就是「编好了的全部对象」清单，
# 手工链接要用的正是这份。所以这一步允许失败，只校验 rsp 落地。
RSP="$BUILD/CMakeFiles/box64.rsp"
cmake --build "$BUILD" --target box64 >/dev/null 2>&1 || true
[ -f "$RSP" ] || { echo "CMake 没生成 box64.rsp，无法列出对象清单：$RSP" >&2; exit 1; }
echo "对象清单: $RSP ($(grep -c '\.o' "$RSP" || true) 项)"

# 关键：PT_INTERP 必须是 rootfs 内的绝对路径。Android 的 linker 解析不了
# /lib/ld-linux-aarch64.so.1，产物会以「正在启动…」卡死且 wine 一行日志都不出。
cd "$BUILD"
"$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe" --target=aarch64-linux-gnu \
  --sysroot="$GLSYSROOT" -O2 -g -DNDEBUG \
  -Wl,--export-dynamic -rdynamic -Wl,--no-as-needed \
  -lc -lm -ldl -lrt -lpthread -lresolv -lutil -Wl,--as-needed -pthread \
  -L"$SHIM" -no-pie -Xlinker --image-base=0x34800000 \
  -Wl,--dynamic-linker="$BOX64_INTERP" \
  "$SHIM/glibc_atexit_shim.o" "@CMakeFiles/box64.rsp" -o box64

BIN="$BUILD/box64"
# 链接产物自检：形态必须与现役 0.4.5 一致，否则装上去只会「正在启动…」
"$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe" -h "$BIN" | grep -q "AArch64" \
  || { echo "产物不是 AArch64 ELF" >&2; exit 1; }
"$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe" -l "$BIN" \
  | awk '/INTERP/{print "PT_INTERP bytes:", $6}'
# 这条不能只看大小：必须读出字符串本身等于 rootfs 内绝对路径（27B 的 /lib/… 也「像」正常）
INTERP_STR="$("$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe" -p .interp "$BIN" \
  | awk 'BEGIN{FS="] "} /\]/{print $2}' | tr -d '\0' | tr -d '\r\n')"
[ "$INTERP_STR" = "$BOX64_INTERP" ] || { echo "PT_INTERP 不符：'$INTERP_STR' != '$BOX64_INTERP'" >&2; exit 1; }
echo "PT_INTERP = $INTERP_STR"
"$LLVM_STRIP" --strip-all "$BIN"

OUT="$ROOT/app/src/main/assets/box64/box64-$VER.tzst"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/usr/local/bin"
cp "$BIN" "$STAGE/usr/local/bin/box64"
# 结构对齐官方：./usr/local/bin/box64 + zstd
# Windows 侧常常没有 zstd 可执行文件（本机就没有），退回 python 的 zstandard 模块压同一个
# tar 流——tzst 只是「tar + zstd 帧」，谁压的都一样。
if command -v zstd >/dev/null 2>&1; then
  tar -C "$STAGE" -c --zstd -f "$OUT" ./usr/local/bin/box64
else
  PY_ZSTD="$(command -v python3 || command -v python || true)"
  [ -n "$PY_ZSTD" ] || { echo "没有 zstd，也没有 python3 可用的 zstandard 模块" >&2; exit 1; }
  "$PY_ZSTD" -c "import zstandard" 2>/dev/null \
    || { echo "缺 zstandard 模块：pip install zstandard 或装 zstd 命令行" >&2; exit 1; }
  echo "本机无 zstd 命令行，改用 python zstandard 压 tar 流"
  tar -C "$STAGE" -c -f - ./usr/local/bin/box64 \
    | "$PY_ZSTD" -c "
import sys, zstandard
c = zstandard.ZstdCompressor(level=19)
with c.stream_writer(sys.stdout.buffer, closefd=False) as w:   # 必须 close 才能写完帧尾
    while True:
        b = sys.stdin.buffer.read(1 << 20)
        if not b:
            break
        w.write(b)
" > "$OUT"
fi
[ -s "$OUT" ] || { echo "产物为空：$OUT" >&2; exit 1; }

echo "== 完成 =="
echo "基线: $(git rev-parse HEAD)  标签: $VER"
echo "产物: $OUT ($(stat -c %s "$OUT") bytes)"
echo "记得把基线 hash 记进 docs/box64-wine-launch-chain.md §2（P2-8 可复现约定）"
