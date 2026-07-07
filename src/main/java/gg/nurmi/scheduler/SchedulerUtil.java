package gg.nurmi.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Every scheduling call in the plugin goes through here. CanvasMC is a Folia fork:
 * there is no single "main thread" — each world region ticks on its own thread, so
 * code touching a Player/Entity/Block must run on that specific region (or the
 * entity's own scheduler), never assume Bukkit's classic single-threaded scheduler.
 */
public final class SchedulerUtil {

    private final Plugin plugin;

    public SchedulerUtil(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs on the global region thread — safe for state that isn't tied to a specific world region (e.g. broadcasts). */
    public ScheduledTask runGlobal(Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }

    public ScheduledTask runGlobalDelayed(Runnable task, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks));
    }

    public ScheduledTask runGlobalRepeating(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delayTicks), periodTicks);
    }

    /** Runs on the region thread owning the given location's chunk. */
    public ScheduledTask runAtLocation(Location location, Runnable task) {
        return Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
    }

    public ScheduledTask runAtLocationDelayed(Location location, Runnable task, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), Math.max(1, delayTicks));
    }

    /** Runs on the region thread owning the given chunk — used to safely read/place blocks by coordinate. */
    public void runAtChunk(org.bukkit.World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
    }

    /** Runs on the entity's own scheduler — required for anything touching a specific Player/Entity. */
    public void runAtEntity(Entity entity, Runnable task, Runnable ifRetired) {
        entity.getScheduler().run(plugin, t -> task.run(), ifRetired);
    }

    /**
     * Returns {@code true} if the task was actually scheduled. Folia returns {@code null} from the
     * underlying call (invoking neither {@code task} nor {@code ifRetired}) when the entity is
     * already retired at call time — callers that track pending state per-entity must check this
     * and treat a {@code false} return as an immediate retirement themselves.
     */
    public boolean runAtEntityDelayed(Entity entity, Runnable task, Runnable ifRetired, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin, t -> task.run(), ifRetired, Math.max(1, delayTicks)) != null;
    }

    /** Off-region async work (DB calls, HTTP, safe-location search) that must never touch Bukkit world state directly. */
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    public ScheduledTask runAsyncRepeating(Runnable task, long delayMillis, long periodMillis) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), delayMillis, periodMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Convenience: run {@code supplier} off-thread, then hop back onto the entity's own
     * scheduler to consume the result. If the entity has been removed/retired by the time
     * the async work finishes, {@code consumer} is simply not invoked.
     */
    public <T> void supplyAsyncThenAtEntity(Supplier<T> supplier, Entity entity, Consumer<T> consumer) {
        supplyAsync(supplier).thenAccept(result -> runAtEntity(entity, () -> consumer.accept(result), () -> {}));
    }
}
