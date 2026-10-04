## 慈心吳婦產科看診進度追蹤與 LINE 通知平台
- 本文件為本專案的需求分析與系統設計文件（System Requirement & Architecture Design Document）。
- 版本：v1.1（2026-10-03）
    - v1.0：初版需求。
    - v1.1：依實際 XML 資料修正資料格式與解析規則；補齊通知規則語意、任務生命週期、LINE 指令、Schema 約束；技術堆疊改為 Spring MVC + Virtual Threads；預設通知門檻改為 10、3、到號。
    - v1.2：新增圖文選單（Rich Menu）與「選擇診間 → 輸入號碼」的互動式追蹤流程。
    - v1.3：所有回覆與推播改為 Flex Message 卡片（10.3），查看所有診間改為 Carousel。
    - v1.4：圖文選單右下角新增齒輪（通知設定），開啟通知設定卡片；選單預設收合；加好友時歡迎卡片與官方帳號後台的加入好友歡迎訊息並存。
    - v1.5：新增後台管理 Bot（第十四章）：主動告警、每日摘要、狀態與 Log 查詢、測試工具；`notification_history` 記錄推播結果。

### 一、專案目標

- 建立一套可部署於 NAS、VPS、Docker 或雲端環境的看診進度追蹤平台，透過定時讀取診所公開提供的診間 XML 資料來源，即時掌握各診間看診號碼變化，並透過 LINE Messaging API 主動推播通知給使用者。

- 系統不採用網頁爬蟲技術，不依賴 Selenium 或畫面解析，而是直接串接診所實際使用的 XML 資料來源，降低維護成本並提高穩定性。

- 系統設計目標包含：

    - 即時追蹤診間看診進度
    - LINE Bot 互動式設定追蹤
    - 多使用者同時追蹤（每位使用者同一時間 1 個追蹤任務）
    - 多診間同時監控
    - 可配置通知規則（使用者可自訂通知門檻）
    - 歷史資料留存
    - 預估候診時間
    - 易於擴充其他診所 Provider

### 二、已驗證之資料來源

- 透過 Chrome DevTools Network 分析確認：
    - 目前網站並非直接從 HTML 顯示叫號資訊，而是透過 JavaScript 定時向 AWS S3 請求 XML。

- 實際呼叫：
    - https://s3-ap-southeast-1.amazonaws.com/charity-wuobs/MedicineNumberList01.xml
    - https://s3-ap-southeast-1.amazonaws.com/charity-wuobs/MedicineNumberList02.xml
    - https://s3-ap-southeast-1.amazonaws.com/charity-wuobs/MedicineNumberList03.xml

- 前端採用 `$.ajax(...)` 輪詢（Polling）方式更新資料，網址尾端使用 `?rand=Math.random()` 作為 Cache Busting，本系統採相同機制。

#### 2.1 實際 XML 格式（2026-10-03 實測）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<RB>
  <TX CODE="7001" />
  <Datas>
    <RoomInfo Number1="052" Number2="" EXECTIME1="20261003121707342" EXECTIME2=""
              DOC1="吳瑞聰" DOC2="" DOC3="" Title1="婦產科" Title2="2" />
  </Datas>
  <RET RETCODE="0000" DESC="" />
</RB>
```

| 屬性 | 意義 | 本版處理 |
|---|---|---|
| `Number1` | 目前看診號碼，可能為 `""`、`"0"`、`"052"`（含前導 0） | 使用 |
| `EXECTIME1` | 資料更新時間，格式 `yyyyMMddHHmmssSSS`（Asia/Taipei） | 使用 |
| `DOC1` | 看診醫師，未看診時可能為空 | 使用 |
| `Title1` | 科別（例：一、二診為「婦產科」，三診為「小兒科」） | 使用，不可寫死 |
| `Title2` | 診別號碼（1、2、3） | 使用 |
| `RET@RETCODE` | 回應代碼，`0000` 表示成功 | 非 `0000` 視為抓取失敗 |
| `Number2`、`EXECTIME2`、`DOC2`、`DOC3` | 意義未知，實測皆為空 | 忽略 |

- 傳輸特性：
    - 回應內容開頭含 UTF-8 BOM，Parser 必須先移除。
    - `Content-Type` 為 `binary/octet-stream`，不可依賴 HTTP Client 的自動 XML 轉換，需以 byte[] 讀取後自行解析。
    - S3 回應帶有 `ETag`，可用 `If-None-Match` 條件請求，回應 304 時跳過解析。

#### 2.2 「看診中」判定

- 符合以下任一條件即視為「未看診」（`RoomStatus.inSession = false`）：
    - `Number1` 為空字串或 `0`。
    - `EXECTIME1` 的日期不是今天（Asia/Taipei）。實測一診的資料曾停留在兩天前。
- 未看診的診間：不觸發任何通知，「診間」指令顯示「未看診」，也不接受新的追蹤。

### 三、系統架構

```
  LINE Platform
       │  Webhook (POST /callback，簽章驗證)
       ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ LineWebhook      │───►│ Command Parser   │───►│ Tracking Service │
