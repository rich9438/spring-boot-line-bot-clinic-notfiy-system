#!/usr/bin/env bash
# 建立圖文選單並設為所有使用者的預設選單；重複執行時會刪除舊的預設選單。
# 用法：./deploy/richmenu/setup-richmenu.sh   （從專案根目錄的 .env 讀取 LINE_CHANNEL_TOKEN）
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
if [[ -z "${LINE_CHANNEL_TOKEN:-}" && -f "$ROOT/.env" ]]; then
  LINE_CHANNEL_TOKEN="$(grep -E '^LINE_CHANNEL_TOKEN=' "$ROOT/.env" | cut -d= -f2-)"
fi
: "${LINE_CHANNEL_TOKEN:?請設定 LINE_CHANNEL_TOKEN（環境變數或 .env）}"

API="https://api.line.me/v2/bot"
DATA_API="https://api-data.line.me/v2/bot"
AUTH="Authorization: Bearer ${LINE_CHANNEL_TOKEN}"

extract_id() { sed -n 's/.*"richMenuId":"\([^"]*\)".*/\1/p'; }

# 呼叫 LINE API；失敗時印出 HTTP 狀態碼與 LINE 回傳的錯誤內容後結束
call() {
  local step="$1"
  shift
  local response status body
  response="$(curl -sS -w '\n%{http_code}' "$@")"
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [[ "$status" != 2* ]]; then
    echo "失敗（${step}）：HTTP ${status}" >&2
    [[ -n "$body" ]] && echo "$body" >&2
    exit 1
  fi
  printf '%s' "$body"
}

NEW_ID=""
DONE=false
# 中途失敗時刪除本次建立、尚未設為預設的選單，避免留下孤兒選單
cleanup_on_failure() {
  if [[ "$DONE" != true && -n "$NEW_ID" ]]; then
    echo "刪除本次建立的選單 $NEW_ID" >&2
    curl -sS -o /dev/null -X DELETE "$API/richmenu/$NEW_ID" -H "$AUTH" || true
  fi
}
trap cleanup_on_failure EXIT

echo "1/5 驗證 richmenu.json"
call "驗證" -X POST "$API/richmenu/validate" -H "$AUTH" -H "Content-Type: application/json" \
  --data-binary @"$DIR/richmenu.json" >/dev/null

echo "2/5 查詢目前的預設選單"
# 尚未設定預設選單時 LINE 回 404，屬正常情況
OLD_ID="$(curl -sS "$API/user/all/richmenu" -H "$AUTH" | extract_id || true)"
echo "    目前預設 = ${OLD_ID:-（無）}"

echo "3/5 建立圖文選單"
NEW_ID="$(call "建立" -X POST "$API/richmenu" -H "$AUTH" -H "Content-Type: application/json" \
  --data-binary @"$DIR/richmenu.json" | extract_id)"
echo "    richMenuId = $NEW_ID"

echo "4/5 上傳選單圖片"
call "上傳圖片" -X POST "$DATA_API/richmenu/$NEW_ID/content" -H "$AUTH" -H "Content-Type: image/png" \
  --data-binary @"$DIR/richmenu.png" >/dev/null

echo "5/5 設為所有使用者的預設選單"
# 此 API 沒有 body，但 LINE 要求 POST 必須帶 Content-Length，否則回 411；-d '' 會送出 Content-Length: 0
call "設為預設" -X POST "$API/user/all/richmenu/$NEW_ID" -H "$AUTH" -d '' >/dev/null
DONE=true

if [[ -n "$OLD_ID" && "$OLD_ID" != "$NEW_ID" ]]; then
  echo "刪除舊的預設選單 $OLD_ID"
  call "刪除舊選單" -X DELETE "$API/richmenu/$OLD_ID" -H "$AUTH" >/dev/null
fi
echo "完成！請關閉並重新開啟與官方帳號的聊天室查看選單。"
