package gg.nurmi.crate;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.crate.gui.CrateOpeningGui;
import gg.nurmi.crate.gui.CratePreviewGui;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class CrateListener implements Listener {

    private static final double KNOCKBACK_STRENGTH = 0.6;
    private static final double KNOCKBACK_UPWARD = 0.3;

    private final OneSMPPlugin plugin;
    private final CrateManager crateManager;

    public CrateListener(OneSMPPlugin plugin, CrateManager crateManager) {
        this.plugin = plugin;
        this.crateManager = crateManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        String typeKey = crateManager.crateTypeAt(block.getLocation());
        if (typeKey == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("onesmp.crate.use")) {
            plugin.messages().send(player, "general.no-permission");
            return;
        }

        CrateType type = crateManager.type(typeKey);
        if (type == null || type.rewards().isEmpty()) {
            plugin.messages().send(player, "crate.not-a-crate");
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            new CratePreviewGui(plugin, type).open(player);
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        String heldType = crateManager.keyType(held);
        if (heldType == null) {
            plugin.messages().send(player, "crate.need-key",
                    Placeholder.component("key", plugin.messages().parse(type.keyName())));
            pushBack(player, block);
            return;
        }
        if (!heldType.equals(typeKey)) {
            plugin.messages().send(player, "crate.wrong-key");
            pushBack(player, block);
            return;
        }

        long cooldownMillis = plugin.getConfig().getLong("anti-spam.action-cooldown-millis", 500);
        if (!plugin.actionCooldown().tryAcquire(player.getUniqueId(), "crate-open", cooldownMillis)) {
            return;
        }

        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
        }

        Location effectLocation = block.getLocation().add(0.5, 0.5, 0.5);
        player.getWorld().playSound(effectLocation, Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLocation, 30, 0.3, 0.3, 0.3, 0.05);

        CrateReward reward = crateManager.rollReward(type);
        CrateOpeningGui gui = new CrateOpeningGui(plugin, crateManager, type, reward);
        gui.open(player);
        gui.start(player);
    }

    // Knocks the player horizontally away from the block, falling back to their facing (then an arbitrary axis) if directly on top of it.
    private void pushBack(Player player, Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Vector direction = player.getLocation().toVector().subtract(center.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = player.getLocation().getDirection().multiply(-1);
            direction.setY(0);
        }
        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(1, 0, 0);
        }
        direction.normalize().multiply(KNOCKBACK_STRENGTH);
        direction.setY(KNOCKBACK_UPWARD);
        player.setVelocity(direction);
    }
}
