package gg.nurmi.shop;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.ConfigMigrator;
import gg.nurmi.util.LanguageManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShopManager {

    private final OneSMPPlugin plugin;
    private final Map<Material, ShopItem> items = new LinkedHashMap<>();

    public ShopManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        LanguageManager.migrateLegacyFile(plugin, "shop.yml", "sell-prices.yml");
        ConfigMigrator.migrate(plugin, "sell-prices.yml");
        File file = new File(plugin.getDataFolder(), "sell-prices.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        items.clear();

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }
        for (String materialKey : itemsSection.getKeys(false)) {
            Material material = Material.matchMaterial(materialKey);
            if (material == null) {
                plugin.getLogger().warning("sell-prices.yml: unknown material '" + materialKey + "', skipping.");
                continue;
            }
            double sellPrice = itemsSection.getDouble(materialKey, -1);
            if (sellPrice < 0) {
                continue;
            }
            items.put(material, new ShopItem(material, sellPrice));
        }
    }

    public ShopItem findItem(Material material) {
        return items.get(material);
    }

    public double sellPriceMultiplier() {
        return plugin.getConfig().getDouble("shop.sell-price-multiplier", 1.0);
    }
}
