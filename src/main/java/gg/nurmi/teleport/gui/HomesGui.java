package gg.nurmi.teleport.gui;

import gg.nurmi.OneSMPPlugin;
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

    public HomesGui(OneSMPPlugin plugin, HomeManager homeManager, TeleportExecutor teleportExecutor, List<Home> homes) {
        this(plugin, homeManager, teleportExecutor, homes, 0);
    }

    public HomesGui(OneSMPPlugin plugin, HomeManager homeManager, TeleportExecutor teleportExecutor, List<Home> homes, int page) {
        super(plugin, plugin.messages().text("teleport.gui-homes-title"), 6);

        Pagination<Home> pagination = new Pagination<>(homes, PAGE_SIZE);
        List<Home> pageHomes = pagination.page(page);

        int slot = 0;
        for (Home home : pageHomes) {
            ItemStack icon = new ItemBuilder(Material.RED_BED)
                    .name(plugin.messages().text("gui.name-format", Placeholder.unparsed("name", home.name())))
                    .lore(
                            plugin.messages().text("gui.click-to-teleport"),
                            plugin.messages().text("teleport.gui-click-to-delete"))
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
                    teleportExecutor.executeSafely(player, home.toLocation());
                }
            });
        }

        addPaginationFooter(pagination, page, (player, targetPage) ->
                new HomesGui(plugin, homeManager, teleportExecutor, homes, targetPage).open(player));
    }
}
