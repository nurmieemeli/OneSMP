package gg.nurmi.stats.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import gg.nurmi.OneSMPPlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.stats.StatsManager;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.function.LongFunction;

public final class StatsTopGui extends AbstractGui {

    private static final int ROWS = 6;
    private static final int CLOSE_SLOT = 49;

    public StatsTopGui(OneSMPPlugin plugin, String title, List<StatsManager.TopEntry> entries, LongFunction<String> valueFormatter) {
        super(plugin, plugin.messages().parse(title), ROWS);

        for (int i = 0; i < entries.size() && i < 45; i++) {
            StatsManager.TopEntry entry = entries.get(i);
            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .name(plugin.messages().text("stats.gui-rank-name",
                            Placeholder.unparsed("rank", String.valueOf(i + 1)),
                            Placeholder.unparsed("target", entry.name() == null ? plugin.messages().raw("general.unknown-name") : entry.name())))
                    .lore(plugin.messages().text("stats.gui-value-lore",
                            Placeholder.unparsed("value", valueFormatter.apply(entry.value()))))
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
                .name(plugin.messages().text("gui.close"))
                .build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());
    }
}
