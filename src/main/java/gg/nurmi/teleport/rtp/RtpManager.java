package gg.nurmi.teleport.rtp;

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

    public boolean isEnabled(World world) {
        ConfigurationSection section = worldSection(world);
        return section != null && section.getBoolean("enabled", false);
    }

    public double cost(World world) {
        ConfigurationSection section = worldSection(world);
        return section == null ? 0 : section.getDouble("cost", 0);
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

    private ConfigurationSection worldSection(World world) {
        ConfigurationSection worlds = plugin.getConfig().getConfigurationSection("rtp.worlds");
        return worlds == null ? null : worlds.getConfigurationSection(world.getName());
    }

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
        plugin.teleportExecutor().executeSafely(player, location, "rtp.success");
    }

    public Location pollCached(World world) {
        ConcurrentLinkedDeque<Location> queue = precache.get(world.getName());
        return queue == null ? null : queue.pollFirst();
    }

    // Tops off one world's cache by at most one location per call (relies on being invoked repeatedly), skipping entirely under low TPS.
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
            ConcurrentLinkedDeque<Location> queue = precache.computeIfAbsent(world.getName(), ignored -> new ConcurrentLinkedDeque<>());
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

    public void findSafeLocation(World world, Consumer<Location> onFound, Runnable onFail) {
        int minRadius = Math.max(0, plugin.getConfig().getInt("rtp.min-radius", 500));
        int maxRadius = Math.max(minRadius + 1, plugin.getConfig().getInt("rtp.max-radius", 5000));
        int maxAttempts = Math.max(1, plugin.getConfig().getInt("rtp.max-attempts", 30));
        attempt(world, minRadius, maxRadius, maxAttempts, onFound, onFail);
    }

    // Picks a random point in the radius ring, loads its chunk, and retries elsewhere if it isn't safe, until attemptsLeft runs out.
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
