package gg.nurmi;

import gg.nurmi.crate.CrateCommand;
import gg.nurmi.crate.CrateListener;
import gg.nurmi.crate.CrateManager;
import gg.nurmi.economy.BalTopCommand;
import gg.nurmi.economy.BalanceCommand;
import gg.nurmi.economy.EcoCommand;
import gg.nurmi.economy.EconomyListener;
import gg.nurmi.economy.EconomyManager;
import gg.nurmi.economy.EconomyPlaceholderExpansion;
import gg.nurmi.economy.PayCommand;
import gg.nurmi.economy.VaultEconomyProvider;
import gg.nurmi.util.ActionCooldown;
import gg.nurmi.util.EffectsManager;
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
import gg.nurmi.message.help.HelpCommand;
import gg.nurmi.message.help.HelpManager;
import gg.nurmi.maintenance.MaintenanceCommand;
import gg.nurmi.maintenance.MaintenanceListener;
import gg.nurmi.maintenance.MaintenanceManager;
import gg.nurmi.market.MarketCommand;
import gg.nurmi.market.MarketManager;
import gg.nurmi.combat.CombatLogListener;
import gg.nurmi.message.DeathMessageListener;
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
import gg.nurmi.teleport.rtp.RtpCommand;
import gg.nurmi.scoreboard.ScoreboardListener;
import gg.nurmi.scoreboard.ScoreboardManager;
import gg.nurmi.teleport.rtp.RtpManager;
import gg.nurmi.util.SchedulerUtil;
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
import gg.nurmi.util.RecentAttackerTracker;
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
import gg.nurmi.vote.VoteCommand;
import gg.nurmi.vote.VoteListener;
import gg.nurmi.vote.VoteManager;
import gg.nurmi.vote.VotePlaceholderExpansion;
import gg.nurmi.vote.VoteTopCommand;
import gg.nurmi.world.WorldCommand;
import gg.nurmi.world.WorldManager;
import io.github.miniplaceholders.api.Expansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Set;

public final class OneSMPPlugin extends JavaPlugin {

    private SchedulerUtil schedulerUtil;
    private Database database;
    private EffectsManager effectsManager;
    private MessageService messageService;
    private ActionCooldown actionCooldown;
    private SubcommandAliases subcommandAliases;
    private EconomyManager economyManager;
    private Expansion economyPlaceholderExpansion;
    private ShopManager shopManager;
    private CrateManager crateManager;
    private TeleportExecutor teleportExecutor;
    private GuildManager guildManager;
    private GuildChatToggle guildChatToggle;
    private Expansion guildPlaceholderExpansion;
    private PacketEventsBootstrap packetEventsBootstrap;
    private SpawnWorldManager spawnWorldManager;
    private TablistManager tablistManager;
    private WorldManager worldManager;
    private StatsManager statsManager;
    private RecentAttackerTracker attackerTracker;
    private Expansion statsPlaceholderExpansion;
    private MaintenanceManager maintenanceManager;
    private MarketManager marketManager;
    private HelpManager helpManager;
    private VoteManager voteManager;
    private Expansion votePlaceholderExpansion;
    private LeaderboardHologramManager leaderboardHologramManager;
    private NametagManager nametagManager;

    @Override
    public void onEnable() {
        ConfigMigrator.migrate(this, "config.yml", Set.of("rtp.worlds"));
        reloadConfig();

        this.schedulerUtil = new SchedulerUtil(this);
        this.effectsManager = new EffectsManager(this);
        this.messageService = new MessageService(this);
        this.subcommandAliases = new SubcommandAliases(this);
        subcommandAliases.load();
        this.actionCooldown = new ActionCooldown();

        this.database = new Database(this);
        database.connect();

        this.packetEventsBootstrap = new PacketEventsBootstrap(this);
        packetEventsBootstrap.detect();

        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        registerEconomy();
        registerShop();
        registerMarket();
        registerCrates();
        registerTeleport();
        registerRtp();
        registerGuild();
        getServer().getPluginManager().registerEvents(new ChatFormatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinLeaveMessageListener(this), this);
        registerSpawn();
        registerWorlds();
        registerMessaging();
        registerNametags();
        registerModeration();
        registerMaintenance();
        registerTablist();
        registerScoreboard();
        registerStats();
        registerHolograms();
        registerHelp();
        registerLinks();
        registerVotes();

        new AliasManager(this).applyAliases();

        Objects.requireNonNull(getCommand("onesmp")).setExecutor(new OneSMPCommand(this));
    }

