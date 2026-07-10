package gg.nurmi.util;

import gg.nurmi.OneSMPPlugin;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;

public final class AliasManager {

    private final OneSMPPlugin plugin;

    public AliasManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyAliases() {
        LanguageManager.migrateLegacyFile(plugin, "aliases.yml", "lang/en_US/aliases.yml");
        String path = LanguageManager.file(plugin, "aliases");
        ConfigMigrator.migrate(plugin, path);
        File file = new File(plugin.getDataFolder(), path);
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
                if (!commandMap.register(normalized, "onesmp", command)) {
                    plugin.getLogger().warning("aliases.yml: alias '" + normalized + "' for '" + commandName
                            + "' is already taken by another command, skipping.");
                }
            }
        }
    }
}
