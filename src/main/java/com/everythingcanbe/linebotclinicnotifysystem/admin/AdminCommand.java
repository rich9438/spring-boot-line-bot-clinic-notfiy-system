package com.everythingcanbe.linebotclinicnotifysystem.admin;

/**
 * 後台管理指令。
 */
public sealed interface AdminCommand {

    record Status() implements AdminCommand {
    }

    /** 單一診間的快取狀態與原始 XML */
    record Room(int roomId) implements AdminCommand {
    }

    /** 最近的 WARN／ERROR；keyword 為 null 表示不篩選 */
    record Errors(int limit, String keyword) implements AdminCommand {
    }

    /** 依 userId 或顯示名稱查詢使用者 */
    record Lookup(String query) implements AdminCommand {
    }

    record Quota() implements AdminCommand {
    }

    record WebhookTest() implements AdminCommand {
    }

    /** 今日摘要（與每日 22:00 推播的內容相同） */
    record Summary() implements AdminCommand {
    }

    /** 貼上的 Flex JSON，驗證後原樣回覆預覽 */
    record FlexPreview(String json) implements AdminCommand {
    }

    record Samples() implements AdminCommand {
    }

    /** 由前台帳號推播範例卡片給下指令的管理者 */
    record SendSample(String key) implements AdminCommand {
    }

    /** 立即執行一次輪詢 */
    record Poll() implements AdminCommand {
    }

    record RichMenus() implements AdminCommand {
    }

    record RichMenuCleanup() implements AdminCommand {
    }

    /** 確認刪除非預設圖文選單（確認卡片上的按鈕） */
    record RichMenuCleanupConfirm() implements AdminCommand {
    }

    record Help() implements AdminCommand {
    }

    record Invalid(String message) implements AdminCommand {
    }

    record Unknown(String text) implements AdminCommand {
    }

}
