package gg.nurmi.world;

import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Mutable settings for a single world - doubles as both the persisted record of an existing
 * managed world and the live "pending creation" session an admin edits through the creation GUI.
 */
public final class WorldSettings {

    public enum GeneratorMode { VANILLA, VOID }

    private final String name;
    private World.Environment environment;
    private WorldType type;
    private GeneratorMode generatorMode;
    private long seed;
    private boolean generateStructures;
    private boolean hardcore;
    private boolean keepSpawnInMemory;
    private Difficulty difficulty;
    private boolean pvp;

    public WorldSettings(String name, World.Environment environment, WorldType type, GeneratorMode generatorMode,
                          long seed, boolean generateStructures, boolean hardcore, boolean keepSpawnInMemory,
                          Difficulty difficulty, boolean pvp) {
        this.name = name;
        this.environment = environment;
        this.type = type;
        this.generatorMode = generatorMode;
        this.seed = seed;
        this.generateStructures = generateStructures;
        this.hardcore = hardcore;
        this.keepSpawnInMemory = keepSpawnInMemory;
        this.difficulty = difficulty;
        this.pvp = pvp;
    }

    public String name() {
        return name;
    }

    public World.Environment environment() {
        return environment;
    }

    public WorldType type() {
        return type;
    }

    public GeneratorMode generatorMode() {
        return generatorMode;
    }

    public long seed() {
        return seed;
    }

    public boolean generateStructures() {
        return generateStructures;
    }

    public boolean hardcore() {
        return hardcore;
    }

    public boolean keepSpawnInMemory() {
        return keepSpawnInMemory;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public boolean pvp() {
        return pvp;
    }

    public void cycleEnvironment() {
        environment = switch (environment) {
            case NORMAL -> World.Environment.NETHER;
            case NETHER -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };
    }

    public void cycleType() {
        type = switch (type) {
            case NORMAL -> WorldType.FLAT;
            case FLAT -> WorldType.LARGE_BIOMES;
            case LARGE_BIOMES -> WorldType.AMPLIFIED;
            default -> WorldType.NORMAL;
        };
    }

    public void cycleGeneratorMode() {
        generatorMode = generatorMode == GeneratorMode.VANILLA ? GeneratorMode.VOID : GeneratorMode.VANILLA;
    }

    public void cycleDifficulty() {
        difficulty = switch (difficulty) {
            case PEACEFUL -> Difficulty.EASY;
            case EASY -> Difficulty.NORMAL;
            case NORMAL -> Difficulty.HARD;
            case HARD -> Difficulty.PEACEFUL;
        };
    }

    public void toggleGenerateStructures() {
        generateStructures = !generateStructures;
    }

    public void toggleHardcore() {
        hardcore = !hardcore;
    }

    public void toggleKeepSpawnInMemory() {
        keepSpawnInMemory = !keepSpawnInMemory;
    }

    public void togglePvp() {
        pvp = !pvp;
    }

    public void rerollSeed() {
        seed = ThreadLocalRandom.current().nextLong();
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }
}
