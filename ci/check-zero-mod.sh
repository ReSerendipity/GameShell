#!/usr/bin/env bash
# ci/check-zero-mod.sh — 零改铁律校验（CI `rebase-guard` 与本地共用这一份实现）
#
# 为什么存在：本项目以源码组件形式内嵌 winlator-app（LGPL-2.1，见 NOTICE §1 / 决策 A8），
# 组件文件不得随意改动——例外必须登记：既是 LGPL §2 的修改标注，也是按需移植官方修复时的冲突面清单。
# 原实现把基线写死成「push 事件字段，取不到就回落 HEAD~1」，在 pull_request 上退化成
# 只比最后一笔，且把 git diff 的失败用 `|| true` 吞成空名单——空名单同样打印 OK。
# 现在按事件取权威基线，基线不可解析就显式失败，绝不回落。
#
# 例外登记不在本文件：见 ci/zero-mod-exceptions.md（改动下列文件前先在那裡核对）。
#
# 用法（本地，仓库根）：
#     ZERO_MOD_BASE=<base sha> bash ci/check-zero-mod.sh
# CI 由 .github/workflows/ci.yml 传入 GH_EVENT / EVENT_BEFORE / PR_BASE_SHA。
#
# 2026-11-02 起支持「登记例外」放行：唯一放行来源是 ci/zero-mod-exceptions.md 内的
# zero-mod-allow 围栏块，按「变更类型 + 路径」精确匹配；理由/日期缺一、任何一行无法
# 解析、或围栏未闭合/重复出现，一律按登记损坏拒绝（exit 2，fail-closed）。不提供任何
# 全局跳过开关，也不读取任何环境变量开关。格式说明见该文件对应章节。
set -euo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

# 只锁官方真实路径（避免误伤 ci/stubs 里的同名桩）
FORBIDDEN='^app/src/main/java/com/winlator/container/Container\.java$|^app/src/main/java/com/winlator/core/LocaleHelper\.java$|^app/src/main/res/values/strings\.xml$'

BASE="${ZERO_MOD_BASE:-}"
if [ -z "$BASE" ]; then
  case "${GH_EVENT:-}" in
    pull_request) BASE="${PR_BASE_SHA:-}" ;;
    push)         BASE="${EVENT_BEFORE:-}" ;;
    *)            BASE="" ;;
  esac
fi

# 新建分支/标签推送时 before 为全 0；与「空」同等处理
case "$BASE" in
  ""|0000000000000000000000000000000000000000) BASE="" ;;
esac

if [ -z "$BASE" ]; then
  echo "::error::零改守卫无法确定比较基线（事件=${GH_EVENT:-未设置}）。" >&2
  echo "::error::不回落 HEAD~1——那只会覆盖最后一笔。请显式传入 ZERO_MOD_BASE=<base sha>。" >&2
  exit 2
fi

if ! git cat-file -e "${BASE}^{commit}" 2>/dev/null; then
  echo "::error::基线 ${BASE} 在本次检出中不可解析（历史被改写或浅克隆）。" >&2
  echo "::error::请用 fetch-depth: 0 重新检出，或传入可解析的 ZERO_MOD_BASE。" >&2
  exit 2
fi

echo "[zero-mod] 比较范围: ${BASE}..HEAD"

# 不设 `|| true`：git 失败必须让校验失败，不能退化成空名单
CHANGED="$(git diff --name-only "$BASE" HEAD)"

if [ -z "$CHANGED" ]; then
  echo "[zero-mod] 比较范围内无文件变更（空名单，非「已校验通过」）"
  exit 0
fi

echo "$CHANGED"

# 用 here-string 而不是 `printf | grep -q`：grep -q 命中即退出，会让上游 printf 收到
# SIGPIPE，在 `set -o pipefail` 下把「命中」翻转成非零状态——违规反而判为通过。
HIT_COUNT="$(grep -Ec "$FORBIDDEN" <<< "$CHANGED" || true)"

