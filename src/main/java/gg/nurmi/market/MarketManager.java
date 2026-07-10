package gg.nurmi.market;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class MarketManager {

    private static final String SELECT_COLUMNS =
            "id, seller_uuid, material, item_data, price, created_at";

    public enum BuyResult {
        SUCCESS, NOT_FOUND, OWN_LISTING, INSUFFICIENT_FUNDS, SOLD_OUT
    }

    public enum CancelResult {
        CANCELLED, NOT_FOUND, NOT_OWNER
    }

    public record BuyOutcome(BuyResult result, MarketListing listing) {
    }

    public record CancelOutcome(CancelResult result, MarketListing listing) {
    }

    private final OneSMPPlugin plugin;
    private final Database database;

    public MarketManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
    }

    public CompletableFuture<Integer> countListings(UUID seller) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM market_listings WHERE seller_uuid = ?")) {
                statement.setString(1, seller.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to count market listings for " + seller, ex);
                return 0;
            }
        });
    }

    public CompletableFuture<MarketListing> create(Player seller, ItemStack item, BigDecimal price) {
        UUID sellerUuid = seller.getUniqueId();
        String sellerName = seller.getName();
        BigDecimal scaledPrice = price.setScale(2, RoundingMode.HALF_UP);
        long createdAt = System.currentTimeMillis();
        byte[] data = item.serializeAsBytes();
        String material = item.getType().name();

        return plugin.scheduler().supplyAsync(() -> {
            String insert = "INSERT INTO market_listings(seller_uuid, material, item_data, price, created_at) "
                    + "VALUES (?, ?, ?, ?, ?)";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, sellerUuid.toString());
                statement.setString(2, material);
                statement.setBytes(3, data);
                statement.setBigDecimal(4, scaledPrice);
                statement.setLong(5, createdAt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    int id = keys.next() ? keys.getInt(1) : -1;
                    return new MarketListing(id, sellerUuid, sellerName, item, scaledPrice, createdAt);
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to create market listing for " + sellerUuid, ex);
                throw new RuntimeException(ex);
            }
        });
    }

    public CompletableFuture<List<MarketListing>> browse(int limit) {
        return plugin.scheduler().supplyAsync(() -> {
            List<MarketListing> listings = new ArrayList<>();
            String sql = "SELECT " + SELECT_COLUMNS + " FROM market_listings ORDER BY created_at DESC LIMIT ?";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        listings.add(readListing(resultSet));
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to browse market listings", ex);
            }
            return listings;
        });
    }

    public CompletableFuture<List<MarketListing>> search(String query, int limit) {
        String likePattern = "%" + query.trim().toUpperCase(Locale.ROOT).replace(' ', '_') + "%";
        return plugin.scheduler().supplyAsync(() -> {
            List<MarketListing> listings = new ArrayList<>();
            String sql = "SELECT " + SELECT_COLUMNS + " FROM market_listings WHERE material LIKE ? ORDER BY created_at DESC LIMIT ?";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, likePattern);
                statement.setInt(2, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        listings.add(readListing(resultSet));
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to search market listings for '" + query + "'", ex);
            }
            return listings;
        });
    }

    public CompletableFuture<List<MarketListing>> mine(UUID seller) {
        return plugin.scheduler().supplyAsync(() -> {
            List<MarketListing> listings = new ArrayList<>();
            String sql = "SELECT " + SELECT_COLUMNS + " FROM market_listings WHERE seller_uuid = ? ORDER BY created_at DESC";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, seller.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        listings.add(readListing(resultSet));
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load market listings for " + seller, ex);
            }
            return listings;
        });
    }

    public CompletableFuture<BuyOutcome> buy(Player buyer, int listingId) {
        return findListing(listingId).thenCompose(maybeListing -> {
            if (maybeListing.isEmpty()) {
                return CompletableFuture.completedFuture(new BuyOutcome(BuyResult.NOT_FOUND, null));
            }
            MarketListing listing = maybeListing.get();
            if (listing.sellerUuid().equals(buyer.getUniqueId())) {
                return CompletableFuture.completedFuture(new BuyOutcome(BuyResult.OWN_LISTING, listing));
            }
            return plugin.economy().withdraw(buyer.getUniqueId(), listing.price()).thenCompose(funded -> {
                if (!funded) {
                    return CompletableFuture.completedFuture(new BuyOutcome(BuyResult.INSUFFICIENT_FUNDS, listing));
                }
                return claim(listing.id()).thenApply(claimed -> {
                    if (!claimed) {
                        plugin.economy().deposit(buyer.getUniqueId(), listing.price());
                        return new BuyOutcome(BuyResult.SOLD_OUT, null);
                    }
                    plugin.economy().deposit(listing.sellerUuid(), listing.price());
                    return new BuyOutcome(BuyResult.SUCCESS, listing);
                });
            });
        });
    }

    public CompletableFuture<CancelOutcome> cancel(UUID seller, int listingId) {
        return findListing(listingId).thenCompose(maybeListing -> {
            if (maybeListing.isEmpty()) {
                return CompletableFuture.completedFuture(new CancelOutcome(CancelResult.NOT_FOUND, null));
            }
            MarketListing listing = maybeListing.get();
            if (!listing.sellerUuid().equals(seller)) {
                return CompletableFuture.completedFuture(new CancelOutcome(CancelResult.NOT_OWNER, null));
            }
            return claim(listingId).thenApply(claimed -> claimed
                    ? new CancelOutcome(CancelResult.CANCELLED, listing)
                    : new CancelOutcome(CancelResult.NOT_FOUND, null));
        });
    }

    private CompletableFuture<Optional<MarketListing>> findListing(int id) {
        return plugin.scheduler().supplyAsync(() -> {
            String sql = "SELECT " + SELECT_COLUMNS + " FROM market_listings WHERE id = ?";
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(readListing(resultSet)) : Optional.<MarketListing>empty();
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load market listing " + id, ex);
                return Optional.<MarketListing>empty();
            }
        });
    }

    // Atomically claims a listing via DELETE + affected-row check, so a concurrent buy/cancel can't act on the same row twice.
    private CompletableFuture<Boolean> claim(int listingId) {
        return plugin.scheduler().supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM market_listings WHERE id = ?")) {
                statement.setInt(1, listingId);
                return statement.executeUpdate() == 1;
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to claim market listing " + listingId, ex);
                return false;
            }
        });
    }

    private MarketListing readListing(ResultSet resultSet) throws SQLException {
        ItemStack item = ItemStack.deserializeBytes(resultSet.getBytes("item_data"));
        UUID sellerUuid = UUID.fromString(resultSet.getString("seller_uuid"));
        return new MarketListing(
                resultSet.getInt("id"),
                sellerUuid,
                Bukkit.getOfflinePlayer(sellerUuid).getName(),
                item,
                resultSet.getBigDecimal("price").setScale(2, RoundingMode.HALF_UP),
                resultSet.getLong("created_at"));
    }
}
