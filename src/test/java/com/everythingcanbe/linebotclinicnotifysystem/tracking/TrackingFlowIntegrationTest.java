package com.everythingcanbe.linebotclinicnotifysystem.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.base.Result;
import com.linecorp.bot.jackson.ModelObjectMapper;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;

import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;
import com.everythingcanbe.linebotclinicnotifysystem.line.Command;
import com.everythingcanbe.linebotclinicnotifysystem.line.CommandDispatcher;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomStatusCache;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomStatusMonitor;
import com.everythingcanbe.linebotclinicnotifysystem.provider.ClinicProvider;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.repository.NotificationHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.RoomStatusHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberThresholdRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.TrackingJobRepository;

/**
 * 追蹤 → 號碼變動 → 推播 → 任務結束 的完整流程（H2 + Flyway，LINE API 與資料來源以 mock 取代）。
 */
@SpringBootTest
class TrackingFlowIntegrationTest {

    private static final String USER = "U-test-user";
    private static final ObjectMapper OBJECT_MAPPER = ModelObjectMapper.createNewObjectMapper();

    @MockitoBean
    ClinicProvider provider;

    @MockitoBean
    MessagingApiClient messagingApiClient;

    @Autowired
    CommandDispatcher dispatcher;

    @Autowired
    RoomStatusMonitor monitor;

    @Autowired
    RoomStatusCache cache;

    @Autowired
    TrackingJobRepository trackingJobRepository;

    @Autowired
    NotificationHistoryRepository notificationHistoryRepository;

    @Autowired
    RoomStatusHistoryRepository roomStatusHistoryRepository;

    @Autowired
    SubscriberThresholdRepository subscriberThresholdRepository;

    @Autowired
    SubscriberRepository subscriberRepository;

    @BeforeEach
    void setUp() {
        notificationHistoryRepository.deleteAll();
        trackingJobRepository.deleteAll();
        subscriberThresholdRepository.deleteAll();
        subscriberRepository.deleteAll();
        roomStatusHistoryRepository.deleteAll();
        cache.clear();

        when(provider.code()).thenReturn("wuobs");
        when(messagingApiClient.pushMessage(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new Result<>(null, null, null)));
    }

    @Test
    void fullFlowFromTrackingToArrival() {
        monitor.onStatus(room(2, 46));

        String reply = replyText(new Command.Track(2, 56));
        assertThat(reply).contains("已開始追蹤", "二診", "56", "剩餘 10 位", "10、3、到號");

        // 剩 10 位時建立：門檻 10 已靜默標記，不再推播
        monitor.onStatus(room(2, 47));
        monitor.onStatus(room(2, 50));
        verify(messagingApiClient, never()).pushMessage(any(), any());

        // 剩 2 位：推播「剩 3 位」門檻一次
        monitor.onStatus(room(2, 54));
        assertThat(pushedMessages()).singleElement().isInstanceOf(FlexMessage.class)
                .extracting(m -> ((FlexMessage) m).altText()).asString().contains("剩餘 2 位");

        // 號碼不變：不重複推播
        clearInvocations(messagingApiClient);
        monitor.onStatus(room(2, 54));
        verify(messagingApiClient, never()).pushMessage(any(), any());

        // 到號
        monitor.onStatus(room(2, 56));
        assertThat(pushedMessages()).singleElement()
                .extracting(m -> ((FlexMessage) m).altText()).asString().contains("請立即報到");

        assertThat(trackingJobRepository.findAll()).singleElement()
                .extracting(TrackingJob::getEndReason).isEqualTo(EndReason.ARRIVED);
        assertThat(replyText(new Command.Status())).contains("目前沒有追蹤");
    }

    @Test
    void interactiveTrackingByChoosingRoomThenNumber() {
        monitor.onStatus(room(1, 35));
        monitor.onStatus(room(2, 46));
        monitor.onStatus(notInSession(3));

        List<Message> picker = dispatcher.dispatch(USER, new Command.ChooseRoom());
        assertThat(picker).singleElement().isInstanceOf(FlexMessage.class)
                .extracting(m -> ((FlexMessage) m).altText()).asString().startsWith("請選擇要追蹤的診間");

        assertThat(replyText(new Command.SelectRoom(2))).contains("二診目前 46 號", "請輸入您的看診號碼");
        assertThat(replyText(new Command.Number(56))).contains("已開始追蹤", "二診", "剩餘 10 位");
        assertThat(trackingJobRepository.findAll()).singleElement()
                .extracting(TrackingJob::getRoomId, TrackingJob::getTargetNumber)
                .containsExactly(2, 56);

        // 選定的診間只能用一次
        assertThat(replyText(new Command.Number(57))).contains("請先選擇診間");
    }

    @Test
    void otherCommandCancelsPendingRoomSelection() {
        monitor.onStatus(room(2, 46));

        replyText(new Command.SelectRoom(2));
        replyText(new Command.Rooms());

        assertThat(replyText(new Command.Number(56))).contains("請先選擇診間");
        assertThat(trackingJobRepository.findAll()).isEmpty();
    }

    @Test
    void roomPickerFallsBackToTextWhenNoRoomInSession() {
        monitor.onStatus(notInSession(1));
        monitor.onStatus(notInSession(2));
        monitor.onStatus(notInSession(3));

        assertThat(replyText(new Command.ChooseRoom())).contains("目前沒有看診中的診間");
        assertThat(replyText(new Command.SelectRoom(2))).contains("未看診");
    }

