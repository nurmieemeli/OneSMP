package gg.nurmi.teleport.rtp.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.teleport.rtp.RtpManager;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.util.WorldIcons;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

public final class RtpWorldSelectGui extends AbstractGui {

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

            ItemStack icon = new ItemBuilder(WorldIcons.iconFor(world.getEnvironment(), false))
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

        addPaginationFooter(pagination, page, (player, targetPage) ->
                new RtpWorldSelectGui(plugin, rtpManager, targetPage).open(player));
    }
}
