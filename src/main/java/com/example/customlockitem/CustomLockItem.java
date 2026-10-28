package com.example.customlockitem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Objects;
public final class CustomLockItem extends JavaPlugin {
    private Items items;
    private CooldownManager cooldowns;
    private TimestopManager timestop;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        cooldowns = new CooldownManager(this);
        timestop = new TimestopManager(this);
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(timestop, this);
        LockItemCommand command = new LockItemCommand(this);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("customlockitems"));
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
        timestop.start();
    }
    @Override
    public void onDisable() {
        if (timestop != null) timestop.shutdown();
    }
    public Items items() {
        return items;
    }
    public CooldownManager cooldowns() {
        return cooldowns;
    }
    /** Radius of the Timestop Clock field in blocks (file-only setting). */
    public double clockRadius() {
        double radius = getConfig().getDouble("timestop_clock.radius", 7.0);
        return Math.max(1.0, Math.min(32.0, radius));
    }
    /** Called when a player right-clicks one of the custom items. */
    public void use(Player player, ItemType type) {
        if (!player.hasPermission("customlockitems.use")) {
            player.sendActionBar(Component.text("You can't use this item.", NamedTextColor.RED));
            return;
        }
        long left = cooldowns.remainingMillis(player, type);
        if (left > 0) {
            player.sendActionBar(Component.text(
                    type.displayName() + " is recharging: " + Text.millis(left), NamedTextColor.RED));
            return;
        }
        switch (type) {
            case LUCKY_COIN -> useCoin(player);
            case TIMESTOP_CLOCK -> useClock(player);
        }
    }
    private void useCoin(Player player) {
        FileConfiguration config = getConfig();
        int seconds = Slider.COIN_DURATION.get(config);
        int level = Slider.COIN_LEVEL.get(config);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, level - 1));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
        player.sendActionBar(Component.text(
                "Lucky Coin: Resistance " + Text.roman(level) + " for " + Text.seconds(seconds),
                NamedTextColor.GOLD));
        cooldowns.start(player, ItemType.LUCKY_COIN, Slider.COIN_COOLDOWN.get(config) * 1000L);
    }
    private void useClock(Player player) {
        FileConfiguration config = getConfig();
        int seconds = Slider.CLOCK_DURATION.get(config);
        int level = Math.max(1, Math.min(5, config.getInt("timestop_clock.resistance_level", 3)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, level - 1));
        timestop.begin(player, seconds * 20);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        player.sendActionBar(Component.text(
                "Timestop active for " + Text.seconds(seconds), NamedTextColor.YELLOW));
        cooldowns.start(player, ItemType.TIMESTOP_CLOCK, Slider.CLOCK_COOLDOWN.get(config) * 1000L);
    }
}
