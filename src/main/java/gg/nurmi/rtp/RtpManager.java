package gg.nurmi.rtp;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.Cooldown;
import gg.nurmi.util.LocationUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Picks a random, safe location within a configurable radius. Candidate chunks are loaded via
 * World#getChunkAtAsync (safe to call from any thread) and then re-entered on the owning region
 * thread via RegionScheduler before any block is actually read, per Folia's threading rules.
 *
 * <p>Also keeps a small per-world precache of already-found safe locations so /rtp can usually
 * teleport instantly instead of waiting on a fresh search - see {@link #precacheTick()}.</p>
 */
public final class RtpManager {

    private final CanvasSuitePlugin plugin;
    private final Cooldown cooldown = new Cooldown();
    private final Random random = new Random();
    private final Map<String, ConcurrentLinkedDeque<Location>> precache = new ConcurrentHashMap<>();
    private final AtomicBoolean filling = new AtomicBoolean();

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

    /** Whether random teleport is configured on for this world at all - opt-in per world, not a blacklist. */
    public boolean isEnabled(World world) {
        ConfigurationSection section = worldSection(world);
        return section != null && section.getBoolean("enabled", false);
    }

    /** This world's configured RTP fee; 0 if unset or the world isn't RTP-enabled. */
    public double cost(World world) {
        ConfigurationSection section = worldSection(world);
        return section == null ? 0 : section.getDouble("cost", 0);
    }

    /** Every currently-loaded world with RTP enabled, in {@link Bukkit#getWorlds()} order. */
    public List<World> enabledWorlds() {
        List<World> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (isEnabled(world)) {
                worlds.add(world);
            }
        }
        return worlds;
    }

    private ConfigurationSection worldSection(World world) {
        ConfigurationSection worlds = plugin.getConfig().getConfigurationSection("rtp.worlds");
        return worlds == null ? null : worlds.getConfigurationSection(world.getName());
    }

    /**
     * Full /rtp flow for a player into a specific world: permission-gated cooldown bypass, the
     * world's configured fee (if any), then a cached or freshly-searched safe location. Sends its
     * own feedback messages, so callers (the command and the world-select GUI) just resolve the
     * target world and call this.
     */
    public void teleportRandomly(Player player, World world) {
        if (!isEnabled(world)) {
            plugin.messages().send(player, "rtp.world-disallowed");
            return;
        }

        boolean bypassCooldown = player.hasPermission("canvassuite.rtp.admin");
        if (!bypassCooldown && isOnCooldown(player.getUniqueId())) {
            plugin.messages().send(player, "rtp.cooldown",
                    Placeholder.unparsed("seconds", String.valueOf(cooldownRemaining(player.getUniqueId()))));
            return;
        }

        BigDecimal cost = BigDecimal.valueOf(cost(world));
        if (cost.signum() > 0) {
            plugin.economy().withdraw(player.getUniqueId(), cost).thenAccept(success -> {
                if (!success) {
                    plugin.messages().send(player, "rtp.insufficient-funds",
                            Placeholder.unparsed("price", plugin.economy().format(cost)));
                    return;
                }
                search(player, world);
            });
        } else {
            search(player, world);
        }
    }

    private void search(Player player, World world) {
        Location cached = pollCached(world);
        if (cached != null) {
            teleport(player, cached);
            return;
        }

        plugin.messages().send(player, "rtp.searching");
        findSafeLocation(world, location -> teleport(player, location),
                () -> plugin.messages().send(player, "rtp.failed"));
    }

    private void teleport(Player player, Location location) {
        applyCooldown(player.getUniqueId());
        plugin.scheduler().runAtEntity(player, () -> {
            plugin.backs().record(player);
            player.teleportAsync(location).thenAccept(success -> {
                if (success) {
                    plugin.messages().send(player, "rtp.success");
                }
            });
        }, () -> {});
    }

    /** Pulls one pre-cached safe location for the world, if any are ready; null if the cache is currently empty. */
    public Location pollCached(World world) {
        ConcurrentLinkedDeque<Location> queue = precache.get(world.getName());
        return queue == null ? null : queue.pollFirst();
    }

    /**
     * Tops off one allowed world's precache by a single location, but only while the server is
     * keeping up (recent TPS at or above the configured floor) - "whenever the server can handle
     * it." Meant to be driven by a repeating task so the search cost spreads out over time instead
     * of bursting all {@code target-size} lookups back to back.
     */
    public void precacheTick() {
        if (!plugin.getConfig().getBoolean("rtp.precache.enabled", true) || !filling.compareAndSet(false, true)) {
            return;
        }
        double minTps = plugin.getConfig().getDouble("rtp.precache.min-tps", 18.0);
        if (Bukkit.getTPS()[0] < minTps) {
            filling.set(false);
            return;
        }

        int targetSize = Math.max(0, plugin.getConfig().getInt("rtp.precache.target-size", 15));
        for (World world : Bukkit.getWorlds()) {
            if (!isEnabled(world)) {
                continue;
            }
            ConcurrentLinkedDeque<Location> queue = precache.computeIfAbsent(world.getName(), name -> new ConcurrentLinkedDeque<>());
            if (queue.size() >= targetSize) {
                continue;
            }

            findSafeLocation(world, location -> {
                queue.addLast(location);
                filling.set(false);
            }, () -> filling.set(false));
            return;
        }
        filling.set(false);
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
