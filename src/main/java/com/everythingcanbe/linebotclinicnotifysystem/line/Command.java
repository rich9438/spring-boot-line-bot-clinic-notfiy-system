package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.List;

/**
 * 使用者在 LINE 輸入的指令。
 */
public sealed interface Command {

    record Track(int roomId, int number) implements Command {
    }

    /** 「追蹤」：顯示診間選擇卡片 */
    record ChooseRoom() implements Command {
    }

    /** 已選定診間，等待使用者輸入號碼（「追蹤 2診」或點選診間卡片按鈕） */
    record SelectRoom(int roomId) implements Command {
    }

    /** 只輸入號碼（例如「56」），搭配先前選定的診間 */
    record Number(int number) implements Command {
    }

    record Status() implements Command {
    }

    record Cancel() implements Command {
    }

    record Rooms() implements Command {
    }

    record SetThresholds(List<Integer> thresholds) implements Command {
    }

    record ShowThresholds() implements Command {
    }

    record ResetThresholds() implements Command {
    }

    record Help() implements Command {
    }

    /** 可辨識的指令但格式錯誤，message 為給使用者的說明 */
    record Invalid(String message) implements Command {
    }

    record Unknown(String text) implements Command {
    }

}
