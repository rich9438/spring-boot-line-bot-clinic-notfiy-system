package com.everythingcanbe.linebotclinicnotifysystem.monitor;

/**
 * 診間資料抓取失敗；consecutiveFailures 為目前連續失敗次數。
 */
public record RoomFetchFailedEvent(int roomId, int consecutiveFailures) {
}
