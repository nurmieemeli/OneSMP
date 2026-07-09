package gg.nurmi.guild;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.guild.gui.GuildMainGui;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

public final class GuildCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "disband", "invite", "accept", "kick", "promote", "demote",
            "sethome", "home", "info", "list", "chat", "leave", "gui");

    private final OneSMPPlugin plugin;
    private final GuildManager guildManager;
    private final GuildChatToggle chatToggle;

    public GuildCommand(OneSMPPlugin plugin, GuildManager guildManager, GuildChatToggle chatToggle) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.chatToggle = chatToggle;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("onesmp.guild.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            openGui(player);
            return true;
        }

        String sub = plugin.subcommandAliases().resolve("guild", args[0]);
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "disband" -> handleDisband(player);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "kick" -> handleKick(player, args);
            case "promote" -> handlePromote(player, args, true);
            case "demote" -> handlePromote(player, args, false);
            case "sethome" -> handleSetHome(player);
            case "home" -> handleHome(player);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player);
            case "chat" -> handleChat(player);
            case "leave" -> handleLeave(player);
            case "gui" -> openGui(player);
            default -> plugin.messages().send(player, "general.unknown-command",
                    Placeholder.unparsed("usage", "/guild <" + String.join("|", SUBCOMMANDS) + ">"));
        }
        return true;
    }

    private void openGui(Player player) {
        guildManager.getGuildByMember(player.getUniqueId()).thenAccept(optionalGuild -> {
            if (optionalGuild.isEmpty()) {
                plugin.messages().send(player, "guild.not-in-guild");
                return;
            }
            plugin.scheduler().runAtEntity(player,
                    () -> new GuildMainGui(plugin, guildManager, optionalGuild.get(), player.getUniqueId()).open(player), () -> {});
        });
    }

    private void requireGuild(Player player, Consumer<Guild> action) {
        guildManager.getGuildByMember(player.getUniqueId()).thenAccept(optionalGuild -> {
            if (optionalGuild.isEmpty()) {
                plugin.messages().send(player, "guild.not-in-guild");
                return;
            }
            action.accept(optionalGuild.get());
        });
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/guild create <name> <tag>"));
            return;
        }
        String name = args[1];
        String tag = args[2];

        guildManager.getGuildByMember(player.getUniqueId()).thenAccept(existing -> {
            if (existing.isPresent()) {
                plugin.messages().send(player, "guild.already-in-guild");
                return;
            }

            double cost = plugin.getConfig().getDouble("guild.creation-cost", 0);
            Runnable doCreate = () -> guildManager.createGuild(player.getUniqueId(), name, tag).thenAccept(result -> {
                if (result != GuildManager.CreateResult.SUCCESS && cost > 0) {
                    plugin.economy().deposit(player.getUniqueId(), BigDecimal.valueOf(cost)); // refund the upfront withdrawal below
                }
                switch (result) {
                    case SUCCESS -> {
                        guildManager.refreshCache(player.getUniqueId());
                        plugin.messages().send(player, "guild.created",
                                Placeholder.unparsed("name", name), Placeholder.unparsed("tag", tag));
                    }
                    case NAME_TAKEN -> plugin.messages().send(player, "guild.name-taken");
                    case TAG_TAKEN -> plugin.messages().send(player, "guild.tag-taken");
                    case INVALID_NAME -> plugin.messages().send(player, "guild.invalid-name",
                            Placeholder.unparsed("min", String.valueOf(plugin.getConfig().getInt("guild.min-name-length", 3))),
                            Placeholder.unparsed("max", String.valueOf(plugin.getConfig().getInt("guild.max-name-length", 16))));
                    case INVALID_TAG -> plugin.messages().send(player, "guild.invalid-tag",
                            Placeholder.unparsed("max", String.valueOf(plugin.getConfig().getInt("guild.max-tag-length", 5))));
                }
            });

            if (cost > 0) {
                plugin.economy().withdraw(player.getUniqueId(), BigDecimal.valueOf(cost)).thenAccept(success -> {
                    if (!success) {
                        plugin.messages().send(player, "guild.insufficient-funds-create",
                                Placeholder.unparsed("price", plugin.economy().format(BigDecimal.valueOf(cost))));
                        return;
                    }
                    doCreate.run();
                });
            } else {
                doCreate.run();
            }
        });
    }

    private void handleDisband(Player player) {
        requireGuild(player, guild -> {
            if (!guild.isOwner(player.getUniqueId())) {
                plugin.messages().send(player, "guild.no-permission-role");
                return;
            }
            guildManager.disbandGuild(guild.id()).thenAccept(deleted -> {
                if (deleted) {
                    guild.members().forEach(member -> guildManager.refreshCache(member.uuid()));
                    plugin.messages().send(player, "guild.disbanded");
                }
            });
        });
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/guild invite <player>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(player, "general.player-not-found", Placeholder.unparsed("target", args[1]));
            return;
        }

        requireGuild(player, guild -> {
            GuildRole role = guild.member(player.getUniqueId()).map(GuildMember::role).orElse(GuildRole.MEMBER);
            if (!role.canManageMembers()) {
                plugin.messages().send(player, "guild.no-permission-role");
                return;
            }
            if (guild.member(target.getUniqueId()).isPresent()) {
                plugin.messages().send(player, "guild.target-in-guild", Placeholder.unparsed("target", target.getName()));
                return;
            }
            if (guild.members().size() >= guild.memberLimit()) {
                plugin.messages().send(player, "guild.member-limit-reached", Placeholder.unparsed("limit", String.valueOf(guild.memberLimit())));
                return;
            }
            guildManager.invite(target.getUniqueId(), guild.id());
            plugin.messages().send(player, "guild.invited", Placeholder.unparsed("target", target.getName()));
            plugin.messages().send(target, "guild.invite-received", Placeholder.unparsed("guild", guild.name()));
        });
    }

    private void handleAccept(Player player) {
        Integer guildId = guildManager.pendingInvite(player.getUniqueId());
        if (guildId == null) {
            plugin.messages().send(player, "guild.invite-none");
            return;
        }
        guildManager.getGuildByMember(player.getUniqueId()).thenAccept(existing -> {
            if (existing.isPresent()) {
                plugin.messages().send(player, "guild.already-in-guild");
                return;
            }
            guildManager.clearInvite(player.getUniqueId());
            guildManager.addMember(guildId, player.getUniqueId(), GuildRole.MEMBER).thenAccept(ignored -> {
                guildManager.refreshCache(player.getUniqueId());
                guildManager.getGuildByMember(player.getUniqueId()).thenAccept(joined ->
                        plugin.messages().send(player, "guild.joined",
                                Placeholder.unparsed("guild", joined.map(Guild::name).orElse(plugin.messages().raw("general.unknown-name")))));
            });
        });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", "/guild kick <player>"));
            return;
        }
        String targetName = args[1];

        requireGuild(player, guild -> {
            GuildRole actorRole = guild.member(player.getUniqueId()).map(GuildMember::role).orElse(GuildRole.MEMBER);
            if (!actorRole.canManageMembers()) {
                plugin.messages().send(player, "guild.no-permission-role");
                return;
            }
            Optional<UUID> targetUuid = resolveMemberUuid(guild, targetName);
            if (targetUuid.isEmpty()) {
                plugin.messages().send(player, "guild.not-member", Placeholder.unparsed("target", targetName));
                return;
            }
            if (guild.isOwner(targetUuid.get())) {
                plugin.messages().send(player, "guild.no-permission-role");
                return;
            }
            guildManager.removeMember(guild.id(), targetUuid.get()).thenRun(() -> {
                guildManager.refreshCache(targetUuid.get());
                plugin.messages().send(player, "guild.kicked", Placeholder.unparsed("target", targetName));
                Player targetOnline = Bukkit.getPlayer(targetUuid.get());
                if (targetOnline != null) {
                    plugin.messages().send(targetOnline, "guild.kicked-notice", Placeholder.unparsed("guild", guild.name()));
                }
            });
        });
    }

    private void handlePromote(Player player, String[] args, boolean promote) {
        String usage = promote ? "/guild promote <player>" : "/guild demote <player>";
        if (args.length < 2) {
            plugin.messages().send(player, "general.unknown-command", Placeholder.unparsed("usage", usage));
            return;
        }
        String targetName = args[1];

        requireGuild(player, guild -> {
            if (!guild.isOwner(player.getUniqueId())) {
                plugin.messages().send(player, "guild.no-permission-role");
                return;
            }
            Optional<UUID> targetUuid = resolveMemberUuid(guild, targetName);
            if (targetUuid.isEmpty() || guild.isOwner(targetUuid.get())) {
                plugin.messages().send(player, "guild.not-member", Placeholder.unparsed("target", targetName));
                return;
            }
            GuildRole newRole = promote ? GuildRole.OFFICER : GuildRole.MEMBER;
            guildManager.setRole(guild.id(), targetUuid.get(), newRole).thenRun(() ->
                    plugin.messages().send(player, promote ? "guild.promoted" : "guild.demoted",
                            Placeholder.unparsed("target", targetName),
                            Placeholder.unparsed("role", newRole.name())));
        });
    }

    private void handleSetHome(Player player) {
        requireGuild(player, guild -> {
            GuildRole role = guild.member(player.getUniqueId()).map(GuildMember::role).orElse(GuildRole.MEMBER);
            if (!role.canManageSettings()) {
                plugin.messages().send(player, "guild.no-permission-role");
                return;
            }
            guildManager.setHome(guild.id(), player.getLocation()).thenRun(() ->
                    plugin.messages().send(player, "guild.home-set"));
        });
    }

    private void handleHome(Player player) {
        requireGuild(player, guild -> {
            if (!guild.hasHome()) {
                plugin.messages().send(player, "guild.home-not-set");
                return;
            }
            plugin.teleportExecutor().executeSafely(player, guild.homeLocation());
        });
    }

    private void handleInfo(Player player, String[] args) {
        var future = args.length > 1 ? guildManager.getGuildByName(args[1]) : guildManager.getGuildByMember(player.getUniqueId());
        future.thenAccept(optionalGuild -> {
            if (optionalGuild.isEmpty()) {
                plugin.messages().send(player, "guild.not-in-guild");
                return;
            }
            Guild guild = optionalGuild.get();
            plugin.messages().send(player, "guild.info-header",
                    Placeholder.unparsed("guild_name", guild.name()), Placeholder.unparsed("guild_tag", guild.tag()));
            plugin.messages().send(player, "guild.info-line",
                    Placeholder.unparsed("label", "Owner"),
                    Placeholder.unparsed("value", Bukkit.getOfflinePlayer(guild.owner()).getName() != null
                            ? Objects.requireNonNull(Bukkit.getOfflinePlayer(guild.owner()).getName()) : guild.owner().toString()));
            plugin.messages().send(player, "guild.info-line",
                    Placeholder.unparsed("label", "Members"),
                    Placeholder.unparsed("value", guild.members().size() + "/" + guild.memberLimit()));
            plugin.messages().send(player, "guild.info-line",
                    Placeholder.unparsed("label", "Bank"),
                    Placeholder.unparsed("value", plugin.economy().format(guild.balance())));
        });
    }

    private void handleList(Player player) {
        guildManager.listGuilds().thenAccept(guilds -> {
            plugin.messages().send(player, "guild.list-header", Placeholder.unparsed("count", String.valueOf(guilds.size())));
            for (Guild guild : guilds) {
                plugin.messages().send(player, "guild.list-entry",
                        Placeholder.unparsed("name", guild.name()), Placeholder.unparsed("tag", guild.tag()));
            }
        });
    }

    private void handleChat(Player player) {
        requireGuild(player, guild -> {
            boolean nowEnabled = chatToggle.toggle(player.getUniqueId());
            plugin.messages().send(player, nowEnabled ? "guild.chat-enabled" : "guild.chat-disabled");
        });
    }

    private void handleLeave(Player player) {
        requireGuild(player, guild -> {
            if (guild.isOwner(player.getUniqueId())) {
                plugin.messages().send(player, "guild.owner-must-disband");
                return;
            }
            guildManager.removeMember(guild.id(), player.getUniqueId()).thenRun(() -> {
                guildManager.refreshCache(player.getUniqueId());
                plugin.messages().send(player, "guild.left", Placeholder.unparsed("guild", guild.name()));
            });
        });
    }

    private Optional<UUID> resolveMemberUuid(Guild guild, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && guild.member(online.getUniqueId()).isPresent()) {
            return Optional.of(online.getUniqueId());
        }
        return guild.members().stream()
                .map(GuildMember::uuid)
                .filter(uuid -> name.equalsIgnoreCase(Bukkit.getOfflinePlayer(uuid).getName()))
                .findFirst();
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return plugin.subcommandAliases().labels("guild").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote")
                .contains(plugin.subcommandAliases().resolve("guild", args[0]))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
