package com.example.customlockitem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
/**
 * The Timestop Clock field.
 *
 * Minecraft can't give one area its own tick rate, so this approximates it:
 *  - Mobs: their AI is switched on for only "tickRate" out of every 20 ticks.
 *  - Players: movement speed, attack speed and mining speed are scaled by tickRate / 20.
 * The user (and anyone else with an active timestop) is immune.
 */
public final class TimestopManager implements Listener {
    private static final int PARTICLE_INTERVAL = 4;
    private static final Attribute[] SLOWED = {
            Attribute.MOVEMENT_SPEED, Attribute.ATTACK_SPEED, Attribute.BLOCK_BREAK_SPEED
    };
    private static final class Affected {
        LivingEntity entity;
        boolean player;
        boolean managesAi;
        double accumulator;
        double appliedFactor = -1;
        Affected(LivingEntity entity) {
            this.entity = entity;
        }
    }
    private final CustomLockItem plugin;
    private final NamespacedKey slowKey;
    private final NamespacedKey frozenKey;
    /** user -> server tick when their timestop ends */
    private final Map<UUID, Long> sessions = new HashMap<>();
    private final Map<UUID, Affected> affected = new HashMap<>();
    private BukkitTask task;
    private long tick;
    public TimestopManager(CustomLockItem plugin) {
        this.plugin = plugin;
        this.slowKey = new NamespacedKey(plugin, "timestop_slow");
        this.frozenKey = new NamespacedKey(plugin, "timestop_frozen");
    }
    // ------------------------------------------------------------ lifecycle
    public void start() {
        // Un-freeze anything left frozen by a crash or a hard stop.
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                recover(living);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }
    public void shutdown() {
        if (task != null) task.cancel();
        for (Affected a : new ArrayList<>(affected.values())) {
            release(a);
        }
        affected.clear();
        sessions.clear();
    }
    /** Begins (or restarts) a timestop around this player. */
    public void begin(Player user, int durationTicks) {
        sessions.put(user.getUniqueId(), tick + durationTicks);
    }
    // ----------------------------------------------------------------- tick
    private void tick() {
        tick++;
        if (sessions.isEmpty() && affected.isEmpty()) return;
        // 1. End finished sessions.
        Iterator<Map.Entry<UUID, Long>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Player user = Bukkit.getPlayer(entry.getKey());
            boolean over = tick >= entry.getValue();
            if (user == null || !user.isOnline() || user.isDead() || over) {
                it.remove();
                if (user != null && user.isOnline() && over) {
                    user.sendActionBar(Component.text("Timestop ended.", NamedTextColor.GRAY));
                    user.playSound(user.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                }
            }
        }
        // 2. Find everyone inside a field and apply the slowdown.
        double radius = plugin.clockRadius();
        double radiusSq = radius * radius;
        double factor = Slider.CLOCK_TICK_RATE.get(plugin.getConfig()) / 20.0;
        Set<UUID> inField = new HashSet<>();
        for (UUID userId : sessions.keySet()) {
            Player user = Bukkit.getPlayer(userId);
            if (user == null) continue;
            Location center = user.getLocation().add(0, 1, 0);
            World world = center.getWorld();
            if (tick % PARTICLE_INTERVAL == 0) {
                drawField(world, center, radius);
            }
            for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand) continue;
                UUID id = entity.getUniqueId();
                if (sessions.containsKey(id)) continue; // timestop users are immune
                if (!living.isValid() || living.isDead()) continue;
                if (living instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
                Location middle = living.getBoundingBox().getCenter().toLocation(world);
                if (middle.distanceSquared(center) > radiusSq) continue;
                if (!inField.add(id)) continue; // already handled by another field this tick
                Affected a = affected.get(id);
                if (a == null) {
                    a = engage(living);
                    affected.put(id, a);
                } else {
                    a.entity = living; // the entity object changes if its chunk was reloaded
                }
                pulse(a, factor);
            }
        }
        // 3. Release anyone who left the field (or when the timestop ended).
        Iterator<Map.Entry<UUID, Affected>> ai = affected.entrySet().iterator();
        while (ai.hasNext()) {
            Map.Entry<UUID, Affected> entry = ai.next();
            if (!inField.contains(entry.getKey())) {
                release(entry.getValue());
                ai.remove();
            }
        }
    }
    // -------------------------------------------------------- slow / release
    private Affected engage(LivingEntity living) {
        Affected a = new Affected(living);
        if (living instanceof Player p) {
            a.player = true;
            p.sendActionBar(Component.text("You are caught in a Timestop!", NamedTextColor.YELLOW));
        } else if (living.hasAI()) {
            a.managesAi = true;
            living.getPersistentDataContainer().set(frozenKey, PersistentDataType.BYTE, (byte) 1);
        }
        return a;
    }
    private void pulse(Affected a, double factor) {
        LivingEntity entity = a.entity;
        if (a.player) {
            if (Math.abs(a.appliedFactor - factor) > 1e-9 && entity instanceof Player p) {
                applySlow(p, factor, a);
            }
        } else if (a.managesAi) {
            // Run the AI on "factor" of all ticks, spread out evenly.
            a.accumulator += factor;
            boolean run = a.accumulator >= 1.0;
            if (run) a.accumulator -= 1.0;
            entity.setAI(run);
        }
    }
    private void release(Affected a) {
        LivingEntity entity = a.entity;
        if (a.player) {
            if (entity instanceof Player p) clearSlow(p);
        } else if (a.managesAi && entity.isValid()) {
            entity.setAI(true);
            entity.getPersistentDataContainer().remove(frozenKey);
        }
    }
    private void applySlow(Player player, double factor, Affected a) {
        clearSlow(player);
        double amount = factor - 1.0; // e.g. -0.5 for half speed
        if (amount < 0) {
            for (Attribute attribute : SLOWED) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) continue;
                instance.addTransientModifier(new AttributeModifier(
                        slowKey, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
        a.appliedFactor = factor;
    }
    private void clearSlow(Player player) {
        for (Attribute attribute : SLOWED) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (modifier.getKey().equals(slowKey)) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }
    // ------------------------------------------------------------ particles
    /** A yellow wireframe sphere plus a ring on the ground, so players can see the edge. */
    private void drawField(World world, Location center, double radius) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.YELLOW, 1.0f);
        int[] latitudes = {-60, -30, 0, 30, 60};
        for (int degrees : latitudes) {
            double phi = Math.toRadians(degrees);
            double ringRadius = radius * Math.cos(phi);
            double y = center.getY() + radius * Math.sin(phi);
            ring(world, center.getX(), y, center.getZ(), ringRadius, dust);
        }
        // Where the sphere meets the floor under the user's feet.
        double groundRadius = Math.sqrt(Math.max(0, radius * radius - 1.0));
        ring(world, center.getX(), center.getY() - 0.85, center.getZ(), groundRadius, dust);
    }
    private void ring(World world, double cx, double y, double cz, double ringRadius, Particle.DustOptions dust) {
        int points = Math.max(12, (int) (ringRadius * 5));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticle(Particle.DUST,
                    cx + ringRadius * Math.cos(angle), y, cz + ringRadius * Math.sin(angle),
                    1, 0, 0, 0, 0, dust);
        }
    }
    // --------------------------------------------------------------- events
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        Affected a = affected.remove(id);
        if (a != null) release(a);
    }
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            recover(entity);
        }
    }
    /** Un-freezes a mob that was frozen when the server stopped or its chunk unloaded. */
    private void recover(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        if (!living.getPersistentDataContainer().has(frozenKey, PersistentDataType.BYTE)) return;
        if (affected.containsKey(living.getUniqueId())) return; // still under a live field
        living.setAI(true);
        living.getPersistentDataContainer().remove(frozenKey);
    }
}
