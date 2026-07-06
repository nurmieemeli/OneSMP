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

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 50;

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
                    teleportExecutor.executeSafely(player, warp.toLocation(), true);
                }
            });
        }

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().parse("<red>Close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());

        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>« Previous Page")).build();
            setButton(PREV_SLOT, prev, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new WarpsGui(plugin, teleportExecutor, warps, page - 1).open(player);
                }
            });
        }
        if (page < pagination.pageCount() - 1) {
            ItemStack next = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>Next Page »")).build();
            setButton(NEXT_SLOT, next, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new WarpsGui(plugin, teleportExecutor, warps, page + 1).open(player);
                }
            });
        }
    }
}
