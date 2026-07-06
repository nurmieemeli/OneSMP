package gg.nurmi.teleport.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.teleport.Home;
import gg.nurmi.teleport.HomeManager;
import gg.nurmi.teleport.TeleportExecutor;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class HomesGui extends AbstractGui {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    public HomesGui(CanvasSuitePlugin plugin, HomeManager homeManager, TeleportExecutor teleportExecutor, List<Home> homes) {
        this(plugin, homeManager, teleportExecutor, homes, 0);
    }

    public HomesGui(CanvasSuitePlugin plugin, HomeManager homeManager, TeleportExecutor teleportExecutor, List<Home> homes, int page) {
        super(plugin.messages().parse("<gradient:#60a5fa:#3b82f6><bold>Your Homes</bold></gradient>"), 6);

        Pagination<Home> pagination = new Pagination<>(homes, PAGE_SIZE);
        List<Home> pageHomes = pagination.page(page);

        int slot = 0;
        for (Home home : pageHomes) {
            ItemStack icon = new ItemBuilder(Material.RED_BED)
                    .name(plugin.messages().parse("<white><name>", Placeholder.unparsed("name", home.name())))
                    .lore(
                            plugin.messages().parse("<gray>Click to teleport"),
                            plugin.messages().parse("<dark_gray>Shift-click to delete"))
                    .build();
            setButton(slot++, icon, event -> {
                if (!(event.getWhoClicked() instanceof Player player)) {
                    return;
                }
                if (event.getClick().isShiftClick()) {
                    homeManager.deleteHome(player.getUniqueId(), home.name()).thenAccept(deleted -> {
                        plugin.messages().send(player, deleted ? "teleport.home-deleted" : "teleport.home-not-found",
                                Placeholder.unparsed("name", home.name()));
                        homeManager.listHomes(player.getUniqueId()).thenAccept(refreshed ->
                                plugin.scheduler().runAtEntity(player,
                                        () -> new HomesGui(plugin, homeManager, teleportExecutor, refreshed, 0).open(player), () -> {}));
                    });
                } else {
                    teleportExecutor.executeSafely(player, home.toLocation(), true);
                }
            });
        }

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().parse("<red>Close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());

        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>« Previous Page")).build();
            setButton(PREV_SLOT, prev, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new HomesGui(plugin, homeManager, teleportExecutor, homes, page - 1).open(player);
                }
            });
        }
        if (page < pagination.pageCount() - 1) {
            ItemStack next = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>Next Page »")).build();
            setButton(NEXT_SLOT, next, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new HomesGui(plugin, homeManager, teleportExecutor, homes, page + 1).open(player);
                }
            });
        }
    }
}
