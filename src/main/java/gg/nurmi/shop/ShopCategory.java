package gg.nurmi.shop;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record ShopCategory(String key, String displayName, Material icon, int slot, Map<Material, ShopItem> items) {

    public List<ShopItem> itemList() {
        return List.copyOf(items.values());
    }
}
