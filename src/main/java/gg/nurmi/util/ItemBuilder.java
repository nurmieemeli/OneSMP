package gg.nurmi.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.stack = new ItemStack(material, amount);
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder(ItemStack existing) {
        this.stack = existing.clone();
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(Component name) {
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        List<Component> undecorated = new ArrayList<>(lines.size());
        for (Component line : lines) {
            undecorated.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(undecorated);
        return this;
    }

    public ItemBuilder lore(Component... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(amount);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
}
