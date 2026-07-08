package gg.nurmi;

import gg.nurmi.economy.BalTopCommand;
import gg.nurmi.economy.BalanceCommand;
import gg.nurmi.economy.EcoCommand;
import gg.nurmi.economy.EconomyListener;
import gg.nurmi.economy.EconomyManager;
import gg.nurmi.economy.EconomyPlaceholderExpansion;
import gg.nurmi.economy.PayCommand;
import gg.nurmi.economy.VaultEconomyProvider;
import gg.nurmi.message.ChatFormatListener;
import gg.nurmi.message.JoinLeaveMessageListener;
import gg.nurmi.util.AliasManager;
import gg.nurmi.util.SubcommandAliases;
import gg.nurmi.util.ConfigMigrator;
import gg.nurmi.guild.GuildCacheListener;
import gg.nurmi.guild.GuildChatToggle;
import gg.nurmi.guild.GuildCommand;
import gg.nurmi.guild.GuildManager;
import gg.nurmi.guild.GuildPlaceholderExpansion;
import gg.nurmi.gui.GuiListener;
import gg.nurmi.stats.hologram.LeaderboardHologramCommand;
import gg.nurmi.stats.hologram.LeaderboardHologramManager;
import gg.nurmi.message.MessageService;
import gg.nurmi.message.IgnoreCommand;
import gg.nurmi.message.MsgCommand;
import gg.nurmi.message.PrivateMessageListener;
import gg.nurmi.message.PrivateMessageManager;
import gg.nurmi.message.ReplyCommand;
import gg.nurmi.message.SocialSpyCommand;
import gg.nurmi.message.SocialSpyToggle;
import gg.nurmi.moderation.PacketVanishController;
import gg.nurmi.moderation.SpectateCommand;
import gg.nurmi.moderation.SpectateManager;
import gg.nurmi.nametag.NametagListener;
import gg.nurmi.nametag.NametagManager;
import gg.nurmi.util.PacketEventsBootstrap;
import gg.nurmi.world.protection.PortalListener;
import gg.nurmi.world.protection.StrongholdDatapackInstaller;
import gg.nurmi.teleport.rtp.RtpCommand;
import gg.nurmi.scoreboard.ScoreboardListener;
import gg.nurmi.scoreboard.ScoreboardManager;
import gg.nurmi.teleport.rtp.RtpManager;
import gg.nurmi.util.SchedulerUtil;
import gg.nurmi.shop.BuyCommand;
import gg.nurmi.shop.SellCommand;
import gg.nurmi.shop.ShopManager;
import gg.nurmi.spawn.FirstJoinListener;
import gg.nurmi.spawn.PlayerRespawnListener;
import gg.nurmi.spawn.SetSpawnCommand;
import gg.nurmi.spawn.SpawnCommand;
import gg.nurmi.spawn.SpawnWorldManager;
import gg.nurmi.spawn.VoidFallRescueListener;
import gg.nurmi.spawn.VoidWorldListener;
import gg.nurmi.stats.StatsCommand;
import gg.nurmi.stats.StatsListener;
import gg.nurmi.stats.StatsManager;
import gg.nurmi.stats.StatsPlaceholderExpansion;
import gg.nurmi.stats.StatsTopCommand;
import gg.nurmi.util.Database;
import gg.nurmi.tablist.TablistListener;
import gg.nurmi.tablist.TablistManager;
import gg.nurmi.teleport.DelHomeCommand;
import gg.nurmi.teleport.DelWarpCommand;
import gg.nurmi.teleport.HomeCommand;
import gg.nurmi.teleport.HomeManager;
import gg.nurmi.teleport.SetHomeCommand;
import gg.nurmi.teleport.SetWarpCommand;
import gg.nurmi.teleport.TeleportExecutor;
import gg.nurmi.teleport.TeleportWarmup;
import gg.nurmi.teleport.TpAcceptCommand;
import gg.nurmi.teleport.TpDenyCommand;
import gg.nurmi.teleport.TpaCommand;
import gg.nurmi.teleport.TpaManager;
import gg.nurmi.teleport.WarpCommand;
import gg.nurmi.teleport.WarpManager;
import gg.nurmi.world.WorldCommand;
import gg.nurmi.world.WorldManager;
import io.github.miniplaceholders.api.Expansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Set;

