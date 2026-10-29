package com.example.customlockitem;
import java.util.Locale;
/** The spirits a player can be turned into with /cli wisp set. */
public enum WispType {
    ABYSS("abyss", "Abyss");
    private final String id;
    private final String displayName;
    WispType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public static WispType fromInput(String input) {
        if (input == null) return null;
        String wanted = input.toLowerCase(Locale.ROOT);
        for (WispType type : values()) {
            if (type.id.equals(wanted)) return type;
        }
        return null;
    }
}
