package com.example.potionguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /potionguard config | ban | allow | limit | list | reload */
public final class PotionCommand implements TabExecutor {

    private final PotionGuard plugin;

    public PotionCommand(PotionGuard plugin) {
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
            case "config" -> config(sender);
            case "ban" -> setMode(sender, args, Mode.BANNED);
            case "allow" -> setMode(sender, args, Mode.ALLOWED);
            case "limit" -> limit(sender, args);
            case "list" -> list(sender);
            case "reload" -> {
                plugin.reloadConfig();
                msg(sender, "PotionGuard config reloaded.", NamedTextColor.GREEN);
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void config(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "The config menu can only be opened in-game.", NamedTextColor.RED);
            return;
        }
        PotionMenu.list(plugin).open(player);
    }

    private void setMode(CommandSender sender, String[] args, Mode mode) {
        if (args.length < 2) {
            msg(sender, "Usage: /potionguard " + args[0].toLowerCase(Locale.ROOT) + " <effect>", NamedTextColor.YELLOW);
            return;
        }
        PotionEffectType type = plugin.rules().find(args[1]);
        if (type == null) {
            msg(sender, "Unknown effect '" + args[1] + "'. Press Tab to see them all.", NamedTextColor.RED);
            return;
        }
        plugin.rules().setMode(type, mode);
        msg(sender, Text.title(Rules.key(type)) + " is now " + mode.label().toLowerCase(Locale.ROOT) + ".",
                mode == Mode.BANNED ? NamedTextColor.RED : NamedTextColor.GREEN);
    }

    private void limit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "Usage: /potionguard limit <effect> [maxLevel] [durationPercent]", NamedTextColor.YELLOW);
            return;
        }
        PotionEffectType type = plugin.rules().find(args[1]);
        if (type == null) {
            msg(sender, "Unknown effect '" + args[1] + "'. Press Tab to see them all.", NamedTextColor.RED);
            return;
        }

        try {
            if (args.length >= 3) plugin.rules().setMaxLevel(type, Integer.parseInt(args[2]));
            if (args.length >= 4) plugin.rules().setDurationPercent(type, Integer.parseInt(args[3]));
        } catch (NumberFormatException e) {
            msg(sender, "Max level and duration percent must be whole numbers.", NamedTextColor.RED);
            return;
        }
        plugin.rules().setMode(type, Mode.LIMITED);

        String text = Text.title(Rules.key(type)) + " is limited to level "
                + Text.roman(plugin.rules().maxLevel(type));
        if (!type.isInstant()) text += " and " + plugin.rules().durationPercent(type) + "% duration";
        msg(sender, text + ".", NamedTextColor.YELLOW);
    }

    private void list(CommandSender sender) {
        List<String> banned = new ArrayList<>();
        List<String> limited = new ArrayList<>();
        for (PotionEffectType type : plugin.rules().all()) {
            Mode mode = plugin.rules().mode(type);
            String name = Text.title(Rules.key(type));
            if (mode == Mode.BANNED) {
                banned.add(name);
            } else if (mode == Mode.LIMITED) {
                limited.add(name + " (max " + Text.roman(plugin.rules().maxLevel(type))
                        + (type.isInstant() ? "" : ", " + plugin.rules().durationPercent(type) + "% time") + ")");
            }
        }
        msg(sender, "Banned: " + (banned.isEmpty() ? "none" : String.join(", ", banned)), NamedTextColor.RED);
        msg(sender, "Limited: " + (limited.isEmpty() ? "none" : String.join(", ", limited)), NamedTextColor.YELLOW);
    }

    private void help(CommandSender sender, String label) {
        msg(sender, "PotionGuard commands:", NamedTextColor.GOLD);
        msg(sender, "/" + label + " config  - open the menu", NamedTextColor.WHITE);
        msg(sender, "/" + label + " ban <effect>  - never give this effect", NamedTextColor.WHITE);
        msg(sender, "/" + label + " allow <effect>  - let it work normally", NamedTextColor.WHITE);
        msg(sender, "/" + label + " limit <effect> [maxLevel] [durationPercent]  - weaken it", NamedTextColor.WHITE);
        msg(sender, "/" + label + " list  - show banned and limited effects", NamedTextColor.WHITE);
        msg(sender, "/" + label + " reload  - reload config.yml", NamedTextColor.WHITE);
    }

    private static void msg(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            out.addAll(List.of("config", "ban", "allow", "limit", "list", "reload"));
        } else if (args.length == 2 && List.of("ban", "allow", "limit").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (PotionEffectType type : plugin.rules().all()) out.add(Rules.key(type));
        }

        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }
}