    // aliases.yml isn't reloaded here - aliases are registered into Bukkit's command map once at enable and can't be re-registered at runtime.
    public void reloadAll() {
        ConfigMigrator.migrate(this, "config.yml", Set.of("rtp.worlds"));
        reloadConfig();
        messageService.reload();
        shopManager.load();
        crateManager.reload();
        subcommandAliases.load();
        maintenanceManager.sync();
        helpManager.load();
        if (leaderboardHologramManager != null) {
            leaderboardHologramManager.refreshAll();
        }
    }

    private void registerStats() {
        this.statsManager = new StatsManager(this);
        this.attackerTracker = new RecentAttackerTracker(this);
        getServer().getPluginManager().registerEvents(attackerTracker, this);
        getServer().getPluginManager().registerEvents(new StatsListener(this, statsManager, attackerTracker), this);
        getServer().getPluginManager().registerEvents(new DeathMessageListener(this, attackerTracker), this);
        getServer().getPluginManager().registerEvents(new CombatLogListener(this, attackerTracker), this);

        Objects.requireNonNull(getCommand("stats")).setExecutor(new StatsCommand(this, statsManager));
        Objects.requireNonNull(getCommand("statstop")).setExecutor(new StatsTopCommand(this, statsManager));

        int periodSeconds = Math.max(5, getConfig().getInt("stats.playtime-autosave-interval-seconds", 60));
        schedulerUtil.runGlobalRepeating(statsManager::flushOnline, periodSeconds * 20L, periodSeconds * 20L);

        this.statsPlaceholderExpansion = StatsPlaceholderExpansion.register(this, statsManager);
    }

