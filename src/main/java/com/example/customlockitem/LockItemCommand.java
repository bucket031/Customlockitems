package com.example.customlockitem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
/** /customlockitems give | config | reload | resetcooldown | help */
public final class LockItemCommand implements TabExecutor {
    private final CustomLockItem plugin;
    public LockItemCommand(CustomLockItem plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "config" -> config(sender);
            case "reload" -> reload(sender);
            case "resetcooldown" -> resetCooldown(sender, args);
            default -> help(sender, label);
        }
        return true;
    }
    // ----------------------------------------------------------------- give
    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "Usage: /customlockitems give <item> [player]  (items: " + itemNames() + ")", NamedTextColor.YELLOW);
            return;
        }
        ItemType type = ItemType.fromInput(args[1]);
        if (type == null) {
            msg(sender, "Unknown item '" + args[1] + "'. Items: " + itemNames(), NamedTextColor.RED);
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                msg(sender, "Player '" + args[2] + "' is not online.", NamedTextColor.RED);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            msg(sender, "Specify a player: /customlockitems give <item> <player>", NamedTextColor.RED);
            return;
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(plugin.items().create(type));
        leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        msg(sender, "Gave " + type.displayName() + " to " + target.getName() + ".", NamedTextColor.GREEN);
        if (!target.equals(sender)) {
            msg(target, "You received a " + type.displayName() + ".", NamedTextColor.GOLD);
        }
    }
    // --------------------------------------------------------------- config
    private void config(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "The config menu can only be opened in-game.", NamedTextColor.RED);
            return;
        }
        new ConfigMenu(plugin, ConfigMenu.Page.MAIN).open(player);
    }
    // --------------------------------------------------------------- reload
    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        msg(sender, "CustomLockItem config reloaded.", NamedTextColor.GREEN);
    }
    // ------------------------------------------------------- resetcooldown
    private void resetCooldown(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "Usage: /customlockitems resetcooldown <player> [item|all]", NamedTextColor.YELLOW);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "Player '" + args[1] + "' is not online.", NamedTextColor.RED);
            return;
        }
        if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
            for (ItemType type : ItemType.values()) {
                plugin.cooldowns().clear(target, type);
            }
            msg(sender, "Reset all cooldowns for " + target.getName() + ".", NamedTextColor.GREEN);
            return;
        }
        ItemType type = ItemType.fromInput(args[2]);
        if (type == null) {
            msg(sender, "Unknown item '" + args[2] + "'. Items: " + itemNames(), NamedTextColor.RED);
            return;
        }
        plugin.cooldowns().clear(target, type);
        msg(sender, "Reset the " + type.displayName() + " cooldown for " + target.getName() + ".", NamedTextColor.GREEN);
    }
    // ----------------------------------------------------------------- help
    private void help(CommandSender sender, String label) {
        msg(sender, "CustomLockItem commands:", NamedTextColor.GOLD);
        msg(sender, "/" + label + " give <item> [player]  - give a custom item", NamedTextColor.WHITE);
        msg(sender, "/" + label + " config  - open the settings menu", NamedTextColor.WHITE);
        msg(sender, "/" + label + " reload  - reload config.yml", NamedTextColor.WHITE);
        msg(sender, "/" + label + " resetcooldown <player> [item|all]  - clear cooldowns", NamedTextColor.WHITE);
        msg(sender, "Items: " + itemNames(), NamedTextColor.GRAY);
    }
    private static String itemNames() {
        List<String> names = new ArrayList<>();
        for (ItemType type : ItemType.values()) {
            names.add(type.id());
        }
        return String.join(", ", names);
    }
    private static void msg(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }
    // --------------------------------------------------------- tab complete
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "config", "reload", "resetcooldown", "help"));
        } else if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                for (ItemType type : ItemType.values()) out.add(type.id());
            } else if (args.length == 3) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        } else if (args[0].equalsIgnoreCase("resetcooldown")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 3) {
                out.add("all");
                for (ItemType type : ItemType.values()) out.add(type.id());
            }
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }
}
