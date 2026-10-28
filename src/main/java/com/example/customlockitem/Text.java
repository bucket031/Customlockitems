package com.example.customlockitem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
/** Small text helpers shared by the plugin. */
final class Text {
    private Text() {}
    /** Item-friendly text (no italics, which Minecraft adds to custom names by default). */
    static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    static Component bold(String text, TextColor color) {
        return Component.text(text, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
    }
    /** 90 -> "1m 30s", 5 -> "5s", 600 -> "10m". */
    static String seconds(long totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + "s";
        long minutes = totalSeconds / 60;
        long rest = totalSeconds % 60;
        return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
    }
    /** Milliseconds rounded up to whole seconds. */
    static String millis(long millis) {
        return seconds((millis + 999) / 1000);
    }
    static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
