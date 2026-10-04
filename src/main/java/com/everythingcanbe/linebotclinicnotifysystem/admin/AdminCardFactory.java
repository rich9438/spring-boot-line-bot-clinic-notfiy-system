package com.everythingcanbe.linebotclinicnotifysystem.admin;

import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.bubble;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.commandRow;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.message;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.messageButton;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.note;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.paragraph;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.postbackButton;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.row;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.separator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.model.FlexComponent;
import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.ClinicChannelOperations;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.DailySummaryService.DailySummary;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.DailySummaryService.RoomSession;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.RecentLogBuffer.LogEntry;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.SubscriberLookupService;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.SubscriberLookupService.LookupResult;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.SystemStatusService.RoomLine;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.SystemStatusService.SystemStatus;
import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;
import com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.Tone;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.PollingHealth;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 後台管理卡片（沿用前台 FlexParts 版型，副標題為「後台管理」）。
 */
@Component
@ConditionalOnAdminEnabled
public class AdminCardFactory {

    static final String SUBTITLE = "後台管理";
    static final int MAX_LOG_MESSAGE = 300;
    static final int MAX_RAW_LENGTH = 1500;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    // ── 狀態查詢 ──────────────────────────────────────────

    public Message status(SystemStatus status) {
        boolean healthy = status.databaseUp() && status.rooms().stream().allMatch(r -> r.consecutiveFailures() == 0);
        List<FlexComponent> body = new ArrayList<>(List.of(
                row("版本", status.version()),
                row("運行時間", formatDuration(status.uptime())),
                row("資料庫", status.databaseUp() ? "正常" : "異常", status.databaseUp() ? Tone.ARRIVED.color
                        : Tone.ALERT.color),
                row("好友數", String.valueOf(status.followers())),
                row("追蹤中", status.activeTrackings() + " 人"),
                row("推播額度", status.quota().map(AdminCardFactory::formatQuota).orElse("查詢失敗")),
                separator()));
        for (RoomLine room : status.rooms()) {
            body.add(roomHealth(room));
        }
        String altText = "系統狀態：" + (healthy ? "正常" : "有異常") + "，追蹤中 " + status.activeTrackings() + " 人";
        return message(altText, bubble(healthy ? Tone.INFO : Tone.ALERT, healthy ? "系統狀態：正常" : "系統狀態：有異常",
                SUBTITLE, body, List.of(
                        messageButton("重新整理", "狀態", false, Tone.INFO),
                        messageButton("最近錯誤", "錯誤", false, Tone.INFO))));
    }

    private static FlexComponent roomHealth(RoomLine room) {
        String state = room.inSession() ? "看診中 " + room.currentNumber() + " 號" : "未看診";
        String fetched = room.lastSuccessAt() == null ? "尚未成功抓取" : room.lastSuccessAt().format(HOUR_MINUTE) + " 抓取";
        String failures = room.consecutiveFailures() > 0 ? " · ⚠️ 連續失敗 " + room.consecutiveFailures() + " 次" : "";
        return commandRow(room.roomName() + "　" + state + "　追蹤 " + room.activeTrackings() + " 人", fetched + failures);
    }

    public Message room(int roomId, Optional<RoomStatus> cached, PollingHealth.RoomHealth health, String raw) {
        String roomName = RoomNames.of(roomId);
        List<FlexComponent> body = new ArrayList<>(List.of(
                row("快取", cached.map(s -> s.inSession() ? "看診中 " + s.currentNumber() + " 號" : "未看診")
                        .orElse("尚無資料")),
                row("資料時間", cached.map(RoomStatus::updateTime).map(t -> t.format(TIME)).orElse("－")),
                row("最後成功", health.lastSuccessAt() == null ? "－" : health.lastSuccessAt().format(TIME)),
                row("連續失敗", health.consecutiveFailures() + " 次",
                        health.consecutiveFailures() > 0 ? Tone.ALERT.color : null),
                separator(),
                paragraph("即時原始資料"),
                note(truncate(raw, MAX_RAW_LENGTH))));
        return message(roomName + " 原始資料", bubble(Tone.INFO, roomName + " 原始資料", SUBTITLE, body, List.of()));
    }

