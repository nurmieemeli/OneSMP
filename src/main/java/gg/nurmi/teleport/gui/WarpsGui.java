package gg.nurmi.teleport.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.teleport.TeleportExecutor;
import gg.nurmi.teleport.Warp;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class WarpsGui extends AbstractGui {

    public WarpsGui(CanvasSuitePlugin plugin, TeleportExecutor teleportExecutor, List<Warp> warps) {
        this(plugin, teleportExecutor, warps, 0);
    }

    public WarpsGui(CanvasSuitePlugin plugin, TeleportExecutor teleportExecutor, List<Warp> warps, int page) {
        super(plugin.messages().parse("<gradient:#c084fc:#a855f7><bold>Server Warps</bold></gradient>"), 6);

        Pagination<Warp> pagination = new Pagination<>(warps, PAGE_SIZE);
        List<Warp> pageWarps = pagination.page(page);

        int slot = 0;
        for (Warp warp : pageWarps) {
            ItemStack icon = new ItemBuilder(Material.ENDER_PEARL)
                    .name(plugin.messages().parse("<white><name>", Placeholder.unparsed("name", warp.name())))
                    .lore(plugin.messages().parse("<gray>Click to teleport"))
                    .build();
            setButton(slot++, icon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    teleportExecutor.executeSafely(player, warp.toLocation());
                }
            });
        }

        addPaginationFooter(pagination, page, (player, targetPage) ->
                new WarpsGui(plugin, teleportExecutor, warps, targetPage).open(player));
    }
}
