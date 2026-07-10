package gg.nurmi.util;

import gg.nurmi.OneSMPPlugin;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public final class TextUtil {

    private TextUtil() {
    }

    public static String prettyName(Material material) {
        return prettyName(material.name());
    }

    public static String prettyName(EntityType entityType) {
        return prettyName(entityType.name());
    }

    // Looks up a translated display name from lang/<language>/messages.yml (entities.<key>, hyphenated -
    // e.g. entities.cave-spider), falling back to the generic ENUM_NAME -> "Enum Name" prettifier for any
    // entity type not covered there, so an exotic/future mob never shows a raw missing-key string.
    public static String entityName(OneSMPPlugin plugin, EntityType entityType) {
        String key = "entities." + entityType.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        String translated = plugin.messages().raw(key);
        return translated.equals(key) ? prettyName(entityType) : translated;
    }

    private static String prettyName(String rawEnumName) {
        String[] parts = rawEnumName.split("_");
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

    public static String formatDuration(OneSMPPlugin plugin, long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append(plugin.messages().raw("general.duration-days"));
        }
        if (days > 0 || hours > 0) {
            builder.append(hours).append(plugin.messages().raw("general.duration-hours"));
        }
        builder.append(minutes).append(plugin.messages().raw("general.duration-minutes"));
        return builder.toString();
    }
}
