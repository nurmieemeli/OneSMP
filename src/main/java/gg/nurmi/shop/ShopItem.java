package gg.nurmi.shop;

import org.bukkit.Material;

public record ShopItem(Material material, double buyPrice, double sellPrice) {

    public boolean buyable() {
        return buyPrice >= 0;
    }

    public boolean sellable() {
        return sellPrice >= 0;
    }
}
