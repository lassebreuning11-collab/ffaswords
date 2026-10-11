package de.ffa.swords;

import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FFASwords extends JavaPlugin implements Listener, TabExecutor {

    // ===================== EINSTELLUNGEN =====================
    private static final double DASH_DISTANCE      = 18.0;
    private static final long   DASH_COOLDOWN_MS   = 3000;

    private static final double STRIKE_CHANCE      = 0.20;
    private static final double STRIKE_DAMAGE      = 2.0;

    private static final double LIFESTEAL_CHANCE   = 0.10;
    private static final double LIFESTEAL_DAMAGE   = 6.0;
    private static final double LIFESTEAL_HEAL     = 6.0;

    private static final double POISON_CHANCE      = 0.30;
    private static final int    POISON_TICKS       = 20 * 20;

    private static final double SONIC_RANGE        = 15.0;
    private static final double SONIC_DAMAGE       = 10.0;
    private static final long   SONIC_COOLDOWN_MS  = 5000;
    // =========================================================

    public enum SwordType {
        DASH("dash", Material.NETHERITE_SWORD, ChatColor.AQUA + "" + ChatColor.BOLD + "Dash Sword",
                "Shift + Rechtsklick: Dash (18 Blöcke)"),
        STRIKE("strike", Material.NETHERITE_SWORD, ChatColor.YELLOW + "" + ChatColor.BOLD + "Strike Sword",
                "20% Chance auf Blitz (1 Herz Schaden)"),
        LIFESTEAL("lifesteal", Material.NETHERITE_SWORD, ChatColor.RED + "" + ChatColor.BOLD + "Lifesteal Sword",
                "10% Chance: 3 Herzen Schaden & du heilst 3 Herzen"),
        POISON("poison", Material.NETHERITE_SWORD, ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Poison Sword",
                "30% Chance auf Gift (20 Sek.)"),
        WARDEN("warden", Material.NETHERITE_SWORD, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Warden Sword",
                "Shift + Rechtsklick: Sonic Boom");

        final String id;
        final Material material;
        final String displayName;
        final String lore;

        SwordType(String id, Material material, String displayName, String lore) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
        }

        static SwordType byId(String id) {
            for (SwordType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
            return null;
        }
    }

    private NamespacedKey swordKey;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Set<UUID> internalDamage = new HashSet<>();

    @Override
    public void onEnable() {
        swordKey = new NamespacedKey(this, "ffa_sword");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ffaswords").setExecutor(this);
        getCommand("ffaswords").setTabCompleter(this);
        getLogger().info("FFASwords aktiviert!");
    }

    // ===================== ITEMS =====================

    private ItemStack createSword(SwordType type) {
        ItemStack item = new ItemStack(type.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.displayName);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + type.lore);
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.STRING, type.id);
        item.setItemMeta(meta);
        return item;
    }

    private SwordType getType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(swordKey, PersistentDataType.STRING);
        return id == null ? null : SwordType.byId(id);
    }

    // ===================== COMMAND =====================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.RED + "Benutzung: /" + label + " give <dash|strike|lifesteal|poison|warden|all> [spieler]");
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + "Gib einen Spieler an.");
            return true;
        }

        if (args[1].equalsIgnoreCase("all")) {
            for (SwordType t : SwordType.values()) target.getInventory().addItem(createSword(t));
            sender.sendMessage(ChatColor.GREEN + "Alle Swords an " + target.getName() + " gegeben.");
            return true;
        }

        SwordType type = SwordType.byId(args[1]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Unbekanntes Sword.");
            return true;
        }
        target.getInventory().addItem(createSword(type));
        sender.sendMessage(ChatColor.GREEN + type.id + " an " + target.getName() + " gegeben.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("give");
        } else if (args.length == 2) {
            for (SwordType t : SwordType.values()) out.add(t.id);
            out.add("all");
        } else if (args.length == 3) {
            for (Player p : getServer().getOnlinePlayers()) out.add(p.getName());
        }
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }

    // ===================== TREFFER-EFFEKTE =====================

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (internalDamage.contains(victim.getUniqueId())) return;

        SwordType type = getType(attacker.getInventory().getItemInMainHand());
        if (type == null) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        switch (type) {
            case STRIKE -> {
                if (rnd.nextDouble() < STRIKE_CHANCE) {
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    trueDamage(attacker, victim, STRIKE_DAMAGE);
                }
            }
            case LIFESTEAL -> {
                if (rnd.nextDouble() < LIFESTEAL_CHANCE) {
                    trueDamage(attacker, victim, LIFESTEAL_DAMAGE);
                    double newHealth = Math.min(attacker.getMaxHealth(), attacker.getHealth() + LIFESTEAL_HEAL);
                    attacker.setHealth(newHealth);
                    Location loc = victim.getLocation().add(0, 1, 0);
                    victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 15, 0.3, 0.5, 0.3, 0.1);
                    attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0), 8, 0.4, 0.3, 0.4, 0);
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
                }
            }
            case POISON -> {
                if (rnd.nextDouble() < POISON_CHANCE) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, POISON_TICKS, 0));
                    victim.getWorld().spawnParticle(Particle.SNEEZE, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                }
            }
            default -> { }
        }
    }

    private void trueDamage(Player attacker, LivingEntity victim, double amount) {
        double newHealth = victim.getHealth() - amount;
        if (newHealth <= 0) {
            internalDamage.add(victim.getUniqueId());
            try {
                victim.damage(10000.0, attacker);
            } finally {
                internalDamage.remove(victim.getUniqueId());
            }
        } else {
            victim.setHealth(newHealth);
            victim.playEffect(EntityEffect.HURT);
        }
    }

    // ===================== SHIFT + RECHTSKLICK =====================

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!p.isSneaking()) return;

        SwordType type = getType(p.getInventory().getItemInMainHand());
        if (type == null) return;

        if (type == SwordType.DASH) {
            e.setCancelled(true);
            if (!checkCooldown(p, type, DASH_COOLDOWN_MS)) return;
            dash(p);
        } else if (type == SwordType.WARDEN) {
            e.setCancelled(true);
            if (!checkCooldown(p, type, SONIC_COOLDOWN_MS)) return;
            sonicBoom(p);
        }
    }

    private boolean checkCooldown(Player p, SwordType type, long cooldownMs) {
        String key = p.getUniqueId() + ":" + type.id;
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(key, 0L);
        if (now - last < cooldownMs) {
            double left = (cooldownMs - (now - last)) / 1000.0;
            p.sendMessage(ChatColor.RED + String.format("Cooldown: %.1fs", left));
            return false;
        }
        cooldowns.put(key, now);
        return true;
    }

    // ===================== DASH =====================

    private void dash(Player p) {
        World w = p.getWorld();
        Location start = p.getLocation();
        Vector dir = start.getDirection().normalize();

        final double step = 0.5;
        Location last = start.clone();

        for (double d = step; d <= DASH_DISTANCE; d += step) {
            Location next = start.clone().add(dir.clone().multiply(d));
            if (!next.getBlock().isPassable()) break;
            if (!next.clone().add(0, 1, 0).getBlock().isPassable()) break;
            last = next;
            w.spawnParticle(Particle.CLOUD, next.clone().add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0.01);
        }

        w.playSound(start, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.5f);
        p.teleport(last);
        p.setFallDistance(0);
        w.playSound(last, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
    }

    // ===================== SONIC BOOM =====================

    private void sonicBoom(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Set<UUID> alreadyHit = new HashSet<>();

        w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1f);

        for (double d = 1.0; d <= SONIC_RANGE; d += 1.0) {
            Location loc = eye.clone().add(dir.clone().multiply(d));
            if (!loc.getBlock().isPassable()) break;

            w.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);

            for (Entity ent : w.getNearbyEntities(loc, 1.0, 1.0, 1.0)) {
                if (ent.equals(p)) continue;
                if (!(ent instanceof LivingEntity le) || ent instanceof ArmorStand) continue;
                if (!alreadyHit.add(ent.getUniqueId())) continue;

                trueDamage(p, le, SONIC_DAMAGE);
                Vector kb = dir.clone().multiply(1.2);
                kb.setY(0.4);
                le.setVelocity(kb);
            }
        }
    }
}
