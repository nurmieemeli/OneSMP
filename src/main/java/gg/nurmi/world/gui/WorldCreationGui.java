package gg.nurmi.world.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.util.ItemBuilder;
import gg.nurmi.world.WorldManager;
import gg.nurmi.world.WorldSettings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class WorldCreationGui extends AbstractGui {

    private static final int INFO_SLOT = 4;
    private static final int ENVIRONMENT_SLOT = 19;
    private static final int TYPE_SLOT = 20;
    private static final int GENERATOR_SLOT = 21;
    private static final int SEED_SLOT = 22;
    private static final int STRUCTURES_SLOT = 23;
    private static final int HARDCORE_SLOT = 24;
    private static final int DIFFICULTY_SLOT = 30;
    private static final int PVP_SLOT = 31;
    private static final int CREATE_SLOT = 49;
    private static final int CANCEL_SLOT = 53;

    private final OneSMPPlugin plugin;
    private final WorldManager worldManager;
    private final UUID adminUuid;
    private final WorldSettings settings;
    private boolean resolved;

    public WorldCreationGui(OneSMPPlugin plugin, WorldManager worldManager, UUID adminUuid) {
        this(plugin, worldManager, adminUuid, worldManager.getSession(adminUuid));
    }

    private WorldCreationGui(OneSMPPlugin plugin, WorldManager worldManager, UUID adminUuid, WorldSettings settings) {
        super(plugin, plugin.messages().text("world.gui-create-title"), 6);
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.adminUuid = adminUuid;
        this.settings = settings;

        setItem(INFO_SLOT, new ItemBuilder(Material.NAME_TAG)
                .name(plugin.messages().text("gui.name-format", Placeholder.unparsed("name", settings.name())))
                .lore(
                        plugin.messages().text("world.gui-environment-lore",
                                Placeholder.unparsed("value", settings.environment().name())),
                        plugin.messages().text("world.gui-type-lore",
                                Placeholder.unparsed("value", settings.type().name())),
                        plugin.messages().text("world.gui-generator-lore",
                                Placeholder.unparsed("value", settings.generatorMode().name())),
                        plugin.messages().text("world.gui-seed-lore",
                                Placeholder.unparsed("value", String.valueOf(settings.seed())))
                ).build());

        setButton(ENVIRONMENT_SLOT, new ItemBuilder(Material.COMPASS)
                .name(plugin.messages().text("world.gui-environment-button",
                        Placeholder.unparsed("value", settings.environment().name())))
                .lore(plugin.messages().text("gui.click-to-cycle"))
                .build(), event -> {
            settings.cycleEnvironment();
            reopen(event);
        });

        setButton(TYPE_SLOT, new ItemBuilder(Material.GRASS_BLOCK)
                .name(plugin.messages().text("world.gui-type-button",
                        Placeholder.unparsed("value", settings.type().name())))
                .lore(
                        plugin.messages().text("gui.click-to-cycle"),
                        plugin.messages().text("world.gui-type-button-note"))
                .build(), event -> {
            settings.cycleType();
            reopen(event);
        });

        setButton(GENERATOR_SLOT, new ItemBuilder(settings.generatorMode() == WorldSettings.GeneratorMode.VOID
                ? Material.STRUCTURE_VOID : Material.GRASS_BLOCK)
                .name(plugin.messages().text("world.gui-generator-button",
                        Placeholder.unparsed("value", settings.generatorMode().name())))
                .lore(plugin.messages().text("gui.click-to-toggle"))
                .build(), event -> {
            settings.cycleGeneratorMode();
            reopen(event);
        });

        setButton(SEED_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name(plugin.messages().text("world.gui-seed-button",
                        Placeholder.unparsed("value", String.valueOf(settings.seed()))))
                .lore(plugin.messages().text("world.gui-click-to-reroll"))
                .build(), event -> {
            settings.rerollSeed();
            reopen(event);
        });

        setButton(STRUCTURES_SLOT, booleanToggle(Material.CHEST, "world.gui-toggle-structures", settings.generateStructures()), event -> {
            settings.toggleGenerateStructures();
            reopen(event);
        });

        setButton(HARDCORE_SLOT, booleanToggle(Material.SKELETON_SKULL, "world.gui-toggle-hardcore", settings.hardcore()), event -> {
            settings.toggleHardcore();
            reopen(event);
        });

        setButton(DIFFICULTY_SLOT, new ItemBuilder(Material.IRON_SWORD)
                .name(plugin.messages().text("world.gui-difficulty-button",
                        Placeholder.unparsed("value", settings.difficulty().name())))
                .lore(plugin.messages().text("gui.click-to-cycle"))
                .build(), event -> {
            settings.cycleDifficulty();
            reopen(event);
        });

        setButton(PVP_SLOT, booleanToggle(Material.DIAMOND_SWORD, "world.gui-toggle-pvp", settings.pvp()), event -> {
            settings.togglePvp();
            reopen(event);
        });

        setButton(CREATE_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name(plugin.messages().text("world.gui-create-button"))
                .build(), event -> {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            resolved = true;
            worldManager.clearSession(adminUuid);
            player.closeInventory();
            worldManager.createWorld(settings, world -> {
                if (world == null) {
                    plugin.messages().send(player, "world.create-failed", Placeholder.unparsed("name", settings.name()));
                    return;
                }
                plugin.messages().send(player, "world.created", Placeholder.unparsed("name", settings.name()));
                plugin.scheduler().runAtEntity(player, () -> player.teleportAsync(world.getSpawnLocation()), () -> {});
            });
        });

        setButton(CANCEL_SLOT, new ItemBuilder(Material.BARRIER)
                .name(plugin.messages().text("world.gui-cancel-button"))
                .build(), event -> {
            resolved = true;
            worldManager.clearSession(adminUuid);
            event.getWhoClicked().closeInventory();
        });
    }

    private void reopen(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            resolved = true;
            new WorldCreationGui(plugin, worldManager, adminUuid, settings).open(player);
        }
    }

    private ItemStack booleanToggle(Material material, String messagePath, boolean value) {
        String state = plugin.messages().raw(value ? "gui.toggle-on" : "gui.toggle-off");
        return new ItemBuilder(material)
                .name(plugin.messages().text(messagePath, Placeholder.parsed("state", state)))
                .lore(plugin.messages().text("gui.click-to-toggle"))
                .glow(value)
                .build();
    }

    // Every toggle button also closes+reopens this GUI to redraw it, setting resolved first so reopen isn't mistaken for abandoning the session.
    @Override
    protected void onClose(InventoryCloseEvent event) {
        if (!resolved) {
            worldManager.clearSession(adminUuid);
        }
    }
}
