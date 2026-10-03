package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.List;

/**
 * 使用者在 LINE 輸入的指令。
 */
public sealed interface Command {

    record Track(int roomId, int number) implements Command {
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
