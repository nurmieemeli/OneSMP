package gg.nurmi.util;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;

public final class AliasManager {

    private final CanvasSuitePlugin plugin;

    public AliasManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void applyAliases() {
        ConfigMigrator.migrate(plugin, "aliases.yml");
        File file = new File(plugin.getDataFolder(), "aliases.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("aliases");
        if (section == null) {
            return;
        }

        CommandMap commandMap = plugin.getServer().getCommandMap();
        for (String commandName : section.getKeys(false)) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) {
                plugin.getLogger().warning("aliases.yml: unknown command '" + commandName + "', skipping.");
                continue;
            }

            for (String alias : section.getStringList(commandName)) {
                String normalized = alias.toLowerCase(Locale.ROOT);
                if (!commandMap.register(normalized, "canvassuite", command)) {
                    plugin.getLogger().warning("aliases.yml: alias '" + normalized + "' for '" + commandName
                            + "' is already taken by another command, skipping.");
                }
            }
        }
    }
}
