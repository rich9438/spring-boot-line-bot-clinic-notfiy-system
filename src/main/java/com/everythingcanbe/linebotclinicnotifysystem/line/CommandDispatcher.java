package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.Tone;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomStatusQueryService;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.StartTrackingResult;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.TrackingService;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.TrackingView;

/**
 * 執行指令並產生回覆訊息（皆為 Flex Message 卡片）。
 */
@Component
public class CommandDispatcher {

    static final int MAX_NUMBER = 999;

    private final TrackingService trackingService;
    private final RoomStatusQueryService roomStatusQuery;
    private final MessageFactory messageFactory;
    private final PendingTrackStore pendingTrackStore;

    public CommandDispatcher(TrackingService trackingService, RoomStatusQueryService roomStatusQuery,
            MessageFactory messageFactory, PendingTrackStore pendingTrackStore) {
        this.trackingService = trackingService;
        this.roomStatusQuery = roomStatusQuery;
        this.messageFactory = messageFactory;
        this.pendingTrackStore = pendingTrackStore;
    }

    public List<Message> dispatch(String lineUserId, Command command) {
        // 選診間後若改下其他指令，放棄等待中的號碼輸入
        if (!(command instanceof Command.Number)) {
            pendingTrackStore.clear(lineUserId);
        }
        return switch (command) {
            case Command.Track track -> List.of(track(lineUserId, track));
            case Command.ChooseRoom ignored -> chooseRoom();
            case Command.SelectRoom select -> List.of(selectRoom(lineUserId, select.roomId()));
            case Command.Number number -> List.of(pendingTrackStore.take(lineUserId)
                    .map(roomId -> track(lineUserId, new Command.Track(roomId, number.number())))
                    .orElseGet(() -> messageFactory.notice(Tone.NEUTRAL, "請先選擇診間",
                            "請點選選單的「追蹤看診號碼」選擇診間，或直接輸入「追蹤 2診 56號」。",
                            FlexParts.messageButton("選擇診間", "追蹤", true, Tone.INFO))));
            case Command.Status ignored -> List.of(status(lineUserId));
            case Command.Cancel ignored -> List.of(trackingService.cancel(lineUserId)
                    ? messageFactory.cancelled()
                    : messageFactory.noTracking());
            case Command.Rooms ignored -> List.of(messageFactory.rooms(roomOptions(), "診間看診進度"));
            case Command.SetThresholds set -> List.of(setThresholds(lineUserId, set.thresholds()));
            case Command.ShowThresholds ignored -> List.of(
                    messageFactory.thresholdSettings("通知設定", trackingService.thresholdSettings(lineUserId)));
            case Command.ResetThresholds ignored -> List.of(
                    messageFactory.thresholdSettings("已恢復預設門檻", trackingService.resetThresholds(lineUserId)));
            case Command.Help ignored -> List.of(messageFactory.help());
            case Command.Invalid invalid -> List.of(messageFactory.notice(Tone.NEUTRAL, "指令格式錯誤",
                    invalid.message(), FlexParts.messageButton("使用說明", "幫助", false, Tone.INFO)));
            case Command.Unknown ignored -> List.of(messageFactory.unknownCommand());
            case Command.NoReply ignored -> List.of();
        };
    }

    private Message track(String lineUserId, Command.Track track) {
        if (!roomStatusQuery.isConfiguredRoom(track.roomId())) {
            return roomNotSupported();
        }
        if (track.number() < 1 || track.number() > MAX_NUMBER) {
            return messageFactory.notice(Tone.ALERT, "號碼格式錯誤", "號碼需介於 1～" + MAX_NUMBER + "。");
        }
        String roomName = RoomNames.of(track.roomId());
        StartTrackingResult result = trackingService.start(lineUserId, track.roomId(), track.number());
        return switch (result) {
            case StartTrackingResult.Started started -> messageFactory.trackingStarted(started);
            case StartTrackingResult.RoomUnavailable ignored -> roomUnavailable(roomName);
            case StartTrackingResult.NotInSession ignored -> notInSession(roomName);
            case StartTrackingResult.NumberReached reached ->
                    messageFactory.numberReached(reached.status(), reached.targetNumber());
        };
    }

    /**
     * 「追蹤」：回覆診間卡片（Carousel），看診中的診間附追蹤按鈕；全部未看診時另附說明。
     */
    private List<Message> chooseRoom() {
        List<MessageFactory.RoomOption> rooms = roomOptions();
        boolean anyInSession = rooms.stream()
                .anyMatch(room -> room.status().map(RoomStatus::inSession).orElse(false));
        Message carousel = messageFactory.rooms(rooms, anyInSession ? "請選擇要追蹤的診間" : "診間看診進度");
        if (anyInSession) {
            return List.of(carousel);
        }
        return List.of(messageFactory.notice(Tone.NEUTRAL, "目前沒有看診中的診間", "請於看診時段再試。"), carousel);
    }

    private Message selectRoom(String lineUserId, int roomId) {
        if (!roomStatusQuery.isConfiguredRoom(roomId)) {
            return roomNotSupported();
        }
        String roomName = RoomNames.of(roomId);
        Optional<RoomStatus> status = roomStatusQuery.current(roomId);
        if (status.isEmpty()) {
            return roomUnavailable(roomName);
        }
        if (!status.get().inSession()) {
            return notInSession(roomName);
        }
        pendingTrackStore.put(lineUserId, roomId);
        return messageFactory.askNumber(status.get());
    }

    private Message status(String lineUserId) {
        Optional<TrackingView> tracking = trackingService.currentTracking(lineUserId);
        if (tracking.isEmpty()) {
            return messageFactory.noTracking();
        }
        TrackingView view = tracking.get();
        Optional<RoomStatus> status = view.status();
        if (status.isPresent() && status.get().inSession()) {
            return messageFactory.trackingStatus(status.get(), view.targetNumber(), view.etaMinutes());
        }
        return messageFactory.trackingStatusUnavailable(view.roomId(), status, view.targetNumber());
    }

    private Message setThresholds(String lineUserId, List<Integer> thresholds) {
        try {
            return messageFactory.thresholdSettings("✅ 通知門檻已更新",
                    trackingService.updateThresholds(lineUserId, thresholds));
        } catch (IllegalArgumentException e) {
            return messageFactory.notice(Tone.ALERT, "門檻設定錯誤", e.getMessage() + "\n" + CommandParser.THRESHOLD_USAGE);
        }
    }

    private List<MessageFactory.RoomOption> roomOptions() {
        return roomStatusQuery.configuredRooms().stream()
                .map(roomId -> new MessageFactory.RoomOption(roomId, roomStatusQuery.current(roomId)))
                .toList();
    }

    private Message roomNotSupported() {
        String rooms = roomStatusQuery.configuredRooms().stream()
                .map(RoomNames::of)
                .collect(Collectors.joining("、"));
        return messageFactory.notice(Tone.ALERT, "無此診間", "目前只支援：" + rooms + "。",
                FlexParts.messageButton("查看診間", "診間", false, Tone.INFO));
    }

    private Message roomUnavailable(String roomName) {
        return messageFactory.notice(Tone.ALERT, "暫時無法取得資料", "暫時無法取得" + roomName + "資料，請稍後再試。");
    }

    private Message notInSession(String roomName) {
        return messageFactory.notice(Tone.NEUTRAL, roomName + "目前未看診", "看診開始後才能追蹤，請於看診時段再試。",
                FlexParts.messageButton("查看診間", "診間", false, Tone.INFO));
    }

}
