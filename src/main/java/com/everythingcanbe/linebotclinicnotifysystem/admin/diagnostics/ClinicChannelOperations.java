package com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.GetWebhookEndpointResponse;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.MessageQuotaResponse;
import com.linecorp.bot.messaging.model.QuotaType;
import com.linecorp.bot.messaging.model.RichMenuResponse;
import com.linecorp.bot.messaging.model.TestWebhookEndpointRequest;
import com.linecorp.bot.messaging.model.TestWebhookEndpointResponse;

import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.line.LineMessenger;

/**
 * 對「前台」Channel 的管理操作：推播額度、Webhook 測試、圖文選單、推播範例卡片。
 */
@Component
@ConditionalOnAdminEnabled
public class ClinicChannelOperations {

    private static final Logger log = LoggerFactory.getLogger(ClinicChannelOperations.class);
    private static final long TIMEOUT_SECONDS = 10;

    private final MessagingApiClient client;
    private final LineMessenger messenger;

    public ClinicChannelOperations(MessagingApiClient client, LineMessenger messenger) {
        this.client = client;
        this.messenger = messenger;
    }

    /**
     * @return 推播額度；查詢失敗時為 empty
     */
    public Optional<Quota> quota() {
        try {
            MessageQuotaResponse quota = await(client.getMessageQuota()).body();
            long used = await(client.getMessageQuotaConsumption()).body().totalUsage();
            Long limit = quota.type() == QuotaType.LIMITED ? quota.value() : null;
            return Optional.of(new Quota(limit, used));
        } catch (Exception e) {
            log.warn("Failed to get message quota: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public WebhookTestResult testWebhook() {
        String endpoint = null;
        try {
            GetWebhookEndpointResponse info = await(client.getWebhookEndpoint()).body();
            endpoint = info.endpoint() == null ? null : info.endpoint().toString();
            TestWebhookEndpointResponse result = await(client.testWebhookEndpoint(
                    new TestWebhookEndpointRequest(null))).body();
            return new WebhookTestResult(endpoint, info.active(), Boolean.TRUE.equals(result.success()),
                    result.statusCode(), result.reason(), result.detail());
        } catch (Exception e) {
            return new WebhookTestResult(endpoint, null, false, null, "API_ERROR", e.getMessage());
        }
    }

    public RichMenus richMenus() {
        List<RichMenuResponse> menus = await(client.getRichMenuList()).body().richmenus();
        String defaultId = null;
        try {
            defaultId = await(client.getDefaultRichMenuId()).body().richMenuId();
        } catch (Exception e) {
            // 尚未設定預設選單時 LINE 回 404
        }
        String finalDefaultId = defaultId;
        return new RichMenus(menus.stream()
                .map(menu -> new RichMenuInfo(menu.richMenuId(), menu.name(), menu.chatBarText(),
                        menu.richMenuId().equals(finalDefaultId)))
                .toList());
    }

    /**
     * 刪除所有非預設的圖文選單（沒有預設選單時不刪除任何選單，避免誤刪）。
     *
     * @return 刪除的數量
     */
    public int deleteNonDefaultRichMenus() {
        RichMenus menus = richMenus();
        if (menus.defaultMenu().isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (RichMenuInfo menu : menus.menus()) {
            if (!menu.isDefault()) {
                await(client.deleteRichMenu(menu.id()));
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * 由前台帳號推播訊息給指定使用者（管理者在前後台的 userId 相同）。
     */
    public boolean push(String lineUserId, List<Message> messages) {
        return messenger.push(lineUserId, messages);
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * @param limit 每月上限；無上限時為 null
     */
    public record Quota(Long limit, long used) {

        public Double ratio() {
            return limit == null || limit == 0 ? null : (double) used / limit;
        }

    }

    public record WebhookTestResult(String endpoint, Boolean active, boolean success, Integer statusCode,
            String reason, String detail) {
    }

    public record RichMenuInfo(String id, String name, String chatBarText, boolean isDefault) {
    }

    public record RichMenus(List<RichMenuInfo> menus) {

        public Optional<RichMenuInfo> defaultMenu() {
            return menus.stream().filter(RichMenuInfo::isDefault).findFirst();
        }

        public long nonDefaultCount() {
            return menus.stream().filter(menu -> !menu.isDefault()).count();
        }

    }

}
