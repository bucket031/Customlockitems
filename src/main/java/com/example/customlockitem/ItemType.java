package com.example.customlockitem;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import java.util.List;
import java.util.Locale;
/** The custom items this plugin knows about. */
public enum ItemType {
    LUCKY_COIN("lucky_coin", "Lucky Coin", Material.SUNFLOWER, NamedTextColor.GOLD,
            List.of("A coin said to bring good fortune.",
                    "",
                    "Right-click: gain a burst of Resistance.")),
    TIMESTOP_CLOCK("timestop_clock", "Timestop Clock", Material.CLOCK, NamedTextColor.YELLOW,
            List.of("A clock that bends time itself.",
                    "",
                    "Right-click: slow everything around you."));
    private final String id;
    private final String displayName;
    private final Material material;
    private final NamedTextColor color;
    private final List<String> lore;
    ItemType(String id, String displayName, Material material, NamedTextColor color, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.color = color;
        this.lore = lore;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material material() { return material; }
    public NamedTextColor color() { return color; }
    public List<String> lore() { return lore; }
    /** Accepts "lucky_coin", "lucky-coin", "LuckyCoin", "lucky coin"... */
    public static ItemType fromInput(String input) {
        if (input == null) return null;
        String wanted = normalize(input);
        for (ItemType type : values()) {
            if (normalize(type.id).equals(wanted) || normalize(type.displayName).equals(wanted)) {
                return type;
            }
        }
        return null;
    }
    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
