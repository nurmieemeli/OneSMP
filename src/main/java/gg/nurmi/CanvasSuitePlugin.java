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
import gg.nurmi.protection.PortalListener;
import gg.nurmi.protection.StrongholdDatapackInstaller;
import gg.nurmi.rtp.RtpCommand;
import gg.nurmi.rtp.RtpManager;
import gg.nurmi.scheduler.SchedulerUtil;
import gg.nurmi.shop.ShopCommand;
import gg.nurmi.shop.ShopManager;
import gg.nurmi.storage.Database;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.schedulerUtil = new SchedulerUtil(this);
        this.messageService = new MessageService(this);

        this.database = new Database(this);
        database.connect();

        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        registerEconomy();
        registerShop();
        registerTeleport();
        registerRtp();
        registerGuild();
        getServer().getPluginManager().registerEvents(new ChatFormatListener(this), this);
        registerProtection();

        getLogger().info("CanvasSuite enabled.");
    }

    private void registerProtection() {
        new StrongholdDatapackInstaller(this).installForAllWorlds();
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
    }

    private void registerRtp() {
        this.rtpManager = new RtpManager(this);
        getCommand("rtp").setExecutor(new RtpCommand(this, rtpManager));
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
}
