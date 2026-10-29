package com.example.customlockitem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
/**
 * The Eye of Exponential End.
 *
 * Needs a Totem of Undying in the off hand to be thrown (the blast is that strong).
 * When it lands, everything alive within the radius takes lethal damage (a Totem of Undying
 * still pops), and every player's worn armor loses durability, the thrower's included.
 * The eye is used up when thrown.
 */
public final class ExponentialEye implements Listener {
    private static final double LETHAL_DAMAGE = 1_000_000.0;
    private final CustomLockItem plugin;
    private final NamespacedKey thrownKey;
    public ExponentialEye(CustomLockItem plugin) {
        this.plugin = plugin;
        this.thrownKey = new NamespacedKey(plugin, "exponential_eye_thrown");
    }
    // ---------------------------------------------------------------- throw
    public void throwEye(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (plugin.items().identify(inventory.getItemInMainHand()) != ItemType.EXPONENTIAL_EYE) {
            player.sendActionBar(Component.text(
                    "Hold the Eye in your main hand.", NamedTextColor.RED));
            return;
        }
        if (inventory.getItemInOffHand().getType() != Material.TOTEM_OF_UNDYING) {
            player.sendActionBar(Component.text(
                    "The blast is too strong. Hold a Totem of Undying in your off hand first.",
                    NamedTextColor.RED));
            return;
        }
        Vector velocity = player.getEyeLocation().getDirection().multiply(1.3);
        Snowball eye = player.launchProjectile(Snowball.class, velocity);
        if (!eye.isValid()) {
            return; // another plugin cancelled the throw, so the eye is not used up
        }
        eye.setItem(new ItemStack(Material.ENDER_EYE));
        eye.getPersistentDataContainer().set(thrownKey, PersistentDataType.BYTE, (byte) 1);
        inventory.setItemInMainHand(new ItemStack(Material.AIR));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_LAUNCH, 1.0f, 0.6f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!eye.isValid()) {
                    cancel();
                    return;
                }
                eye.getWorld().spawnParticle(Particle.PORTAL, eye.getLocation(), 8, 0.15, 0.15, 0.15, 0.4);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
    // --------------------------------------------------------------- impact
    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball eye)) return;
        if (!eye.getPersistentDataContainer().has(thrownKey, PersistentDataType.BYTE)) return;
        Player thrower = eye.getShooter() instanceof Player p ? p : null;
        Location center = eye.getLocation();
        eye.remove();
        detonate(center, thrower);
    }
    private void detonate(Location center, Player thrower) {
        World world = center.getWorld();
        if (world == null) return;
        double radius = Slider.EYE_RADIUS.get(plugin.getConfig());
        int armorLoss = Slider.EYE_ARMOR_DAMAGE.get(plugin.getConfig());
        // Effects only. The explosion itself never breaks blocks.
        world.createExplosion(center, 0f, false, false);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 10, 3.0, 1.5, 3.0, 0);
        shockwave(center, radius);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand) continue;
            if (living.getLocation().distanceSquared(center) > radius * radius) continue;
            if (living instanceof Player player) {
                if (player.getGameMode() == GameMode.SPECTATOR) continue;
                wearArmor(player, armorLoss);
            }
            living.setNoDamageTicks(0);
            if (living.equals(thrower) || thrower == null) {
                living.damage(LETHAL_DAMAGE);
            } else {
                living.damage(LETHAL_DAMAGE, thrower);
            }
        }
    }
    /** Removes durability from every worn armor piece. A piece that runs out breaks. */
    private void wearArmor(Player player, int loss) {
        if (loss <= 0) return;
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean broke = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType().isAir()) continue;
            ItemMeta meta = piece.getItemMeta();
            if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) continue;
            int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : piece.getType().getMaxDurability();
            if (max <= 0) continue;
            int damage = damageable.getDamage() + loss;
            if (damage >= max) {
                armor[i] = null;
                broke = true;
            } else {
                damageable.setDamage(damage);
                piece.setItemMeta(meta);
            }
        }
        player.getInventory().setArmorContents(armor);
        if (broke) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
    }
    /** A ring that sweeps out to the edge of the blast so the radius is visible. */
    private void shockwave(Location center, double radius) {
        World world = center.getWorld();
        int steps = 10;
        new BukkitRunnable() {
            int step = 1;
            @Override
            public void run() {
                double r = radius * step / steps;
                int points = (int) Math.max(24, r * 6);
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    Location at = center.clone().add(Math.cos(angle) * r, 0.5, Math.sin(angle) * r);
                    world.spawnParticle(Particle.END_ROD, at, 1, 0, 0, 0, 0);
                }
                if (++step > steps) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
