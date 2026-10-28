package com.example.customlockitem;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
/**
 * Per-player cooldowns. They are stored on the player, so they survive
 * logging out and server restarts, and owning several copies of an item
 * doesn't get around them.
 */
public final class CooldownManager {
    private final JavaPlugin plugin;
    public CooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    private NamespacedKey key(ItemType type) {
        return new NamespacedKey(plugin, "cooldown_" + type.id());
    }
    public long remainingMillis(Player player, ItemType type) {
        Long until = player.getPersistentDataContainer().get(key(type), PersistentDataType.LONG);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }
    public void start(Player player, ItemType type, long durationMillis) {
        if (durationMillis <= 0) {
            clear(player, type);
            return;
        }
        player.getPersistentDataContainer().set(key(type), PersistentDataType.LONG,
                System.currentTimeMillis() + durationMillis);
    }
    public void clear(Player player, ItemType type) {
        player.getPersistentDataContainer().remove(key(type));
    }
}