public final class CanvasSuitePlugin extends JavaPlugin {

    private SchedulerUtil schedulerUtil;
    private Database database;
    private MessageService messageService;
    private SubcommandAliases subcommandAliases;
    private EconomyManager economyManager;
    private Expansion economyPlaceholderExpansion;
    private ShopManager shopManager;
    private TeleportExecutor teleportExecutor;
    private GuildManager guildManager;
    private GuildChatToggle guildChatToggle;
    private Expansion guildPlaceholderExpansion;
    private PacketEventsBootstrap packetEventsBootstrap;
    private SpawnWorldManager spawnWorldManager;
    private TablistManager tablistManager;
    private WorldManager worldManager;
    private StatsManager statsManager;
    private Expansion statsPlaceholderExpansion;

    @Override
    public void onEnable() {
        ConfigMigrator.migrate(this, "config.yml", Set.of("rtp.worlds"));
        reloadConfig();

        this.schedulerUtil = new SchedulerUtil(this);
        this.messageService = new MessageService(this);
        this.subcommandAliases = new SubcommandAliases(this);
        subcommandAliases.load();

        this.database = new Database(this);
        database.connect();

        this.packetEventsBootstrap = new PacketEventsBootstrap(this);
        packetEventsBootstrap.detect();

        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        registerEconomy();
        registerShop();
        registerTeleport();
        registerRtp();
        registerGuild();
        getServer().getPluginManager().registerEvents(new ChatFormatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinLeaveMessageListener(this), this);
        registerProtection();
        registerSpawn();
        registerWorlds();
        registerMessaging();
        registerNametags();
        registerModeration();
        registerTablist();
        registerScoreboard();
        registerStats();
        registerHolograms();

        new AliasManager(this).applyAliases();

        getLogger().info("CanvasSuite enabled.");
    }

    private void registerStats() {
        this.statsManager = new StatsManager(this);
        getServer().getPluginManager().registerEvents(new StatsListener(this, statsManager), this);

        Objects.requireNonNull(getCommand("stats")).setExecutor(new StatsCommand(this, statsManager));
        Objects.requireNonNull(getCommand("statstop")).setExecutor(new StatsTopCommand(this, statsManager));

        int periodSeconds = Math.max(5, getConfig().getInt("stats.playtime-autosave-interval-seconds", 60));
        schedulerUtil.runGlobalRepeating(statsManager::flushOnline, periodSeconds * 20L, periodSeconds * 20L);

        this.statsPlaceholderExpansion = StatsPlaceholderExpansion.register(statsManager);
    }

    private void registerHolograms() {
        boolean available = getServer().getPluginManager().getPlugin("FancyHolograms") != null;
        if (!available) {
            getLogger().warning("FancyHolograms not found - leaderboard holograms will be disabled. "
                    + "Install FancyHolograms to enable /statshologram.");
        }

        LeaderboardHologramManager hologramManager = available ? new LeaderboardHologramManager(this) : null;
        Objects.requireNonNull(getCommand("statshologram")).setExecutor(new LeaderboardHologramCommand(this, hologramManager));

        if (available) {
            int periodSeconds = Math.max(5, getConfig().getInt("hologram.refresh-interval-seconds", 30));
            schedulerUtil.runGlobalRepeating(hologramManager::refreshAll, periodSeconds * 20L, periodSeconds * 20L);
        }
    }

