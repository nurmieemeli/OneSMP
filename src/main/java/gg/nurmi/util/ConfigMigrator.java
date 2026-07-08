package gg.nurmi.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ConfigMigrator {

    private ConfigMigrator() {}

    public static void migrate(Plugin plugin, String fileName) {
        migrate(plugin, fileName, Set.of());
    }

    public static void migrate(Plugin plugin, String fileName, Set<String> opaquePaths) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
            return;
        }

        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream == null) {
            return;
        }
        YamlConfiguration defaults;
        try (Reader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read bundled default for " + fileName + ": " + ex.getMessage());
            return;
        }

        YamlConfiguration existing = YamlConfiguration.loadConfiguration(file);
        List<String> added = new ArrayList<>();
        copyMissing(defaults, existing, "", added, opaquePaths);
        if (added.isEmpty()) {
            return;
        }

        File backup = new File(plugin.getDataFolder(), fileName + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            existing.save(file);
            plugin.getLogger().info(fileName + ": added " + added.size() + " missing config option(s) - "
                    + String.join(", ", added) + ". A copy of the previous file was saved as '"
                    + backup.getName() + "' since re-saving the file drops any custom comments.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not update " + fileName + " with new config options: " + ex.getMessage());
        }
    }

    private static void copyMissing(ConfigurationSection defaults, ConfigurationSection existing, String path,
            List<String> added, Set<String> opaquePaths) {
        for (String key : defaults.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (opaquePaths.contains(fullPath)) {
                if (!existing.isSet(key)) {
                    ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
                    existing.set(key, defaultSection != null ? defaultSection.getValues(true) : defaults.get(key));
                    added.add(fullPath);
                }
                continue;
            }

            ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
            if (defaultSection != null) {
                ConfigurationSection existingSection = existing.getConfigurationSection(key);
                if (existingSection == null) {
                    if (existing.isSet(key)) {
                        continue;
                    }
                    existingSection = existing.createSection(key);
                }
                copyMissing(defaultSection, existingSection, fullPath, added, opaquePaths);
            } else if (!existing.isSet(key)) {
                existing.set(key, defaults.get(key));
                added.add(fullPath);
            }
        }
    }
}