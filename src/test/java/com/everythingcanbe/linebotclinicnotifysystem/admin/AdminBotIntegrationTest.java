package com.everythingcanbe.linebotclinicnotifysystem.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.base.Result;
import com.linecorp.bot.jackson.ModelObjectMapper;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;

import com.everythingcanbe.linebotclinicnotifysystem.admin.audit.AdminAuditLogRepository;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.DailySummaryService;
import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;
import com.everythingcanbe.linebotclinicnotifysystem.domain.NotificationHistory;
import com.everythingcanbe.linebotclinicnotifysystem.domain.RoomStatusHistory;
import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;
import com.everythingcanbe.linebotclinicnotifysystem.line.PushFailedEvent;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomFetchFailedEvent;
import com.everythingcanbe.linebotclinicnotifysystem.provider.ClinicProvider;
import com.everythingcanbe.linebotclinicnotifysystem.repository.NotificationHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.RoomStatusHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.TrackingJobRepository;

/**
 * 後台 Bot：Webhook 簽章與白名單、各指令、告警（後台與前台的 LINE client 皆以 mock 取代）。
 */
@SpringBootTest(properties = {
        "admin.enabled=true",
        "admin.channel-token=test-admin-token",
        "admin.channel-secret=" + AdminBotIntegrationTest.ADMIN_SECRET,
        "admin.user-ids=" + AdminBotIntegrationTest.ADMIN_ID + ", U-second-admin",
        "admin.alerts.fetch-failure-threshold=3"
})
@AutoConfigureMockMvc
class AdminBotIntegrationTest {

    static final String ADMIN_SECRET = "admin-secret";
    static final String ADMIN_ID = "U-admin";

    private static final ObjectMapper OBJECT_MAPPER = ModelObjectMapper.createNewObjectMapper();

    @MockitoBean
    AdminMessenger adminMessenger;

    @MockitoBean
    MessagingApiClient clinicClient;

    @MockitoBean
    ClinicProvider provider;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    SubscriberRepository subscriberRepository;

    @Autowired
    TrackingJobRepository trackingJobRepository;

    @Autowired
    NotificationHistoryRepository notificationHistoryRepository;

    @Autowired
    RoomStatusHistoryRepository roomStatusHistoryRepository;

    @Autowired
    AdminAuditLogRepository auditLogRepository;

    @Autowired
    DailySummaryService dailySummaryService;

    @BeforeEach
    void setUp() {
        notificationHistoryRepository.deleteAll();
        trackingJobRepository.deleteAll();
        subscriberRepository.deleteAll();
        roomStatusHistoryRepository.deleteAll();
        auditLogRepository.deleteAll();
        clearInvocations(adminMessenger, clinicClient);
        when(provider.code()).thenReturn("wuobs");
        when(adminMessenger.validate(anyList())).thenReturn(Optional.empty());
        when(clinicClient.pushMessage(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new Result<>(null, null, null)));
    }

    @Test
    void adminCommandIsAnsweredAndAudited() throws Exception {
        String reply = send(ADMIN_ID, "狀態");

        assertThat(reply).contains("系統狀態", "一診", "二診", "三診");
        assertThat(auditLogRepository.findAll()).singleElement()
                .satisfies(log -> assertThat(log.getCommand()).isEqualTo("狀態"));
    }

