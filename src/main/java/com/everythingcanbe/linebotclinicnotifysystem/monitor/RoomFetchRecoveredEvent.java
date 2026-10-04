package com.everythingcanbe.linebotclinicnotifysystem.monitor;

/**
 * 診間資料在連續失敗後恢復；failedCount 為恢復前的連續失敗次數。
 */
public record RoomFetchRecoveredEvent(int roomId, int failedCount) {
}
