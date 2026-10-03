## 慈心吳婦產科看診進度追蹤與 LINE 通知平台
- 本文件為本專案的需求分析與系統設計文件（System Requirement & Architecture Design Document）。
- 版本：v1.1（2026-10-03）
    - v1.0：初版需求。
    - v1.1：依實際 XML 資料修正資料格式與解析規則；補齊通知規則語意、任務生命週期、LINE 指令、Schema 約束；技術堆疊改為 Spring MVC + Virtual Threads；預設通知門檻改為 10、3、到號。

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
       │                                                 ▲
┌──────────────────┐    ┌──────────────────┐            │ 寫入歷史 / 讀取任務
│ Queue Polling    │───►│ ClinicProvider   │──► AWS S3 XML Source
│ Scheduler (30s)  │    │ (WuObs)          │            │
└────────┬─────────┘    └────────┬─────────┘            │
         │                       ▼                       │
         │              ┌──────────────────┐            │
         │              │ XmlRoomStatus    │            │
         │              │ Parser           │            │
         │              └────────┬─────────┘            │
         │                       ▼                       │
         │              ┌──────────────────┐            │
         └─────────────►│ RoomStatusCache  │────────────┤
           號碼有變動時   └────────┬─────────┘            │
                                 ▼                       │
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
| 追蹤 | `追蹤 2診 56號`、`追蹤 二診 56`、`追蹤 2 56` | 建立追蹤；若已有追蹤則取代舊的 |
| 目前狀態 | `目前狀態`、`狀態` | 顯示目前追蹤的進度與預估時間 |
| 取消追蹤 | `取消追蹤`、`取消` | 結束目前追蹤（`CANCELLED`） |
| 診間 | `診間` | 列出所有診間目前號碼 |
| 設定門檻 | `設定門檻 10 5`、`設定門檻 15,8,3` | 自訂通知門檻（1～50，最多 5 個，0 自動包含） |
| 查看門檻 | `查看門檻`、`門檻` | 顯示目前門檻設定 |
| 重設門檻 | `重設門檻` | 恢復系統預設門檻 |
| 幫助 | `幫助`、`help`、`?` | 顯示所有支援指令 |
| 其他 | — | 回覆「看不懂這個指令」並提示輸入「幫助」 |

- 追蹤驗證：
    - 診別必須在 `clinic.rooms` 設定中，號碼範圍 1～999。
    - 診間未看診 → 回覆「目前未看診，無法追蹤」。
    - 號碼已過（`current > target`）→ 回覆「此號碼已過號」，不建立任務。

- 追蹤成功回覆：

```plaintext
✅ 已開始追蹤
診別：二診
號碼：56
目前：46號（剩餘 10 位）
通知門檻：10、3、到號
```

- 進度提醒推播（Flex）：

```
┌────────────────┐
│ 即將輪到您看診  │
├────────────────┤
│ 二診            │
│ 醫師：吳瑞聰     │
│ 目前：53號      │
│ 您是：56號      │
│ 剩餘：3位       │
│ 預估：8分鐘     │
└────────────────┘
```

- 到號通知（Flex）：

```
┌────────────────┐
│ 請立即報到      │
├────────────────┤
│ 二診            │
│ 已輪到56號      │
└────────────────┘
```

- 過號通知（Flex）：「二診目前 58 號，您的 56 號已過號，請洽櫃台。追蹤已結束。」

- 查詢狀態（Flex）：

```
┌────────────────┐
│ 慈心吳婦產科     │
├────────────────┤
│ 二診            │
│ 目前 46號       │
│ 您是 56號       │
│ 剩餘 10位       │
│ 預估 20分鐘     │
└────────────────┘
```

- 查看所有診間：

```plaintext
一診（婦產科 吳瑞聰）：35號
二診（婦產科）：46號
三診（小兒科）：未看診
```

- LINE 事件：
    - Follow（加好友／解除封鎖）：建立或更新 subscriber（透過 Profile API 取得顯示名稱），回覆歡迎訊息與指令說明。
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

- Persistence
    - PostgreSQL + Flyway
    - Room Status History
    - Tracking Job
    - Notification Rule
    - Notification History
