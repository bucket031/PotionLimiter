package com.example.potionguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The config menu.
 * List page: every effect. Left-click cycles Allowed / Limited / Banned, right-click edits limits.
 * Detail page: pick a mode and set the max level and how much duration is kept.
 */
public final class PotionMenu implements InventoryHolder {

    private static final int PER_PAGE = 45;

    // List page bottom row
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int FIRST_TOGGLE_SLOT = 46;
    private static final int HELP_SLOT = 51;

    // Detail page layout
    private static final int D_ICON = 4;
    private static final int D_ALLOWED = 10;
    private static final int D_LIMITED = 11;
    private static final int D_BANNED = 12;
    private static final int D_LEVEL_DOWN = 19;
    private static final int D_LEVEL_SHOW = 20;
    private static final int D_LEVEL_UP = 21;
    private static final int D_TIME_DOWN = 28;
    private static final int D_TIME_SHOW = 29;
    private static final int D_TIME_UP = 30;
    private static final int D_BACK = 35;

    private final PotionGuard plugin;
    private final PotionEffectType detail; // null = list page
    private int page;
    private Inventory inventory;

    public PotionMenu(PotionGuard plugin, PotionEffectType detail, int page) {
        this.plugin = plugin;
        this.detail = detail;
        this.page = page;
    }

    public static PotionMenu list(PotionGuard plugin) {
        return new PotionMenu(plugin, null, 0);
    }

    private Rules rules() {
        return plugin.rules();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        if (detail == null) {
            inventory = Bukkit.createInventory(this, 54, Component.text("PotionGuard"));
        } else {
            inventory = Bukkit.createInventory(this, 36,
                    Component.text(Text.title(Rules.key(detail))));
        }
        refresh();
        player.openInventory(inventory);
    }

    private void refresh() {
        inventory.clear();
        if (detail == null) drawList();
        else drawDetail();
    }

    // ================================================================ list page

    private int pageCount() {
        return Math.max(1, (rules().all().size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void drawList() {
        List<PotionEffectType> all = rules().all();
        page = Math.max(0, Math.min(page, pageCount() - 1));

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= all.size()) break;
            inventory.setItem(i, effectIcon(all.get(index)));
        }

        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler());
        }

