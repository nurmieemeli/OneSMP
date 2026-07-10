package gg.nurmi.shop.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.shop.ShopItem;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.Map;

public final class SellGui extends AbstractGui {

    private static final int INPUT_SLOTS = 45;
    private static final int INFO_SLOT = 45;
    private static final int CONFIRM_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final OneSMPPlugin plugin;
    private boolean returned;

    public SellGui(OneSMPPlugin plugin) {
        super(plugin, plugin.messages().text("shop.gui-title"), 6);
        this.plugin = plugin;

        openSlots(0, INPUT_SLOTS);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(plugin.messages().text("gui.filler")).build();
        for (int slot = INPUT_SLOTS; slot < 54; slot++) {
            if (slot != INFO_SLOT && slot != CONFIRM_SLOT && slot != CLOSE_SLOT) {
                setItem(slot, filler);
            }
        }

        setButton(CONFIRM_SLOT, buildConfirmIcon(), this::handleConfirm);

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().text("gui.close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());

        updateInfo();
    }

    @Override
    protected void onClose(InventoryCloseEvent event) {
        if (returned || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        returned = true;
        returnItems(player);
    }

    @Override
    protected void onOpenSlotChange(HumanEntity viewer) {
        if (viewer instanceof Player player) {
            plugin.scheduler().runAtEntityDelayed(player, this::updateInfo, () -> {}, 1);
        }
    }

    private void handleConfirm(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        long cooldownMillis = plugin.getConfig().getLong("anti-spam.action-cooldown-millis", 500);
        if (!plugin.actionCooldown().tryAcquire(player.getUniqueId(), "shop-sell", cooldownMillis)) {
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        int itemsSold = 0;
        Inventory inventory = getInventory();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            ShopItem shopItem = plugin.shop().findItem(stack.getType());
            if (shopItem == null) {
                continue;
            }
            double effectiveSell = effectiveUnitPrice(stack, shopItem);
            total = total.add(BigDecimal.valueOf(effectiveSell).multiply(BigDecimal.valueOf(stack.getAmount())));
            itemsSold += stack.getAmount();
            inventory.setItem(slot, null);
        }

        if (itemsSold == 0) {
            plugin.messages().send(player, "shop.sell-empty");
            return;
        }

        BigDecimal payout = total;
        int soldAmount = itemsSold;
        plugin.economy().deposit(player.getUniqueId(), payout).thenRun(() ->
                plugin.scheduler().runAtEntity(player, () -> {
                    plugin.messages().send(player, "shop.sold-bulk",
                            Placeholder.unparsed("amount", String.valueOf(soldAmount)),
                            Placeholder.unparsed("price", plugin.economy().format(payout)));
                    updateInfo();
                }, () -> {}));
    }

    private void returnItems(Player player) {
        Inventory inventory = getInventory();
        boolean droppedAny = false;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                droppedAny = true;
            }
        }
        if (droppedAny) {
            plugin.messages().send(player, "shop.items-dropped");
        }
    }

    private void updateInfo() {
        BigDecimal total = BigDecimal.ZERO;
        int itemCount = 0;
        Inventory inventory = getInventory();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            ShopItem shopItem = plugin.shop().findItem(stack.getType());
            if (shopItem == null) {
                continue;
            }
            double effectiveSell = effectiveUnitPrice(stack, shopItem);
            total = total.add(BigDecimal.valueOf(effectiveSell).multiply(BigDecimal.valueOf(stack.getAmount())));
            itemCount += stack.getAmount();
        }

        setItem(INFO_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name(plugin.messages().text("shop.gui-value-name",
                        Placeholder.unparsed("price", plugin.economy().format(total))))
                .lore(plugin.messages().text("shop.gui-count-lore",
                        Placeholder.unparsed("count", String.valueOf(itemCount))))
                .build());
    }

    // Damaged tools/armor sell for less: payout scales with remaining durability, so a tool
    // one hit from breaking pays out near-nothing instead of full price. Unbreakable items and
    // anything without durability (blocks, food, etc.) are unaffected.
    private double effectiveUnitPrice(ItemStack stack, ShopItem shopItem) {
        double basePrice = shopItem.sellPrice() * plugin.shop().sellPriceMultiplier();
        short maxDurability = stack.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return basePrice;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.isUnbreakable() || !damageable.hasDamage()) {
            return basePrice;
        }
        double remaining = 1.0 - ((double) damageable.getDamage() / maxDurability);
        remaining = Math.max(0.0, Math.min(1.0, remaining));
        return basePrice * remaining;
    }

    private ItemStack buildConfirmIcon() {
        return new ItemBuilder(Material.LIME_WOOL)
                .name(plugin.messages().text("shop.gui-confirm-button"))
                .lore(plugin.messages().text("shop.gui-confirm-lore"))
                .build();
    }
}