    public Message logs(List<LogEntry> entries, String keyword) {
        String title = keyword == null ? "最近錯誤" : "Log：" + keyword;
        List<FlexComponent> body = new ArrayList<>();
        if (entries.isEmpty()) {
            body.add(paragraph(keyword == null ? "目前沒有 WARN／ERROR 紀錄。" : "沒有符合「" + keyword + "」的紀錄。"));
        }
        for (LogEntry entry : entries) {
            body.add(commandRow(entry.time().format(LOG_TIME) + "　" + entry.level() + "　" + entry.logger(),
                    truncate(entry.message(), MAX_LOG_MESSAGE)));
        }
        body.add(note("僅保留應用程式啟動後最近的 WARN／ERROR"));
        return message(title + "（" + entries.size() + " 筆）",
                bubble(entries.isEmpty() ? Tone.ARRIVED : Tone.PROGRESS, title, SUBTITLE, body, List.of()));
    }

    public Message quota(Optional<ClinicChannelOperations.Quota> quota) {
        if (quota.isEmpty()) {
            return error("查詢額度失敗", "無法取得前台推播額度，請輸入「錯誤」查看原因。");
        }
        ClinicChannelOperations.Quota q = quota.get();
        Double ratio = q.ratio();
        Tone tone = ratio == null || ratio < 0.8 ? Tone.INFO : ratio < 1 ? Tone.PROGRESS : Tone.ALERT;
        List<FlexComponent> body = List.of(
                row("每月上限", q.limit() == null ? "無上限" : q.limit() + " 則"),
                row("本月已用", q.used() + " 則"),
                row("使用率", ratio == null ? "－" : Math.round(ratio * 100) + "%"),
                note("回覆（Reply）不計入額度；後台告警使用後台帳號的額度"));
        return message("前台推播額度：" + formatQuota(q), bubble(tone, "前台推播額度", SUBTITLE, body, List.of()));
    }

    public Message webhook(ClinicChannelOperations.WebhookTestResult result) {
        List<FlexComponent> body = new ArrayList<>(List.of(
                row("網址", result.endpoint() == null ? "未設定" : result.endpoint()),
                row("Use webhook", result.active() == null ? "－" : result.active() ? "開啟" : "關閉"),
                row("測試結果", result.success() ? "成功" : "失敗",
                        result.success() ? Tone.ARRIVED.color : Tone.ALERT.color)));
        if (result.statusCode() != null) {
            body.add(row("HTTP 狀態", String.valueOf(result.statusCode())));
        }
        if (!result.success()) {
            body.add(note(result.reason() + (result.detail() == null ? "" : "：" + result.detail())));
        }
        return message("前台 Webhook 測試：" + (result.success() ? "成功" : "失敗"),
                bubble(result.success() ? Tone.ARRIVED : Tone.ALERT, "前台 Webhook 測試", SUBTITLE, body, List.of()));
    }

    // ── 使用者查詢 ────────────────────────────────────────

    public Message lookup(LookupResult result) {
        return switch (result) {
            case LookupResult.NotFound notFound -> notice(Tone.NEUTRAL, "查無使用者",
                    "找不到「" + notFound.query() + "」。可用顯示名稱的部分文字或完整 userId 查詢。");
            case LookupResult.Multiple multiple -> lookupMultiple(multiple);
            case LookupResult.Found found -> lookupFound(found);
        };
    }

    private Message lookupMultiple(LookupResult.Multiple multiple) {
        List<FlexComponent> body = new ArrayList<>();
        body.add(paragraph("符合「" + multiple.query() + "」的使用者（最多 6 位），點選查看："));
        for (SubscriberLookupService.SubscriberSummary subscriber : multiple.subscribers()) {
            body.add(messageButton(subscriber.displayName() + (subscriber.following() ? "" : "（已封鎖）"),
                    "查詢 " + subscriber.lineUserId(), false, Tone.INFO));
        }
        return message("符合「" + multiple.query() + "」的使用者有 " + multiple.subscribers().size() + " 位",
                bubble(Tone.INFO, "找到多位使用者", SUBTITLE, body, List.of()));
    }