    @Test
    void nonAdminGetsUnauthorizedCardWithUserId() throws Exception {
        String reply = send("U-stranger", "狀態");

        assertThat(reply).contains("未授權", "U-stranger").doesNotContain("系統狀態");
        assertThat(auditLogRepository.findAll()).isEmpty();
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        String body = textEvent(ADMIN_ID, "狀態");
        mockMvc.perform(post(AdminWebhookController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Line-Signature", "invalid")
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(adminMessenger, never()).reply(any(), anyList());
    }

    @Test
    void flexPreviewRepliesWithTheCard() throws Exception {
        String bubble = "{\"type\":\"bubble\",\"body\":{\"type\":\"box\",\"layout\":\"vertical\","
                + "\"contents\":[{\"type\":\"text\",\"text\":\"預覽，測試\"}]}}";

        List<Message> messages = sendForMessages(ADMIN_ID, bubble);

        assertThat(messages).singleElement().isInstanceOf(FlexMessage.class)
                .extracting(m -> ((FlexMessage) m).altText()).isEqualTo("Flex 預覽");
        assertThat(OBJECT_MAPPER.writeValueAsString(messages)).contains("預覽，測試");
    }

    @Test
    void flexPreviewAcceptsExportedSampleRequestBody() throws Exception {
        String sample = Files.readString(Path.of("deploy/flex-samples/04-progress.json"));

        List<Message> messages = sendForMessages(ADMIN_ID, sample);

        assertThat(messages).singleElement().isInstanceOf(FlexMessage.class)
                .extracting(m -> ((FlexMessage) m).altText()).asString().startsWith("⏰ 即將輪到您");
    }

    @Test
    void flexPreviewReportsLineValidationError() throws Exception {
        when(adminMessenger.validate(anyList())).thenReturn(Optional.of("invalid property"));

        assertThat(send(ADMIN_ID, "{\"type\":\"bubble\"}")).contains("LINE 驗證失敗", "invalid property");
    }

    @Test
    void sendSamplePushesFromClinicChannelToAdmin() throws Exception {
        String reply = send(ADMIN_ID, "範例 04");

        assertThat(reply).contains("已推播範例", "04-progress");
        ArgumentCaptor<PushMessageRequest> captor = ArgumentCaptor.forClass(PushMessageRequest.class);
        verify(clinicClient).pushMessage(any(), captor.capture());
        assertThat(captor.getValue().to()).isEqualTo(ADMIN_ID);
    }

    @Test
    void lookupShowsTrackingAndDeliveryHistory() throws Exception {
        Subscriber subscriber = subscriberRepository.save(new Subscriber("U" + "a".repeat(32), "王小明",
                LocalDateTime.now()));
        TrackingJob job = new TrackingJob(subscriber, "wuobs", 2, 56, LocalDateTime.now().minusMinutes(30));
        job.end(EndReason.ARRIVED, LocalDateTime.now());
        trackingJobRepository.save(job);
        notificationHistoryRepository.save(new NotificationHistory(job, 0, true, LocalDateTime.now()));
        notificationHistoryRepository.updateDelivered(job.getId(), 0, true);

        String reply = send(ADMIN_ID, "查詢 小明");

        assertThat(reply).contains("王小明", "二診 56 號", "到號", "已送達");
    }

    @Test
    void recentErrorsAreQueryable() throws Exception {
        LoggerFactory.getLogger("com.example.Probe").warn("probe warning for admin test");

        assertThat(send(ADMIN_ID, "log probe")).contains("probe warning for admin test");
    }

    @Test
    void dailySummaryCountsTodayActivity() {
        Subscriber subscriber = subscriberRepository.save(new Subscriber("U-sum", "summary", LocalDateTime.now()));
        TrackingJob arrived = new TrackingJob(subscriber, "wuobs", 2, 56, LocalDateTime.now());
        arrived.end(EndReason.ARRIVED, LocalDateTime.now());
        trackingJobRepository.save(arrived);
        trackingJobRepository.save(new TrackingJob(subscriber, "wuobs", 1, 30, LocalDateTime.now()));
        notificationHistoryRepository.save(new NotificationHistory(arrived, 3, true, LocalDateTime.now()));
        notificationHistoryRepository.updateDelivered(arrived.getId(), 3, false);
        roomStatusHistoryRepository.save(new RoomStatusHistory("wuobs", 2, 10, null, null, null, LocalDateTime.now()));
        roomStatusHistoryRepository.save(new RoomStatusHistory("wuobs", 2, 25, null, null, null, LocalDateTime.now()));

        DailySummaryService.DailySummary summary = dailySummaryService.summarize(LocalDate.now());

        assertThat(summary.newTrackings()).isEqualTo(2);
        assertThat(summary.ended(EndReason.ARRIVED)).isEqualTo(1);
        assertThat(summary.pushedFailed()).isEqualTo(1);
        assertThat(summary.activeNow()).isEqualTo(1);
        assertThat(summary.rooms()).singleElement()
                .satisfies(room -> assertThat(room.minNumber()).isEqualTo(10))
                .satisfies(room -> assertThat(room.maxNumber()).isEqualTo(25));
    }

    @Test
    void fetchFailureAlertsOnceWhenReachingThreshold() {
        eventPublisher.publishEvent(new RoomFetchFailedEvent(2, 2));
        eventPublisher.publishEvent(new RoomFetchFailedEvent(2, 3));
        eventPublisher.publishEvent(new RoomFetchFailedEvent(2, 4));

        assertThat(pushedAlerts("資料來源異常")).singleElement().asString().contains("二診", "3 次");
    }

    @Test
    void pushFailureAlertIsThrottled() {
        eventPublisher.publishEvent(new PushFailedEvent("U" + "b".repeat(32), "401 Unauthorized"));
        eventPublisher.publishEvent(new PushFailedEvent("U" + "c".repeat(32), "401 Unauthorized"));

        assertThat(pushedAlerts("前台推播失敗")).singleElement().asString().contains("401");
    }

    /**
     * 等待背景推播完成後，回傳 altText 含 keyword 的告警（排除非同步的啟動通知等其他推播）。
     */
    @SuppressWarnings("unchecked")
    private List<String> pushedAlerts(String keyword) {
        verify(adminMessenger, timeout(2000).atLeastOnce()).pushToAdmins(anyList());
        // 再等一下，確認背景執行緒沒有多送
        verify(adminMessenger, after(300).atLeastOnce()).pushToAdmins(anyList());
        return Mockito.mockingDetails(adminMessenger).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("pushToAdmins"))
                .map(invocation -> ((List<Message>) invocation.getArgument(0)).getFirst())
                .map(message -> ((FlexMessage) message).altText())
                .filter(altText -> altText.contains(keyword))
                .toList();
    }

    private String send(String userId, String text) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(sendForMessages(userId, text));
    }

    private List<Message> sendForMessages(String userId, String text) throws Exception {
        clearInvocations(adminMessenger);
        String body = textEvent(userId, text);
        mockMvc.perform(post(AdminWebhookController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Line-Signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(adminMessenger, timeout(3000)).reply(eq("reply-token"), captor.capture());
        return captor.getValue();
    }

    private static String textEvent(String userId, String text) throws Exception {
        String escaped = OBJECT_MAPPER.writeValueAsString(text);
        return """
                {"destination":"Uadmin","events":[{"type":"message","mode":"active","timestamp":1759470000000,
                "webhookEventId":"01H100","deliveryContext":{"isRedelivery":false},
                "source":{"type":"user","userId":"%s"},"replyToken":"reply-token",
                "message":{"type":"text","id":"1","quoteToken":"q","text":%s}}]}""".formatted(userId, escaped);
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(ADMIN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

}
