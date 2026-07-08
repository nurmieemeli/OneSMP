package gg.nurmi.world.gui;

import gg.nurmi.CanvasSuitePlugin;
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

    public WorldListGui(CanvasSuitePlugin plugin, WorldManager worldManager, int page) {
        super(plugin, plugin.messages().parse("<gradient:#38bdf8:#818cf8><bold>Managed Worlds</bold></gradient>"), 6);

        List<WorldSettings> worlds = List.copyOf(worldManager.listWorlds());
        Pagination<WorldSettings> pagination = new Pagination<>(worlds, PAGE_SIZE);
        List<WorldSettings> pageWorlds = pagination.page(page);

        int slot = 0;
        for (WorldSettings settings : pageWorlds) {
            ItemStack icon = new ItemBuilder(WorldIcons.iconFor(settings.environment(),
                    settings.generatorMode() == WorldSettings.GeneratorMode.VOID))
                    .name(plugin.messages().parse("<white><name>", Placeholder.unparsed("name", settings.name())))
                    .lore(
                            plugin.messages().parse("<gray>Environment: <white><value>",
                                    Placeholder.unparsed("value", settings.environment().name())),
                            plugin.messages().parse("<gray>Generator: <white><value>",
                                    Placeholder.unparsed("value", settings.generatorMode().name())),
                            plugin.messages().parse("<gray>Click for details"))
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
