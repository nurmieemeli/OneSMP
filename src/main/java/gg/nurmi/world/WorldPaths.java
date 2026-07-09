package gg.nurmi.world;

// Bukkit treats a '/'-containing WorldCreator name as both a nested path and the world's identifier, so a container prefix just needs folding into the name consistently.
public final class WorldPaths {

    private WorldPaths() {}

    public static String resolve(String container, String worldName) {
        if (container == null || container.isBlank()) {
            return worldName;
        }
        String normalized = container.replace('\\', '/');
        int start = 0;
        int end = normalized.length();
        while (start < end && normalized.charAt(start) == '/') {
            start++;
        }
        while (end > start && normalized.charAt(end - 1) == '/') {
            end--;
        }
        normalized = normalized.substring(start, end);
        return normalized.isEmpty() ? worldName : normalized + "/" + worldName;
    }
}
