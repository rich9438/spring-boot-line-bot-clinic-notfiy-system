package com.everythingcanbe.linebotclinicnotifysystem.provider;

import java.util.List;

/**
 * 診所看診進度資料來源。新增其他醫院時實作此介面即可，不需修改核心業務邏輯。
 */
public interface ClinicProvider {

    String code();

    /**
     * 抓取所有設定的診間；單一診間失敗時略過該診間，不影響其他診間。
     */
    List<RoomStatus> fetchAllRooms();

    RoomStatus fetchRoom(Integer roomId);

    /**
     * 直接向資料來源取得原始內容（後台診斷用，不使用快取）。
     */
    default String fetchRaw(Integer roomId) {
        throw new UnsupportedOperationException("Raw content is not supported by " + code());
    }

}
