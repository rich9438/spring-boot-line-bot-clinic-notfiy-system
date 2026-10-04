package com.everythingcanbe.linebotclinicnotifysystem.line;

/**
 * Postback data 格式：{@code action=<action>&key=value}。
 */
public final class PostbackActions {

    public static final String SELECT_ROOM = "select-room";

    private PostbackActions() {
    }

    public static String selectRoom(int roomId) {
        return "action=" + SELECT_ROOM + "&room=" + roomId;
    }

}
