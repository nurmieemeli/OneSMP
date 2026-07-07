package gg.nurmi.world.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.world.WorldManager;
import gg.nurmi.world.WorldSettings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WorldDetailGui extends AbstractGui {

    private static final int INFO_SLOT = 4;
    private static final int TELEPORT_SLOT = 20;
    private static final int DELETE_SLOT = 24;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    public WorldDetailGui(CanvasSuitePlugin plugin, WorldManager worldManager, String worldName) {
        super(plugin.messages().parse("<white><name>", Placeholder.unparsed("name", worldName)), 6);

        WorldSettings settings = worldManager.getSettings(worldName);

        setItem(INFO_SLOT, new ItemBuilder(Material.NAME_TAG)
                .name(plugin.messages().parse("<white><name>", Placeholder.unparsed("name", worldName)))
                .lore(
                        plugin.messages().parse("<gray>Environment: <white><value>", Placeholder.unparsed("value", settings.environment().name())),
                        plugin.messages().parse("<gray>Type: <white><value>", Placeholder.unparsed("value", settings.type().name())),
                        plugin.messages().parse("<gray>Generator: <white><value>", Placeholder.unparsed("value", settings.generatorMode().name())),
                        plugin.messages().parse("<gray>Seed: <white><value>", Placeholder.unparsed("value", String.valueOf(settings.seed()))),
                        plugin.messages().parse("<gray>Difficulty: <white><value>", Placeholder.unparsed("value", settings.difficulty().name())),
                        plugin.messages().parse("<gray>PVP: <white><value>", Placeholder.unparsed("value", String.valueOf(settings.pvp()))))
                .build());

        setButton(TELEPORT_SLOT, new ItemBuilder(Material.ENDER_PEARL)
                .name(plugin.messages().parse("<white>Teleport Here"))
                .build(), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    player.closeInventory();
                    plugin.teleportExecutor().executeSafely(player, world.getSpawnLocation(), true);
                }
            }
        });

        setButton(DELETE_SLOT, new ItemBuilder(Material.TNT)
                .name(plugin.messages().parse("<red>Delete (unload + stop tracking)"))
                .lore(plugin.messages().parse("<dark_gray>Folder stays on disk - use /world delete <name> wipe to permanently remove it"))
                .build(), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
                worldManager.deleteWorld(worldName, false, success -> {
                    if (success) {
                        plugin.messages().send(player, "world.deleted", Placeholder.unparsed("name", worldName));
                    }
                });
            }
        });

        setButton(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name(plugin.messages().parse("<gray>« Back"))
                .build(), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new WorldListGui(plugin, worldManager, 0).open(player);
            }
        });

        setButton(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name(plugin.messages().parse("<red>Close"))
                .build(), event -> event.getWhoClicked().closeInventory());
    }
}
