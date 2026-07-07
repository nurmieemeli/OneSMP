package gg.nurmi;

import gg.nurmi.economy.BalTopCommand;
import gg.nurmi.economy.BalanceCommand;
import gg.nurmi.economy.EcoCommand;
import gg.nurmi.economy.EconomyListener;
import gg.nurmi.economy.EconomyManager;
import gg.nurmi.economy.PayCommand;
import gg.nurmi.economy.VaultEconomyProvider;
import gg.nurmi.chat.ChatFormatListener;
import gg.nurmi.guild.GuildChatToggle;
import gg.nurmi.guild.GuildCommand;
import gg.nurmi.guild.GuildManager;
import gg.nurmi.gui.GuiListener;
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
import gg.nurmi.packets.PacketEventsBootstrap;
import gg.nurmi.protection.PortalListener;
import gg.nurmi.protection.StrongholdDatapackInstaller;
import gg.nurmi.rtp.RtpCommand;
import gg.nurmi.scoreboard.ScoreboardListener;
import gg.nurmi.scoreboard.ScoreboardManager;
import gg.nurmi.rtp.RtpManager;
import gg.nurmi.scheduler.SchedulerUtil;
import gg.nurmi.shop.ShopCommand;
import gg.nurmi.shop.ShopManager;
import gg.nurmi.spawn.FirstJoinListener;
import gg.nurmi.spawn.PlayerRespawnListener;
import gg.nurmi.spawn.SetSpawnCommand;
import gg.nurmi.spawn.SpawnCommand;
import gg.nurmi.spawn.SpawnWorldManager;
import gg.nurmi.spawn.VoidFallRescueListener;
import gg.nurmi.spawn.VoidWorldListener;
import gg.nurmi.storage.Database;
import gg.nurmi.tablist.TablistListener;
import gg.nurmi.tablist.TablistManager;
import gg.nurmi.teleport.BackCommand;
import gg.nurmi.teleport.BackManager;
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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CanvasSuitePlugin extends JavaPlugin {

    private SchedulerUtil schedulerUtil;
    private Database database;
    private MessageService messageService;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private HomeManager homeManager;
    private WarpManager warpManager;
    private BackManager backManager;
    private TpaManager tpaManager;
    private TeleportExecutor teleportExecutor;
    private RtpManager rtpManager;
    private GuildManager guildManager;
    private GuildChatToggle guildChatToggle;
    private PacketEventsBootstrap packetEventsBootstrap;
    private SpawnWorldManager spawnWorldManager;
    private NametagManager nametagManager;
    private SpectateManager spectateManager;
    private TablistManager tablistManager;
    private ScoreboardManager scoreboardManager;
    private WorldManager worldManager;
    private PrivateMessageManager privateMessageManager;
    private SocialSpyToggle socialSpyToggle;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.schedulerUtil = new SchedulerUtil(this);
        this.messageService = new MessageService(this);

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
        registerProtection();
        registerSpawn();
        registerWorlds();
        registerMessaging();
        registerNametags();
        registerModeration();
        registerTablist();
        registerScoreboard();

        getLogger().info("CanvasSuite enabled.");
    }

    private void registerScoreboard() {
        if (!getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        this.scoreboardManager = new ScoreboardManager(this);
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
        this.spectateManager = new SpectateManager(vanishController);
        getCommand("spectate").setExecutor(new SpectateCommand(this, spectateManager));
    }

    private void registerWorlds() {
        this.worldManager = new WorldManager(this);
        worldManager.loadWorlds();

        WorldCommand worldCommand = new WorldCommand(this, worldManager);
        getCommand("world").setExecutor(worldCommand);
        getCommand("world").setTabCompleter(worldCommand);
    }

    private void registerMessaging() {
        this.socialSpyToggle = new SocialSpyToggle();
        this.privateMessageManager = new PrivateMessageManager(this, socialSpyToggle);

        getServer().getPluginManager().registerEvents(new PrivateMessageListener(privateMessageManager), this);

        getCommand("msg").setExecutor(new MsgCommand(this, privateMessageManager));
        getCommand("reply").setExecutor(new ReplyCommand(this, privateMessageManager));
        getCommand("ignore").setExecutor(new IgnoreCommand(this, privateMessageManager));
        getCommand("socialspy").setExecutor(new SocialSpyCommand(this, socialSpyToggle));
    }

    private void registerNametags() {
        if (!getConfig().getBoolean("nametag.enabled", true)) {
            return;
        }
        this.nametagManager = new NametagManager(this);
        getServer().getPluginManager().registerEvents(new NametagListener(nametagManager), this);
        int periodSeconds = Math.max(5, getConfig().getInt("nametag.refresh-interval-seconds", 30));
        schedulerUtil.runGlobalRepeating(nametagManager::refreshAll, periodSeconds * 20L, periodSeconds * 20L);
    }

    private void registerSpawn() {
        this.spawnWorldManager = new SpawnWorldManager(this);
        spawnWorldManager.ensureWorldExists();

        getCommand("setspawn").setExecutor(new SetSpawnCommand(this, spawnWorldManager));
        getCommand("spawn").setExecutor(new SpawnCommand(this, spawnWorldManager));

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
        this.rtpManager = new RtpManager(this);
        getCommand("rtp").setExecutor(new RtpCommand(this, rtpManager));

        if (getConfig().getBoolean("rtp.precache.enabled", true)) {
            int periodSeconds = Math.max(1, getConfig().getInt("rtp.precache.interval-seconds", 5));
            schedulerUtil.runGlobalRepeating(rtpManager::precacheTick, periodSeconds * 20L, periodSeconds * 20L);
        }
    }

    private void registerGuild() {
        this.guildManager = new GuildManager(this);
        this.guildChatToggle = new GuildChatToggle();
        GuildCommand guildCommand = new GuildCommand(this, guildManager, guildChatToggle);
        getCommand("guild").setExecutor(guildCommand);
        getCommand("guild").setTabCompleter(guildCommand);
    }

    private void registerTeleport() {
        this.homeManager = new HomeManager(this);
        this.warpManager = new WarpManager(this);
        this.backManager = new BackManager();
        this.tpaManager = new TpaManager(this);
        TeleportWarmup warmup = new TeleportWarmup(this);
        this.teleportExecutor = new TeleportExecutor(this, backManager, warmup);

        getServer().getPluginManager().registerEvents(backManager, this);

        getCommand("sethome").setExecutor(new SetHomeCommand(this, homeManager));
        getCommand("home").setExecutor(new HomeCommand(this, homeManager, teleportExecutor));
        getCommand("delhome").setExecutor(new DelHomeCommand(this, homeManager));

        getCommand("setwarp").setExecutor(new SetWarpCommand(this, warpManager));
        getCommand("warp").setExecutor(new WarpCommand(this, warpManager, teleportExecutor));
        getCommand("delwarp").setExecutor(new DelWarpCommand(this, warpManager));

        getCommand("tpa").setExecutor(new TpaCommand(this, tpaManager, false));
        getCommand("tpahere").setExecutor(new TpaCommand(this, tpaManager, true));
        getCommand("tpaccept").setExecutor(new TpAcceptCommand(this, tpaManager, teleportExecutor));
        getCommand("tpdeny").setExecutor(new TpDenyCommand(this, tpaManager));

        getCommand("back").setExecutor(new BackCommand(this, backManager, teleportExecutor));
    }

    private void registerShop() {
        this.shopManager = new ShopManager(this);
        getCommand("shop").setExecutor(new ShopCommand(this));
    }

    private void registerEconomy() {
        this.economyManager = new EconomyManager(this);
        getServer().getPluginManager().registerEvents(new EconomyListener(economyManager), this);

        getCommand("balance").setExecutor(new BalanceCommand(this, economyManager));
        getCommand("pay").setExecutor(new PayCommand(this, economyManager));
        getCommand("baltop").setExecutor(new BalTopCommand(this, economyManager));
        getCommand("eco").setExecutor(new EcoCommand(this, economyManager));

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            getServer().getServicesManager().register(Economy.class, new VaultEconomyProvider(economyManager),
                    this, ServicePriority.Normal);
            getLogger().info("Hooked into Vault for economy support.");
        }
    }

    @Override
    public void onDisable() {
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

    public EconomyManager economy() {
        return economyManager;
    }

    public ShopManager shop() {
        return shopManager;
    }

    public HomeManager homes() {
        return homeManager;
    }

    public WarpManager warps() {
        return warpManager;
    }

    public BackManager backs() {
        return backManager;
    }

    public TpaManager tpa() {
        return tpaManager;
    }

    public TeleportExecutor teleportExecutor() {
        return teleportExecutor;
    }

    public RtpManager rtp() {
        return rtpManager;
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

    public NametagManager nametags() {
        return nametagManager;
    }

    public SpectateManager spectate() {
        return spectateManager;
    }

    public TablistManager tablist() {
        return tablistManager;
    }

    public ScoreboardManager scoreboard() {
        return scoreboardManager;
    }

    public WorldManager worlds() {
        return worldManager;
    }

    public PrivateMessageManager privateMessages() {
        return privateMessageManager;
    }

    public SocialSpyToggle socialSpy() {
        return socialSpyToggle;
    }
}
