package gg.nurmi.guild.gui;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.guild.Guild;
import gg.nurmi.guild.GuildManager;
import gg.nurmi.guild.GuildMember;
import gg.nurmi.guild.GuildRole;
import gg.nurmi.gui.AbstractGui;
import gg.nurmi.gui.Pagination;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public final class GuildMembersGui extends AbstractGui {

    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    public GuildMembersGui(CanvasSuitePlugin plugin, GuildManager guildManager, Guild guild, UUID viewerUuid) {
        super(plugin.messages().parse("<white>Members of <guild_name>", Placeholder.unparsed("guild_name", guild.name())), 6);

        GuildRole viewerRole = guild.member(viewerUuid).map(GuildMember::role).orElse(GuildRole.MEMBER);
        Pagination<GuildMember> pagination = new Pagination<>(guild.members(), PAGE_SIZE);
        List<GuildMember> members = pagination.page(0);

        int slot = 0;
        for (GuildMember member : members) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.uuid());
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : member.uuid().toString();

            ItemStack icon = new ItemBuilder(Material.PLAYER_HEAD)
                    .name(plugin.messages().parse("<white>" + name + " <gray>(" + member.role().name() + ")"))
                    .lore(viewerRole.canManageMembers() && !guild.isOwner(member.uuid()) && !member.uuid().equals(viewerUuid)
                            ? List.of(
                                    plugin.messages().parse("<gray>Click to kick"),
                                    plugin.messages().parse("<dark_gray>Shift-click to promote/demote (owner only)"))
                            : List.of())
                    .build();
            if (icon.getItemMeta() instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(offlinePlayer);
                icon.setItemMeta(skullMeta);
            }

            setButton(slot++, icon, event -> {
                if (!(event.getWhoClicked() instanceof Player player)) {
                    return;
                }
                if (guild.isOwner(member.uuid()) || member.uuid().equals(viewerUuid)) {
                    return;
                }
                if (event.getClick().isShiftClick()) {
                    if (!viewerRole.canManageSettings()) {
                        plugin.messages().send(player, "guild.no-permission-role");
                        return;
                    }
                    GuildRole newRole = member.role() == GuildRole.OFFICER ? GuildRole.MEMBER : GuildRole.OFFICER;
                    guildManager.setRole(guild.id(), member.uuid(), newRole).thenRun(() -> {
                        plugin.messages().send(player, newRole == GuildRole.OFFICER ? "guild.promoted" : "guild.demoted",
                                Placeholder.unparsed("target", name), Placeholder.unparsed("role", newRole.name()));
                        new GuildMembersGui(plugin, guildManager, guild, viewerUuid).open(player);
                    });
                } else {
                    if (!viewerRole.canManageMembers()) {
                        plugin.messages().send(player, "guild.no-permission-role");
                        return;
                    }
                    guildManager.removeMember(guild.id(), member.uuid()).thenRun(() -> {
                        plugin.messages().send(player, "guild.kicked", Placeholder.unparsed("target", name));
                        Player targetOnline = Bukkit.getPlayer(member.uuid());
                        if (targetOnline != null) {
                            plugin.messages().send(targetOnline, "guild.kicked-notice", Placeholder.unparsed("guild", guild.name()));
                        }
                    });
                    player.closeInventory();
                }
            });
        }

        ItemStack back = new ItemBuilder(Material.ARROW).name(plugin.messages().parse("<gray>« Back")).build();
        setButton(BACK_SLOT, back, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new GuildMainGui(plugin, guildManager, guild, viewerUuid).open(player);
            }
        });

        ItemStack close = new ItemBuilder(Material.BARRIER).name(plugin.messages().parse("<red>Close")).build();
        setButton(CLOSE_SLOT, close, event -> event.getWhoClicked().closeInventory());
    }
}
