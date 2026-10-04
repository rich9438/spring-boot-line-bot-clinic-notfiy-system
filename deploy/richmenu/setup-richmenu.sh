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

echo "1/5 驗證 richmenu.json"
curl -fsS -X POST "$API/richmenu/validate" -H "$AUTH" -H "Content-Type: application/json" \
  --data-binary @"$DIR/richmenu.json" >/dev/null

echo "2/5 查詢目前的預設選單"
OLD_ID="$(curl -sS "$API/user/all/richmenu" -H "$AUTH" | extract_id || true)"

echo "3/5 建立圖文選單"
NEW_ID="$(curl -fsS -X POST "$API/richmenu" -H "$AUTH" -H "Content-Type: application/json" \
  --data-binary @"$DIR/richmenu.json" | extract_id)"
echo "    richMenuId = $NEW_ID"

echo "4/5 上傳選單圖片"
curl -fsS -X POST "$DATA_API/richmenu/$NEW_ID/content" -H "$AUTH" -H "Content-Type: image/png" \
  --data-binary @"$DIR/richmenu.png" >/dev/null

echo "5/5 設為所有使用者的預設選單"
curl -fsS -X POST "$API/user/all/richmenu/$NEW_ID" -H "$AUTH" >/dev/null

if [[ -n "$OLD_ID" && "$OLD_ID" != "$NEW_ID" ]]; then
  echo "刪除舊的預設選單 $OLD_ID"
  curl -fsS -X DELETE "$API/richmenu/$OLD_ID" -H "$AUTH" >/dev/null
fi
echo "完成！請關閉並重新開啟與官方帳號的聊天室查看選單。"
