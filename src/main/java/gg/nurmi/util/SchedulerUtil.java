package gg.nurmi.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SchedulerUtil {

    private final Plugin plugin;

    public SchedulerUtil(Plugin plugin) {
        this.plugin = plugin;
    }

    public ScheduledTask runGlobal(Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    public ScheduledTask runGlobalDelayed(Runnable task, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), Math.max(1, delayTicks));
    }

    public ScheduledTask runGlobalRepeating(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> task.run(), Math.max(1, delayTicks), periodTicks);
    }

    public ScheduledTask runAtLocation(Location location, Runnable task) {
        return Bukkit.getRegionScheduler().run(plugin, location, ignored -> task.run());
    }

    public ScheduledTask runAtLocationDelayed(Location location, Runnable task, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, ignored -> task.run(), Math.max(1, delayTicks));
    }

    public void runAtChunk(org.bukkit.World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
    }

    public void runAtEntity(Entity entity, Runnable task, Runnable ifRetired) {
        entity.getScheduler().run(plugin, ignored -> task.run(), ifRetired);
    }

    public boolean runAtEntityDelayed(Entity entity, Runnable task, Runnable ifRetired, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin, ignored -> task.run(), ifRetired, Math.max(1, delayTicks)) != null;
    }

    public ScheduledTask runAtEntityRepeating(Entity entity, Runnable task, Runnable ifRetired, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(plugin, ignored -> task.run(), ifRetired, Math.max(1, delayTicks), periodTicks);
    }

    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    public ScheduledTask runAsyncRepeating(Runnable task, long delayMillis, long periodMillis) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> task.run(), delayMillis, periodMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public <T> void supplyAsyncThenAtEntity(Supplier<T> supplier, Entity entity, Consumer<T> consumer) {
        supplyAsync(supplier).thenAccept(result -> runAtEntity(entity, () -> consumer.accept(result), () -> {}));
    }
}
