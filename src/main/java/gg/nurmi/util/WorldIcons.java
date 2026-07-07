package gg.nurmi.util;

import org.bukkit.Material;
import org.bukkit.World;

/** Shared GUI-icon mapping for a world's environment, used by both the RTP and world-list menus. */
public final class WorldIcons {

    private WorldIcons() {
    }

    public static Material iconFor(World.Environment environment, boolean isVoid) {
        return switch (environment) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> isVoid ? Material.STRUCTURE_VOID : Material.GRASS_BLOCK;
        };
    }
}
