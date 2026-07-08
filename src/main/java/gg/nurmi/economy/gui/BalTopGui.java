package gg.nurmi.economy.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.economy.EconomyManager;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class BalTopGui extends AbstractGui {

    private static final int ROWS = 6;
    private static final int CLOSE_SLOT = 49;

    public BalTopGui(CanvasSuitePlugin plugin, List<EconomyManager.BalanceEntry> entries) {
        super(plugin, plugin.messages().parse("<gradient:#facc15:#fbbf24><bold>Balance Leaderboard</bold></gradient>"), ROWS);

        for (int i = 0; i < entries.size() && i < 45; i++) {
            EconomyManager.BalanceEntry entry = entries.get(i);
            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .name(plugin.messages().parse("<yellow>#<rank> <white><target>",
                            Placeholder.unparsed("rank", String.valueOf(i + 1)),
                            Placeholder.unparsed("target", entry.name() == null ? "?" : entry.name())))
                    .lore(plugin.messages().parse("<gray>Balance: <green><balance>",
                            Placeholder.unparsed("balance", plugin.economy().format(entry.balance()))))
                    .build();

            if (head.getItemMeta() instanceof SkullMeta skullMeta) {
                PlayerProfile profile = Bukkit.getOfflinePlayer(entry.uuid()).getPlayerProfile();
                if (!profile.hasTextures()) {
                    profile.complete(true);
                }
                skullMeta.setPlayerProfile(profile);
                head.setItemMeta(skullMeta);
            }
            setItem(i, head);
        }

        ItemStack close = new ItemBuilder(Material.BARRIER)
                .name(plugin.messages().parse("<red>Close"))
                .build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());
    }
}
