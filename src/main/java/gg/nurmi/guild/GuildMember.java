package gg.nurmi.guild;

import java.util.UUID;

public record GuildMember(UUID uuid, GuildRole role, long joinedAt) {
}
