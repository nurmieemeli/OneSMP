package gg.nurmi.message;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.RecentAttackerTracker;
import gg.nurmi.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

// Overrides vanilla's death message (client-side translated to each viewer's own language) with a MiniMessage
// template from messages.yml, so every viewer sees it in the server's configured language instead.
public final class DeathMessageListener implements Listener {

    private final OneSMPPlugin plugin;
    private final RecentAttackerTracker attackerTracker;

    public DeathMessageListener(OneSMPPlugin plugin, RecentAttackerTracker attackerTracker) {
        this.plugin = plugin;
        this.attackerTracker = attackerTracker;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        TagResolver victimPlaceholder = Placeholder.unparsed("victim", victim.getName());

        RecentAttackerTracker.KillCredit credit = attackerTracker.resolve(victim);
        Component message;
        if (credit != null) {
            message = plugin.messages().text("death.pvp", victimPlaceholder,
                    Placeholder.unparsed("killer", credit.name()));
        } else {
            EntityDamageEvent lastDamage = victim.getLastDamageCause();
            if (lastDamage instanceof EntityDamageByEntityEvent byEntity
                    && byEntity.getDamager() instanceof LivingEntity mob && !(mob instanceof Player)) {
                message = plugin.messages().text("death.mob", victimPlaceholder,
                        Placeholder.unparsed("mob", TextUtil.entityName(plugin, mob.getType())));
            } else {
                String key = causeKey(lastDamage != null ? lastDamage.getCause() : null);
                message = plugin.messages().text(key, victimPlaceholder);
            }
        }
        event.deathMessage(message);
    }

    private String causeKey(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return "death.generic";
        }
        return switch (cause) {
            case FALL -> "death.cause-fall";
            case FIRE -> "death.cause-fire";
            case FIRE_TICK -> "death.cause-fire-tick";
            case LAVA -> "death.cause-lava";
            case DROWNING -> "death.cause-drowning";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "death.cause-explosion";
            case VOID -> "death.cause-void";
            case LIGHTNING -> "death.cause-lightning";
            case STARVATION -> "death.cause-starvation";
            case POISON -> "death.cause-poison";
            case MAGIC -> "death.cause-magic";
            case WITHER -> "death.cause-wither";
            case FALLING_BLOCK -> "death.cause-falling-block";
            case THORNS -> "death.cause-thorns";
            case DRAGON_BREATH -> "death.cause-dragon-breath";
            case FLY_INTO_WALL -> "death.cause-fly-into-wall";
            case HOT_FLOOR -> "death.cause-hot-floor";
            case CRAMMING -> "death.cause-cramming";
            case FREEZE -> "death.cause-freeze";
            case SONIC_BOOM -> "death.cause-sonic-boom";
            case WORLD_BORDER -> "death.cause-world-border";
            case SUFFOCATION -> "death.cause-suffocation";
            case MELTING -> "death.cause-melting";
            case CONTACT -> "death.cause-contact";
            default -> "death.generic";
        };
    }
}
