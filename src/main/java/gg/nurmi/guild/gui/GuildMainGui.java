package gg.nurmi.guild.gui;

import gg.nurmi.CanvasSuitePlugin;
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

    public GuildMainGui(CanvasSuitePlugin plugin, GuildManager guildManager, Guild guild, UUID viewerUuid) {
        super(plugin.messages().parse("<gradient:#fbbf24:#f59e0b><bold><guild_name></bold></gradient> <gray>[<guild_tag>]</gray>",
                Placeholder.unparsed("guild_name", guild.name()), Placeholder.unparsed("guild_tag", guild.tag())), 3);

        GuildRole viewerRole = guild.member(viewerUuid).map(GuildMember::role).orElse(GuildRole.MEMBER);
        String ownerName = Optional.ofNullable(Bukkit.getOfflinePlayer(guild.owner()).getName()).orElse(guild.owner().toString());

        ItemStack info = new ItemBuilder(Material.BOOK)
                .name(plugin.messages().parse("<yellow>Guild Info"))
                .lore(
                        plugin.messages().parse("<gray>Owner: <white>" + ownerName),
                        plugin.messages().parse("<gray>Members: <white>" + guild.members().size() + "/" + guild.memberLimit()),
                        plugin.messages().parse("<gray>Bank: <green>" + plugin.economy().format(guild.balance())))
                .build();
        setItem(4, info);

        ItemStack membersIcon = new ItemBuilder(Material.PLAYER_HEAD).name(plugin.messages().parse("<white>Members")).build();
        setButton(11, membersIcon, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new GuildMembersGui(plugin, guildManager, guild, viewerUuid).open(player);
            }
        });

        if (guild.hasHome()) {
            ItemStack homeIcon = new ItemBuilder(Material.RED_BED).name(plugin.messages().parse("<white>Guild Home")).build();
            setButton(13, homeIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    plugin.teleportExecutor().executeSafely(player, guild.homeLocation());
                }
            });
        }

        ItemStack chatToggle = new ItemBuilder(Material.WRITABLE_BOOK).name(plugin.messages().parse("<white>Toggle Guild Chat")).build();
        setButton(17, chatToggle, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                boolean enabled = plugin.guildChat().toggle(player.getUniqueId());
                plugin.messages().send(player, enabled ? "guild.chat-enabled" : "guild.chat-disabled");
            }
        });

        if (viewerRole.canManageSettings()) {
            ItemStack setHomeIcon = new ItemBuilder(Material.COMPASS).name(plugin.messages().parse("<white>Set Guild Home Here")).build();
            setButton(15, setHomeIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    guildManager.setHome(guild.id(), player.getLocation()).thenRun(() -> plugin.messages().send(player, "guild.home-set"));
                }
            });

            ItemStack disbandIcon = new ItemBuilder(Material.TNT).name(plugin.messages().parse("<red>Disband Guild")).build();
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
            ItemStack leaveIcon = new ItemBuilder(Material.OAK_DOOR).name(plugin.messages().parse("<red>Leave Guild")).build();
            setButton(21, leaveIcon, event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    guildManager.removeMember(guild.id(), player.getUniqueId()).thenRun(() ->
                            plugin.messages().send(player, "guild.left", Placeholder.unparsed("guild", guild.name())));
                    player.closeInventory();
                }
            });
        }

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().parse("<red>Close")).build();
        setButton(22, close, event -> event.getWhoClicked().closeInventory());
    }
}