│ Handler          │    └──────────────────┘    └────────┬─────────┘
└──────────────────┘                                     │ CRUD
       ▲ Reply API                                       ▼
       │                                         ┌──────────────────┐
       │                                         │ PostgreSQL       │
       │                                         └──────────────────┘
       │                                                ▲
┌──────────────────┐    ┌──────────────────┐            │ 寫入歷史 / 讀取任務
│ Queue Polling    │───►│ ClinicProvider   │──► AWS S3 XML Source
│ Scheduler (30s)  │    │ (WuObs)          │            │
└────────┬─────────┘    └────────┬─────────┘            │
         │                       ▼                      │
         │              ┌──────────────────┐            │
         │              │ XmlRoomStatus    │            │
         │              │ Parser           │            │
         │              └────────┬─────────┘            │
         │                       ▼                      │
         │              ┌──────────────────┐            │
         └─────────────►│ RoomStatusCache  │────────────┤
           號碼有變動時   └────────┬─────────┘            │
                                 ▼                      │
                        ┌──────────────────┐            │
                        │ Notification     │────────────┘
                        │ Engine + Rules   │
                        └────────┬─────────┘
                                 ▼
                           LINE Push API
```

### 四、技術堆疊

- Backend
    - Java 21
    - Spring Boot 4.1.x
    - Spring MVC（Webhook 端點）+ Virtual Threads（`spring.threads.virtual.enabled=true`）
    - Spring Scheduler
    - Spring RestClient（抓取 XML）
    - Spring Data JPA
    - Spring Validation
    - Flyway（資料庫版本管理）
    - LINE Messaging API SDK：`com.linecorp.bot:line-bot-spring-boot-webmvc` / `line-bot-spring-boot-handler` 10.2.0（以 Spring Boot 4.1.1 建置）

- v1.0 原列 Spring WebFlux。WebFlux 是 reactive 模型，而 JPA 是阻塞式，兩者混用效益不高，因此 v1.1 改為 Spring MVC + Virtual Threads。

- Database
    - PostgreSQL 16（正式環境）
    - H2（PostgreSQL 相容模式，僅供自動化測試）

- Cache
    - 本版：記憶體內 `RoomStatusCache`（ConcurrentHashMap）
    - 未來可選 Redis：當前診間狀態快取、通知去重、計算候診速度

- 不使用：LINE Notify 因為已停止新申請。

- Deployment
    - Docker
    - Docker Compose（app + PostgreSQL 16）
    - 未來可部署：Linux VPS

### 五、模組設計

- Clinic Provider Module
    - 目的：抽象化不同醫院、診所資料來源。
    - 介面：

```java
public interface ClinicProvider {

    String code();               // Provider 代碼，例：wuobs

    List<RoomStatus> fetchAllRooms();

    RoomStatus fetchRoom(Integer roomId);

}
```

- 目前實作：`WuObsClinicProvider`
    - 依設定的診間清單，以 Virtual Threads 平行抓取各診 XML。
    - 每個診間各自保存 ETag 快取，S3 回應 304 時沿用上次結果。
    - 單一診間抓取失敗只記 log，不影響其他診間。

- 未來可擴充，無須修改核心業務邏輯：`ChangGungProvider`、`NTUHProvider`、`MackayProvider`。為此所有資料表的診間資料皆附帶 `provider_code`。

### 六、XML Parser Module

- 格式見 2.1。

- 轉換對象：
```java
public record RoomStatus(

    String providerCode,

    Integer roomId,

    String roomName,         // 例：二診

    String doctorName,

    String department,

    Integer currentNumber,   // 未看診時為 null

    boolean inSession,

    LocalDateTime updateTime // 由 EXECTIME1 轉換

) {
}
```

- Parser 責任：`XmlRoomStatusParser` 負責 XML → RoomStatus。
    - 移除 BOM。
    - 停用 DTD／外部實體（防 XXE）。
    - `Number1` 空字串、非數字 → `null`；`"052"` → `52`。
    - `RETCODE` 非 `0000` → 拋出例外。
    - 依 2.2 判定 `inSession`。

### 七、Scheduler Module

- 定時輪詢 XML：

```yaml
clinic:
  polling:
    interval-seconds: 30
