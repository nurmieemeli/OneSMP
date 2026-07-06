package gg.nurmi.guild;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record Guild(int id, String name, String tag, UUID owner, BigDecimal balance, int memberLimit,
                     String homeWorld, double homeX, double homeY, double homeZ, float homeYaw, float homePitch,
                     long createdAt, List<GuildMember> members) {

    public boolean hasHome() {
        return homeWorld != null;
    }

    public Location homeLocation() {
        if (!hasHome()) {
            return null;
        }
        World world = Bukkit.getWorld(homeWorld);
        if (world == null) {
            return null;
        }
        return new Location(world, homeX, homeY, homeZ, homeYaw, homePitch);
    }

    public Optional<GuildMember> member(UUID uuid) {
        return members.stream().filter(member -> member.uuid().equals(uuid)).findFirst();
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }
}
