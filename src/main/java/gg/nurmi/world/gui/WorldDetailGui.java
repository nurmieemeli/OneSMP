package gg.nurmi.world.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.world.WorldManager;
import gg.nurmi.world.WorldSettings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WorldDetailGui extends AbstractGui {

    private static final int INFO_SLOT = 4;
    private static final int TELEPORT_SLOT = 20;
    private static final int DELETE_SLOT = 24;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    public WorldDetailGui(OneSMPPlugin plugin, WorldManager worldManager, String worldName) {
        super(plugin, plugin.messages().text("gui.name-format", Placeholder.unparsed("name", worldName)), 6);

        WorldSettings settings = worldManager.getSettings(worldName);

        setItem(INFO_SLOT, new ItemBuilder(Material.NAME_TAG)
                .name(plugin.messages().text("gui.name-format", Placeholder.unparsed("name", worldName)))
                .lore(
                        plugin.messages().text("world.gui-environment-lore", Placeholder.unparsed("value", settings.environment().name())),
                        plugin.messages().text("world.gui-type-lore", Placeholder.unparsed("value", settings.type().name())),
                        plugin.messages().text("world.gui-generator-lore", Placeholder.unparsed("value", settings.generatorMode().name())),
                        plugin.messages().text("world.gui-seed-lore", Placeholder.unparsed("value", String.valueOf(settings.seed()))),
                        plugin.messages().text("world.gui-difficulty-lore", Placeholder.unparsed("value", settings.difficulty().name())),
                        plugin.messages().text("world.gui-pvp-lore", Placeholder.unparsed("value", String.valueOf(settings.pvp()))))
                .build());

        setButton(TELEPORT_SLOT, new ItemBuilder(Material.ENDER_PEARL)
                .name(plugin.messages().text("world.gui-teleport-here"))
                .build(), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                World world = worldManager.getWorld(worldName);
                if (world != null) {
                    player.closeInventory();
                    plugin.teleportExecutor().executeSafely(player, world.getSpawnLocation());
                }
            }
        });

        setButton(DELETE_SLOT, new ItemBuilder(Material.TNT)
                .name(plugin.messages().text("world.gui-delete-button"))
                .lore(plugin.messages().text("world.gui-delete-lore"))
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
                .name(plugin.messages().text("gui.back"))
                .build(), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new WorldListGui(plugin, worldManager, 0).open(player);
            }
        });

        setButton(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name(plugin.messages().text("gui.close"))
                .build(), event -> event.getWhoClicked().closeInventory());
    }
}
