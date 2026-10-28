package com.example.customlockitem;
import org.bukkit.configuration.file.FileConfiguration;
/** Every value the config menu can change with a slider. */
public enum Slider {
    COIN_DURATION("lucky_coin.duration_seconds", "Duration", 1, 60, 1, 5, 5, Kind.SECONDS),
    COIN_COOLDOWN("lucky_coin.cooldown_seconds", "Cooldown", 0, 3600, 5, 60, 600, Kind.SECONDS),
    COIN_LEVEL("lucky_coin.resistance_level", "Resistance level", 1, 5, 1, 1, 5, Kind.LEVEL),
    CLOCK_DURATION("timestop_clock.duration_seconds", "Duration", 1, 120, 1, 10, 15, Kind.SECONDS),
    CLOCK_COOLDOWN("timestop_clock.cooldown_seconds", "Cooldown", 0, 3600, 5, 60, 90, Kind.SECONDS),
    CLOCK_TICK_RATE("timestop_clock.tick_rate", "Tick rate", 1, 20, 1, 5, 10, Kind.TICKS);
    public enum Kind { SECONDS, LEVEL, TICKS }
    /** How many positions the clickable bar has at most (menu rows are 9 wide, minus 2 buttons). */
    public static final int MAX_POSITIONS = 7;
    private final String path;
    private final String label;
    private final int min;
    private final int max;
    private final int step;
    private final int bigStep;
    private final int def;
    private final Kind kind;
    Slider(String path, String label, int min, int max, int step, int bigStep, int def, Kind kind) {
        this.path = path;
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.bigStep = bigStep;
        this.def = def;
        this.kind = kind;
    }
    public String label() { return label; }
    public int step() { return step; }
    public int bigStep() { return bigStep; }
    public int def() { return def; }
    public int get(FileConfiguration config) {
        return clamp(config.getInt(path, def));
    }
    public void set(FileConfiguration config, int value) {
        config.set(path, clamp(value));
    }
    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }
    /** Number of clickable positions on the bar. */
    public int positions() {
        return Math.max(1, Math.min(MAX_POSITIONS, (max - min) / step + 1));
    }
    /** The value you get by clicking position idx (0 = far left). */
    public int valueAtPosition(int idx) {
        int n = positions();
        if (n == 1) return min;
        double fraction = idx / (double) (n - 1);
        int steps = (int) Math.round(fraction * ((max - min) / (double) step));
        return clamp(min + steps * step);
    }
    /** Which position the "knob" sits on for a value. */
    public int positionOf(int value) {
        int n = positions();
        if (n == 1) return 0;
        double fraction = (clamp(value) - min) / (double) (max - min);
        return (int) Math.round(fraction * (n - 1));
    }
    public String format(int value) {
        return switch (kind) {
            case SECONDS -> value == 0 ? "None" : Text.seconds(value);
            case LEVEL -> "Resistance " + Text.roman(value);
            case TICKS -> value >= 20
                    ? "20 ticks/s (normal speed)"
                    : value + " ticks/s (" + (value * 5) + "% speed)";
        };
    }
    /** How much one click changes the value, for button tooltips. */
    public String amount(int delta) {
        return switch (kind) {
            case SECONDS -> Text.seconds(delta);
            case LEVEL -> delta + (delta == 1 ? " level" : " levels");
            case TICKS -> delta + (delta == 1 ? " tick" : " ticks");
        };
    }
}
