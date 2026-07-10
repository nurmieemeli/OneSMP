package gg.nurmi.world;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.world.WorldSettings.GeneratorMode;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class WorldManager {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    private final OneSMPPlugin plugin;
    private final Map<String, WorldSettings> managed = new LinkedHashMap<>();
    private final Map<UUID, WorldSettings> pendingSessions = new ConcurrentHashMap<>();

    public WorldManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadWorlds() {
        load();
        if (managed.isEmpty()) {
            return;
        }
        plugin.scheduler().runGlobal(() -> {
            for (WorldSettings settings : managed.values()) {
                ensureBukkitWorldLoaded(settings);
            }
        });
    }

    public void createWorld(WorldSettings settings, Consumer<World> onDone) {
        plugin.scheduler().runGlobal(() -> {
            World world = ensureBukkitWorldLoaded(settings);
            if (world != null) {
                managed.put(settings.name(), settings);
                save();
            }
            onDone.accept(world);
        });
    }

    public void deleteWorld(String name, boolean wipeFiles, Consumer<Boolean> onDone) {
        plugin.scheduler().runGlobal(() -> {
            World world = Bukkit.getWorld(storageName(name));
            if (world == null) {
                managed.remove(name);
                save();
                onDone.accept(true);
                return;
            }

            File folder = world.getWorldFolder();
            boolean unloaded = Bukkit.unloadWorld(world, false);
            managed.remove(name);
            save();

            if (!unloaded) {
                onDone.accept(false);
                return;
            }
            if (!wipeFiles) {
                onDone.accept(true);
                return;
            }
            plugin.scheduler().runAsync(() -> onDone.accept(deleteRecursively(folder.toPath())));
        });
    }

    private World ensureBukkitWorldLoaded(WorldSettings settings) {
        String storageName = storageName(settings.name());
        World world = Bukkit.getWorld(storageName);
        if (world == null) {
            WorldCreator creator = new WorldCreator(storageName)
                    .environment(settings.environment())
                    .type(settings.type())
                    .seed(settings.seed())
                    .generateStructures(settings.generateStructures())
                    .hardcore(settings.hardcore());
            if (settings.generatorMode() == GeneratorMode.VOID) {
                creator.generator(new VoidChunkGenerator());
            }
            world = creator.createWorld();
        }
        if (world != null) {
            world.setDifficulty(settings.difficulty());
            world.setGameRule(GameRules.PVP, settings.pvp());
        }
        return world;
    }

    private boolean deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return true;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to delete " + path, ex);
                }
            });
            return !Files.exists(root);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to wipe world folder " + root, ex);
            return false;
        }
    }

    public Collection<WorldSettings> listWorlds() {
        return List.copyOf(managed.values());
    }

    // Unlike listWorlds(), also surfaces worlds the server itself loaded (default dimensions, etc.), synthesized read-only from live Bukkit state.
    public List<WorldSettings> listAllLoadedWorlds() {
        List<WorldSettings> all = new ArrayList<>(managed.values());
        for (World world : Bukkit.getWorlds()) {
            if (!managed.containsKey(world.getName())) {
                all.add(synthesizeSettings(world));
            }
        }
        return all;
    }

    public boolean isManaged(String name) {
        return managed.containsKey(name);
    }

    private WorldSettings synthesizeSettings(World world) {
        return new WorldSettings(world.getName(), world.getEnvironment(), world.getWorldType(),
                GeneratorMode.VANILLA, world.getSeed(), world.canGenerateStructures(), world.isHardcore(),
                world.getDifficulty(), world.getGameRuleValue(GameRules.PVP));
    }

    public WorldSettings getSettings(String name) {
        return managed.get(name);
    }

    public boolean worldNameTaken(String name) {
        return managed.containsKey(name) || Bukkit.getWorld(storageName(name)) != null;
    }

    // Tries the name as-is first (vanilla worlds), then falls back to the container-qualified name for worlds OneSMP created itself.
    public World getWorld(String name) {
        World direct = Bukkit.getWorld(name);
        return direct != null ? direct : Bukkit.getWorld(storageName(name));
    }

    private String storageName(String name) {
        return WorldPaths.resolve(plugin.getConfig().getString("world-creation.container", ""), name);
    }

    public boolean isValidName(String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    public WorldSettings startSession(UUID admin, String name, Long fixedSeed) {
        ConfigurationSection defaults = plugin.getConfig().getConfigurationSection("world-creation.defaults");
        World.Environment environment = parseEnvironment(configString(defaults, "environment", "NORMAL"));
        WorldType type = parseType(configString(defaults, "type", "NORMAL"));
        GeneratorMode generatorMode = "VOID".equalsIgnoreCase(configString(defaults, "generator", "VANILLA"))
                ? GeneratorMode.VOID : GeneratorMode.VANILLA;
        boolean generateStructures = defaults == null || defaults.getBoolean("generate-structures", true);
        boolean hardcore = defaults != null && defaults.getBoolean("hardcore", false);
        Difficulty difficulty = parseDifficulty(configString(defaults, "difficulty", "NORMAL"));
        boolean pvp = defaults == null || defaults.getBoolean("pvp", true);
        long seed = fixedSeed != null ? fixedSeed : ThreadLocalRandom.current().nextLong();

        WorldSettings settings = new WorldSettings(name, environment, type, generatorMode, seed,
                generateStructures, hardcore, difficulty, pvp);
        pendingSessions.put(admin, settings);
        return settings;
    }

    public WorldSettings getSession(UUID admin) {
        return pendingSessions.get(admin);
    }

    public void clearSession(UUID admin) {
        pendingSessions.remove(admin);
    }

    private static String configString(ConfigurationSection section, String key, String fallback) {
        return section == null ? fallback : section.getString(key, fallback);
    }

    private static World.Environment parseEnvironment(String raw) {
        try {
            return World.Environment.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return World.Environment.NORMAL;
        }
    }

    private static WorldType parseType(String raw) {
        try {
            return WorldType.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return WorldType.NORMAL;
        }
    }

    private static Difficulty parseDifficulty(String raw) {
        try {
            return Difficulty.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Difficulty.NORMAL;
        }
    }

    private void load() {
        managed.clear();
        File file = file();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection == null) {
            return;
        }
        for (String key : worldsSection.getKeys(false)) {
            ConfigurationSection section = worldsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            WorldSettings settings = new WorldSettings(
                    key,
                    parseEnvironment(section.getString("environment", "NORMAL")),
                    parseType(section.getString("type", "NORMAL")),
                    "VOID".equalsIgnoreCase(section.getString("generator", "VANILLA"))
                            ? GeneratorMode.VOID
                            : GeneratorMode.VANILLA,
                    section.getLong("seed", 0L),
                    section.getBoolean("generate-structures", true),
                    section.getBoolean("hardcore", false),
                    parseDifficulty(section.getString("difficulty", "NORMAL")),
                    section.getBoolean("pvp", true));
            managed.put(key, settings);
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (WorldSettings settings : managed.values()) {
            String base = "worlds." + settings.name();
            config.set(base + ".environment", settings.environment().name());
            config.set(base + ".type", settings.type().name());
            config.set(base + ".generator", settings.generatorMode().name());
            config.set(base + ".seed", settings.seed());
            config.set(base + ".generate-structures", settings.generateStructures());
            config.set(base + ".hardcore", settings.hardcore());
            config.set(base + ".difficulty", settings.difficulty().name());
            config.set(base + ".pvp", settings.pvp());
        }
        try {
            config.save(file());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save worlds.yml", ex);
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), "worlds.yml");
    }
}