if [ "${HIT_COUNT:-0}" -gt 0 ]; then
  # ── 登记例外放行（fail-closed，勿放宽）─────────────────────────────────────
  # 唯一放行来源：ci/zero-mod-exceptions.md 的 zero-mod-allow 围栏块，一行一条：
  #     <A|M|D> <路径> | <理由> | <日期 YYYY-MM-DD>
  # 规则：
  #   1. 「变更类型 + 路径」与某条登记完全一致才放行该命中；无通配/前缀/目录级匹配。
  #   2. 理由与日期缺任一字段、任何一行无法解析、围栏未闭合或重复出现 → 整表按
  #      登记损坏拒绝（exit 2，fail-closed，绝不静默放行）。
  #   3. 重命名（R*）/复制（C*）等复合状态没有可登记类型，一律按未登记处理。
  #   4. 不提供全局跳过开关，也不读取任何环境变量开关。
  REGISTRY="ci/zero-mod-exceptions.md"

  if [ ! -f "$REGISTRY" ]; then
    echo "::error::FAIL 零改铁律：${HIT_COUNT} 个官方核心文件在本次范围被修改（violation of zero-modification rule）" >&2
    echo "::error::例外登记 ${REGISTRY} 缺失，无任何放行依据。" >&2
    grep -En "$FORBIDDEN" <<< "$CHANGED" | sed 's/^/  命中: /' >&2
    exit 1
  fi

  # 提取围栏块；未闭合 / 重复出现均视为登记表损坏
  if ! ALLOW_BLOCK="$(awk '
        /^```zero-mod-allow$/ { if (inblock) bad = 1; inblock = 1; next }
        inblock && /^```$/    { inblock = 0; next }
        inblock               { print }
        END                   { if (bad || inblock) exit 3 }
      ' "$REGISTRY" 2>/dev/null)"; then
    echo "::error::零改例外登记（zero-mod-allow 围栏块）未闭合或重复出现，按登记损坏拒绝（fail-closed）。" >&2
    exit 2
  fi

  # 逐行严格解析；出现任何无法解析的行 → 整表失效（fail-closed）
  ENTRY_RE='^([AMD])[[:space:]]+([^[:space:]]+)[[:space:]]*\|[[:space:]]*([^|]*[^[:space:]][^|]*)[[:space:]]*\|[[:space:]]*([0-9]{4}-[0-9]{2}-[0-9]{2})[[:space:]]*$'
  ALLOW_KEYS=""
  MALFORMED=""
  while IFS= read -r entry; do
    case "$entry" in
      ''|'#'*) continue ;;  # 允许空行与 # 注释行
    esac
    if ! [[ "$entry" =~ $ENTRY_RE ]]; then
      MALFORMED="${MALFORMED}${entry}
"
      continue
    fi
    ALLOW_KEYS="${ALLOW_KEYS}${BASH_REMATCH[1]} ${BASH_REMATCH[2]}
"
  done <<< "$ALLOW_BLOCK"

  if [ -n "$MALFORMED" ]; then
    echo "::error::零改例外登记（zero-mod-allow 块）存在无法解析的行，整表按损坏拒绝（fail-closed）：" >&2
    printf '%s' "$MALFORMED" | sed 's/^/  无法解析: /' >&2
    echo "::error::格式：<A|M|D> <路径> | <理由> | <日期 YYYY-MM-DD>；字段缺一不可，字段内不得含 |。" >&2
    exit 2
  fi

  # 命中路径与登记键逐一精确比对（重命名的状态是 R100 之类，永不匹配 A|M|D 键）
  VIOLATIONS=""
  ALLOWED_HITS=""
  while IFS=$'\t' read -r st rest; do
    [ -n "$st" ] || continue
    while IFS= read -r p; do
      [ -n "$p" ] || continue
      if [[ "$p" =~ $FORBIDDEN ]]; then
        if printf '%s\n' "$ALLOW_KEYS" | grep -Fxq -- "${st} ${p}"; then
          ALLOWED_HITS="${ALLOWED_HITS}${st} ${p}
"
        else
          VIOLATIONS="${VIOLATIONS}${st} ${p}
"
        fi
      fi
    done <<< "${rest//$'\t'/$'\n'}"
  done < <(git diff --name-status "$BASE" HEAD)

  if [ -n "$VIOLATIONS" ]; then
    echo "::error::FAIL 零改铁律：以下官方核心文件变更未在 zero-mod-allow 登记中精确匹配（路径+变更类型）：" >&2
    printf '%s' "$VIOLATIONS" | sed 's/^/  未放行: /' >&2
    echo "::error::登记与格式见 ci/zero-mod-exceptions.md；未登记的官方文件改动一律不接受。" >&2
    exit 1
  fi

  if [ -n "$ALLOWED_HITS" ]; then
    printf '%s' "$ALLOWED_HITS" | sed 's/^/  已放行（登记例外）: /'
  fi
  echo "[zero-mod] OK 零改铁律通过（命中均已与 zero-mod-allow 登记精确匹配）"
  exit 0
fi

echo "[zero-mod] OK 零改铁律通过（已比对 $(grep -c '' <<< "$CHANGED") 个变更文件）"
