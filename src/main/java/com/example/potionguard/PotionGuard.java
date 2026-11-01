package com.example.potionguard;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PotionGuard extends JavaPlugin {

    private Rules rules;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        rules = new Rules(this);

        getServer().getPluginManager().registerEvents(new PotionListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        PotionCommand command = new PotionCommand(this);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("potionguard"));
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    public Rules rules() {
        return rules;
    }
}
