package com.example.customlockitem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
/** Routes clicks inside the config menus. Nothing can be taken out of them. */
public final class MenuListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ConfigMenu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != top) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("customlockitems.admin")) {
            player.closeInventory();
            return;
        }
        menu.handleClick(player, event.getSlot(), event.isShiftClick());
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigMenu) {
            event.setCancelled(true);
        }
    }
}