    private void registerScoreboard() {
        if (!getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        ScoreboardManager scoreboardManager = new ScoreboardManager(this);
        getServer().getPluginManager().registerEvents(new ScoreboardListener(this, scoreboardManager), this);
        int periodSeconds = Math.max(1, getConfig().getInt("scoreboard.refresh-interval-seconds", 5));
        schedulerUtil.runGlobalRepeating(scoreboardManager::refreshAll, periodSeconds * 20L, periodSeconds * 20L);
    }

    private void registerTablist() {
        if (!getConfig().getBoolean("tablist.enabled", true)) {
            return;
        }
        this.tablistManager = new TablistManager(this);
        getServer().getPluginManager().registerEvents(new TablistListener(tablistManager), this);
        int periodSeconds = Math.max(1, getConfig().getInt("tablist.refresh-interval-seconds", 5));
        schedulerUtil.runGlobalRepeating(tablistManager::refreshAllHeaderFooters, periodSeconds * 20L, periodSeconds * 20L);
    }

    private void registerModeration() {
        PacketVanishController vanishController = new PacketVanishController(this);
        SpectateManager spectateManager = new SpectateManager(vanishController);
        Objects.requireNonNull(getCommand("spectate")).setExecutor(new SpectateCommand(this, spectateManager));
    }

    private void registerWorlds() {
        this.worldManager = new WorldManager(this);
        worldManager.loadWorlds();

        WorldCommand worldCommand = new WorldCommand(this, worldManager);
        Objects.requireNonNull(getCommand("world")).setExecutor(worldCommand);
        Objects.requireNonNull(getCommand("world")).setTabCompleter(worldCommand);
    }

    private void registerMessaging() {
        SocialSpyToggle socialSpyToggle = new SocialSpyToggle();
        PrivateMessageManager privateMessageManager = new PrivateMessageManager(this, socialSpyToggle);

        getServer().getPluginManager().registerEvents(new PrivateMessageListener(privateMessageManager), this);

        Objects.requireNonNull(getCommand("msg")).setExecutor(new MsgCommand(this, privateMessageManager));
        Objects.requireNonNull(getCommand("reply")).setExecutor(new ReplyCommand(this, privateMessageManager));
        Objects.requireNonNull(getCommand("ignore")).setExecutor(new IgnoreCommand(this, privateMessageManager));
        Objects.requireNonNull(getCommand("socialspy")).setExecutor(new SocialSpyCommand(this, socialSpyToggle));
    }

    private void registerNametags() {
        if (!getConfig().getBoolean("nametag.enabled", true)) {
            return;
        }
        NametagManager nametagManager = new NametagManager(this);
        getServer().getPluginManager().registerEvents(new NametagListener(nametagManager), this);
        int periodSeconds = Math.max(5, getConfig().getInt("nametag.refresh-interval-seconds", 30));
        schedulerUtil.runGlobalRepeating(nametagManager::refreshAll, periodSeconds * 20L, periodSeconds * 20L);

        int remountTicks = Math.max(20, getConfig().getInt("nametag.guild-tag.remount-interval-ticks", 100));
        schedulerUtil.runGlobalRepeating(nametagManager::reassertMounts, remountTicks, remountTicks);
    }

    private void registerSpawn() {
        this.spawnWorldManager = new SpawnWorldManager(this);
        spawnWorldManager.ensureWorldExists();

        Objects.requireNonNull(getCommand("setspawn")).setExecutor(new SetSpawnCommand(this, spawnWorldManager));
        Objects.requireNonNull(getCommand("spawn")).setExecutor(new SpawnCommand(this, spawnWorldManager));

        getServer().getPluginManager().registerEvents(new FirstJoinListener(this, spawnWorldManager), this);
        getServer().getPluginManager().registerEvents(new VoidWorldListener(spawnWorldManager), this);
        getServer().getPluginManager().registerEvents(new VoidFallRescueListener(spawnWorldManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(spawnWorldManager), this);
    }

    private void registerProtection() {
        new StrongholdDatapackInstaller(this).installForAllWorlds();
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
    }

    private void registerRtp() {
        RtpManager rtpManager = new RtpManager(this);
        Objects.requireNonNull(getCommand("rtp")).setExecutor(new RtpCommand(this, rtpManager));

        if (getConfig().getBoolean("rtp.precache.enabled", true)) {
            int periodSeconds = Math.max(1, getConfig().getInt("rtp.precache.interval-seconds", 5));
            schedulerUtil.runGlobalRepeating(rtpManager::precacheTick, periodSeconds * 20L, periodSeconds * 20L);
        }
    }

    private void registerGuild() {
        this.guildManager = new GuildManager(this);
        this.guildChatToggle = new GuildChatToggle();
        GuildCommand guildCommand = new GuildCommand(this, guildManager, guildChatToggle);
        Objects.requireNonNull(getCommand("guild")).setExecutor(guildCommand);
        Objects.requireNonNull(getCommand("guild")).setTabCompleter(guildCommand);

        getServer().getPluginManager().registerEvents(new GuildCacheListener(guildManager), this);
        this.guildPlaceholderExpansion = GuildPlaceholderExpansion.register(this, guildManager);
    }

    private void registerTeleport() {
        HomeManager homeManager = new HomeManager(this);
        WarpManager warpManager = new WarpManager(this);
        TpaManager tpaManager = new TpaManager(this);
        TeleportWarmup warmup = new TeleportWarmup(this);
        this.teleportExecutor = new TeleportExecutor(this, warmup);

        Objects.requireNonNull(getCommand("sethome")).setExecutor(new SetHomeCommand(this, homeManager));
        Objects.requireNonNull(getCommand("home")).setExecutor(new HomeCommand(this, homeManager, teleportExecutor));
        Objects.requireNonNull(getCommand("delhome")).setExecutor(new DelHomeCommand(this, homeManager));

        Objects.requireNonNull(getCommand("setwarp")).setExecutor(new SetWarpCommand(this, warpManager));
        Objects.requireNonNull(getCommand("warp")).setExecutor(new WarpCommand(this, warpManager, teleportExecutor));
        Objects.requireNonNull(getCommand("delwarp")).setExecutor(new DelWarpCommand(this, warpManager));

        Objects.requireNonNull(getCommand("tpa")).setExecutor(new TpaCommand(this, tpaManager, false));
        Objects.requireNonNull(getCommand("tpahere")).setExecutor(new TpaCommand(this, tpaManager, true));
        Objects.requireNonNull(getCommand("tpaccept")).setExecutor(new TpAcceptCommand(this, tpaManager, teleportExecutor));
        Objects.requireNonNull(getCommand("tpdeny")).setExecutor(new TpDenyCommand(this, tpaManager));
    }

    private void registerShop() {
        this.shopManager = new ShopManager(this);
        Objects.requireNonNull(getCommand("buy")).setExecutor(new BuyCommand(this));
        Objects.requireNonNull(getCommand("sell")).setExecutor(new SellCommand(this));
    }

    private void registerEconomy() {
        this.economyManager = new EconomyManager(this);
        getServer().getPluginManager().registerEvents(new EconomyListener(economyManager), this);

        Objects.requireNonNull(getCommand("balance")).setExecutor(new BalanceCommand(this, economyManager));
        Objects.requireNonNull(getCommand("pay")).setExecutor(new PayCommand(this, economyManager));
        Objects.requireNonNull(getCommand("baltop")).setExecutor(new BalTopCommand(this, economyManager));
        Objects.requireNonNull(getCommand("eco")).setExecutor(new EcoCommand(this, economyManager));

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            getServer().getServicesManager().register(Economy.class, new VaultEconomyProvider(economyManager),
                    this, ServicePriority.Normal);
            getLogger().info("Hooked into Vault for economy support.");
        }

        this.economyPlaceholderExpansion = EconomyPlaceholderExpansion.register(economyManager);
    }

    @Override
    public void onDisable() {
        if (tablistManager != null) {
            tablistManager.shutdown();
        }
        if (statsManager != null) {
            statsManager.flushOnlineBlocking();
        }
        if (statsPlaceholderExpansion != null && statsPlaceholderExpansion.registered()) {
            statsPlaceholderExpansion.unregister();
        }
        if (economyPlaceholderExpansion != null && economyPlaceholderExpansion.registered()) {
            economyPlaceholderExpansion.unregister();
        }
        if (guildPlaceholderExpansion != null && guildPlaceholderExpansion.registered()) {
            guildPlaceholderExpansion.unregister();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("CanvasSuite disabled.");
    }

    public SchedulerUtil scheduler() {
        return schedulerUtil;
    }

    public Database database() {
        return database;
    }

    public MessageService messages() {
        return messageService;
    }

    public SubcommandAliases subcommandAliases() {
        return subcommandAliases;
    }

    public EconomyManager economy() {
        return economyManager;
    }

    public ShopManager shop() {
        return shopManager;
    }

    public TeleportExecutor teleportExecutor() {
        return teleportExecutor;
    }

    public GuildManager guilds() {
        return guildManager;
    }

    public GuildChatToggle guildChat() {
        return guildChatToggle;
    }

    public PacketEventsBootstrap packetEvents() {
        return packetEventsBootstrap;
    }

    public SpawnWorldManager spawnWorld() {
        return spawnWorldManager;
    }

    public WorldManager worlds() {
        return worldManager;
    }

    public StatsManager stats() {
        return statsManager;
    }
}