    @Test
    void skippedNumberIsReportedAsMissed() {
        monitor.onStatus(room(2, 50));
        replyText(new Command.Track(2, 52));

        monitor.onStatus(room(2, 55));

        assertThat(pushedMessages()).singleElement()
                .extracting(m -> ((FlexMessage) m).altText()).asString().contains("已過號");
        assertThat(trackingJobRepository.findAll()).singleElement()
                .extracting(TrackingJob::getEndReason).isEqualTo(EndReason.MISSED);
    }

    @Test
    void numberResetEndsTracking() {
        monitor.onStatus(room(2, 25));
        replyText(new Command.Track(2, 40));

        // 午休後重新叫號（中間一度未看診）
        monitor.onStatus(notInSession(2));
        monitor.onStatus(room(2, 1));

        assertThat(pushedMessages()).singleElement()
                .extracting(m -> ((FlexMessage) m).altText()).asString().contains("重新開始");
        assertThat(trackingJobRepository.findAll()).singleElement()
                .extracting(TrackingJob::getEndReason).isEqualTo(EndReason.SESSION_RESET);
    }

    @Test
    void newTrackingReplacesOldOne() {
        monitor.onStatus(room(2, 10));
        monitor.onStatus(room(1, 10));
        replyText(new Command.Track(2, 30));

        assertThat(replyText(new Command.Track(1, 40))).contains("已取代先前的追蹤");
        assertThat(trackingJobRepository.findAll())
                .extracting(TrackingJob::isActive, TrackingJob::getRoomId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(false, 2),
                        org.assertj.core.groups.Tuple.tuple(true, 1));
    }

    @Test
    void cannotTrackPassedNumberOrClosedRoom() {
        monitor.onStatus(room(2, 30));
        monitor.onStatus(notInSession(3));

        assertThat(replyText(new Command.Track(2, 20))).contains("已過號");
        assertThat(replyText(new Command.Track(2, 30))).contains("請立即報到");
        assertThat(replyText(new Command.Track(3, 10))).contains("未看診");
        assertThat(replyText(new Command.Track(9, 10))).contains("目前只支援");
        assertThat(trackingJobRepository.findAll()).isEmpty();
    }

    @Test
    void customThresholds() {
        assertThat(replyText(new Command.SetThresholds(List.of(15, 5)))).contains("15、5、到號");
        assertThat(replyText(new Command.ShowThresholds())).contains("15、5、到號");
        assertThat(replyText(new Command.SetThresholds(List.of(1, 2, 3, 4, 5, 6)))).contains("最多 5 個");
        assertThat(replyText(new Command.SetThresholds(List.of(99)))).contains("1～50");

        monitor.onStatus(room(2, 30));
        replyText(new Command.Track(2, 50));
        monitor.onStatus(room(2, 36));
        assertThat(pushedMessages()).singleElement()
                .extracting(m -> ((FlexMessage) m).altText()).asString().contains("剩餘 14 位");

        assertThat(replyText(new Command.ShowThresholds())).contains("通知設定：15、5、到號", "自訂");
        assertThat(replyText(new Command.ResetThresholds())).contains("已恢復預設門檻：10、3、到號", "系統預設");
        assertThat(dispatcher.dispatch(USER, new Command.NoReply())).isEmpty();
    }

    @Test
    void statusAndCancel() {
        monitor.onStatus(room(2, 46));
        replyText(new Command.Track(2, 56));

        List<Message> status = dispatcher.dispatch(USER, new Command.Status());
        assertThat(status).singleElement().isInstanceOf(FlexMessage.class)
                .extracting(m -> ((FlexMessage) m).altText()).asString().contains("剩餘 10 位");

        assertThat(replyText(new Command.Cancel())).contains("已取消追蹤");
        assertThat(replyText(new Command.Cancel())).contains("目前沒有追蹤");
    }

    @Test
    void roomsListing() {
        monitor.onStatus(room(1, 35));
        monitor.onStatus(room(2, 46));
        monitor.onStatus(notInSession(3));

        String rooms = replyText(new Command.Rooms());

        // 一個 Carousel、每個診間一張卡片；只有看診中的診間有追蹤按鈕
        assertThat(rooms)
                .contains("\"type\":\"carousel\"", "診間看診進度：一診 35 號、二診 46 號、三診 未看診")
                .contains("action=select-room&room=1", "action=select-room&room=2")
                .doesNotContain("action=select-room&room=3");
        assertThat(rooms.split("\"type\":\"bubble\"", -1)).hasSize(4);
    }

    /**
     * 回覆一律為 Flex 卡片；回傳序列化後的 JSON 以便檢查內容。
     */
    private String replyText(Command command) {
        List<Message> messages = dispatcher.dispatch(USER, command);
        assertThat(messages).isNotEmpty().allSatisfy(m -> assertThat(m).isInstanceOf(FlexMessage.class));
        try {
            return OBJECT_MAPPER.writeValueAsString(messages);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Message> pushedMessages() {
        ArgumentCaptor<PushMessageRequest> captor = ArgumentCaptor.forClass(PushMessageRequest.class);
        verify(messagingApiClient, times(1)).pushMessage(any(), captor.capture());
        assertThat(captor.getValue().to()).isEqualTo(USER);
        clearInvocations(messagingApiClient);
        return captor.getValue().messages();
    }

    private static RoomStatus room(int roomId, int number) {
        String department = roomId == 3 ? "小兒科" : "婦產科";
        return new RoomStatus("wuobs", roomId, new String[] {"", "一診", "二診", "三診"}[roomId], "吳瑞聰", department,
                number, true, LocalDateTime.now());
    }

    private static RoomStatus notInSession(int roomId) {
        String department = roomId == 3 ? "小兒科" : "婦產科";
        return new RoomStatus("wuobs", roomId, new String[] {"", "一診", "二診", "三診"}[roomId], null, department,
                null, false, LocalDateTime.now());
    }

}
