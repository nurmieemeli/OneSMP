package gg.nurmi.crate.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.crate.CrateReward;
import gg.nurmi.crate.CrateType;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class CratePreviewGui extends AbstractGui {

    public CratePreviewGui(OneSMPPlugin plugin, CrateType type) {
        this(plugin, type, 0);
    }

    public CratePreviewGui(OneSMPPlugin plugin, CrateType type, int page) {
        super(plugin, plugin.messages().text("crate.gui-preview-title",
                Placeholder.component("type", plugin.messages().parse(type.displayName()))), 6);

        List<CrateReward> rewards = type.rewards();
        double totalWeight = rewards.stream().mapToDouble(CrateReward::weight).sum();
        Pagination<CrateReward> pagination = new Pagination<>(rewards, PAGE_SIZE);
        List<CrateReward> pageRewards = pagination.page(page);

        int slot = 0;
        for (CrateReward reward : pageRewards) {
            double chance = totalWeight > 0 ? (reward.weight() / totalWeight) * 100 : 0;
            setItem(slot++, buildIcon(plugin, reward, chance));
        }

        addPaginationFooter(pagination, page, (player, targetPage) ->
                new CratePreviewGui(plugin, type, targetPage).open(player));
    }

    private ItemStack buildIcon(OneSMPPlugin plugin, CrateReward reward, double chance) {
        return new ItemBuilder(reward.iconMaterial())
                .name(plugin.messages().parse(reward.displayName()))
                .lore(plugin.messages().text("crate.gui-chance-lore",
                        Placeholder.unparsed("chance", String.format("%.2f", chance))))
                .build();
    }
}