```

- 執行流程（fixed delay，每 30 秒）：
    1. 透過 Provider 抓取所有設定的診間。
    2. 與 `RoomStatusCache` 中的上一筆狀態比對 `currentNumber`、`inSession`。
    3. 有變動時：更新快取，看診中則寫入 `room_status_history`。
    4. 觸發 Notification Engine 評估該診間的所有 active 追蹤任務。

- 應用程式剛啟動時快取為空，第一次抓到的狀態也視為「變動」並觸發評估。去重機制可確保不會重複推播。

- 每日清除（`DailyCleanupJob`）：每日 23:59（Asia/Taipei）將所有仍為 active 的追蹤任務結束（`end_reason = EXPIRED`），避免隔天號碼重置後誤發通知。

### 八、Notification Rule Engine

- 這是系統核心：例如目前號碼 46、使用者號碼 56，剩餘 10 位。Rule Engine 判斷是否需要通知、是否已通知過、是否重複通知。

- 介面：
```java
public interface NotificationRule {

    Optional<NotificationDecision> evaluate(
        TrackingJob job,
        RoomStatus status,
        EvaluationContext context   // 前一個號碼、使用者門檻、已送出門檻
    );

}
```

- v1.0 的 `boolean match(...)` 只能表達「是否觸發」，無法表達「推播哪一種訊息、是否結束任務」，因此改為回傳 `NotificationDecision`（訊息類型、要記錄的門檻、是否結束任務）。

- 預設門檻：剩 10 位、剩 3 位、到號（0）。
    - v1.0 為 20、10、5、2、到號。考量 LINE 免費方案每月約 200 則推播額度，v1.1 減少為 3 個門檻。
    - YAML 只提供預設值，使用者可用「設定門檻」指令自訂，存於 `notification_rule` 表。
    - 門檻 `0` 代表「到號」，永遠存在，使用者無法移除。

- 規則（剩餘 `remaining = target_number - current_number`，依下列優先順序，命中一條即停止）：

| 優先 | 規則 | 條件 | 動作 |
|---|---|---|---|
| 1 | `SessionResetRule` | 號碼倒退 ≥ `notification.session-reset-drop`（預設 10） | 推播「看診已重新開始，追蹤已結束」，結束任務（`SESSION_RESET`） |
| 2 | `MissedNumberRule` | `remaining < 0` 且尚未送出到號通知 | 推播「已過號」，結束任務（`MISSED`） |
| 3 | `ThresholdReachedRule` | 存在尚未送出、且 `threshold ≥ remaining` 的門檻 | 只推播最小的已跨越門檻一則；比它大的門檻一併記錄為已處理；若為到號（0）則結束任務（`ARRIVED`） |

- 範例：
    - 52 → 56（剩 4 位），門檻 10、3、0：跨越 10，推播「剩 4 位」一則，記錄門檻 10。
    - 45 → 54（剩 2 位）：一次跨越 10 和 3，只推播一則「剩 2 位」，同時記錄 10 和 3。
    - 號碼回頭叫過號病人（倒退 < 10）：不視為重置，已送出門檻不會重送。

- 建立追蹤時已在門檻內：例如建立時剩 8 位，門檻 10 會直接記錄為已處理（不推播），回覆訊息中顯示目前進度。

- 防重複：`notification_history` 具 `UNIQUE(tracking_job_id, threshold)` 約束。

### 九、候診時間預估引擎

- 資料：當日、最近 60 分鐘內同一診間的 `room_status_history`（只在號碼變動時寫入）。

- 計算方式：
    - `avg = (最新紀錄時間 - 最早紀錄時間)分鐘 / (最新號碼 - 最早號碼)`
    - 號碼前進少於 3 號（`notification.eta.min-advance`）時不計算，顯示「資料不足」。
    - `ETA = 剩餘號碼 × avg`，四捨五入到分鐘。

- 範例：

```plaintext
14:00  40號
14:10  44號
14:20  48號