    private Message lookupFound(LookupResult.Found found) {
        SubscriberLookupService.SubscriberSummary subscriber = found.subscriber();
        List<FlexComponent> body = new ArrayList<>(List.of(
                row("名稱", subscriber.displayName()),
                row("好友", subscriber.following() ? "是" : "已封鎖",
                        subscriber.following() ? null : Tone.ALERT.color),
                row("加入", found.createdAt().format(TIME)),
                note(subscriber.lineUserId()),
                separator(),
                paragraph("最近追蹤")));
        if (found.jobs().isEmpty()) {
            body.add(note("沒有追蹤紀錄"));
        }
        for (SubscriberLookupService.JobLine job : found.jobs()) {
            String state = job.active() ? "追蹤中" : describe(job.endReason());
            String time = job.createdAt().format(TIME) + (job.endedAt() == null ? "" : " → "
                    + job.endedAt().format(HOUR_MINUTE));
            body.add(commandRow(job.roomName() + " " + job.targetNumber() + " 號　" + state, time));
        }
        body.add(separator());
        body.add(paragraph("最近推播"));
        if (found.notifications().isEmpty()) {
            body.add(note("沒有推播紀錄"));
        }
        for (SubscriberLookupService.NotificationLine notification : found.notifications()) {
            String what = notification.threshold() == 0 ? "到號" : "剩 " + notification.threshold() + " 位";
            String delivered = notification.delivered() == null ? "未記錄"
                    : notification.delivered() ? "已送達" : "⚠️ 推播失敗";
            body.add(commandRow(notification.roomName() + " " + notification.targetNumber() + " 號　" + what,
                    notification.sentAt().format(TIME) + " · " + delivered));
        }
        return message("使用者查詢：" + subscriber.displayName(),
                bubble(Tone.INFO, "使用者查詢", SUBTITLE, body, List.of()));
    }

    // ── 測試工具 ──────────────────────────────────────────

    public Message samples(Set<String> keys) {
        List<FlexComponent> body = new ArrayList<>();
        body.add(paragraph("輸入「範例 編號」由前台帳號推播給你，例如：範例 04"));
        body.add(note(String.join("\n", keys)));
        return message("範例卡片共 " + keys.size() + " 張", bubble(Tone.INFO, "範例卡片", SUBTITLE, body, List.of(
                messageButton("範例 04", "範例 04", false, Tone.INFO),
                messageButton("範例 11", "範例 11", false, Tone.INFO))));
    }

    public Message sampleSent(String key, boolean success) {
        return success
                ? notice(Tone.ARRIVED, "已推播範例", "已由前台帳號推播「" + key + "」給你，請到前台聊天室查看。")
                : error("推播範例失敗", "前台推播「" + key + "」失敗，請輸入「錯誤」查看原因。");
    }

    public Message poll(List<Integer> configuredRooms, List<RoomStatus> statuses) {
        List<FlexComponent> body = new ArrayList<>();
        for (Integer roomId : configuredRooms) {
            Optional<RoomStatus> status = statuses.stream().filter(s -> s.roomId().equals(roomId)).findFirst();
            body.add(status.map(s -> row(s.roomName(), (s.inSession() ? "看診中 " + s.currentNumber() + " 號" : "未看診")
                    + (s.updateTime() == null ? "" : "（" + s.updateTime().format(TIME) + "）")))
                    .orElseGet(() -> row(RoomNames.of(roomId), "抓取失敗", Tone.ALERT.color)));
        }
        boolean allSucceeded = statuses.size() == configuredRooms.size();
        return message("手動抓取：" + statuses.size() + "/" + configuredRooms.size() + " 成功",
                bubble(allSucceeded ? Tone.ARRIVED : Tone.ALERT, "手動抓取結果", SUBTITLE, body, List.of()));
    }

    public Message richMenus(ClinicChannelOperations.RichMenus menus) {
        List<FlexComponent> body = new ArrayList<>();
        if (menus.menus().isEmpty()) {
            body.add(paragraph("前台目前沒有用 API 建立的圖文選單。"));
        }
        for (ClinicChannelOperations.RichMenuInfo menu : menus.menus()) {
            body.add(commandRow((menu.isDefault() ? "⭐ " : "") + menu.name() + (menu.isDefault() ? "（預設）" : ""),
                    menu.id() + " · " + menu.chatBarText()));
        }
        if (menus.defaultMenu().isEmpty() && !menus.menus().isEmpty()) {
            body.add(note("⚠️ 尚未設定預設選單"));
        }
        List<FlexComponent> footer = menus.defaultMenu().isPresent() && menus.nonDefaultCount() > 0
                ? List.of(messageButton("清理 " + menus.nonDefaultCount() + " 個未使用選單", "選單清理", false, Tone.INFO))
                : List.of();
        return message("前台圖文選單共 " + menus.menus().size() + " 個",
                bubble(Tone.INFO, "前台圖文選單", SUBTITLE, body, footer));
    }

    public Message richMenuCleanupConfirm(ClinicChannelOperations.RichMenus menus) {
        if (menus.defaultMenu().isEmpty()) {
            return error("無法清理", "尚未設定預設選單，為避免誤刪不執行清理。請先執行 setup-richmenu.sh。");
        }
        if (menus.nonDefaultCount() == 0) {
            return notice(Tone.ARRIVED, "不需清理", "目前只有預設選單「" + menus.defaultMenu().get().name() + "」。");
        }
        List<FlexComponent> body = List.of(paragraph("將刪除 " + menus.nonDefaultCount() + " 個非預設選單，預設選單「"
                + menus.defaultMenu().get().name() + "」會保留。此操作無法復原。"));
        return message("確認刪除 " + menus.nonDefaultCount() + " 個圖文選單？",
                bubble(Tone.ALERT, "確認清理圖文選單", SUBTITLE, body, List.of(
                        postbackButton("確認刪除", AdminCommandParser.CLEANUP_CONFIRM_DATA, "確認刪除選單", false,
                                Tone.ALERT))));
    }

