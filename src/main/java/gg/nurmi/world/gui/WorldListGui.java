package gg.nurmi.world.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.world.WorldManager;
import gg.nurmi.world.WorldSettings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class WorldListGui extends AbstractGui {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    public WorldListGui(CanvasSuitePlugin plugin, WorldManager worldManager, int page) {
        super(plugin.messages().parse("<gradient:#38bdf8:#818cf8><bold>Managed Worlds</bold></gradient>"), 6);

        List<WorldSettings> worlds = List.copyOf(worldManager.listWorlds());
        Pagination<WorldSettings> pagination = new Pagination<>(worlds, PAGE_SIZE);
        List<WorldSettings> pageWorlds = pagination.page(page);

        int slot = 0;
        for (WorldSettings settings : pageWorlds) {
            ItemStack icon = new ItemBuilder(iconFor(settings))
                    .name(plugin.messages().parse("<white><name>", Placeholder.unparsed("name", settings.name())))
                    .lore(
                            plugin.messages().parse("<gray>Environment: <white><value>", Placeholder.unparsed("value", settings.environment().name())),
                            plugin.messages().parse("<gray>Generator: <white><value>", Placeholder.unparsed("value", settings.generatorMode().name())),
                            plugin.messages().parse("<gray>Click for details"))
                    .build();
            setButton(slot++, icon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new WorldDetailGui(plugin, worldManager, settings.name()).open(player);
                }
            });
        }

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().parse("<red>Close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());

        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>« Previous Page")).build();
            setButton(PREV_SLOT, prev, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new WorldListGui(plugin, worldManager, page - 1).open(player);
                }
            });
        }
        if (page < pagination.pageCount() - 1) {
            ItemStack next = new ItemBuilder(Material.PAPER).name(plugin.messages().parse("<gray>Next Page »")).build();
            setButton(NEXT_SLOT, next, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new WorldListGui(plugin, worldManager, page + 1).open(player);
                }
            });
        }
    }

    private Material iconFor(WorldSettings settings) {
        return switch (settings.environment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> settings.generatorMode() == WorldSettings.GeneratorMode.VOID ? Material.STRUCTURE_VOID : Material.GRASS_BLOCK;
        };
    }
}