得到：平均 2.5 分鐘/號
推估：目前 48 號，您是 56 號，剩 8 位，預估約 20 分鐘後
```

### 十、LINE Bot 功能（搭配 LINE Flex Message 卡片）

- 只處理 1 對 1 聊天（`UserSource`），群組與多人聊天室的訊息一律忽略。
- 指令前後空白、全形空白、全形數字皆可接受。

| 指令 | 範例 | 說明 |
|---|---|---|
| 追蹤（互動式） | `追蹤` | 回覆診間選擇卡片，選定後輸入號碼（見 10.2） |
| 追蹤（指定診間） | `追蹤 2診`、`追蹤 二診` | 直接選定診間，接著輸入號碼 |
| 追蹤（完整） | `追蹤 2診 56號`、`追蹤 二診 56`、`追蹤 2 56` | 建立追蹤；若已有追蹤則取代舊的 |
| 號碼 | `56`、`56號` | 搭配先前選定的診間建立追蹤；未選診間時提示操作方式 |
| 目前狀態 | `目前狀態`、`狀態` | 顯示目前追蹤的進度與預估時間 |
| 取消追蹤 | `取消追蹤`、`取消` | 結束目前追蹤（`CANCELLED`） |
| 診間 | `診間` | 列出所有診間目前號碼 |
| 設定門檻 | `設定門檻 10 5`、`設定門檻 15,8,3` | 自訂通知門檻（1～50，最多 5 個，0 自動包含） |
| 通知設定 | `通知設定`、`查看門檻`、`門檻`、`設定` | 顯示通知設定卡片（選單右下角齒輪） |
| 重設門檻 | `重設門檻` | 恢復系統預設門檻（通知設定卡片的「重設為預設」按鈕） |
| 幫助 | `幫助`、`help`、`?` | 顯示所有支援指令 |
| 其他 | — | 回覆「看不懂這個指令」卡片並提供「使用說明」按鈕 |

#### 10.1 圖文選單（Rich Menu）

- 尺寸 2500×1686，上方一個大按鈕、下方四個小按鈕，右下角另有齒輪按鈕，動作皆為「文字」（message action）：

```
┌──────────────────────────────────────────┐
│        🔔 追蹤看診號碼（傳送「追蹤」）      │
├──────────┬──────────┬──────────┬─────────┤
│   診間    │ 目前狀態  │ 取消追蹤  │  幫助   │
│          │          │          │     ┌───┤
│          │          │          │     │ ⚙ │ ← 傳送「通知設定」
└──────────┴──────────┴──────────┴─────┴───┘
```

| 區塊 | bounds（x, y, w, h） | 傳送文字 |
|---|---|---|
| 追蹤 | 0, 0, 2500, 843 | 追蹤 |
| 診間 | 0, 843, 625, 843 | 診間 |
| 目前狀態 | 625, 843, 625, 843 | 目前狀態 |
| 取消追蹤 | 1250, 843, 625, 843 | 取消追蹤 |
| 幫助（上） | 1875, 843, 625, 632 | 幫助 |
| 幫助（左下） | 1875, 1475, 417, 211 | 幫助 |
| 齒輪 | 2292, 1475, 208, 211 | 通知設定 |

- 齒輪佔幫助區塊右下角，高度為幫助區塊的 1/4、寬度為 1/3，無文字。幫助區塊拆成兩個不重疊的矩形，點選區互不干擾。
- `selected: false`：使用者開啟聊天室時選單預設收合，點聊天室下方「看診進度選單」展開。
- 檔案位於 `deploy/richmenu/`：`richmenu.json`（API request body）、`richmenu.png`（由 `RichMenuImage.java` 產生，齒輪座標需與 json 一致）、`setup-richmenu.sh`（建立並設為預設選單，會刪除舊的預設選單）。
- 以 API 設定的預設選單優先於 LINE 官方帳號管理後台設定的選單。

#### 10.2 互動式追蹤流程

```
使用者點「追蹤」 ──► Bot 回覆「診間卡片」（Carousel，每診一張，同「診間」指令）
                       ・看診中的診間：藍色卡片，顯示目前叫號與「追蹤X診」按鈕
                       ・未看診的診間：灰色卡片「未看診」，無按鈕
使用者點「追蹤二診」 ──► Postback：action=select-room&room=2
                       （inputOption=openKeyboard，自動開啟鍵盤）
                     ──► Bot 回覆「請輸入看診號碼」卡片（二診目前 46 號）
