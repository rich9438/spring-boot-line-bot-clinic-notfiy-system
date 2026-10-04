package com.everythingcanbe.linebotclinicnotifysystem.line;

/**
 * Postback data 格式：{@code action=<action>&key=value}。
 */
public final class PostbackActions {

    public static final String SELECT_ROOM = "select-room";
    public static final String CUSTOM_THRESHOLDS = "custom-thresholds";

    private PostbackActions() {
    }

    public static String customThresholds() {
        return "action=" + CUSTOM_THRESHOLDS;
    }

    public static String selectRoom(int roomId) {
        return "action=" + SELECT_ROOM + "&room=" + roomId;
    }

}
