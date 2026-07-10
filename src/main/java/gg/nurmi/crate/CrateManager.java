package gg.nurmi.crate;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.ConfigMigrator;
import gg.nurmi.util.Database;
import gg.nurmi.util.LanguageManager;
import gg.nurmi.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class CrateManager {

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    private final OneSMPPlugin plugin;
    private final Database database;
    private final NamespacedKey keyDataKey;
    private final Map<String, CrateType> types = new LinkedHashMap<>();
    private final Map<BlockKey, String> boundBlocks = new ConcurrentHashMap<>();

    public CrateManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
        this.keyDataKey = new NamespacedKey(plugin, "crate_key");
        loadTypes();
        loadBoundBlocks();
    }

    public void reload() {
        loadTypes();
    }

    private void loadTypes() {
        LanguageManager.migrateLegacyFile(plugin, "crates.yml", "lang/en_US/crates.yml");
        String path = LanguageManager.file(plugin, "crates");
        ConfigMigrator.migrate(plugin, path, Set.of("crates"));
        File file = new File(plugin.getDataFolder(), path);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        types.clear();

        ConfigurationSection cratesSection = config.getConfigurationSection("crates");
        if (cratesSection == null) {
            return;
        }

        for (String key : cratesSection.getKeys(false)) {
            ConfigurationSection typeSection = cratesSection.getConfigurationSection(key);
            if (typeSection == null) {
                continue;
            }

            String displayName = typeSection.getString("display-name", key);
            String keyName = typeSection.getString("key-name", displayName + " Key");
            Material keyMaterial = Material.matchMaterial(typeSection.getString("key-material", "TRIPWIRE_HOOK"));
            if (keyMaterial == null) {
                plugin.getLogger().warning("crates.yml: unknown key-material for crate '" + key + "', defaulting to TRIPWIRE_HOOK.");
                keyMaterial = Material.TRIPWIRE_HOOK;
            }
            List<String> keyLore = typeSection.getStringList("key-lore");

            List<CrateReward> rewards = new ArrayList<>();
            ConfigurationSection rewardsSection = typeSection.getConfigurationSection("rewards");
            if (rewardsSection != null) {
                for (String rewardKey : rewardsSection.getKeys(false)) {
                    ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(rewardKey);
                    if (rewardSection == null) {
                        continue;
                    }
                    rewards.add(new CrateReward(
                            rewardKey,
                            rewardSection.getDouble("weight", 1.0),
                            rewardSection.getString("display-name", rewardKey),
                            parseItems(rewardSection.getConfigurationSection("items"), key, rewardKey),
                            rewardSection.getDouble("money", 0),
                            rewardSection.getStringList("commands"),
                            rewardSection.getBoolean("broadcast", false),
                            parseDisplayItem(rewardSection.getString("display-item"), key, rewardKey)
                    ));
                }
            }
            if (rewards.isEmpty()) {
                plugin.getLogger().warning("crates.yml: crate '" + key + "' has no rewards configured, it will never give anything.");
            }

            types.put(key, new CrateType(key, displayName, keyName, keyLore, keyMaterial, rewards));
        }
    }

    private Material parseDisplayItem(String materialKey, String crateKey, String rewardKey) {
        if (materialKey == null) {
            return null;
        }
        Material material = Material.matchMaterial(materialKey);
        if (material == null) {
            plugin.getLogger().warning("crates.yml: unknown display-item '" + materialKey + "' in crate '" + crateKey
                    + "' reward '" + rewardKey + "', ignoring.");
        }
        return material;
    }

    private Map<Material, Integer> parseItems(ConfigurationSection itemsSection, String crateKey, String rewardKey) {
        Map<Material, Integer> items = new LinkedHashMap<>();
        if (itemsSection == null) {
            return items;
        }
        for (String materialKey : itemsSection.getKeys(false)) {
            Material material = Material.matchMaterial(materialKey);
            if (material == null) {
                plugin.getLogger().warning("crates.yml: unknown material '" + materialKey + "' in crate '" + crateKey
                        + "' reward '" + rewardKey + "', skipping.");
                continue;
            }
            items.put(material, Math.max(1, itemsSection.getInt(materialKey, 1)));
        }
        return items;
    }

    private void loadBoundBlocks() {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT world, x, y, z, crate_type FROM crates");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BlockKey key = new BlockKey(resultSet.getString("world"), resultSet.getInt("x"),
                        resultSet.getInt("y"), resultSet.getInt("z"));
                boundBlocks.put(key, resultSet.getString("crate_type"));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load crate blocks", ex);
        }
    }

    // Must be called only after the void spawn world and any /world create worlds have actually loaded
    // (see SpawnWorldManager/WorldManager) - those are only loaded via a scheduled task queued during
    // onEnable(), not synchronously, so calling this too early would fail to resolve their locations.
    public void respawnHolograms() {
        if (boundBlocks.isEmpty() || !fancyHologramsAvailable()) {
            return;
        }
        Map<BlockKey, String> snapshot = Map.copyOf(boundBlocks);
        plugin.scheduler().runGlobal(() -> {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            for (Map.Entry<BlockKey, String> entry : snapshot.entrySet()) {
                respawnHologram(manager, entry.getKey(), entry.getValue());
            }
        });
    }

    private void respawnHologram(HologramManager manager, BlockKey key, String typeKey) {
        CrateType type = types.get(typeKey);
        if (type == null) {
            return;
        }
        World world = Bukkit.getWorld(key.world());
        if (world == null) {
            plugin.getLogger().warning("Crate hologram at " + key.world() + " " + key.x() + "," + key.y() + "," + key.z()
                    + " couldn't be recreated - its world isn't loaded.");
            return;
        }

        manager.getHologram(hologramName(key)).ifPresent(manager::removeHologram);
        createHologram(new Location(world, key.x(), key.y(), key.z()), type);
    }

    public Map<String, CrateType> types() {
        return types;
    }

    public CrateType type(String key) {
        return types.get(key);
    }

    public String crateTypeAt(Location location) {
        return boundBlocks.get(BlockKey.of(location));
    }

    public boolean bind(Location location, String typeKey, UUID createdBy) {
        CrateType type = types.get(typeKey);
        if (type == null) {
            return false;
        }
        BlockKey key = BlockKey.of(location);
        boundBlocks.put(key, typeKey);
        createHologram(location, type);

        plugin.scheduler().runAsync(() -> {
            String upsert = database.isMysql()
                    ? "INSERT INTO crates(world, x, y, z, crate_type, created_by) VALUES (?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE crate_type = VALUES(crate_type), created_by = VALUES(created_by)"
                    : "INSERT INTO crates(world, x, y, z, crate_type, created_by) VALUES (?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(world, x, y, z) DO UPDATE SET crate_type = excluded.crate_type, created_by = excluded.created_by";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, key.world());
                statement.setInt(2, key.x());
                statement.setInt(3, key.y());
                statement.setInt(4, key.z());
                statement.setString(5, typeKey);
                statement.setString(6, createdBy.toString());
                statement.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist crate block", ex);
            }
        });
        return true;
    }

    public boolean unbind(Location location) {
        BlockKey key = BlockKey.of(location);
        if (boundBlocks.remove(key) == null) {
            return false;
        }
        removeHologram(location);

        plugin.scheduler().runAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM crates WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                statement.setString(1, key.world());
                statement.setInt(2, key.x());
                statement.setInt(3, key.y());
                statement.setInt(4, key.z());
                statement.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete crate block", ex);
            }
        });
        return true;
    }

    private boolean fancyHologramsAvailable() {
        return Bukkit.getPluginManager().getPlugin("FancyHolograms") != null;
    }

    private String hologramName(BlockKey key) {
        return "cs_crate_" + key.world() + "_" + key.x() + "_" + key.y() + "_" + key.z();
    }

    private void createHologram(Location location, CrateType type) {
        if (!fancyHologramsAvailable()) {
            return;
        }
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        String name = hologramName(BlockKey.of(location));
        if (manager.getHologram(name).isPresent()) {
            return;
        }

        Location hologramLocation = location.clone().add(0.5, 1.3, 0.5);
        hologramLocation.setPitch(0f);
        TextHologramData data = new TextHologramData(name, hologramLocation);
        data.setText(new ArrayList<>(List.of(type.displayName())));
        data.setPersistent(false);
        data.setVisibilityDistance(50);
        data.setScale(new Vector3f(1.2f, 1.2f, 1.2f));
        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
        hologram.forceUpdate();
    }

    private void removeHologram(Location location) {
        if (!fancyHologramsAvailable()) {
            return;
        }
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(hologramName(BlockKey.of(location))).ifPresent(manager::removeHologram);
    }

    public ItemStack createKey(String typeKey, int amount) {
        CrateType type = types.get(typeKey);
        if (type == null) {
            return null;
        }
        List<Component> lore = type.keyLore().stream().map(line -> plugin.messages().parse(line)).toList();
        return new ItemBuilder(type.keyMaterial(), amount)
                .name(plugin.messages().parse(type.keyName()))
                .lore(lore)
                .persistentData(keyDataKey, PersistentDataType.STRING, typeKey)
                .build();
    }

    public String keyType(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keyDataKey, PersistentDataType.STRING);
    }

    // Weighted random pick: walks the pool accumulating weight until the roll falls within a reward's slice.
    public CrateReward rollReward(CrateType type) {
        if (type.rewards().isEmpty()) {
            return null;
        }
        double totalWeight = type.rewards().stream().mapToDouble(CrateReward::weight).sum();
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (CrateReward reward : type.rewards()) {
            cumulative += reward.weight();
            if (roll < cumulative) {
                return reward;
            }
        }
        return type.rewards().getLast();
    }

    public void grantReward(Player player, CrateType type, CrateReward reward) {
        for (Map.Entry<Material, Integer> entry : reward.items().entrySet()) {
            Map<Integer, ItemStack> leftover = player.getInventory()
                    .addItem(new ItemStack(entry.getKey(), entry.getValue()));
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
        if (reward.money() > 0) {
            plugin.economy().deposit(player.getUniqueId(), BigDecimal.valueOf(reward.money()));
        }
        for (String command : reward.commands()) {
            String parsed = command.replace("%player%", player.getName());
            plugin.scheduler().runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed));
        }

        Component rewardName = plugin.messages().parse(reward.displayName());
        Component typeName = plugin.messages().parse(type.displayName());
        plugin.messages().send(player, "crate.opened",
                Placeholder.component("type", typeName),
                Placeholder.component("reward", rewardName));

        if (reward.broadcast()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.messages().send(online, "crate.broadcast",
                        Placeholder.unparsed("player", player.getName()),
                        Placeholder.component("type", typeName),
                        Placeholder.component("reward", rewardName));
            }
        }
    }
}
