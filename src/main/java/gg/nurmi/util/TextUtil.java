package gg.nurmi.util;

import org.bukkit.Material;

public final class TextUtil {

    private TextUtil() {
    }

    public static String prettyName(Material material) {
        String[] parts = material.name().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return builder.toString();
    }
}