    public Message richMenuCleanupDone(int deleted) {
        return notice(Tone.ARRIVED, "清理完成", "已刪除 " + deleted + " 個非預設圖文選單。");
    }

    // ── 告警 ──────────────────────────────────────────────

    public Message fetchFailed(int roomId, int failures, LocalDateTime lastSuccessAt) {
        String roomName = RoomNames.of(roomId);
        List<FlexComponent> body = List.of(
                paragraph(roomName + "連續 " + failures + " 次抓取失敗，使用者可能收不到通知。"),
                row("最後成功", lastSuccessAt == null ? "啟動後尚未成功" : lastSuccessAt.format(TIME)),
                note("恢復時會再通知一次"));
        return message("⚠️ 資料來源異常：" + roomName + "連續 " + failures + " 次抓取失敗",
                bubble(Tone.ALERT, "⚠️ 資料來源異常", SUBTITLE, body, List.of(
                        messageButton("查看錯誤", "錯誤", false, Tone.ALERT),
                        messageButton("立即重試", "抓取", false, Tone.ALERT))));
    }

    public Message fetchRecovered(int roomId, int failedCount) {
        String roomName = RoomNames.of(roomId);
        return notice(Tone.ARRIVED, "✅ 資料來源已恢復", roomName + "在連續 " + failedCount + " 次失敗後已恢復正常。");
    }

    public Message pushFailed(String lineUserId, String error) {
        List<FlexComponent> body = List.of(
                row("對象", mask(lineUserId)),
                paragraph(truncate(error, MAX_LOG_MESSAGE)),
                note("常見原因：Token 失效（401）、超過額度或限流（429）、使用者已封鎖。30 分鐘內不重複通知。"));
        return message("⚠️ 前台推播失敗：" + truncate(error, 100),
                bubble(Tone.ALERT, "⚠️ 前台推播失敗", SUBTITLE, body, List.of(
                        messageButton("查看額度", "額度", false, Tone.ALERT),
                        messageButton("查看錯誤", "錯誤", false, Tone.ALERT))));
    }

    public Message quotaWarning(ClinicChannelOperations.Quota quota) {
        boolean exhausted = quota.ratio() != null && quota.ratio() >= 1;
        String title = exhausted ? "⚠️ 推播額度已用完" : "⚠️ 推播額度即將用完";
        List<FlexComponent> body = List.of(
                row("本月已用", formatQuota(quota)),
                paragraph(exhausted ? "本月已無法再推播通知，使用者將收不到門檻與到號通知。"
                        : "請留意本月剩餘額度，必要時調整預設通知門檻或升級方案。"));
        return message(title + "：" + formatQuota(quota),
                bubble(exhausted ? Tone.ALERT : Tone.PROGRESS, title, SUBTITLE, body, List.of()));
    }

    public Message startup(String version, long activeTrackings) {
        List<FlexComponent> body = List.of(
                row("版本", version),
                row("追蹤中", activeTrackings + " 人"),
                note("服務已啟動或重新啟動完成"));
        return message("🚀 服務已啟動：" + version, bubble(Tone.INFO, "🚀 服務已啟動", SUBTITLE, body, List.of(
                messageButton("系統狀態", "狀態", false, Tone.INFO))));
    }