        if (page > 0) {
            inventory.setItem(PREV_SLOT, button(Material.ARROW, "Previous page", NamedTextColor.WHITE));
        }
        if (page < pageCount() - 1) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "Next page", NamedTextColor.WHITE));
        }

        Rules.Source[] sources = Rules.Source.values();
        for (int i = 0; i < sources.length; i++) {
            Rules.Source source = sources[i];
            boolean on = rules().applies(source);
            ItemStack item = new ItemStack(source.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.plain(source.label() + ": " + (on ? "rules apply" : "rules OFF"),
                    on ? NamedTextColor.GREEN : NamedTextColor.RED));
            meta.lore(List.of(Text.plain("Click to turn the rules "
                    + (on ? "off" : "on") + " for this.", NamedTextColor.GRAY)));
            hideExtras(meta);
            item.setItemMeta(meta);
            inventory.setItem(FIRST_TOGGLE_SLOT + i, item);
        }

        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta helpMeta = help.getItemMeta();
        helpMeta.displayName(Text.plain("How to use", NamedTextColor.GOLD));
        helpMeta.lore(List.of(
                Text.plain("Left-click an effect: Allowed > Limited > Banned", NamedTextColor.GRAY),
                Text.plain("Right-click an effect: set the limits", NamedTextColor.GRAY),
                Text.plain("Page " + (page + 1) + " of " + pageCount(), NamedTextColor.DARK_GRAY)));
        help.setItemMeta(helpMeta);
        inventory.setItem(HELP_SLOT, help);
    }

    private ItemStack effectIcon(PotionEffectType type) {
        Mode mode = rules().mode(type);
        NamedTextColor color = modeColor(mode);

        ItemStack item = new ItemStack(mode == Mode.BANNED ? Material.BARRIER : Material.POTION);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.plain(Text.title(Rules.key(type)), color));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Text.plain("Mode: " + mode.label(), color));
        if (mode == Mode.LIMITED) {
            lore.add(Text.plain("Max level: " + Text.roman(rules().maxLevel(type)), NamedTextColor.GRAY));
            if (!type.isInstant()) {
                lore.add(Text.plain("Duration kept: " + rules().durationPercent(type) + "%",
                        NamedTextColor.GRAY));
            }
        }
        lore.add(Component.empty());
        lore.add(Text.plain("Left-click: change mode", NamedTextColor.DARK_GRAY));
        lore.add(Text.plain("Right-click: edit limits", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(type.getColor());
        }
        if (mode == Mode.LIMITED) {
            meta.setEnchantmentGlintOverride(true);
        }
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    // ============================================================== detail page

    private void drawDetail() {
        PotionEffectType type = detail;
        Mode mode = rules().mode(type);

        inventory.setItem(D_ICON, effectIcon(type));

        inventory.setItem(D_ALLOWED, modeButton(Material.LIME_WOOL, Mode.ALLOWED, mode,
                "The effect works normally."));
        inventory.setItem(D_LIMITED, modeButton(Material.YELLOW_WOOL, Mode.LIMITED, mode,
                "A lower level and a shorter duration."));
        inventory.setItem(D_BANNED, modeButton(Material.RED_WOOL, Mode.BANNED, mode,
                "The effect is never given."));

        int level = rules().maxLevel(type);
        inventory.setItem(D_LEVEL_DOWN, button(Material.RED_STAINED_GLASS_PANE,
                "Lower max level", NamedTextColor.RED, "Shift-click: 5 at a time"));
        inventory.setItem(D_LEVEL_SHOW, info(Material.EXPERIENCE_BOTTLE,
                "Max level: " + Text.roman(level),
                "The highest level a potion can give.",
                "Level I is the weakest."));
        inventory.setItem(D_LEVEL_UP, button(Material.LIME_STAINED_GLASS_PANE,
                "Raise max level", NamedTextColor.GREEN, "Shift-click: 5 at a time"));

        if (type.isInstant()) {
            inventory.setItem(D_TIME_SHOW, info(Material.CLOCK,
                    "Duration: not used",
                    "This is an instant effect,",
                    "so it has no duration."));
        } else {
            int percent = rules().durationPercent(type);
            inventory.setItem(D_TIME_DOWN, button(Material.RED_STAINED_GLASS_PANE,
                    "Shorter duration", NamedTextColor.RED, "10% at a time"));
            inventory.setItem(D_TIME_SHOW, info(Material.CLOCK,
                    "Duration kept: " + percent + "%",
                    "How much of the normal duration",
                    "is left after the limit."));
            inventory.setItem(D_TIME_UP, button(Material.LIME_STAINED_GLASS_PANE,
                    "Longer duration", NamedTextColor.GREEN, "10% at a time"));
        }

        inventory.setItem(D_BACK, button(Material.ARROW, "Back to the list", NamedTextColor.WHITE));
    }

    private ItemStack modeButton(Material material, Mode mode, Mode current, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.plain(mode.label(), modeColor(mode)));
        meta.lore(List.of(
                Text.plain(description, NamedTextColor.GRAY),
                Text.plain(mode == current ? "Selected" : "Click to select",
                        mode == current ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        if (mode == current) meta.setEnchantmentGlintOverride(true);
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    // ================================================================= clicks

    public void click(Player player, int slot, ClickType click) {
        if (!player.hasPermission("potionguard.admin")) return;

        boolean changed = detail == null ? clickList(player, slot, click) : clickDetail(player, slot, click);
        if (changed) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }

    private boolean clickList(Player player, int slot, ClickType click) {
        if (slot < 0 || slot >= 54) return false;

        if (slot < PER_PAGE) {
            List<PotionEffectType> all = rules().all();
            int index = page * PER_PAGE + slot;
            if (index >= all.size()) return false;
            PotionEffectType type = all.get(index);

            if (click.isRightClick()) {
                new PotionMenu(plugin, type, page).open(player);
            } else {
                Mode next = Mode.values()[(rules().mode(type).ordinal() + 1) % Mode.values().length];
                rules().setMode(type, next);
                refresh();
            }
            return true;
        }

        if (slot == PREV_SLOT && page > 0) {
            page--;
            refresh();
            return true;
        }
        if (slot == NEXT_SLOT && page < pageCount() - 1) {
            page++;
            refresh();
            return true;
        }

        Rules.Source[] sources = Rules.Source.values();
        int toggle = slot - FIRST_TOGGLE_SLOT;
        if (toggle >= 0 && toggle < sources.length) {
            rules().setApplies(sources[toggle], !rules().applies(sources[toggle]));
            refresh();
            return true;
        }
        return false;
    }

    private boolean clickDetail(Player player, int slot, ClickType click) {
        PotionEffectType type = detail;
        int levelStep = click.isShiftClick() ? 5 : 1;

        switch (slot) {
            case D_ALLOWED -> rules().setMode(type, Mode.ALLOWED);
            case D_LIMITED -> rules().setMode(type, Mode.LIMITED);
            case D_BANNED -> rules().setMode(type, Mode.BANNED);
            case D_LEVEL_DOWN -> {
                rules().setMaxLevel(type, rules().maxLevel(type) - levelStep);
                rules().setMode(type, Mode.LIMITED);
            }
            case D_LEVEL_UP -> {
                rules().setMaxLevel(type, rules().maxLevel(type) + levelStep);
                rules().setMode(type, Mode.LIMITED);
            }
            case D_TIME_DOWN -> {
                if (type.isInstant()) return false;
                rules().setDurationPercent(type, rules().durationPercent(type) - 10);
                rules().setMode(type, Mode.LIMITED);
            }
            case D_TIME_UP -> {
                if (type.isInstant()) return false;
                rules().setDurationPercent(type, rules().durationPercent(type) + 10);
                rules().setMode(type, Mode.LIMITED);
            }
            case D_BACK -> {
                new PotionMenu(plugin, null, page).open(player);
                return true;
            }
            default -> {
                return false;
            }
        }

        refresh();
        return true;
    }

    // ================================================================= helpers

    private static NamedTextColor modeColor(Mode mode) {
        return switch (mode) {
            case ALLOWED -> NamedTextColor.GREEN;
            case LIMITED -> NamedTextColor.YELLOW;
            case BANNED -> NamedTextColor.RED;
        };
    }

    private static void hideExtras(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.plain(name, color));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(Text.plain(line, NamedTextColor.GRAY));
            meta.lore(lines);
        }
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack info(Material material, String name, String... lore) {
        return button(material, name, NamedTextColor.GOLD, lore);
    }
}