使用者輸入「56」 ──► 建立追蹤，回覆「已開始追蹤」卡片
```

- 「已選診間、等待輸入號碼」為短暫的對話狀態，存於記憶體（`PendingTrackStore`），5 分鐘後失效，使用一次即清除。
- 等待期間若使用者改下其他指令，即放棄等待中的選擇。
- 本版為單一實例部署；若未來水平擴展，此狀態需移至 Redis。
- 沒有任何診間看診中時，回覆兩則訊息：「目前沒有看診中的診間」提示卡片＋診間 Carousel（皆為灰色）。

- 追蹤驗證（皆以卡片回覆）：
    - 診別必須在 `clinic.rooms` 設定中 → 否則「無此診間」。
    - 號碼範圍 1～999 → 否則「號碼格式錯誤」。
    - 診間未看診 → 「X診目前未看診」。
    - 號碼已到（`current == target`）→ 「已輪到您了」；號碼已過（`current > target`）→ 「此號碼已過號」；皆不建立任務。

#### 10.3 Flex Message 卡片設計

- **所有回覆與推播一律使用 Flex Message**，不論是否處於看診時段、成功或錯誤。
- 統一版型（`FlexParts`）：

```
┌──────────────────────────┐
│ 標題（白字）               │ ← header 色帶，顏色代表狀態
│ 慈心吳婦產科               │ ← 副標題（診間卡片為科別）
├──────────────────────────┤
│ 二診          婦產科 吳瑞聰 │ ← 診別標題
│ ┌──────────┬───────────┐ │
│ │ 目前叫號  │ 您的號碼   │ │ ← 號碼面板（大字）
│ │    46    │    56     │ │
│ └──────────┴───────────┘ │
│ 剩餘   10 位              │ ← 資訊列
│ 預估   約 20 分鐘          │
│ 資料更新：10/05 14:20      │ ← 註記（小字灰色）
├──────────────────────────┤
│ [ 目前狀態 ] [ 取消追蹤 ]  │ ← footer 按鈕（選用）
└──────────────────────────┘
```

- 狀態色：

| 色系 | 色碼 | 用途 |
|---|---|---|
| 資訊（藍） | `#2E86C1` | 追蹤成功、查詢狀態、看診中的診間、輸入號碼、說明 |
| 即將輪到（橘） | `#E67E22` | 進度提醒推播 |
| 到號（綠） | `#27AE60` | 到號通知 |
| 警示（紅） | `#C0392B` | 過號、診次重置、錯誤 |
| 無作用（灰） | `#7F8C8D` | 未看診、沒有追蹤、取消成功 |

- altText 會顯示於推播通知與聊天列表，必須能單獨閱讀（例：「⏰ 即將輪到您：二診 目前 53 號，您是 56 號，剩餘 3 位」）。

- 卡片一覽（範例 request body 位於 `deploy/flex-samples/`）：

| 情境 | 卡片 | 色系 | 主要內容 | 按鈕 | 範例檔 |
|---|---|---|---|---|---|
| 追蹤成功 | ✅ 已開始追蹤 | 藍 | 號碼面板、剩餘、預估、通知門檻；取代舊追蹤時加註 | 目前狀態、取消追蹤 | `01-tracking-started` |
| 點「追蹤」 | 診間 Carousel | 藍／灰 | 同「診間」 | 看診中：追蹤X診（Postback） | `02-choose-room` |
| 選定診間 | 請輸入看診號碼 | 藍 | 目前叫號（大字）、輸入說明 | — | `03-ask-number` |
| 進度提醒（推播） | ⏰ 即將輪到您看診 | 橘 | 號碼面板、剩餘（橘字）、預估 | 目前狀態 | `04-progress` |
| 到號（推播） | 🔔 請立即報到 | 綠 | 「已輪到 56 號」（大字） | — | `05-arrived` |
| 過號（推播） | ⚠️ 已過號 | 紅 | 號碼面板、請洽櫃台 | 重新追蹤 | `06-missed` |
| 診次重置（推播） | ⚠️ 追蹤已結束 | 紅 | 號碼面板、重新開始說明 | 重新追蹤 | `07-session-reset` |
| 查詢狀態（看診中） | 看診進度 | 藍 | 號碼面板、剩餘、預估、資料更新時間 | 重新整理、取消追蹤 | `08-status` |
| 查詢狀態（未看診／無資料） | 看診進度 | 灰 | 目前叫號顯示「未看診」或「－」 | 重新整理、取消追蹤 | `09-status-not-in-session` |
| 查詢狀態（沒有追蹤） | 目前沒有追蹤 | 灰 | 操作說明 | 開始追蹤 | `10-no-tracking` |
| 查看所有診間 | 診間 Carousel | 藍／灰 | 每診一張：科別、目前叫號（大字）／未看診、醫師、資料更新時間 | 看診中：追蹤X診 | `11-rooms` |
| 全部未看診 | 提示＋診間 Carousel | 灰 | 兩則訊息 | — | `12-rooms-all-closed` |
| 通知設定（齒輪） | 通知設定／✅ 通知門檻已更新／已恢復預設門檻 | 藍 | 目前門檻（自訂／系統預設）、門檻標籤、使用說明 | 自訂門檻（開啟鍵盤並預填「設定門檻 」）、重設為預設 | `13-threshold-settings` |
| 取消追蹤 | 已取消追蹤 | 灰 | — | 開始追蹤 | `14-cancelled` |
| 錯誤／提示 | 例：此號碼已過號、無此診間 | 紅／灰 | 說明文字 | 視情境 | `15-number-passed` |
| 幫助 | 使用說明 | 藍 | 指令列表 | 開始追蹤、查看診間 | `16-help` |
| 加好友 | 歡迎使用看診進度通知 | 藍 | 三步驟使用說明 | 開始追蹤、查看診間 | `17-welcome` |
| 無法辨識的指令 | 看不懂這個指令 | 灰 | — | 使用說明 | `18-unknown-command` |

