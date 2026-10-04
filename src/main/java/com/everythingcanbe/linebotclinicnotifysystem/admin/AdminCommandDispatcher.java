package com.everythingcanbe.linebotclinicnotifysystem.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.jackson.ModelObjectMapper;
import com.linecorp.bot.messaging.model.FlexContainer;
import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.ClinicChannelOperations;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.DailySummaryService;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.RecentLogBuffer;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.SubscriberLookupService;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.SystemStatusService;
import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.line.FlexSamples;
import com.everythingcanbe.linebotclinicnotifysystem.line.MessageFactory;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.PollingHealth;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.QueuePollingScheduler;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomStatusCache;
import com.everythingcanbe.linebotclinicnotifysystem.provider.ClinicProvider;

/**
 * 執行後台管理指令並產生回覆卡片。
 */
@Component
@ConditionalOnAdminEnabled
public class AdminCommandDispatcher {

    static final int MAX_PREVIEW_MESSAGES = 5;

    private final AdminCardFactory cards;
    private final AdminMessenger adminMessenger;
    private final SystemStatusService statusService;
    private final SubscriberLookupService lookupService;
    private final DailySummaryService summaryService;
    private final RecentLogBuffer logBuffer;
    private final ClinicChannelOperations clinicChannel;
    private final QueuePollingScheduler pollingScheduler;
    private final PollingHealth pollingHealth;
    private final RoomStatusCache cache;
    private final ClinicProvider provider;
    private final ClinicProperties clinicProperties;
    private final MessageFactory messageFactory;
    private final Clock clock;
    private final ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();

    public AdminCommandDispatcher(AdminCardFactory cards, AdminMessenger adminMessenger,
            SystemStatusService statusService, SubscriberLookupService lookupService,
            DailySummaryService summaryService, RecentLogBuffer logBuffer, ClinicChannelOperations clinicChannel,
            QueuePollingScheduler pollingScheduler, PollingHealth pollingHealth, RoomStatusCache cache,
            ClinicProvider provider, ClinicProperties clinicProperties, MessageFactory messageFactory, Clock clock) {
        this.cards = cards;
        this.adminMessenger = adminMessenger;
        this.statusService = statusService;
        this.lookupService = lookupService;
        this.summaryService = summaryService;
        this.logBuffer = logBuffer;
        this.clinicChannel = clinicChannel;
        this.pollingScheduler = pollingScheduler;
        this.pollingHealth = pollingHealth;
        this.cache = cache;
        this.provider = provider;
        this.clinicProperties = clinicProperties;
        this.messageFactory = messageFactory;
        this.clock = clock;
    }

    public List<Message> dispatch(String adminId, AdminCommand command) {
        return switch (command) {
            case AdminCommand.Status ignored -> List.of(cards.status(statusService.status()));
            case AdminCommand.Room room -> List.of(room(room.roomId()));
            case AdminCommand.Errors errors -> List.of(cards.logs(logBuffer.recent(errors.limit(), errors.keyword()),
                    errors.keyword()));
            case AdminCommand.Lookup lookup -> List.of(cards.lookup(lookupService.lookup(lookup.query())));
            case AdminCommand.Quota ignored -> List.of(cards.quota(clinicChannel.quota()));
            case AdminCommand.WebhookTest ignored -> List.of(cards.webhook(clinicChannel.testWebhook()));
            case AdminCommand.Summary ignored -> List.of(cards.dailySummary(
                    summaryService.summarize(LocalDate.now(clock))));
            case AdminCommand.FlexPreview preview -> flexPreview(preview.json());
            case AdminCommand.Samples ignored -> List.of(cards.samples(samples().keySet()));
            case AdminCommand.SendSample sample -> List.of(sendSample(adminId, sample.key()));
            case AdminCommand.Poll ignored -> List.of(cards.poll(clinicProperties.rooms(), pollingScheduler.poll()));
            case AdminCommand.RichMenus ignored -> List.of(cards.richMenus(clinicChannel.richMenus()));
            case AdminCommand.RichMenuCleanup ignored -> List.of(
                    cards.richMenuCleanupConfirm(clinicChannel.richMenus()));
            case AdminCommand.RichMenuCleanupConfirm ignored -> List.of(
                    cards.richMenuCleanupDone(clinicChannel.deleteNonDefaultRichMenus()));
            case AdminCommand.Help ignored -> List.of(cards.help());
            case AdminCommand.Invalid invalid -> List.of(cards.error("指令格式錯誤", invalid.message()));
            case AdminCommand.Unknown ignored -> List.of(cards.unknown());
        };
    }

    private Message room(int roomId) {
        if (!clinicProperties.rooms().contains(roomId)) {
            return cards.error("無此診間", "設定的診間：" + clinicProperties.rooms());
        }
        String raw;
        try {
            raw = provider.fetchRaw(roomId);
        } catch (Exception e) {
            raw = "抓取失敗：" + e.getMessage();
        }
        return cards.room(roomId, cache.get(roomId), pollingHealth.get(roomId), raw);
    }

    /**
     * 接受 bubble／carousel、單則 flex message，或含 messages 的 request body（例如 deploy/flex-samples 的檔案）。
     */
    List<Message> flexPreview(String json) {
        List<Message> messages;
        try {
            messages = parseMessages(objectMapper.readTree(json));
        } catch (Exception e) {
            return List.of(cards.error("JSON 格式錯誤", e.getMessage()));
        }
        if (messages.isEmpty() || messages.size() > MAX_PREVIEW_MESSAGES) {
            return List.of(cards.error("無法預覽", "訊息數需介於 1～" + MAX_PREVIEW_MESSAGES + " 則。"));
        }
        Optional<String> error = adminMessenger.validate(messages);
        return error.<List<Message>>map(e -> List.of(cards.error("LINE 驗證失敗", e))).orElse(messages);
    }

    private List<Message> parseMessages(JsonNode node) throws Exception {
        if (node.has("messages")) {
            return objectMapper.convertValue(node.get("messages"), new TypeReference<List<Message>>() {
            });
        }
        String type = node.path("type").asText();
        return switch (type) {
            case "flex" -> List.of(objectMapper.treeToValue(node, Message.class));
            case "bubble", "carousel" -> List.of(new FlexMessage("Flex 預覽",
                    objectMapper.treeToValue(node, FlexContainer.class)));
            default -> throw new IllegalArgumentException(
                    "請貼上 type 為 bubble、carousel 或 flex 的 JSON，或含 messages 的 request body");
        };
    }

    private Message sendSample(String adminId, String key) {
        Map<String, List<Message>> samples = samples();
        String normalized = key.matches("\\d") ? "0" + key : key;
        Optional<String> match = samples.keySet().stream()
                .filter(name -> name.equals(normalized) || name.startsWith(normalized + "-"))
                .findFirst();
        if (match.isEmpty()) {
            return cards.error("找不到範例", "沒有「" + key + "」，輸入「範例」查看清單。");
        }
        return cards.sampleSent(match.get(), clinicChannel.push(adminId, samples.get(match.get())));
    }

    private Map<String, List<Message>> samples() {
        return FlexSamples.all(messageFactory);
    }

}
