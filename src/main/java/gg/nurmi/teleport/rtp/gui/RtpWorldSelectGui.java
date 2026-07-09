package gg.nurmi.teleport.rtp.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.teleport.rtp.RtpManager;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.util.WorldIcons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

public final class RtpWorldSelectGui extends AbstractGui {

    public RtpWorldSelectGui(OneSMPPlugin plugin, RtpManager rtpManager) {
        this(plugin, rtpManager, 0);
    }

    public RtpWorldSelectGui(OneSMPPlugin plugin, RtpManager rtpManager, int page) {
        super(plugin, plugin.messages().text("rtp.gui-title"), 6);

        List<World> worlds = rtpManager.enabledWorlds();
        Pagination<World> pagination = new Pagination<>(worlds, PAGE_SIZE);
        List<World> pageWorlds = pagination.page(page);

        int slot = 0;
        for (World world : pageWorlds) {
            double cost = rtpManager.cost(world);
            Component costLine = cost > 0
                    ? plugin.messages().text("rtp.gui-cost-lore", Placeholder.unparsed("price", plugin.economy().format(BigDecimal.valueOf(cost))))
                    : plugin.messages().text("rtp.gui-cost-free-lore");

            ItemStack icon = new ItemBuilder(WorldIcons.iconFor(world.getEnvironment(), false))
                    .name(plugin.messages().text("gui.name-format", Placeholder.unparsed("name", world.getName())))
                    .lore(
                            costLine,
                            plugin.messages().text("gui.click-to-teleport"))
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
