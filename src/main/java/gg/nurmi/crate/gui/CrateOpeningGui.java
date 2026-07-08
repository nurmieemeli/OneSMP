package gg.nurmi.crate.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.crate.CrateManager;
import gg.nurmi.crate.CrateReward;
import gg.nurmi.crate.CrateType;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Purely cosmetic slot-machine-style reel: the reward was already rolled before this GUI opens,
 * this just spends a few seconds visually "landing" on it before granting it for real. Closing the
 * GUI early (or the animation finishing on its own) both fall through to the same one-shot finish.
 */
public final class CrateOpeningGui extends AbstractGui {

    private static final int ROWS = 3;
    private static final int REEL_SIZE = 9;
    private static final int ROW_START = 9;
    private static final int CENTER_INDEX = 4;
    private static final int TOTAL_FRAMES = 24;
    private static final long INITIAL_DELAY = 1;
    private static final long MAX_DELAY = 9;
    private static final long HOLD_TICKS = 40;

    private final CanvasSuitePlugin plugin;
    private final CrateManager crateManager;
    private final CrateType type;
    private final CrateReward reward;
    private final List<CrateReward> reel = new ArrayList<>(REEL_SIZE);
    private boolean finished;

    public CrateOpeningGui(CanvasSuitePlugin plugin, CrateManager crateManager, CrateType type, CrateReward reward) {
        super(plugin, plugin.messages().parse("<gradient:#fbbf24:#f59e0b><bold><type></bold></gradient>",
                Placeholder.component("type", plugin.messages().parse(type.displayName()))), ROWS);
        this.plugin = plugin;
        this.crateManager = crateManager;
        this.type = type;
        this.reward = reward;

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(plugin.messages().parse(" ")).build();
        ItemStack pointer = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name(plugin.messages().parse("<green>▼")).build();
        for (int i = 0; i < REEL_SIZE; i++) {
            setItem(i, i == CENTER_INDEX ? pointer : border);
            setItem(2 * ROW_START + i, i == CENTER_INDEX ? pointer : border);
        }

        for (int i = 0; i < REEL_SIZE; i++) {
            reel.add(randomDecoy());
        }
        renderReel(false);
    }

    public void start(Player player) {
        plugin.scheduler().runAtEntityDelayed(player, () -> tick(player, 1), () -> finish(player), INITIAL_DELAY);
    }

    private void tick(Player player, int frame) {
        if (finished) {
            return;
        }
        reel.remove(0);
        reel.add(frame == TOTAL_FRAMES - CENTER_INDEX ? reward : randomDecoy());

        boolean finalFrame = frame >= TOTAL_FRAMES;
        renderReel(finalFrame);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 0.7f + (float) frame / TOTAL_FRAMES);

        if (finalFrame) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            plugin.scheduler().runAtEntityDelayed(player, () -> finish(player), () -> finish(player), HOLD_TICKS);
            return;
        }

        long delay = Math.round(INITIAL_DELAY + (MAX_DELAY - INITIAL_DELAY) * Math.pow((double) frame / TOTAL_FRAMES, 3));
        plugin.scheduler().runAtEntityDelayed(player, () -> tick(player, frame + 1), () -> finish(player), Math.max(1, delay));
    }

    private void renderReel(boolean finalFrame) {
        for (int i = 0; i < REEL_SIZE; i++) {
            boolean landedOnWinner = finalFrame && i == CENTER_INDEX;
            setItem(ROW_START + i, iconFor(reel.get(i), landedOnWinner));
        }
    }

    private CrateReward randomDecoy() {
        List<CrateReward> pool = type.rewards();
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private ItemStack iconFor(CrateReward reward, boolean glow) {
        Material material = reward.items().keySet().stream().findFirst()
                .orElse(reward.money() > 0 ? Material.GOLD_INGOT : Material.NETHER_STAR);
        return new ItemBuilder(material)
                .name(plugin.messages().parse(reward.displayName()))
                .glow(glow)
                .build();
    }

    private void finish(Player player) {
        if (finished) {
            return;
        }
        finished = true;
        player.closeInventory();
        crateManager.grantReward(player, type, reward);
    }

    @Override
    protected void onClose(InventoryCloseEvent event) {
        if (!finished && event.getPlayer() instanceof Player player) {
            finish(player);
        }
    }
}
