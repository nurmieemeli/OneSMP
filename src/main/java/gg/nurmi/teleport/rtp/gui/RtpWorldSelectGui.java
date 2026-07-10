package gg.nurmi.teleport.rtp.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.teleport.rtp.RtpManager;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.util.WorldIcons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

public final class RtpWorldSelectGui extends AbstractGui {

    public RtpWorldSelectGui(OneSMPPlugin plugin, RtpManager rtpManager, Player player) {
        this(plugin, rtpManager, player, 0);
    }

    public RtpWorldSelectGui(OneSMPPlugin plugin, RtpManager rtpManager, Player player, int page) {
        super(plugin, plugin.messages().text("rtp.gui-title"), rows(rtpManager.enabledWorlds().size()));

        List<World> worlds = rtpManager.enabledWorlds();
        Pagination<World> pagination = new Pagination<>(worlds, PAGE_SIZE);
        List<World> pageWorlds = pagination.page(page);
        int contentRows = footerRowStart / 9;
        int statusSlot = footerRowStart + 1;
        int[] fillerSlots = {footerRowStart, footerRowStart + 2, footerRowStart + 6, footerRowStart + 7, footerRowStart + 8};

        boolean bypassCooldown = player.hasPermission("onesmp.rtp.admin");
        boolean onCooldown = !bypassCooldown && rtpManager.isOnCooldown(player.getUniqueId());
        BigDecimal balance = plugin.economy().getCached(player.getUniqueId());

        int[] slots = centeredSlots(contentRows, pageWorlds.size());
        for (int i = 0; i < pageWorlds.size(); i++) {
            World world = pageWorlds.get(i);
            double cost = rtpManager.cost(world);
            boolean canAfford = cost <= 0 || balance.compareTo(BigDecimal.valueOf(cost)) >= 0;
            Component costLine = cost > 0
                    ? plugin.messages().text("rtp.gui-cost-lore", Placeholder.unparsed("price", plugin.economy().format(BigDecimal.valueOf(cost))))
                    : plugin.messages().text("rtp.gui-cost-free-lore");
            Component statusLine = onCooldown
                    ? plugin.messages().text("rtp.gui-on-cooldown-lore")
                    : !canAfford
                            ? plugin.messages().text("rtp.gui-insufficient-funds-lore")
                            : plugin.messages().text("gui.click-to-teleport");

            ItemStack icon = new ItemBuilder(WorldIcons.iconFor(world.getEnvironment(), false))
                    .name(plugin.messages().text("gui.name-format", Placeholder.unparsed("name", rtpManager.displayName(world))))
                    .lore(costLine, statusLine)
                    .glow(cost <= 0)
                    .build();
            setButton(slots[i], icon, event -> {
                if (event.getWhoClicked() instanceof Player clicker) {
                    clicker.closeInventory();
                    rtpManager.teleportRandomly(clicker, world);
                }
            });
        }

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(plugin.messages().text("gui.filler")).build();
        for (int fillerSlot : fillerSlots) {
            setItem(fillerSlot, filler);
        }
        setItem(statusSlot, buildStatusIcon(plugin, rtpManager, player, onCooldown));

        addPaginationFooter(pagination, page, (viewer, targetPage) ->
                new RtpWorldSelectGui(plugin, rtpManager, viewer, targetPage).open(viewer));
    }

    // Content rows scale to fit however many worlds are enabled (1-5), plus one fixed footer row.
    private static int rows(int worldCount) {
        int contentRows = Math.min(5, Math.max(1, (int) Math.ceil(worldCount / 9.0)));
        return contentRows + 1;
    }

    // Spreads items evenly across the content rows (sizes differ by at most one), then centers each row horizontally.
    private static int[] centeredSlots(int contentRows, int itemCount) {
        int[] slots = new int[itemCount];
        int base = itemCount / contentRows;
        int remainder = itemCount % contentRows;
        int index = 0;
        for (int row = 0; row < contentRows && index < itemCount; row++) {
            int rowCount = base + (row < remainder ? 1 : 0);
            int startCol = (9 - rowCount) / 2;
            for (int col = 0; col < rowCount; col++) {
                slots[index++] = row * 9 + startCol + col;
            }
        }
        return slots;
    }

    private static ItemStack buildStatusIcon(OneSMPPlugin plugin, RtpManager rtpManager, Player player, boolean onCooldown) {
        Component readyLine = onCooldown
                ? plugin.messages().text("rtp.gui-status-cooldown-lore",
                        Placeholder.unparsed("seconds", String.valueOf(rtpManager.cooldownRemaining(player.getUniqueId()))))
                : plugin.messages().text("rtp.gui-status-ready-lore");

        return new ItemBuilder(Material.CLOCK)
                .name(plugin.messages().text("rtp.gui-status-name"))
                .lore(readyLine)
                .build();
    }
}
