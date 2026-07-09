package gg.nurmi.guild.gui;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.guild.Guild;
import gg.nurmi.guild.GuildManager;
import gg.nurmi.guild.GuildMember;
import gg.nurmi.guild.GuildRole;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class GuildMainGui extends AbstractGui {

    public GuildMainGui(OneSMPPlugin plugin, GuildManager guildManager, Guild guild, UUID viewerUuid) {
        super(plugin, plugin.messages().text("guild.gui-main-title",
                Placeholder.unparsed("guild_name", guild.name()), Placeholder.unparsed("guild_tag", guild.tag())), 3);

        GuildRole viewerRole = guild.member(viewerUuid).map(GuildMember::role).orElse(GuildRole.MEMBER);
        String ownerName = Optional.ofNullable(Bukkit.getOfflinePlayer(guild.owner()).getName()).orElse(guild.owner().toString());

        ItemStack info = new ItemBuilder(Material.BOOK)
                .name(plugin.messages().text("guild.gui-info-button"))
                .lore(
                        plugin.messages().text("guild.gui-owner-lore", Placeholder.unparsed("owner", ownerName)),
                        plugin.messages().text("guild.gui-members-lore",
                                Placeholder.unparsed("count", String.valueOf(guild.members().size())),
                                Placeholder.unparsed("limit", String.valueOf(guild.memberLimit()))),
                        plugin.messages().text("guild.gui-bank-lore", Placeholder.unparsed("balance", plugin.economy().format(guild.balance()))))
                .build();
        setItem(4, info);

        ItemStack membersIcon = new ItemBuilder(Material.PLAYER_HEAD).name(plugin.messages().text("guild.gui-members-button")).build();
        setButton(11, membersIcon, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new GuildMembersGui(plugin, guildManager, guild, viewerUuid).open(player);
            }
        });

        if (guild.hasHome()) {
            ItemStack homeIcon = new ItemBuilder(Material.RED_BED).name(plugin.messages().text("guild.gui-home-button")).build();
            setButton(13, homeIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    plugin.teleportExecutor().executeSafely(player, guild.homeLocation());
                }
            });
        }

        ItemStack chatToggle = new ItemBuilder(Material.WRITABLE_BOOK).name(plugin.messages().text("guild.gui-toggle-chat-button")).build();
        setButton(17, chatToggle, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                boolean enabled = plugin.guildChat().toggle(player.getUniqueId());
                plugin.messages().send(player, enabled ? "guild.chat-enabled" : "guild.chat-disabled");
            }
        });

        if (viewerRole.canManageSettings()) {
            ItemStack setHomeIcon = new ItemBuilder(Material.COMPASS).name(plugin.messages().text("guild.gui-set-home-button")).build();
            setButton(15, setHomeIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    guildManager.setHome(guild.id(), player.getLocation()).thenRun(() -> plugin.messages().send(player, "guild.home-set"));
                }
            });

            ItemStack disbandIcon = new ItemBuilder(Material.TNT).name(plugin.messages().text("guild.gui-disband-button")).build();
            setButton(21, disbandIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    guildManager.disbandGuild(guild.id()).thenAccept(deleted -> {
                        if (deleted) {
                            plugin.messages().send(player, "guild.disbanded");
                        }
                    });
                    player.closeInventory();
                }
            });
        } else {
            ItemStack leaveIcon = new ItemBuilder(Material.OAK_DOOR).name(plugin.messages().text("guild.gui-leave-button")).build();
            setButton(21, leaveIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    guildManager.removeMember(guild.id(), player.getUniqueId()).thenRun(() ->
                            plugin.messages().send(player, "guild.left", Placeholder.unparsed("guild", guild.name())));
                    player.closeInventory();
                }
            });
        }

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().text("gui.close")).build();
        setButton(22, close, event -> event.getWhoClicked().closeInventory());
    }
}
