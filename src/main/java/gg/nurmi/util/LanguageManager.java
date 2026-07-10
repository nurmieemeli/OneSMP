package gg.nurmi.util;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;

// Resolves config.yml's "language" key to a lang/<code>/<base>.yml path, with a few dev-conveniences:
// unsupported codes fall back to en_US instead of failing to load, and a pre-existing root-level
// messages.yml/help.yml/crates.yml (from before per-language support existed) is moved into the new
// lang/ layout on first run so upgrading servers don't silently lose their customizations.
public final class LanguageManager {

    public static final List<String> SUPPORTED = List.of("en_US", "fi_FI", "sv_SE", "de_DE", "ru_RU", "es_ES");

    private LanguageManager() {
    }

    public static String resolve(Plugin plugin) {
        String configured = plugin.getConfig().getString("language", "en_US");
        if (!SUPPORTED.contains(configured)) {
            plugin.getLogger().warning("Unknown language '" + configured + "' in config.yml - falling back to en_US. Supported: "
                    + String.join(", ", SUPPORTED));
            return "en_US";
        }
        return configured;
    }

    public static String file(Plugin plugin, String baseName) {
        return "lang/" + resolve(plugin) + "/" + baseName + ".yml";
    }

    public static void migrateLegacyFile(Plugin plugin, String legacyName, String newPath) {
        File legacy = new File(plugin.getDataFolder(), legacyName);
        File target = new File(plugin.getDataFolder(), newPath);
        if (!legacy.exists() || target.exists()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        if (legacy.renameTo(target)) {
            plugin.getLogger().info("Moved " + legacyName + " to " + newPath + " as part of the new per-language config layout.");
        }
    }
}
