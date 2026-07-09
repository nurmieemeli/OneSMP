package gg.nurmi.world.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.util.WorldIcons;
import gg.nurmi.world.WorldManager;
import gg.nurmi.world.WorldSettings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class WorldListGui extends AbstractGui {

    public WorldListGui(OneSMPPlugin plugin, WorldManager worldManager, int page) {
        super(plugin, plugin.messages().text("world.gui-list-title"), 6);

        List<WorldSettings> worlds = List.copyOf(worldManager.listWorlds());
        Pagination<WorldSettings> pagination = new Pagination<>(worlds, PAGE_SIZE);
        List<WorldSettings> pageWorlds = pagination.page(page);

        int slot = 0;
        for (WorldSettings settings : pageWorlds) {
            ItemStack icon = new ItemBuilder(WorldIcons.iconFor(settings.environment(),
                    settings.generatorMode() == WorldSettings.GeneratorMode.VOID))
                    .name(plugin.messages().text("gui.name-format", Placeholder.unparsed("name", settings.name())))
                    .lore(
                            plugin.messages().text("world.gui-environment-lore",
                                    Placeholder.unparsed("value", settings.environment().name())),
                            plugin.messages().text("world.gui-generator-lore",
                                    Placeholder.unparsed("value", settings.generatorMode().name())),
                            plugin.messages().text("world.gui-click-for-details"))
                    .build();
            setButton(slot++, icon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new WorldDetailGui(plugin, worldManager, settings.name()).open(player);
                }
            });
        }

        addPaginationFooter(pagination, page, (player, targetPage) ->
                new WorldListGui(plugin, worldManager, targetPage).open(player));
    }
}
