package com.example.customlockitem;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
/** Handles using the items, and keeps them from being placed, crafted away or destroyed. */
public final class ItemListener implements Listener {
    private final CustomLockItem plugin;
    public ItemListener(CustomLockItem plugin) {
        this.plugin = plugin;
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemType type = plugin.items().identify(event.getItem());
        if (type == null) return;
        // Stops the sunflower from being planted, and stops chests etc. opening while holding an item.
        event.setCancelled(true);
        plugin.use(event.getPlayer(), type);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.items().identify(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (plugin.items().identify(ingredient) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
    /** "Unbreakable": dropped copies survive fire, lava, cactus and explosions (but not the void). */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        if (event.getEntity() instanceof Item dropped && plugin.items().identify(dropped.getItemStack()) != null) {
            event.setCancelled(true);
        }
    }
}
