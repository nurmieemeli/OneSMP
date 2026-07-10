package gg.nurmi.util;

import gg.nurmi.OneSMPPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SubcommandAliases {

    private final OneSMPPlugin plugin;
    private final Map<String, Map<String, String>> aliasToCanonical = new HashMap<>();
    private final Map<String, List<String>> labels = new HashMap<>();

    public SubcommandAliases(OneSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        LanguageManager.migrateLegacyFile(plugin, "subcommand-aliases.yml", "lang/en_US/subcommand-aliases.yml");
        String path = LanguageManager.file(plugin, "subcommand-aliases");
        ConfigMigrator.migrate(plugin, path);
        File file = new File(plugin.getDataFolder(), path);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String commandName : config.getKeys(false)) {
            ConfigurationSection commandSection = config.getConfigurationSection(commandName);
            if (commandSection == null) {
                continue;
            }
            String commandKey = commandName.toLowerCase(Locale.ROOT);
            Map<String, String> lookup = new HashMap<>();
            List<String> commandLabels = new ArrayList<>();

            for (String canonical : commandSection.getKeys(false)) {
                String canonicalLower = canonical.toLowerCase(Locale.ROOT);
                lookup.put(canonicalLower, canonicalLower);
                commandLabels.add(canonicalLower);
            }
            for (String canonical : commandSection.getKeys(false)) {
                String canonicalLower = canonical.toLowerCase(Locale.ROOT);
                for (String alias : commandSection.getStringList(canonical)) {
                    String normalized = alias.toLowerCase(Locale.ROOT);
                    String existing = lookup.get(normalized);
                    if (existing != null && !existing.equals(canonicalLower)) {
                        plugin.getLogger().warning("subcommand-aliases.yml: alias '" + normalized + "' for '"
                                + commandName + " " + canonical + "' conflicts with '" + commandName + " " + existing
                                + "', skipping.");
                        continue;
                    }
                    lookup.put(normalized, canonicalLower);
                    commandLabels.add(normalized);
                }
            }

            aliasToCanonical.put(commandKey, lookup);
            labels.put(commandKey, commandLabels);
        }
    }

    public String resolve(String command, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        Map<String, String> lookup = aliasToCanonical.get(command.toLowerCase(Locale.ROOT));
        if (lookup == null) {
            return normalized;
        }
        return lookup.getOrDefault(normalized, normalized);
    }

    public List<String> labels(String command) {
        return labels.getOrDefault(command.toLowerCase(Locale.ROOT), List.of());
    }
}
