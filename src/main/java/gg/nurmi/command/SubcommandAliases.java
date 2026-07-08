package gg.nurmi.command;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Layers configurable extra aliases on top of individual subcommands (e.g. "/world tp" also
 * answering to "/world teleport"), loaded from subcommand-aliases.yml. Unlike top-level command
 * aliases, subcommands aren't real Bukkit commands, so this just builds an alias -> canonical
 * lookup per command that each multi-subcommand executor consults before dispatching on args[0].
 */
public final class SubcommandAliases {

    private final CanvasSuitePlugin plugin;
    private final Map<String, Map<String, String>> aliasToCanonical = new HashMap<>();
    private final Map<String, List<String>> labels = new HashMap<>();

    public SubcommandAliases(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "subcommand-aliases.yml");
        if (!file.exists()) {
            plugin.saveResource("subcommand-aliases.yml", false);
        }
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

    /**
     * Resolves a typed subcommand label to its canonical name. Returns the input (lowercased)
     * unchanged if it's not a recognized alias, so callers can still fall through to their own
     * "unknown subcommand" handling.
     */
    public String resolve(String command, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        Map<String, String> lookup = aliasToCanonical.get(command.toLowerCase(Locale.ROOT));
        if (lookup == null) {
            return normalized;
        }
        return lookup.getOrDefault(normalized, normalized);
    }

    /** All labels (canonical names + configured aliases) for a command's subcommands, for tab completion. */
    public List<String> labels(String command) {
        return labels.getOrDefault(command.toLowerCase(Locale.ROOT), List.of());
    }
}
