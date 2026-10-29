package com.example.customlockitem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
/**
 * The /customlockitems config menus.
 *
 * Minecraft inventories have no real sliders, so each slider is one row:
 *   [ - ] [ bar: up to 7 clickable positions ] [ + ]
 * Click the bar to jump to a value, or use - / + to fine tune (shift-click for bigger steps).
 */
public final class ConfigMenu implements InventoryHolder {
    public enum Page { MAIN, COIN, CLOCK, EYE }
    private static final int COIN_ICON_SLOT = 11;
    private static final int EYE_ICON_SLOT = 13;
    private static final int CLOCK_ICON_SLOT = 15;
    private static final int BACK_SLOT = 36;
    private static final int RESET_SLOT = 44;
    private final CustomLockItem plugin;
    private final Page page;
    private final Inventory inventory;
    public ConfigMenu(CustomLockItem plugin, Page page) {
        this.plugin = plugin;
        this.page = page;
        Component title = switch (page) {
            case MAIN -> Component.text("CustomLockItem Config");
            case COIN -> Component.text("Lucky Coin Settings");
            case CLOCK -> Component.text("Timestop Clock Settings");
            case EYE -> Component.text("Eye of Exponential End Settings");
        };
        this.inventory = Bukkit.createInventory(this, page == Page.MAIN ? 27 : 45, title);
        render();
    }
    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
    public void open(Player player) {
        player.openInventory(inventory);
    }
    // ------------------------------------------------------------- drawing
    private Slider[] sliders() {
        return slidersFor(page);
    }
    private static Slider[] slidersFor(Page which) {
        return switch (which) {
            case COIN -> new Slider[]{Slider.COIN_DURATION, Slider.COIN_COOLDOWN, Slider.COIN_LEVEL};
            case CLOCK -> new Slider[]{Slider.CLOCK_DURATION, Slider.CLOCK_COOLDOWN, Slider.CLOCK_TICK_RATE};
            case EYE -> new Slider[]{Slider.EYE_RADIUS, Slider.EYE_ARMOR_DAMAGE};
            case MAIN -> new Slider[0];
        };
    }
    private void render() {
        inventory.clear();
        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        if (page == Page.MAIN) {
            inventory.setItem(COIN_ICON_SLOT, plugin.items().icon(ItemType.LUCKY_COIN, summary(Page.COIN)));
            inventory.setItem(EYE_ICON_SLOT, plugin.items().icon(ItemType.EXPONENTIAL_EYE, summary(Page.EYE)));
            inventory.setItem(CLOCK_ICON_SLOT, plugin.items().icon(ItemType.TIMESTOP_CLOCK, summary(Page.CLOCK)));
            return;
        }
        ItemType type = switch (page) {
            case COIN -> ItemType.LUCKY_COIN;
            case EYE -> ItemType.EXPONENTIAL_EYE;
            default -> ItemType.TIMESTOP_CLOCK;
        };
        inventory.setItem(4, plugin.items().icon(type, List.of(
                Text.plain("Click a bar to jump to a value.", NamedTextColor.GRAY),
                Text.plain("Use - / + to fine tune.", NamedTextColor.GRAY),
                Text.plain("Shift-click - / + for bigger steps.", NamedTextColor.GRAY))));
        Slider[] sliders = sliders();
        for (int i = 0; i < sliders.length; i++) {
            renderSlider(sliders[i], (i + 1) * 9);
        }
        inventory.setItem(BACK_SLOT, button(Material.ARROW, "Back", NamedTextColor.WHITE,
                List.of("Return to the item list")));
        inventory.setItem(RESET_SLOT, button(Material.BARRIER, "Reset to defaults", NamedTextColor.RED,
                List.of("Reset every slider on this page")));
    }
    private void renderSlider(Slider slider, int base) {
        FileConfiguration config = plugin.getConfig();
        int value = slider.get(config);
        int positions = slider.positions();
        int thumb = slider.positionOf(value);
        int start = trackStart(slider);
        inventory.setItem(base, button(Material.RED_CONCRETE, "- " + slider.label(), NamedTextColor.RED, List.of(
                "Click: -" + slider.amount(slider.step()),
                "Shift-click: -" + slider.amount(slider.bigStep()))));
        inventory.setItem(base + 8, button(Material.GREEN_CONCRETE, "+ " + slider.label(), NamedTextColor.GREEN, List.of(
                "Click: +" + slider.amount(slider.step()),
                "Shift-click: +" + slider.amount(slider.bigStep()))));
        for (int idx = 0; idx < positions; idx++) {
            int slot = base + start + idx;
            if (idx == thumb) {
                ItemStack knob = button(Material.GOLD_BLOCK, slider.label() + ": " + slider.format(value),
                        NamedTextColor.GOLD, List.of("Current value"));
                glint(knob);
                inventory.setItem(slot, knob);
            } else {
                Material material = idx < thumb
                        ? Material.YELLOW_STAINED_GLASS_PANE
                        : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                inventory.setItem(slot, button(material,
                        slider.label() + ": set to " + slider.format(slider.valueAtPosition(idx)),
                        NamedTextColor.YELLOW, List.of("Click to set this value")));
            }
        }
    }
    /** Slot offset (within the row) of the bar's first position, so short bars sit in the middle. */
    private static int trackStart(Slider slider) {
        return 1 + (Slider.MAX_POSITIONS - slider.positions()) / 2;
    }
    private List<Component> summary(Page which) {
        FileConfiguration config = plugin.getConfig();
        List<Component> lines = new ArrayList<>();
        for (Slider slider : slidersFor(which)) {
            lines.add(Text.plain(slider.label() + ": " + slider.format(slider.get(config)), NamedTextColor.GRAY));
        }
        lines.add(Component.empty());
        lines.add(Text.plain("Click to edit", NamedTextColor.YELLOW));
        return lines;
    }
    private static ItemStack button(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.plain(name, color));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.plain(line, NamedTextColor.GRAY));
        }
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }
    private static void glint(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
    }
    // -------------------------------------------------------------- clicks
    public void handleClick(Player player, int slot, boolean shift) {
        FileConfiguration config = plugin.getConfig();
        if (page == Page.MAIN) {
            if (slot == COIN_ICON_SLOT) openLater(player, Page.COIN);
            else if (slot == EYE_ICON_SLOT) openLater(player, Page.EYE);
            else if (slot == CLOCK_ICON_SLOT) openLater(player, Page.CLOCK);
            return;
        }
        if (slot == BACK_SLOT) {
            openLater(player, Page.MAIN);
            return;
        }
        if (slot == RESET_SLOT) {
            for (Slider slider : sliders()) {
                slider.set(config, slider.def());
            }
            changed(player);
            return;
        }
        Slider[] sliders = sliders();
        int row = slot / 9 - 1;
        if (row < 0 || row >= sliders.length) return;
        Slider slider = sliders[row];
        int column = slot % 9;
        int current = slider.get(config);
        int amount = shift ? slider.bigStep() : slider.step();
        Integer updated = null;
        if (column == 0) {
            updated = current - amount;
        } else if (column == 8) {
            updated = current + amount;
        } else {
            int idx = column - trackStart(slider);
            if (idx >= 0 && idx < slider.positions()) {
                updated = slider.valueAtPosition(idx);
            }
        }
        if (updated == null) return;
        slider.set(config, updated);
        changed(player);
    }
    private void changed(Player player) {
        plugin.saveConfig();
        render();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
    }
    private void openLater(Player player, Page target) {
        Bukkit.getScheduler().runTask(plugin, () -> new ConfigMenu(plugin, target).open(player));
    }
}
