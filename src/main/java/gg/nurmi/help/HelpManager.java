package gg.nurmi.help;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.ConfigMigrator;
import gg.nurmi.util.LanguageManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HelpManager {

    private final OneSMPPlugin plugin;
    private final Map<String, HelpCategory> categories = new LinkedHashMap<>();
    private final Map<String, String> messages = new LinkedHashMap<>();

    public HelpManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        LanguageManager.migrateLegacyFile(plugin, "help.yml", "lang/en_US/help.yml");
        String path = LanguageManager.file(plugin, "help");
        ConfigMigrator.migrate(plugin, path, Set.of("categories"));
        File file = new File(plugin.getDataFolder(), path);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        categories.clear();
        messages.clear();

        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, key));
            }
        }

        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection == null) {
            return;
        }

        for (String key : categoriesSection.getKeys(false)) {
            ConfigurationSection categorySection = categoriesSection.getConfigurationSection(key);
            if (categorySection == null) {
                continue;
            }

            String displayName = categorySection.getString("display-name", key);
            Material icon = Material.matchMaterial(categorySection.getString("icon", "BOOK"));
            if (icon == null) {
                icon = Material.BOOK;
            }
            String description = categorySection.getString("description", "");

            List<HelpArticle> articles = new ArrayList<>();
            ConfigurationSection articlesSection = categorySection.getConfigurationSection("articles");
            if (articlesSection != null) {
                for (String articleKey : articlesSection.getKeys(false)) {
                    ConfigurationSection articleSection = articlesSection.getConfigurationSection(articleKey);
                    if (articleSection == null) {
                        continue;
                    }
                    String title = articleSection.getString("title", articleKey);
                    List<String> body = articleSection.getStringList("body");
                    articles.add(new HelpArticle(articleKey.toLowerCase(Locale.ROOT), title, body));
                }
            }

            categories.put(key.toLowerCase(Locale.ROOT), new HelpCategory(key.toLowerCase(Locale.ROOT), displayName, icon, description, articles));
        }
    }

    public List<HelpCategory> categories() {
        return List.copyOf(categories.values());
    }

    public HelpCategory category(String key) {
        return key == null ? null : categories.get(key.toLowerCase(Locale.ROOT));
    }

    public String message(String key) {
        return messages.getOrDefault(key, key);
    }
}
