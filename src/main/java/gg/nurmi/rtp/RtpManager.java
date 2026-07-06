package gg.nurmi.rtp;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.Cooldown;
import gg.nurmi.util.LocationUtil;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Picks a random, safe location within a configurable radius. Candidate chunks are loaded via
 * World#getChunkAtAsync (safe to call from any thread) and then re-entered on the owning region
 * thread via RegionScheduler before any block is actually read, per Folia's threading rules.
 */
public final class RtpManager {

    private final CanvasSuitePlugin plugin;
    private final Cooldown cooldown = new Cooldown();
    private final Random random = new Random();

    public RtpManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOnCooldown(UUID uuid) {
        return cooldown.isOnCooldown(uuid);
    }

    public long cooldownRemaining(UUID uuid) {
        return cooldown.remainingSeconds(uuid);
    }

    public void applyCooldown(UUID uuid) {
        int seconds = plugin.getConfig().getInt("rtp.cooldown-seconds", 300);
        if (seconds > 0) {
            cooldown.set(uuid, seconds);
        }
    }

    public double cost() {
        return plugin.getConfig().getDouble("rtp.cost", 0);
    }

    public boolean isWorldAllowed(World world) {
        List<String> disallowed = plugin.getConfig().getStringList("rtp.disallowed-worlds");
        return !disallowed.contains(world.getName());
    }

    /** Safe to call from any thread. {@code onFound}/{@code onFail} run off the calling thread. */
    public void findSafeLocation(World world, Consumer<Location> onFound, Runnable onFail) {
        int minRadius = Math.max(0, plugin.getConfig().getInt("rtp.min-radius", 500));
        int maxRadius = Math.max(minRadius + 1, plugin.getConfig().getInt("rtp.max-radius", 5000));
        int maxAttempts = Math.max(1, plugin.getConfig().getInt("rtp.max-attempts", 30));
        attempt(world, minRadius, maxRadius, maxAttempts, onFound, onFail);
    }

    private void attempt(World world, int minRadius, int maxRadius, int attemptsLeft, Consumer<Location> onFound, Runnable onFail) {
        if (attemptsLeft <= 0) {
            onFail.run();
            return;
        }

        double angle = random.nextDouble() * Math.PI * 2;
        int radius = minRadius + random.nextInt(Math.max(1, maxRadius - minRadius));
        int x = (int) Math.round(Math.cos(angle) * radius);
        int z = (int) Math.round(Math.sin(angle) * radius);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        world.getChunkAtAsync(chunkX, chunkZ).thenRun(() ->
                plugin.scheduler().runAtChunk(world, chunkX, chunkZ, () -> {
                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);
                    if (LocationUtil.isSafe(candidate) && !isAvoidedBiome(world, x, y, z)) {
                        onFound.accept(candidate);
                    } else {
                        attempt(world, minRadius, maxRadius, attemptsLeft - 1, onFound, onFail);
                    }
                }));
    }

    private boolean isAvoidedBiome(World world, int x, int y, int z) {
        List<String> avoidBiomes = plugin.getConfig().getStringList("rtp.avoid-biomes");
        if (avoidBiomes.isEmpty()) {
            return false;
        }
        String biomeKey = world.getBiome(x, y, z).getKey().toString();
        return avoidBiomes.contains(biomeKey);
    }
}
