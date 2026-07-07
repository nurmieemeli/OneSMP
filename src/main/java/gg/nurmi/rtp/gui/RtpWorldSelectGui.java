package gg.nurmi.rtp.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.rtp.RtpManager;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

/** Lists every RTP-enabled world; clicking one runs the same flow as `/rtp <world>`. */
public final class RtpWorldSelectGui extends AbstractGui {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    public RtpWorldSelectGui(CanvasSuitePlugin plugin, RtpManager rtpManager) {
        this(plugin, rtpManager, 0);
    }

    public RtpWorldSelectGui(CanvasSuitePlugin plugin, RtpManager rtpManager, int page) {
        super(plugin.messages().parse("<gradient:#34d399:#10b981><bold>Random Teleport</bold></gradient>"), 6);

        List<World> worlds = rtpManager.enabledWorlds();
        Pagination<World> pagination = new Pagination<>(worlds, PAGE_SIZE);
        List<World> pageWorlds = pagination.page(page);

        int slot = 0;
        for (World world : pageWorlds) {
            double cost = rtpManager.cost(world);
            String costLine = cost > 0
                    ? "<gray>Cost: <green>" + plugin.economy().format(BigDecimal.valueOf(cost))
                    : "<gray>Cost: <green>Free";

            ItemStack icon = new ItemBuilder(iconFor(world))
                    .name(plugin.messages().parse("<white><name>", Placeholder.unparsed("name", world.getName())))
                    .lore(
                            plugin.messages().parse(costLine),
                            plugin.messages().parse("<gray>Click to teleport"))
                    .build();
            setButton(slot++, icon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    player.closeInventory();
                    rtpManager.teleportRandomly(player, world);
                }
            });
        }

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().parse("<red>Close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());

        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>« Previous Page")).build();
            setButton(PREV_SLOT, prev, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new RtpWorldSelectGui(plugin, rtpManager, page - 1).open(player);
                }
            });
        }
        if (page < pagination.pageCount() - 1) {
            ItemStack next = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>Next Page »")).build();
            setButton(NEXT_SLOT, next, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new RtpWorldSelectGui(plugin, rtpManager, page + 1).open(player);
                }
            });
        }
    }

    private Material iconFor(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }
}