    public Message dailySummary(DailySummary summary) {
        long pushed = summary.pushedDelivered() + summary.pushedFailed() + summary.pushedUnknown();
        List<FlexComponent> body = new ArrayList<>(List.of(
                row("新增追蹤", summary.newTrackings() + " 筆"),
                row("到號", summary.ended(EndReason.ARRIVED) + " 筆"),
                row("過號", summary.ended(EndReason.MISSED) + " 筆"),
                row("取消／取代", (summary.ended(EndReason.CANCELLED) + summary.ended(EndReason.REPLACED)) + " 筆"),
                row("診次重置", summary.ended(EndReason.SESSION_RESET) + " 筆"),
                row("推播", pushed + " 則（失敗 " + summary.pushedFailed() + "）",
                        summary.pushedFailed() > 0 ? Tone.ALERT.color : null),
                row("追蹤中", summary.activeNow() + " 人"),
                row("好友數", String.valueOf(summary.followers())),
                separator(),
                paragraph("看診時段")));
        if (summary.rooms().isEmpty()) {
            body.add(note("今日無看診紀錄"));
        }
        for (RoomSession room : summary.rooms()) {
            body.add(row(room.roomName(), room.firstAt().format(HOUR_MINUTE) + "–" + room.lastAt().format(HOUR_MINUTE)
                    + "（" + room.minNumber() + "–" + room.maxNumber() + " 號）"));
        }
        String date = summary.date().format(DateTimeFormatter.ofPattern("MM/dd"));
        return message("📊 每日摘要 " + date + "：新增追蹤 " + summary.newTrackings() + " 筆、推播 " + pushed + " 則",
                bubble(Tone.INFO, "📊 每日摘要 " + date, SUBTITLE, body, List.of()));
    }

    // ── 說明與通用 ────────────────────────────────────────

    public Message help() {
        List<FlexComponent> body = List.of(
                commandRow("狀態", "版本、資料庫、各診抓取與追蹤狀況、推播額度"),
                commandRow("診間 2", "該診快取與即時原始 XML"),
                commandRow("錯誤 / 錯誤 20 / log 關鍵字", "最近的 WARN／ERROR"),
                commandRow("查詢 名稱 / 查詢 Uxxx", "使用者的追蹤與推播紀錄"),
                commandRow("摘要", "今日統計（每日 22:00 自動推播）"),
                commandRow("額度 / webhook", "前台推播額度、Webhook 連線測試"),
                commandRow("貼上 Flex JSON", "驗證並預覽卡片（訊息上限 5000 字）"),
                commandRow("範例 / 範例 04", "由前台帳號推播範例卡片給自己"),
                commandRow("抓取", "立即抓取一次診間資料"),
                commandRow("選單 / 選單清理", "前台圖文選單查詢與清理"));
        return message("後台指令說明", bubble(Tone.INFO, "後台指令", SUBTITLE, body, List.of(
                messageButton("狀態", "狀態", true, Tone.INFO),
                messageButton("最近錯誤", "錯誤", false, Tone.INFO))));
    }

    public Message unauthorized(String lineUserId) {
        List<FlexComponent> body = List.of(
                paragraph("此帳號僅供系統管理者使用。"),
                row("你的 userId", lineUserId),
                note("如需管理權限，請將 userId 提供給系統管理者加入 ADMIN_LINE_USER_IDS。"));
        return message("未授權：此帳號僅供系統管理者使用", bubble(Tone.NEUTRAL, "未授權", SUBTITLE, body, List.of()));
    }

    public Message unknown() {
        return message("看不懂這個指令，輸入「幫助」查看後台指令", bubble(Tone.NEUTRAL, "看不懂這個指令", SUBTITLE,
                List.of(paragraph("輸入「幫助」查看所有後台指令。")),
                List.of(messageButton("幫助", "幫助", true, Tone.INFO))));
    }

    public Message notice(Tone tone, String title, String text) {
        return message(title + "：" + text, bubble(tone, title, SUBTITLE, List.of(paragraph(text)), List.of()));
    }

    public Message error(String title, String text) {
        return notice(Tone.ALERT, title, text);
    }

    // ── 格式化 ────────────────────────────────────────────

    static String formatQuota(ClinicChannelOperations.Quota quota) {
        if (quota.limit() == null) {
            return quota.used() + " 則（無上限）";
        }
        return quota.used() + " / " + quota.limit() + " 則（" + Math.round(quota.ratio() * 100) + "%）";
    }

    static String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        if (days > 0) {
            return days + " 天 " + hours + " 小時";
        }
        return hours > 0 ? hours + " 小時 " + minutes + " 分" : minutes + " 分";
    }

    static String mask(String lineUserId) {
        if (lineUserId == null || lineUserId.length() < 12) {
            return String.valueOf(lineUserId);
        }
        return lineUserId.substring(0, 6) + "…" + lineUserId.substring(lineUserId.length() - 4);
    }

    static String truncate(String text, int max) {
        if (text == null) {
            return "－";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…（已截斷）";
    }

    private static String describe(EndReason reason) {
        if (reason == null) {
            return "已結束";
        }
        return switch (reason) {
            case ARRIVED -> "到號";
            case MISSED -> "過號";
            case CANCELLED -> "取消";
            case REPLACED -> "被取代";
            case SESSION_RESET -> "診次重置";
            case EXPIRED -> "每日清除";
            case UNFOLLOWED -> "封鎖";
        };
    }

}
