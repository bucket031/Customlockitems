package com.example.customlockitem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;
/** Creates the custom items and recognises them again later. */
public final class Items {
    private final NamespacedKey itemKey;
    public Items(JavaPlugin plugin) {
        this.itemKey = new NamespacedKey(plugin, "custom_item");
    }
    /** The real, usable item. */
    public ItemStack create(ItemType type) {
        ItemStack stack = new ItemStack(type.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.bold(type.displayName(), type.color()));
        List<Component> lore = new ArrayList<>();
        for (String line : type.lore()) {
            lore.add(Text.plain(line, NamedTextColor.GRAY));
        }
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.id());
        stack.setItemMeta(meta);
        return stack;
    }
    /** A look-alike for menus. It is NOT tagged, so it can never be used as the real item. */
    public ItemStack icon(ItemType type, List<Component> extraLore) {
        ItemStack stack = new ItemStack(type.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.bold(type.displayName(), type.color()));
        meta.lore(extraLore);
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
        return stack;
    }
    /** Returns which custom item this is, or null if it is an ordinary item. */
    public ItemType identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return id == null ? null : ItemType.fromInput(id);
    }
}
