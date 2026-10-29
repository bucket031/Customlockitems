package com.example.customlockitem;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Wisps: players turned into a spirit with /cli wisp set <name> abyss.
 *
 * A spirit can't be seen by other players. Instead, a small orb of black and gray particles
 * floats where they stand. When a spirit talks, nobody sees a normal chat line: the words appear
 * as a purple gradient above the hotbar. Normal chat is shown to everyone, /msg only to the person named.
 */
public final class WispManager implements Listener {
    private static final List<String> MSG_COMMANDS = List.of("msg", "tell", "w", "whisper");
    private static final double VIEW_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final TextColor PURPLE = TextColor.color(0x9B30FF);
    private static final TextColor LIGHT_PURPLE = TextColor.color(0xE0B0FF);
    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(8, 8, 8), 1.3f);
    private static final Particle.DustOptions GRAY = new Particle.DustOptions(Color.fromRGB(125, 125, 125), 1.0f);
    private final CustomLockItem plugin;
    private final NamespacedKey wispKey;
    /** online players who are currently a wisp */
    private final Map<UUID, WispType> active = new ConcurrentHashMap<>(); // read by the async chat thread
    /** the repeating "keep the words on screen" task for each viewer */
    private final Map<UUID, BukkitTask> speech = new HashMap<>();
    private BukkitTask task;
    private int tick;
    public WispManager(CustomLockItem plugin) {
        this.plugin = plugin;
        this.wispKey = new NamespacedKey(plugin, "wisp");
    }
    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            WispType type = storedType(player);
            if (type != null) active.put(player.getUniqueId(), type);
        }
        hideEveryone();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::run, 2L, 2L);
    }
    public void shutdown() {
        if (task != null) task.cancel();
        speech.values().forEach(BukkitTask::cancel);
        speech.clear();
        for (UUID id : active.keySet()) {
            Player spirit = Bukkit.getPlayer(id);
            if (spirit != null) revealToAll(spirit);
        }
        active.clear();
    }
    // ------------------------------------------------------------ set / clear
    public WispType typeOf(Player player) {
        return active.get(player.getUniqueId());
    }
    public void set(Player player, WispType type) {
        player.getPersistentDataContainer().set(wispKey, PersistentDataType.STRING, type.id());
        active.put(player.getUniqueId(), type);
        hideFromAll(player);
        player.sendActionBar(Component.text("You are now the " + type.displayName() + ".",
                NamedTextColor.YELLOW));
    }
    public boolean clear(Player player) {
        boolean was = active.remove(player.getUniqueId()) != null;
        player.getPersistentDataContainer().remove(wispKey);
        revealToAll(player);
        return was;
    }
    public List<String> names() {
        return active.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null)
                .map(p -> p.getName() + " (" + active.get(p.getUniqueId()).id() + ")")
                .toList();
    }
    private WispType storedType(Player player) {
        String stored = player.getPersistentDataContainer().get(wispKey, PersistentDataType.STRING);
        return WispType.fromInput(stored);
    }
    // ------------------------------------------------------- hiding the body
    private void hideFromAll(Player spirit) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(spirit)) viewer.hideEntity(plugin, spirit);
        }
    }
    private void revealToAll(Player spirit) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(spirit)) viewer.showEntity(plugin, spirit);
        }
    }
    private void hideEveryone() {
        for (UUID id : active.keySet()) {
            Player spirit = Bukkit.getPlayer(id);
            if (spirit != null) hideFromAll(spirit);
        }
    }
    // ------------------------------------------------------------- the orb
    private void run() {
        tick++;
        // Once a second, make sure nobody can see a spirit's body (covers joins, respawns, world changes).
        if (tick % 10 == 0) hideEveryone();
        double radius = orbRadius();
        double bob = Math.sin(tick * 0.15) * 0.12;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (UUID id : active.keySet()) {
            Player spirit = Bukkit.getPlayer(id);
            if (spirit == null || spirit.isDead()) continue;
            Location base = spirit.getLocation();
            double cx = base.getX();
            double cy = base.getY() + 1.0 + bob;
            double cz = base.getZ();
            for (Player viewer : base.getWorld().getPlayers()) {
                if (viewer.equals(spirit)) continue; // the spirit doesn't see its own orb
                if (viewer.getLocation().distanceSquared(base) > VIEW_DISTANCE_SQUARED) continue;
                for (int i = 0; i < 14; i++) {
                    double u = random.nextDouble() * 2.0 - 1.0;
                    double angle = random.nextDouble() * 2.0 * Math.PI;
                    double r = radius * (0.5 + 0.5 * random.nextDouble());
                    double s = Math.sqrt(1.0 - u * u);
                    viewer.spawnParticle(Particle.DUST,
                            cx + r * s * Math.cos(angle), cy + r * u, cz + r * s * Math.sin(angle),
                            1, 0, 0, 0, 0, i % 2 == 0 ? BLACK : GRAY);
                }
            }
        }
    }
    /** Size of the orb in blocks (file-only setting). */
    private double orbRadius() {
        double value = plugin.getConfig().getDouble("abyss.orb_radius", 0.5);
        return Math.max(0.2, Math.min(2.0, value));
    }
    // ---------------------------------------------------------------- talking
    /** Normal chat from a spirit: cancelled, then shown above everyone's hotbar. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player speaker = event.getPlayer();
        WispType type = active.get(speaker.getUniqueId());
        if (type == null) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLogger().info("[" + type.displayName() + "] " + text);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                show(viewer, text);
            }
        });
    }
    /** /msg, /tell, /w and /whisper from a spirit: shown only to the person named. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player speaker = event.getPlayer();
        WispType type = active.get(speaker.getUniqueId());
        if (type == null) return;
        String[] parts = event.getMessage().trim().split("\\s+", 3);
        String label = parts[0].substring(1).toLowerCase(Locale.ROOT);
        if (label.startsWith("minecraft:")) label = label.substring("minecraft:".length());
        if (!MSG_COMMANDS.contains(label) || parts.length < 3) return; // too short: normal usage message
        event.setCancelled(true);
        Player target = Bukkit.getPlayerExact(parts[1]);
        if (target == null) {
            speaker.sendMessage(Component.text("No player named '" + parts[1] + "' is online.", NamedTextColor.RED));
            return;
        }
        String text = parts[2];
        plugin.getLogger().info("[" + type.displayName() + " -> " + target.getName() + "] " + text);
        show(target, text);
        speaker.sendMessage(Component.text("You whisper to " + target.getName() + ": " + text,
                NamedTextColor.GRAY, TextDecoration.ITALIC));
    }
    /** Colors the text letter by letter, from purple to light purple. */
    private static Component gradient(String text) {
        int[] letters = text.codePoints().toArray();
        Component out = Component.empty();
        for (int i = 0; i < letters.length; i++) {
            float t = letters.length <= 1 ? 0f : i / (float) (letters.length - 1);
            out = out.append(Component.text(new String(Character.toChars(letters[i])),
                    TextColor.lerp(t, PURPLE, LIGHT_PURPLE)));
        }
        return out;
    }
    /**
     * Shows text above the hotbar as a purple to light purple gradient. Action bar text fades after about 3 seconds,
     * so longer messages are sent again until they have been up long enough to read.
     */
    private void show(Player viewer, String text) {
        UUID id = viewer.getUniqueId();
        BukkitTask old = speech.remove(id);
        if (old != null) old.cancel();
        Component line = gradient(text);
        viewer.sendActionBar(line);
        int total = Math.max(60, Math.min(240, 60 + text.length() * 3));
        if (total <= 50) return;
        BukkitTask repeat = new BukkitRunnable() {
            int left = total;
            @Override
            public void run() {
                left -= 30;
                if (left <= 0 || !viewer.isOnline()) {
                    cancel();
                    speech.remove(id);
                    return;
                }
                viewer.sendActionBar(line);
            }
        }.runTaskTimer(plugin, 30L, 30L);
        speech.put(id, repeat);
    }
    // ------------------------------------------------------------ join / quit
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        WispType type = storedType(player);
        if (type != null) active.put(player.getUniqueId(), type);
        // Hide every spirit from the player who just joined, and hide this player if they are a spirit.
        Bukkit.getScheduler().runTask(plugin, this::hideEveryone);
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        active.remove(id);
        BukkitTask old = speech.remove(id);
        if (old != null) old.cancel();
    }
}
