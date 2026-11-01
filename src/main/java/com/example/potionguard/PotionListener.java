package com.example.potionguard;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Applies the rules whenever a potion tries to give an effect. */
public final class PotionListener implements Listener {

    private static final String BYPASS = "potionguard.bypass";

    private final PotionGuard plugin;

    public PotionListener(PotionGuard plugin) {
        this.plugin = plugin;
    }

    private Rules rules() {
        return plugin.rules();
    }

    private static boolean bypass(Entity entity) {
        return entity instanceof Player player && player.hasPermission(BYPASS);
    }

    // ------------------------------------------------- normal timed effects

    /** Covers drinking, splash potions, lingering clouds and tipped arrows. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEffect(EntityPotionEffectEvent event) {
        Rules.Source source = sourceOf(event.getCause());
        if (source == null || !rules().applies(source)) return;

        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.ADDED
                && action != EntityPotionEffectEvent.Action.CHANGED) return;

        PotionEffect incoming = event.getNewEffect();
        if (incoming == null) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (bypass(entity)) return;

        PotionEffect adjusted = rules().adjust(incoming);
        if (adjusted == incoming) return;

        event.setCancelled(true);
        if (adjusted == null) return;

        PotionEffect old = event.getOldEffect();
        if (old != null && !stronger(adjusted, old)) return;

        // Re-apply the weaker version next tick (effects added by a plugin are not touched again).
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) entity.addPotionEffect(adjusted);
        });
    }

    private static Rules.Source sourceOf(EntityPotionEffectEvent.Cause cause) {
        return switch (cause) {
            case POTION_DRINK -> Rules.Source.DRINKING;
            case POTION_SPLASH -> Rules.Source.SPLASHED;
            case AREA_EFFECT_CLOUD -> Rules.Source.LINGERING;
            case ARROW -> Rules.Source.ARROWS;
            default -> null;
        };
    }

    private static boolean stronger(PotionEffect candidate, PotionEffect old) {
        if (candidate.getAmplifier() != old.getAmplifier()) {
            return candidate.getAmplifier() > old.getAmplifier();
        }
        if (candidate.getDuration() < 0) return old.getDuration() >= 0;
        if (old.getDuration() < 0) return false;
        return candidate.getDuration() > old.getDuration();
    }

    // ------------------------------------------------------ splash potions

    /** Instant effects (healing / harming) never reach the event above, so scale them here. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSplash(PotionSplashEvent event) {
        if (!rules().applies(Rules.Source.SPLASHED)) return;

        double scale = potionScale(event.getPotion().getEffects());
        if (scale >= 1.0) return;

        for (LivingEntity entity : new ArrayList<>(event.getAffectedEntities())) {
            if (bypass(entity)) continue;
            event.setIntensity(entity, event.getIntensity(entity) * scale);
        }
    }

    /** 0 if every effect is banned, the weakest instant scale if all are instant, otherwise 1. */
    private double potionScale(Collection<PotionEffect> effects) {
        if (effects.isEmpty()) return 1.0;

        boolean allBanned = true;
        boolean allInstant = true;
        double weakest = 1.0;

        for (PotionEffect effect : effects) {
            PotionEffectType type = effect.getType();
            if (rules().mode(type) != Mode.BANNED) allBanned = false;
            if (!type.isInstant()) allInstant = false;
            weakest = Math.min(weakest, rules().instantScale(type, effect.getAmplifier()));
        }

        if (allBanned) return 0.0;
        return allInstant ? weakest : 1.0;
    }

    // ----------------------------------------------------- lingering clouds

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        if (!rules().applies(Rules.Source.LINGERING)) return;

        AreaEffectCloud cloud = event.getEntity();
        List<PotionEffect> effects = collect(cloud.getBasePotionType(), cloud.getCustomEffects());
        if (effects.isEmpty()) return;

        for (PotionEffect effect : effects) {
            if (rules().mode(effect.getType()) != Mode.BANNED) return;
        }

        // Everything in this cloud is banned: nobody (without bypass) is affected.
        event.getAffectedEntities().removeIf(entity -> !bypass(entity));
    }

    // ------------------------------------------------------ drinking potions

    /** Instant potions (healing / harming) are used up by hand so they can be weakened or blocked. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (!rules().applies(Rules.Source.DRINKING)) return;

        Player player = event.getPlayer();
        if (bypass(player)) return;

        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION) return;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;

        List<PotionEffect> effects = collect(meta.getBasePotionType(), meta.getCustomEffects());
        if (effects.isEmpty()) return;

        List<PotionEffect> result = new ArrayList<>();
        boolean changed = false;
        for (PotionEffect effect : effects) {
            if (!effect.getType().isInstant()) return; // timed effects are handled by onEffect
            PotionEffect adjusted = rules().adjust(effect);
            if (adjusted != effect) changed = true;
            if (adjusted != null) result.add(adjusted);
        }
        if (!changed) return;

        event.setCancelled(true);

        EquipmentSlot hand = event.getHand();
        ItemStack snapshot = item.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            ItemStack held = player.getInventory().getItem(hand);
            if (held == null || !held.isSimilar(snapshot)) return;

            if (player.getGameMode() != GameMode.CREATIVE) {
                if (held.getAmount() > 1) {
                    held.setAmount(held.getAmount() - 1);
                    player.getInventory().setItem(hand, held);
                    Map<Integer, ItemStack> left = player.getInventory()
                            .addItem(new ItemStack(Material.GLASS_BOTTLE));
                    left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                } else {
                    player.getInventory().setItem(hand, new ItemStack(Material.GLASS_BOTTLE));
                }
            }

            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);
            for (PotionEffect effect : result) {
                player.addPotionEffect(effect);
            }
        });
    }

    // --------------------------------------------------------------- helpers

    private static List<PotionEffect> collect(PotionType base, List<PotionEffect> custom) {
        List<PotionEffect> list = new ArrayList<>();
        if (base != null) list.addAll(base.getPotionEffects());
        list.addAll(custom);
        return list;
    }
}
