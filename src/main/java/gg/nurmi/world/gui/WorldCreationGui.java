package gg.nurmi.world.gui;

import gg.nurmi.CanvasSuitePlugin;
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

/**
 * Reads/mutates the admin's live {@link WorldSettings} session (held by {@link WorldManager}) in
 * place; every toggle click re-opens a fresh instance of this GUI to reflect the change, the same
 * refresh-by-reconstruction pattern used by every other menu in this plugin (e.g. GuildMembersGui).
 */
public final class WorldCreationGui extends AbstractGui {

    private static final int INFO_SLOT = 4;
    private static final int ENVIRONMENT_SLOT = 19;
    private static final int TYPE_SLOT = 20;
    private static final int GENERATOR_SLOT = 21;
    private static final int SEED_SLOT = 22;
    private static final int STRUCTURES_SLOT = 23;
    private static final int HARDCORE_SLOT = 24;
    private static final int KEEP_SPAWN_SLOT = 29;
    private static final int DIFFICULTY_SLOT = 30;
    private static final int PVP_SLOT = 31;
    private static final int CREATE_SLOT = 49;
    private static final int CANCEL_SLOT = 53;

    private final CanvasSuitePlugin plugin;
    private final WorldManager worldManager;
    private final UUID adminUuid;
    private boolean resolved;

    public WorldCreationGui(CanvasSuitePlugin plugin, WorldManager worldManager, UUID adminUuid) {
        super(plugin.messages().parse("<gradient:#34d399:#10b981><bold>Create World</bold></gradient>"), 6);
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.adminUuid = adminUuid;

        WorldSettings settings = worldManager.getSession(adminUuid);

        setItem(INFO_SLOT, new ItemBuilder(Material.NAME_TAG)
                .name(plugin.messages().parse("<white><name>", Placeholder.unparsed("name", settings.name())))
                .lore(
                        plugin.messages().parse("<gray>Environment: <white><value>", Placeholder.unparsed("value", settings.environment().name())),
                        plugin.messages().parse("<gray>Type: <white><value>", Placeholder.unparsed("value", settings.type().name())),
                        plugin.messages().parse("<gray>Generator: <white><value>", Placeholder.unparsed("value", settings.generatorMode().name())),
                        plugin.messages().parse("<gray>Seed: <white><value>", Placeholder.unparsed("value", String.valueOf(settings.seed())))
                ).build());

        setButton(ENVIRONMENT_SLOT, new ItemBuilder(Material.COMPASS)
                .name(plugin.messages().parse("<white>Environment: <yellow><value>", Placeholder.unparsed("value", settings.environment().name())))
                .lore(plugin.messages().parse("<gray>Click to cycle"))
                .build(), event -> {
            settings.cycleEnvironment();
            reopen(event);
        });

        setButton(TYPE_SLOT, new ItemBuilder(Material.GRASS_BLOCK)
                .name(plugin.messages().parse("<white>World Type: <yellow><value>", Placeholder.unparsed("value", settings.type().name())))
                .lore(
                        plugin.messages().parse("<gray>Click to cycle"),
                        plugin.messages().parse("<dark_gray>Ignored when Generator = VOID"))
                .build(), event -> {
            settings.cycleType();
            reopen(event);
        });

        setButton(GENERATOR_SLOT, new ItemBuilder(settings.generatorMode() == WorldSettings.GeneratorMode.VOID
                ? Material.STRUCTURE_VOID : Material.GRASS_BLOCK)
                .name(plugin.messages().parse("<white>Generator: <yellow><value>", Placeholder.unparsed("value", settings.generatorMode().name())))
                .lore(plugin.messages().parse("<gray>Click to toggle"))
                .build(), event -> {
            settings.cycleGeneratorMode();
            reopen(event);
        });

        setButton(SEED_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name(plugin.messages().parse("<white>Seed: <yellow><value>", Placeholder.unparsed("value", String.valueOf(settings.seed()))))
                .lore(plugin.messages().parse("<gray>Click to reroll"))
                .build(), event -> {
            settings.rerollSeed();
            reopen(event);
        });

        setButton(STRUCTURES_SLOT, booleanToggle(Material.CHEST, "Generate Structures", settings.generateStructures()), event -> {
            settings.toggleGenerateStructures();
            reopen(event);
        });

        setButton(HARDCORE_SLOT, booleanToggle(Material.SKELETON_SKULL, "Hardcore", settings.hardcore()), event -> {
            settings.toggleHardcore();
            reopen(event);
        });

        setButton(KEEP_SPAWN_SLOT, booleanToggle(Material.BEACON, "Keep Spawn In Memory", settings.keepSpawnInMemory()), event -> {
            settings.toggleKeepSpawnInMemory();
            reopen(event);
        });

        setButton(DIFFICULTY_SLOT, new ItemBuilder(Material.IRON_SWORD)
                .name(plugin.messages().parse("<white>Difficulty: <yellow><value>", Placeholder.unparsed("value", settings.difficulty().name())))
                .lore(plugin.messages().parse("<gray>Click to cycle"))
                .build(), event -> {
            settings.cycleDifficulty();
            reopen(event);
        });

        setButton(PVP_SLOT, booleanToggle(Material.DIAMOND_SWORD, "PVP", settings.pvp()), event -> {
            settings.togglePvp();
            reopen(event);
        });

        setButton(CREATE_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name(plugin.messages().parse("<green><bold>Create World"))
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
                .name(plugin.messages().parse("<red>Cancel"))
                .build(), event -> {
            resolved = true;
            worldManager.clearSession(adminUuid);
            event.getWhoClicked().closeInventory();
        });
    }

    private void reopen(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            // This instance is being replaced by a fresh one reading the same (still-live) session,
            // so its own close (triggered by opening the replacement) must not clear that session.
            resolved = true;
            new WorldCreationGui(plugin, worldManager, adminUuid).open(player);
        }
    }

    private ItemStack booleanToggle(Material material, String label, boolean value) {
        String state = value ? "<green>ON" : "<red>OFF";
        return new ItemBuilder(material)
                .name(plugin.messages().parse("<white>" + label + ": " + state))
                .lore(plugin.messages().parse("<gray>Click to toggle"))
                .glow(value)
                .build();
    }

    @Override
    protected void onClose(InventoryCloseEvent event) {
        if (!resolved) {
            worldManager.clearSession(adminUuid);
        }
    }
}
