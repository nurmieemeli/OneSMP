package gg.nurmi.command;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Layers configurable extra aliases from aliases.yml on top of every command already declared in
 * plugin.yml, by directly registering the existing PluginCommand object (the one that already has
 * its executor/tab-completer set) under each extra label in Bukkit's own CommandMap - no reflection
 * or custom Command subclass needed, and tab completion/permissions work identically since it's
 * literally the same Command instance handling dispatch regardless of which label was typed.
 *
 * <p>Must run after every command has been registered via plugin.yml (which happens automatically
 * at plugin load, before onEnable), so call this once, near the end of onEnable().</p>
 */
public final class AliasManager {

    private final CanvasSuitePlugin plugin;

    public AliasManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void applyAliases() {
        File file = new File(plugin.getDataFolder(), "aliases.yml");
        if (!file.exists()) {
            plugin.saveResource("aliases.yml", false);
        }
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
