package gg.nurmi.teleport.rtp;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.Cooldown;
import gg.nurmi.util.LocationUtil;
import gg.nurmi.world.WorldPaths;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RtpManager {

    private static final int NETHER_CEILING_SCAN_START = 120;

    private final OneSMPPlugin plugin;
    private final Cooldown cooldown = new Cooldown();
    private final Random random = new Random();
    private final Map<String, ConcurrentLinkedDeque<Location>> precache = new ConcurrentHashMap<>();
    private final AtomicBoolean filling = new AtomicBoolean();

    public RtpManager(OneSMPPlugin plugin) {
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

    public boolean isEnabled(World world) {
        ConfigurationSection section = worldSection(world);
        return section != null && section.getBoolean("enabled", false);
    }

    public double cost(World world) {
        ConfigurationSection section = worldSection(world);
        return section == null ? 0 : section.getDouble("cost", 0);
    }

    // Lives in messages.yml (translatable) rather than config.yml, falling back to the world's own name if untranslated.
    public String displayName(World world) {
        String container = plugin.getConfig().getString("world-creation.container", "");
        String logicalName = WorldPaths.strip(container, world.getName());
        return plugin.messages().raw("rtp.world-names." + logicalName, logicalName);
    }

    public List<World> enabledWorlds() {
        List<World> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (isEnabled(world)) {
                worlds.add(world);
            }
        }
        return worlds;
    }

    // Tries the raw Bukkit world name first, then falls back to the container-stripped name admins actually write under rtp.worlds.
    private ConfigurationSection worldSection(World world) {
        ConfigurationSection worlds = plugin.getConfig().getConfigurationSection("rtp.worlds");
        if (worlds == null) {
            return null;
        }
        ConfigurationSection section = worlds.getConfigurationSection(world.getName());
        if (section != null) {
            return section;
        }
        String container = plugin.getConfig().getString("world-creation.container", "");
        String logicalName = WorldPaths.strip(container, world.getName());
        return logicalName.equals(world.getName()) ? null : worlds.getConfigurationSection(logicalName);
    }

    public void teleportRandomly(Player player, World world) {
        if (!isEnabled(world)) {
            plugin.messages().send(player, "rtp.world-disallowed");
            return;
        }

        boolean bypassCooldown = player.hasPermission("onesmp.rtp.admin");
        if (!bypassCooldown && isOnCooldown(player.getUniqueId())) {
            plugin.messages().send(player, "rtp.cooldown",
                    Placeholder.unparsed("seconds", String.valueOf(cooldownRemaining(player.getUniqueId()))));
            return;
        }

        search(player, world);
    }

    private void search(Player player, World world) {
        Location cached = pollCached(world);
        if (cached != null) {
            chargeAndTeleport(player, world, cached);
            return;
        }

        plugin.messages().send(player, "rtp.searching");
        findSafeLocation(world, location -> chargeAndTeleport(player, world, location),
                () -> plugin.messages().send(player, "rtp.failed"));
    }

    // Only charges once a destination exists, and refunds if the teleport itself still doesn't happen (combat block, warmup cancelled, etc.).
    private void chargeAndTeleport(Player player, World world, Location location) {
        BigDecimal cost = BigDecimal.valueOf(cost(world));
        if (cost.signum() <= 0) {
            teleport(player, location, BigDecimal.ZERO);
            return;
        }
        plugin.economy().withdraw(player.getUniqueId(), cost).thenAccept(success -> {
            if (!success) {
                plugin.messages().send(player, "rtp.insufficient-funds",
                        Placeholder.unparsed("price", plugin.economy().format(cost)));
                return;
            }
            teleport(player, location, cost);
        });
    }

    private void teleport(Player player, Location location, BigDecimal chargedCost) {
        plugin.teleportExecutor().executeSafely(player, location, "rtp.success",
                () -> applyCooldown(player.getUniqueId()),
                () -> {
                    if (chargedCost.signum() > 0) {
                        plugin.economy().deposit(player.getUniqueId(), chargedCost);
                    }
                });
    }

    public Location pollCached(World world) {
        ConcurrentLinkedDeque<Location> queue = precache.get(world.getName());
        return queue == null ? null : queue.pollFirst();
    }

    // Tops off every under-target world's cache by one location per call (relies on repeated invocation), skipping under low TPS.
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
        List<World> worldsToFill = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!isEnabled(world)) {
                continue;
            }
            ConcurrentLinkedDeque<Location> queue = precache.computeIfAbsent(world.getName(), ignored -> new ConcurrentLinkedDeque<>());
            if (queue.size() < targetSize) {
                worldsToFill.add(world);
            }
        }

        if (worldsToFill.isEmpty()) {
            filling.set(false);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(worldsToFill.size());
        for (World world : worldsToFill) {
            ConcurrentLinkedDeque<Location> queue = precache.get(world.getName());
            findSafeLocation(world, location -> {
                queue.addLast(location);
                if (remaining.decrementAndGet() == 0) {
                    filling.set(false);
                }
            }, () -> {
                if (remaining.decrementAndGet() == 0) {
                    filling.set(false);
                }
            });
        }
    }

    public void findSafeLocation(World world, Consumer<Location> onFound, Runnable onFail) {
        int minRadius = Math.max(0, plugin.getConfig().getInt("rtp.min-radius", 500));
        int maxRadius = Math.max(minRadius + 1, plugin.getConfig().getInt("rtp.max-radius", 5000));
        int maxAttempts = Math.max(1, plugin.getConfig().getInt("rtp.max-attempts", 30));
        attempt(world, minRadius, maxRadius, maxAttempts, onFound, onFail);
    }

    // Picks a random point in the radius ring and retries elsewhere if it isn't safe, until attemptsLeft runs out.
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
                    Integer y = groundY(world, x, z);
                    if (y == null) {
                        attempt(world, minRadius, maxRadius, attemptsLeft - 1, onFound, onFail);
                        return;
                    }
                    Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);
                    if (LocationUtil.isSafe(candidate) && !isAvoidedBiome(world, x, y, z)) {
                        onFound.accept(candidate);
                    } else {
                        attempt(world, minRadius, maxRadius, attemptsLeft - 1, onFound, onFail);
                    }
                }));
    }

    // For the Nether, scans down from just below the bedrock roof for an open pocket instead of using the heightmap, which would return the roof itself.
    private Integer groundY(World world, int x, int z) {
        if (world.getEnvironment() != World.Environment.NETHER) {
            return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        }
        int top = Math.min(world.getMaxHeight() - 2, NETHER_CEILING_SCAN_START);
        int bottom = world.getMinHeight() + 1;
        for (int y = top; y > bottom; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()
                    && world.getBlockAt(x, y + 1, z).getType().isAir()
                    && world.getBlockAt(x, y + 2, z).getType().isAir()) {
                return y;
            }
        }
        return null;
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