    // Bound-crate holograms are recreated here (not in registerCrates()) since that runs before the worlds their blocks live in are loaded.
    private void registerHolograms() {
        crateManager.respawnHolograms();

        boolean available = getServer().getPluginManager().getPlugin("FancyHolograms") != null;
        if (!available) {
            getLogger().warning("FancyHolograms not found - leaderboard holograms will be disabled. "
                    + "Install FancyHolograms to enable /statshologram.");
        }

        this.leaderboardHologramManager = available ? new LeaderboardHologramManager(this) : null;
        Objects.requireNonNull(getCommand("statshologram")).setExecutor(new LeaderboardHologramCommand(this, leaderboardHologramManager));

        if (available) {
            int periodSeconds = Math.max(5, getConfig().getInt("hologram.refresh-interval-seconds", 30));
            schedulerUtil.runGlobalRepeating(leaderboardHologramManager::refreshAll, periodSeconds * 20L, periodSeconds * 20L);
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

    private void registerMaintenance() {
        this.maintenanceManager = new MaintenanceManager(this);
        MaintenanceCommand maintenanceCommand = new MaintenanceCommand(this, maintenanceManager);
        Objects.requireNonNull(getCommand("maintenance")).setExecutor(maintenanceCommand);
        Objects.requireNonNull(getCommand("maintenance")).setTabCompleter(maintenanceCommand);
        getServer().getPluginManager().registerEvents(new MaintenanceListener(this, maintenanceManager), this);
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
        this.nametagManager = new NametagManager(this);
        getServer().getPluginManager().registerEvents(new NametagListener(nametagManager), this);
        int periodSeconds = Math.max(5, getConfig().getInt("nametag.refresh-interval-seconds", 30));
        schedulerUtil.runGlobalRepeating(nametagManager::refreshAll, periodSeconds * 20L, periodSeconds * 20L);

        int remountTicks = Math.max(20, getConfig().getInt("nametag.guild-tag.remount-interval-ticks", 100));
        schedulerUtil.runGlobalRepeating(nametagManager::reassertMounts, remountTicks, remountTicks);

        int worldCheckTicks = Math.max(10, getConfig().getInt("nametag.guild-tag.world-check-interval-ticks", 20));
        schedulerUtil.runGlobalRepeating(nametagManager::checkWorldChanges, worldCheckTicks, worldCheckTicks);
    }

    private void registerSpawn() {
        this.spawnWorldManager = new SpawnWorldManager(this);
        spawnWorldManager.ensureWorldExists();

        Objects.requireNonNull(getCommand("setspawn")).setExecutor(new SetSpawnCommand(this, spawnWorldManager));
        Objects.requireNonNull(getCommand("spawn")).setExecutor(new SpawnCommand(this, spawnWorldManager, teleportExecutor));

        getServer().getPluginManager().registerEvents(new FirstJoinListener(this, spawnWorldManager), this);
        getServer().getPluginManager().registerEvents(new VoidWorldListener(spawnWorldManager), this);
        getServer().getPluginManager().registerEvents(new VoidFallRescueListener(spawnWorldManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this, spawnWorldManager), this);
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
        Objects.requireNonNull(getCommand("sell")).setExecutor(new SellCommand(this));
    }

    private void registerMarket() {
        this.marketManager = new MarketManager(this);
        MarketCommand marketCommand = new MarketCommand(this, marketManager);
        Objects.requireNonNull(getCommand("market")).setExecutor(marketCommand);
        Objects.requireNonNull(getCommand("market")).setTabCompleter(marketCommand);
    }

    private void registerHelp() {
        this.helpManager = new HelpManager(this);
        HelpCommand helpCommand = new HelpCommand(this, helpManager);
        Objects.requireNonNull(getCommand("help")).setExecutor(helpCommand);
        Objects.requireNonNull(getCommand("help")).setTabCompleter(helpCommand);
    }

    private void registerLinks() {
        Objects.requireNonNull(getCommand("discord")).setExecutor(
                new LinkCommand(this, "links.discord-url", "links.discord", "onesmp.discord.use"));
        Objects.requireNonNull(getCommand("store")).setExecutor(
                new LinkCommand(this, "links.store-url", "links.store", "onesmp.store.use"));
    }

    private void registerVotes() {
        this.voteManager = new VoteManager(this);
        VoteListener voteListener = new VoteListener(this, voteManager);
        getServer().getPluginManager().registerEvents(voteListener, this);
        voteListener.registerIfAvailable();

        Objects.requireNonNull(getCommand("vote")).setExecutor(new VoteCommand(this, voteManager));
        Objects.requireNonNull(getCommand("votetop")).setExecutor(new VoteTopCommand(this, voteManager));

        this.votePlaceholderExpansion = VotePlaceholderExpansion.register(this, voteManager);
    }

    private void registerCrates() {
        this.crateManager = new CrateManager(this);
        CrateCommand crateCommand = new CrateCommand(this, crateManager);
        Objects.requireNonNull(getCommand("crate")).setExecutor(crateCommand);
        Objects.requireNonNull(getCommand("crate")).setTabCompleter(crateCommand);
        getServer().getPluginManager().registerEvents(new CrateListener(this, crateManager), this);
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
        if (votePlaceholderExpansion != null && votePlaceholderExpansion.registered()) {
            votePlaceholderExpansion.unregister();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("OneSMP disabled.");
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

    public EffectsManager effects() {
        return effectsManager;
    }

    public ActionCooldown actionCooldown() {
        return actionCooldown;
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

    public MarketManager market() {
        return marketManager;
    }

    public HelpManager help() {
        return helpManager;
    }

    public VoteManager votes() {
        return voteManager;
    }

    public CrateManager crates() {
        return crateManager;
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

    // Null if nametag.enabled is false in config.yml - registerNametags() skips creating it entirely.
    public NametagManager nametags() {
        return nametagManager;
    }

    public RecentAttackerTracker attackerTracker() {
        return attackerTracker;
    }

    public MaintenanceManager maintenance() {
        return maintenanceManager;
    }
}
