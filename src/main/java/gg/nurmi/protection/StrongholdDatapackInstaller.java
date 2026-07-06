package gg.nurmi.protection;

import gg.nurmi.CanvasSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Bukkit/Paper has no cancellable "structure about to generate" event for strongholds
 * (AsyncStructureGenerateEvent exists but isn't Cancellable), and structure placement is baked
 * into a world's registries at load time — there is no supported runtime API to turn a structure
 * off in an already-loaded world. The only real fix is a datapack that overrides the stronghold
 * structure set to place zero of them, seeded into each world folder so it applies on next load.
 *
 * <p>This only prevents *new* stronghold generation; it does not remove strongholds that have
 * already generated in previously-visited chunks.</p>
 */
public final class StrongholdDatapackInstaller {

    private static final String PACK_NAME = "canvassuite_no_stronghold";

    private static final String STRUCTURE_SET_JSON = """
            {
              "structures": [
                { "structure": "minecraft:stronghold", "weight": 1 }
              ],
              "placement": {
                "type": "minecraft:concentric_rings",
                "distance": 32,
                "spread": 3,
                "count": 0,
                "preferred_biomes": "#minecraft:stronghold_biased_to"
              }
            }
            """;

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "pack_format": 48,
                "supported_formats": { "min_inclusive": 6, "max_inclusive": 100 },
                "description": "CanvasSuite: disables stronghold generation"
              }
            }
            """;

    private final CanvasSuitePlugin plugin;

    public StrongholdDatapackInstaller(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void installForAllWorlds() {
        if (!plugin.getConfig().getBoolean("protection.disable-stronghold-generation", true)) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            installFor(world);
        }
    }

    private void installFor(World world) {
        Path packRoot = world.getWorldFolder().toPath().resolve("datapacks").resolve(PACK_NAME);
        Path structureSetFile = packRoot.resolve("data/minecraft/worldgen/structure_set/strongholds.json");
        if (Files.exists(structureSetFile)) {
            return;
        }

        try {
            Files.createDirectories(structureSetFile.getParent());
            Files.writeString(structureSetFile, STRUCTURE_SET_JSON);
            Files.writeString(packRoot.resolve("pack.mcmeta"), PACK_MCMETA);

            plugin.getLogger().warning("Installed the no-stronghold datapack for world '" + world.getName()
                    + "'. It only takes effect on that world's next load (enable it in the world's "
                    + "datapack list and restart the server) — existing strongholds already generated are unaffected.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to install no-stronghold datapack for world '" + world.getName() + "'", ex);
        }
    }
}