- 通知設定卡片的「自訂門檻」按鈕為 Postback（`action=custom-thresholds`，`inputOption=openKeyboard`，`fillInText=設定門檻 `）：開啟鍵盤並預填文字，使用者補上數字送出即完成設定；Bot 收到此 Postback 不回覆。需 LINE App 12.6 以上，舊版可直接輸入「設定門檻 15 8 3」。

- 查看所有診間採用 **Carousel**（一則訊息、每個診間一張 `kilo` 尺寸卡片，可左右滑動），診間增加時自動多一張（上限 12 張）。

- 範例工具：
    - 重新匯出：`./mvnw test -Dtest=FlexSampleExporter -Dflex.samples.dir=deploy/flex-samples`
    - 驗證格式（不耗額度）：`./deploy/flex-samples/send-sample.sh validate`
    - 推播到自己的 LINE 預覽：`./deploy/flex-samples/send-sample.sh 04-progress`（需 `.env` 的 `LINE_USER_ID`）
    - 也可將檔案中 `messages[n].contents` 貼到 [Flex Message Simulator](https://developers.line.biz/flex-simulator/) 預覽。

- LINE 事件：
    - Postback：解析 `action=select-room&room=N`，等同「追蹤 N診」。
    - Follow（加好友／解除封鎖）：建立或更新 subscriber（透過 Profile API 取得顯示名稱），回覆歡迎卡片（`17-welcome`）。官方帳號後台「加入好友的歡迎訊息」可同時開啟，兩者會依序出現；即使資料庫寫入失敗，仍會送出歡迎卡片。
    - Unfollow（封鎖）：結束該使用者的 active 追蹤（`UNFOLLOWED`）。
    - 收到任何文字訊息時，若 subscriber 不存在則自動建立。

- Reply 與 Push：
    - 指令回應一律使用 Reply API（不計入推播額度）。
    - 門檻通知、到號、過號、重置使用 Push API。
    - Push 失敗只記 log，不中斷排程，也不回滾通知紀錄（避免重試造成重複推播）。

### 十一、YAML 配置原則

- 重要結論：YAML 不存放使用者資料，因為這是 Runtime Data。錯誤示範如下：

```yaml
tracking:
  rich:
    room: 2
    number: 56
```

- 正確做法：YAML 只保存系統配置；敏感資訊一律使用環境變數。

```yaml
clinic:
  provider: wuobs
  name: 慈心吳婦產科
  zone-id: Asia/Taipei
  polling:
    interval-seconds: 30
  wuobs:
    url-template: https://s3-ap-southeast-1.amazonaws.com/charity-wuobs/MedicineNumberList%02d.xml
  rooms:
    - 1
    - 2
    - 3

notification:
  default-thresholds:
    - 10
    - 3
    - 0
  session-reset-drop: 10
  eta:
    window-minutes: 60
    min-advance: 3

line:
  bot:
    channel-token: ${LINE_CHANNEL_TOKEN}
    channel-secret: ${LINE_CHANNEL_SECRET}
    handler:
      path: /callback

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:clinic}
    username: ${DB_USERNAME:clinic}
    password: ${DB_PASSWORD:clinic}
```

### 十二、PostgreSQL Schema

- 由 Flyway 管理（`src/main/resources/db/migration`），JPA 使用 `ddl-auto: validate`。
- 主鍵使用 `BIGINT GENERATED BY DEFAULT AS IDENTITY`（PostgreSQL 與 H2 皆相容）。
- 時間欄位存 Asia/Taipei 的本地時間（`TIMESTAMP`）。

- subscriber 使用者

```sql
CREATE TABLE subscriber (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    line_user_id  VARCHAR(100) NOT NULL UNIQUE,
    display_name  VARCHAR(100),
    following     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL
);
```

- tracking_job 追蹤任務

```sql
CREATE TABLE tracking_job (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    subscriber_id  BIGINT      NOT NULL REFERENCES subscriber (id),
    provider_code  VARCHAR(30) NOT NULL,
    room_id        INTEGER     NOT NULL,
    target_number  INTEGER     NOT NULL,
    active         BOOLEAN     NOT NULL,
    end_reason     VARCHAR(20),   -- ARRIVED / MISSED / CANCELLED / REPLACED / SESSION_RESET / EXPIRED / UNFOLLOWED
    created_at     TIMESTAMP   NOT NULL,
    ended_at       TIMESTAMP
);
CREATE INDEX idx_tracking_job_active_room ON tracking_job (provider_code, room_id, active);
CREATE INDEX idx_tracking_job_subscriber ON tracking_job (subscriber_id, active);
```

- 「每人一個 active 任務」由 TrackingService 在同一交易中保證（建立新任務前先將舊任務標記為 `REPLACED`）。

- notification_rule 使用者自訂門檻（對應 Entity `SubscriberThreshold`，避免與 `NotificationRule` 介面撞名）

```sql
CREATE TABLE notification_rule (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    subscriber_id  BIGINT    NOT NULL REFERENCES subscriber (id),
    threshold      INTEGER   NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT uk_notification_rule UNIQUE (subscriber_id, threshold)
);
```

- 使用者沒有任何 `notification_rule` 紀錄時，套用 YAML 預設門檻。

- notification_history 避免重複通知

```sql
CREATE TABLE notification_history (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tracking_job_id  BIGINT    NOT NULL REFERENCES tracking_job (id),
    threshold        INTEGER   NOT NULL,
    pushed           BOOLEAN   NOT NULL,  -- false 表示靜默略過（建立時已在門檻內或被更小的門檻涵蓋）
    sent_at          TIMESTAMP NOT NULL,
    CONSTRAINT uk_notification_history UNIQUE (tracking_job_id, threshold)
);
```

- room_status_history 看診紀錄（只在號碼變動且看診中時寫入）

```sql
CREATE TABLE room_status_history (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    provider_code   VARCHAR(30) NOT NULL,
    room_id         INTEGER     NOT NULL,
    current_number  INTEGER     NOT NULL,
    doctor_name     VARCHAR(50),
    department      VARCHAR(50),
    exec_time       TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL
);
CREATE INDEX idx_room_status_history_room_time ON room_status_history (provider_code, room_id, created_at);
```

- 用於：候診預測、統計分析、速度分析。

### 十三、非功能需求

- 時區：系統一律以 `Asia/Taipei` 判斷「當日」與排程時間。
- LINE 推播額度：台灣免費方案每月約 200 則推播（Reply 不計），預設每個追蹤最多 3 則門檻通知。
- 資料來源禮貌：每 30 秒每診 1 次請求，並使用 ETag 條件請求。
- 安全：
    - Webhook 由 SDK 驗證 `X-Line-Signature`。
    - LINE Token、Secret、DB 密碼只透過環境變數注入，不進版控。
    - XML 解析停用外部實體。
- 可靠性：資料來源或 LINE API 錯誤不可使排程停止。

### 十四、後台管理 Bot

- 目的：讓少數幾位管理者在手機上收到告警、查詢系統狀態與 Log、測試卡片，不需 SSH 進主機。
- 架構：
    - **同一個 Provider 下另建一個 Messaging API Channel**（後台官方帳號）。LINE userId 依 Provider 區分，同 Provider 時管理者在前後台的 userId 相同，可直接由前台推播測試卡片給自己。
    - 與前台共用同一個 Spring Boot 專案。後台 Webhook 為 `POST /admin/callback`，以後台 Channel Secret 驗證簽章（SDK `LineSignatureValidator` + `WebhookParser`），事件於背景執行緒處理。
    - 後台的 `MessagingApiClient` 包在 `AdminMessenger` 內，不註冊成 bean，避免影響 SDK 對前台 client 的自動設定。
    - `admin.enabled=false`（預設）時不載入任何後台元件；告警使用後台 Channel 的推播額度，不佔用前台額度。
- 權限：`ADMIN_LINE_USER_IDS` 白名單（逗號分隔，權限相同）。非白名單使用者的任何訊息只會收到「未授權」卡片（顯示其 userId，方便新增管理者）。所有管理指令寫入 `admin_audit_log`。

#### 14.1 主動告警（推播給所有管理者）

| 告警 | 觸發條件 |
|---|---|
| 資料來源異常／恢復 | 單一診間連續抓取失敗達 `admin.alerts.fetch-failure-threshold`（預設 5 次 ≈ 2.5 分鐘）時通知一次；恢復時再通知 |
| 前台推播失敗 | 前台 Push API 失敗（Token 失效、限流、額度用完等） |
| 推播額度預警 | 每小時檢查前台額度，達 `quota-warn-ratio`（預設 80%）與 100% 各通知一次（每月重置） |
| 服務啟動 | 應用程式啟動完成（部署或意外重啟），附版本 |
| 每日摘要 | 每日 22:00：新增追蹤、到號／過號／取消／重置數、推播成功／失敗、各診看診時段與叫號範圍、追蹤人數、好友數 |

- 節流：同類告警 `admin.alerts.throttle`（預設 30 分鐘）內只送一次。

#### 14.2 指令（回覆皆為 Flex 卡片）

| 指令 | 說明 |
|---|---|
| `狀態` | 版本、運行時間、資料庫、好友數、追蹤人數、推播額度、各診抓取狀況（最後成功時間、連續失敗次數） |
| `診間 2` | 該診快取狀態與即時原始 XML |
| `錯誤`、`錯誤 20`、`log 關鍵字` | 記憶體中最近的 WARN／ERROR（保留最近 500 筆，重啟後清空） |
| `查詢 名稱`、`查詢 Uxxx` | 使用者是否為好友、最近 5 筆追蹤與結束原因、最近 10 筆推播與送達結果；名稱符合多人時列出按鈕 |
| `摘要` | 今日統計（同每日摘要） |
| `額度` | 前台推播額度與本月用量 |
| `webhook` | 呼叫 LINE 的 Webhook 測試 API，確認 LINE → server 連線 |
| 貼上 Flex JSON | 接受 bubble／carousel、flex message，或含 `messages` 的 request body（如 `deploy/flex-samples/*.json`）；經 LINE 驗證後原樣回覆預覽（LINE 文字訊息上限 5000 字） |
| `範例`、`範例 04` | 範例清單；指定編號時由**前台帳號**推播該卡片給下指令的管理者 |
| `抓取` | 立即抓取一次各診資料並顯示結果 |
| `選單`、`選單清理` | 前台圖文選單清單（標示預設）；刪除非預設選單（需按確認，未設定預設選單時不執行） |
| `幫助` | 指令說明 |

#### 14.3 資料表變更（Flyway `V2__admin.sql`）

```sql
ALTER TABLE notification_history ADD COLUMN delivered BOOLEAN;  -- 推播結果：true 送達、false 失敗、null 未推播

CREATE TABLE admin_audit_log (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    admin_line_user_id  VARCHAR(100) NOT NULL,
    command             VARCHAR(200) NOT NULL,
    created_at          TIMESTAMP    NOT NULL
);
```

#### 14.4 設定

```yaml
admin:
  enabled: ${ADMIN_ENABLED:false}
  channel-token: ${ADMIN_LINE_CHANNEL_TOKEN:}
  channel-secret: ${ADMIN_LINE_CHANNEL_SECRET:}
  user-ids: ${ADMIN_LINE_USER_IDS:}
  alerts:
    fetch-failure-threshold: 5
    throttle: 30m
    quota-warn-ratio: 0.8
    daily-summary-cron: "0 0 22 * * *"
```

- 後續階段：網頁後台（統計圖表、預估準確度分析）、營運操作（公告、推播暫停開關）。

### 總結功能清單

- Queue Monitoring
    - XML Provider
    - XML Parser
    - Scheduler
    - Room Status Cache

- User Management
    - LINE User Binding（Follow / Unfollow）
    - Subscriber Management

- Tracking
    - 開始追蹤
    - 查詢追蹤
    - 取消追蹤
    - 自動結束（到號 / 過號 / 重置 / 每日清除）

- Notification
    - 自訂通知門檻
    - 通知歷史紀錄
    - 防重複通知

- Messaging
    - LINE Reply Message
    - LINE Push Message
    - LINE Flex Message

- Admin（後台管理 Bot）
    - 主動告警與每日摘要
    - 狀態、Log、使用者查詢
    - Flex 預覽、範例推播、手動抓取、圖文選單管理

- Persistence
    - PostgreSQL + Flyway
    - Room Status History
    - Tracking Job
    - Notification Rule
    - Notification History
