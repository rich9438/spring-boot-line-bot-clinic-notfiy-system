#!/usr/bin/env bash
# Flex 卡片範例工具：驗證格式，或推播到自己的 LINE 預覽。
#
# 用法（於專案根目錄）：
#   ./deploy/flex-samples/send-sample.sh validate           驗證所有範例（不推播、不耗額度）
#   ./deploy/flex-samples/send-sample.sh 04-progress        推播單一範例到自己的 LINE
#   ./deploy/flex-samples/send-sample.sh all                推播全部範例（約耗 18 則推播額度）
#
# 需要：.env 內的 LINE_CHANNEL_TOKEN；推播另需 LINE_USER_ID
#      （LINE Developers Console → Basic settings → Your user ID）
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
env_value() { [[ -f "$ROOT/.env" ]] && grep -E "^$1=" "$ROOT/.env" | cut -d= -f2- || true; }
LINE_CHANNEL_TOKEN="${LINE_CHANNEL_TOKEN:-$(env_value LINE_CHANNEL_TOKEN)}"
LINE_USER_ID="${LINE_USER_ID:-$(env_value LINE_USER_ID)}"
: "${LINE_CHANNEL_TOKEN:?請設定 LINE_CHANNEL_TOKEN（環境變數或 .env）}"

API="https://api.line.me/v2/bot/message"
AUTH="Authorization: Bearer ${LINE_CHANNEL_TOKEN}"

call() {
  local name="$1"
  shift
  local response status body
  response="$(curl -sS -w '\n%{http_code}' -H "$AUTH" -H "Content-Type: application/json" "$@")"
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [[ "$status" != 2* ]]; then
    echo "  ✗ ${name}：HTTP ${status} ${body}" >&2
    return 1
  fi
}

validate() {
  local file="$1" name
  name="$(basename "$file" .json)"
  # 驗證 API 只接受 messages，移除 "to" 欄位
  if sed '/"to" : /d' "$file" | call "$name" -X POST "$API/validate/push" --data-binary @-; then
    echo "  ✓ ${name}"
  else
    return 1
  fi
}

push() {
  local file="$1" name
  name="$(basename "$file" .json)"
  : "${LINE_USER_ID:?推播需要 LINE_USER_ID（環境變數或 .env）}"
  validate "$file"
  sed "s/\${LINE_USER_ID}/${LINE_USER_ID}/" "$file" | call "$name" -X POST "$API/push" --data-binary @-
  echo "    已推播"
}

target="${1:-validate}"
failed=0
case "$target" in
  validate)
    echo "驗證所有範例："
    for file in "$DIR"/*.json; do validate "$file" || failed=1; done
    ;;
  all)
    echo "推播所有範例："
    for file in "$DIR"/*.json; do push "$file" || failed=1; done
    ;;
  *)
    file="$DIR/${target%.json}.json"
    [[ -f "$file" ]] || { echo "找不到範例：$target" >&2; exit 1; }
    push "$file" || failed=1
    ;;
esac
exit "$failed"
