package gg.nurmi.shop;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.ConfigMigrator;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ShopManager {

    private final CanvasSuitePlugin plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        ConfigMigrator.migrate(plugin, "shop.yml", Set.of("categories"));
        File file = new File(plugin.getDataFolder(), "shop.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        categories.clear();

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
            Material icon = Material.matchMaterial(categorySection.getString("icon", "CHEST"));
            if (icon == null) {
                icon = Material.CHEST;
            }
            int slot = categorySection.getInt("slot", 0);

            Map<Material, ShopItem> items = new LinkedHashMap<>();
            ConfigurationSection itemsSection = categorySection.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String materialKey : itemsSection.getKeys(false)) {
                    Material material = Material.matchMaterial(materialKey);
                    if (material == null) {
                        plugin.getLogger().warning("shop.yml: unknown material '" + materialKey + "' in category '" + key + "', skipping.");
                        continue;
                    }
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(materialKey);
                    double buyPrice = itemSection == null ? -1 : itemSection.getDouble("buy-price", -1);
                    double sellPrice = itemSection == null ? -1 : itemSection.getDouble("sell-price", -1);
                    items.put(material, new ShopItem(material, buyPrice, sellPrice));
                }
            }

            categories.put(key, new ShopCategory(key, displayName, icon, slot, items));
        }
    }

    public Map<String, ShopCategory> categories() {
        return categories;
    }

    public ShopCategory category(String key) {
        return categories.get(key);
    }

    public double sellPriceMultiplier() {
        return plugin.getConfig().getDouble("shop.sell-price-multiplier", 1.0);
    }
}
