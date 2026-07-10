package gg.nurmi.world;

public final class WorldPaths {

    private WorldPaths() {}

    // Bukkit treats a '/'-containing WorldCreator name as both a nested path and the world's identifier, so this just folds the prefix into the name.
    public static String resolve(String container, String worldName) {
        if (container == null || container.isBlank()) {
            return worldName;
        }
        String normalized = normalize(container);
        return normalized.isEmpty() ? worldName : normalized + "/" + worldName;
    }

    // Reverses resolve(): strips the container prefix off a Bukkit world name to match the logical name admins actually configure.
    public static String strip(String container, String storedName) {
        if (container == null || container.isBlank()) {
            return storedName;
        }
        String prefix = normalize(container) + "/";
        return storedName.startsWith(prefix) ? storedName.substring(prefix.length()) : storedName;
    }

    private static String normalize(String container) {
        String normalized = container.replace('\\', '/');
        int start = 0;
        int end = normalized.length();
        while (start < end && normalized.charAt(start) == '/') {
            start++;
        }
        while (end > start && normalized.charAt(end - 1) == '/') {
            end--;
        }
        return normalized.substring(start, end);
    }
}
