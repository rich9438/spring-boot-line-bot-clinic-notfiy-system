package com.everythingcanbe.linebotclinicnotifysystem.provider;

/**
 * 診別號碼與中文名稱（一診、二診…）的轉換。
 */
public final class RoomNames {

    private static final String DIGITS = "零一二三四五六七八九";

    private RoomNames() {
    }

    public static String of(int roomId) {
        return toChinese(roomId) + "診";
    }

    static String toChinese(int n) {
        if (n >= 0 && n < 10) {
            return String.valueOf(DIGITS.charAt(n));
        }
        if (n >= 10 && n < 20) {
            return "十" + (n == 10 ? "" : DIGITS.charAt(n - 10));
        }
        return String.valueOf(n);
    }

    /**
     * 將「2」、「二」、「十一」等字串轉為診別號碼，無法辨識時回傳 null。
     */
    public static Integer parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.chars().allMatch(Character::isDigit)) {
            return Integer.valueOf(text);
        }
        if (text.length() == 1) {
            int digit = DIGITS.indexOf(text.charAt(0));
            if (digit > 0) {
                return digit;
            }
            return text.equals("十") ? 10 : null;
        }
        if (text.length() == 2 && text.charAt(0) == '十') {
            int digit = DIGITS.indexOf(text.charAt(1));
            return digit > 0 ? 10 + digit : null;
        }
        return null;
    }

}
