<p align="center">
  <img src="assets/banner.svg" alt="OneSMP - Paper &amp; Folia" width="100%">
</p>

<p align="center">
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-38bdf8?style=for-the-badge&logo=openjdk&logoColor=white">
  <img alt="Paper and Folia native" src="https://img.shields.io/badge/Paper%20%26%20Folia-native-818cf8?style=for-the-badge">
  <img alt="Minecraft 1.21.8+" src="https://img.shields.io/badge/Minecraft-1.21.8%2B-38bdf8?style=for-the-badge&logo=minecraft&logoColor=white">
  <img alt="Build with Maven" src="https://img.shields.io/badge/build-Maven-818cf8?style=for-the-badge&logo=apachemaven&logoColor=white">
</p>

<p align="center"><b>Seek no more, you've found it.</b></p>

<p align="center">
A single-jar SMP plugin for <a href="https://papermc.io/software/paper">Paper</a> and <a href="https://papermc.io/software/folia">Folia</a> servers: economy, a player-to-player market, an admin shop, crates, teleportation (homes/warps/TPA/RTP), guilds, world creation, a void spawn world, chat formatting, nametags/tablist/scoreboard, player stats with leaderboard holograms, moderator tools, private messaging, and native-dialog help articles — built against Paper's region/entity scheduler API, which runs correctly on both a regular Paper server and Folia's per-region threading, with chest GUIs and Paper dialogs wherever they make sense.
</p>

<p align="center">
  <a href="#requirements">Requirements</a> •
  <a href="#features">Features</a> •
  <a href="#commands">Commands</a> •
  <a href="#permissions">Permissions</a> •
  <a href="#configuration">Configuration</a> •
  <a href="#installation">Installation</a> •
  <a href="#building-from-source">Building</a> •
  <a href="#notes--limitations">Notes & Limitations</a>
</p>

---

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Paper or Folia 1.21.8+ | ✅ | Or a Paper/Folia-based fork on an equivalent version |
| Java 25 | ✅ | |
| [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) | ✅ | Hard dependency — the plugin won't load without it |
| [PacketEvents](https://www.spigotmc.org/resources/packetevents.80279/) | Optional | Powers overhead nametags, `/spectate`, and the tablist's reserved-slot filler |
| [LuckPerms](https://luckperms.net/) | Optional | Sorts the tablist by each player's effective group weight |
| [MiniPlaceholders-LuckPerms expansion](https://github.com/MiniPlaceholders/MiniPlaceholders) | Optional | Resolves `<luckperms_prefix>`/`<luckperms_suffix>` in chat, nametags, and the scoreboard |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Optional | If present, OneSMP registers itself as the Vault economy provider |
| [FancyHolograms](https://modrinth.com/plugin/fancyholograms) | Optional | Powers `/statshologram` and the name hologram above a bound crate |
| [NuVotifier](https://github.com/NuVotifier/NuVotifier) | Optional | Powers automatic vote rewards; without it `/vote` still shows links, just no auto-reward |
| MySQL server | Optional | Falls back to a local SQLite file automatically |

Every optional dependency degrades gracefully when missing — see [Notes & Limitations](#notes--limitations). All player-facing text renders through [Adventure MiniMessage](https://docs.advntr.dev/minimessage/format.html) with [MiniPlaceholders](https://github.com/MiniPlaceholders/MiniPlaceholders) support (global, audience, and relational placeholders).

## Features

### Economy
Vault-compatible currency with a configurable symbol/name, starting balance, and max-balance cap. `/balance`, `/pay`, `/baltop` (paginated GUI), and admin-only `/eco give|take|set`. Registers as the server's Vault economy provider automatically when Vault is installed.

### Market
A player-to-player marketplace backed by the database, so listings survive restarts and sell while the seller is offline. `/market sell <price>` lists the item in your hand for a set total price; `/market` opens a paginated GUI of active listings to buy from. `/market search <query>` filters that GUI — run it with no query, or click the compass button in the browse GUI, to search through a native Paper [dialog](https://docs.papermc.io/paper/dev/dialogs/) text prompt instead of typing the query as a command argument. `/market mine` lists your own active listings, cancellable to reclaim the item. `market.max-listings-per-player` in `config.yml` caps how many listings one player can have active. Overflow-safe throughout: if a buyer's or canceller's inventory is full, the item drops at their feet instead of being lost.

### Shop
`/sell` opens an empty inventory to drop items into, showing a running payout as you fill it and paying out on confirm. Per-item prices live in `sell-prices.yml`; `shop.sell-price-multiplier` in `config.yml` scales every price at once. Unsold items left in the menu when it's closed drop back into your inventory.

### Crates
Crate types — key appearance and a weighted reward pool of items, money, and console commands — are defined entirely in `lang/<language>/crates.yml`. `/crate create <type>` binds the block an admin is looking at; `/crate remove` unbinds it. `/crate key <type> <player> [amount]` gives key items tagged with a hidden marker for that crate type, so they can't be duplicated from an ordinary item of the same material. Right-clicking a bound crate with a matching key consumes it, rolls a weighted-random reward, and plays a slot-machine-style reel that always lands on the reward already rolled — purely cosmetic, and the reward is granted the moment the reel stops (or immediately if the menu is closed early). Left-clicking opens a read-only preview of every reward in the pool with its exact drop chance. With FancyHolograms installed, `/crate create` also drops a floating name hologram above the block, removed again by `/crate remove`. Like the leaderboard holograms, OneSMP owns its location/persistence and recreates it fresh from its own storage on enable rather than relying on FancyHolograms to remember it.

### Teleportation
**Homes** — `/sethome [name]`, `/home [name]`, `/delhome <name>`, with per-rank limits via `onesmp.home.limit.<n>`/`onesmp.home.unlimited`. Running `/home` with no name opens a GUI of all your homes. **Warps** — admin-defined via `/setwarp`/`/delwarp`; `/warp` with no name opens a browser GUI. **TPA** — `/tpa <player>`/`/tpahere <player>` with clickable Accept/Deny buttons in chat, automatic expiry, `/tpaccept`/`/tpdeny`. All three share the same configurable teleport warmup, cancelled on movement.

### Spawn & Void World
A dedicated void world is created automatically on first enable — a custom `ChunkGenerator` with no terrain, bedrock, caves, structures, or mobs — with a small starter platform so nobody spawns into open air. `/setspawn` (admin) sets the server spawn point; `/spawn` teleports there through the same warmup as homes/warps. First-time joiners land there automatically, as does anyone who dies with no valid bed/anchor. Inside the void world, non-creative players can't break or place blocks, use buckets, open/toggle a block (except doors), trample farmland, take damage, or lose food — but using a held item itself (throwing a bottle, eating, drinking, etc.) is never blocked, only whatever it would change about the world. Falling out (real void damage, not a Y-check) teleports straight back to spawn.

### World Creation
`/world create <name> [seed]` opens a GUI to configure a new world — environment, world type, generator (vanilla or the void generator spawn uses), seed, structures, hardcore, difficulty, PvP — before creating it. `/world list` browses every managed world with a detail/teleport/delete view; `/world teleport <name>` goes through the same warmup as `/spawn`. `/world delete <name>` unloads and stops tracking a world but leaves its folder on disk; only `/world delete <name> wipe` permanently deletes the files. Settings persist in `worlds.yml` and reapply identically on every restart.

### Random Teleport
`/rtp [world]` teleports to a random safe spot within a configurable radius; with no world given it opens a GUI listing every RTP-enabled world and its fee. Enabled per world with a configurable cost, cooldown (bypassable via `onesmp.rtp.admin`), and biome avoid-list. A background precache keeps a small pool of pre-verified safe locations per world, topped off only while TPS allows, so `/rtp` usually resolves instantly instead of searching live.

### Guilds
`/guild create <name> <tag>` (optional cost), `/guild disband`, `/guild invite`/`accept`, `/guild kick`, `/guild promote`/`demote` across Member/Officer/Owner roles, `/guild info`, `/guild list`, `/guild leave`, `/guild sethome`/`home`, and `/guild chat` for a guild-only channel. `/guild gui` (or bare `/guild`) opens a full management GUI covering everything the commands do. The guild tag renders on a genuine second nametag line in-world. Configurable name/tag length limits, member limit, and creation cost.

### Chat Formatting
One MiniMessage-rendered chat format for everyone, with full MiniPlaceholders and relational placeholder support, configurable in `config.yml`. A built-in `<guild_segment>` placeholder renders to nothing for guildless players. Player-typed message content is always inserted as plain text — players can't smuggle click/hover/color tags into chat unless granted `onesmp.chat.format`, which applies to DMs too. Join/leave messages are MiniMessage templates, each independently toggleable (off means no message at all, not a blank one, replacing vanilla's line either way).

### Nametags & Tablist
Overhead nametag prefixes are sent as per-player scoreboard team packets only — no real `Scoreboard`/`Team` is ever registered. Guild members additionally get their tag on a genuine second line via an invisible `TEXT_DISPLAY` entity mounted as a passenger, since vanilla team prefixes can't add a second line. The tablist stays full-size at all times: real players are mirrored (sorted by LuckPerms weight, then alphabetically) onto pre-allocated synthetic entries, with unused slots filled by a configurable placeholder skin instead of the grid shrinking — with more players online than the grid holds, the last slot becomes a "+N more" summary. Requires PacketEvents; without it, nametags and the reserved-slot filler are silently disabled while tablist header/footer (native Paper API) keep working.

### Player Stats
Tracks kills, deaths, current/best killstreak, and playtime per player. Kill credit isn't limited to direct melee — a shot arrow, thrown trident, a player-lit TNT, a broken End Crystal, or a triggered respawn anchor still counts, and knocking someone into lava/the void/off a cliff still credits you as long as the fatal follow-through happens within `stats.indirect-kill-window-seconds`. Death messages are fully replaced with MiniMessage templates from `messages.yml` (PvP, mob kills, and every common environmental cause), so every viewer sees them in the server's configured language instead of vanilla's per-client translation. `/stats [player]` shows a summary for yourself or anyone else, online or offline; `/statstop <kills|deaths|killstreak|playtime|kd>` opens a paginated leaderboard GUI. Hitting a configurable killstreak milestone broadcasts server-wide. Playtime accrues live and flushes periodically, on quit, and on shutdown, so a crash loses at most one autosave interval. Exposed as MiniPlaceholders tags (`<stats_kills>`, `<stats_deaths>`, `<stats_kd>`, `<stats_killstreak>`, `<stats_best_killstreak>`, `<stats_playtime>`) usable anywhere.

### Combat Log Protection
A player who disconnects within `combat-log.timeout-seconds` of being hit by another player — directly, via a shot projectile, via TNT they lit, via an End Crystal they broke, or via a respawn anchor they triggered — is killed on the way out instead of safely logging out of a losing fight. It goes through the normal damage pipeline, so a totem of undying, resistance, or absorption still applies exactly as it would for any other lethal hit, and the resulting death is credited/announced through the same stats and death-message systems as any other kill. Toggleable via `combat-log.enabled`. Independently, `combat-log.disable-teleport` (also on by default) blocks a tagged player from using any teleport — homes, warps, spawn, RTP, guild home, world teleports, or accepting a TPA request — for that same window, and blocks anyone else from having their TPA request to a tagged player accepted, so a losing fighter can't escape or call someone in to help by teleporting.

### Leaderboard Holograms
With FancyHolograms installed, `/statshologram create <stat> <name> [limit]` drops a text hologram that's periodically repopulated with a live leaderboard for kills, deaths, killstreak, playtime, or K/D. `/statshologram remove`/`list` manage existing ones. OneSMP owns each hologram's location and persistence itself (FancyHolograms is used purely as the renderer, marked non-persistent on its side) — every hologram is recreated fresh from OneSMP's own storage on enable, so it isn't affected by FancyHolograms' own save state. Without FancyHolograms, `/statshologram` replies with a clear "not installed" message instead of erroring.

### Scoreboard
A MiniMessage/MiniPlaceholders-driven sidebar scoreboard with a configurable title and lines, refreshed on an interval and rendered relationally per viewer. Toggleable entirely via `scoreboard.enabled`.

### Moderator Spectate
`/spectate <player>` toggles a packet-driven vanish + spectator view of the target — the target never sees the moderator, who can fly through blocks to observe. Requires PacketEvents; refuses to run without it rather than offer a false sense of invisibility.

### Help
`/help` opens a native Paper [dialog](https://docs.papermc.io/paper/dev/dialogs/) listing categories; clicking one shows its articles, and clicking an article shows its content — server-side navigation, no inventory GUI or chat involved. `/help <category>` and `/help <category> <article>` jump straight in. Categories and articles (title, description, icon, MiniMessage body) are entirely defined in `lang/<language>/help.yml`.

### Private Messaging
`/msg`/`/tell`/`/w`/`/pm`, `/reply`, `/ignore`, and `/socialspy` (mirrors every DM to whoever has it enabled), with a configurable per-sender cooldown.

### Links
`/discord` and `/store` print a clickable, hoverable link built from `links.discord-url`/`links.store-url` in `config.yml`.

### Vote Rewards
Hooks into [NuVotifier](https://github.com/NuVotifier/NuVotifier) (optional, via reflection — no compile-time dependency) to reward players for voting: configurable money and crate keys per vote, plus a bonus reward at consecutive-daily-vote streak milestones (e.g. 7, 30 days). Crate keys are delivered immediately if the voter is online, or queued and handed over the next time they join if not. `/vote` shows the configured vote-site links and the player's own vote count/streak; `/votetop` opens a paginated leaderboard GUI. Without NuVotifier installed, `/vote` still works as a plain link list — rewards just won't trigger automatically.

### Feedback & Effects
Every command reply is automatically paired with a sound — dedicated `<success>`/`<fail>` markers right after `<prefix>` in `messages.yml` pick the chime or the denial grunt, kept deliberately separate from whatever color the message actually uses so translating or restyling a message can never break its sound. Teleporting plays a particle/sound burst at both ends; every chest-GUI menu plays a soft click on open; joining plays a short chime. Each category (and cosmetic effects as a whole) is independently toggleable under `effects` in `config.yml` — purely cosmetic, never affects whether an action succeeds.

### Localization
Six languages ship out of the box — `en_US`, `fi_FI`, `sv_SE`, `de_DE`, `ru_RU`, `es_ES` — covering every player-facing string: chat/GUI/dialog text, help articles, crate/reward names, and command/subcommand aliases. Set `language` in `config.yml` to switch, and run `/onesmp reload` (or restart) to apply it.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/onesmp reload` (`/cs`) | Reload `config.yml`, `sell-prices.yml`, and the active language's `messages.yml`/`crates.yml`/`help.yml`/`subcommand-aliases.yml` | `onesmp.admin` |
| `/balance [player]` (`/bal`, `/money`) | Check a balance | `onesmp.economy.use` |
| `/pay <player> <amount>` | Send money | `onesmp.economy.use` |
| `/baltop` (`/bt`) | Rich-list GUI | `onesmp.economy.use` |
| `/eco <give\|take\|set> <player> <amount>` | Admin economy control | `onesmp.economy.admin` |
| `/sell` | Open the sell GUI | `onesmp.shop.sell` |
| `/market [sell <price>\|search <query>\|mine\|cancel <id>]` | Browse, search, list, and cancel market listings | `onesmp.market.use` |
| `/crate <create\|remove\|key> ...` | Bind/unbind crate blocks, give keys (admin) | `onesmp.crate.admin` |
| `/sethome [name]` / `/home [name]` (`/homes`) / `/delhome <name>` | Manage & use homes | `onesmp.home.use` |
| `/setwarp <name>` / `/delwarp <name>` | Manage warps | `onesmp.warp.admin` |
| `/warp [name]` (`/warps`) | Warp browser / teleport | `onesmp.warp.use` |
| `/tpa <player>` / `/tpahere <player>` | Teleport requests | `onesmp.tpa.use` |
| `/tpaccept` / `/tpdeny` | Answer a request | `onesmp.tpa.use` |
| `/rtp [world]` (`/wild`, `/wilderness`, `/randomtp`) | Random teleport, or open the world-select GUI | `onesmp.rtp.use` |
| `/guild <sub>` (`/g`, `/clan`, `/team`, `/faction`) | Guild management (create/disband/invite/accept/kick/promote/demote/sethome/home/info/list/chat/leave/gui) | `onesmp.guild.use` |
| `/setspawn` | Set the server spawn point | `onesmp.spawn.admin` |
| `/spawn` | Teleport to spawn | `onesmp.spawn.use` |
| `/world <create\|list\|delete\|teleport> ...` (`/worlds`) | Create and manage worlds | `onesmp.world.admin` |
| `/spectate [player]` | Toggle moderator spectate/vanish | `onesmp.moderation.spectate` |
| `/msg <player> <message>` (`/tell`, `/w`, `/pm`) | Send a private message | `onesmp.msg.use` |
| `/reply <message>` (`/r`) | Reply to your last DM | `onesmp.msg.use` |
| `/ignore <player>` | Toggle ignoring a player's DMs | `onesmp.msg.use` |
| `/socialspy` | Toggle mirroring all DMs to yourself | `onesmp.msg.socialspy` |
| `/stats [player]` | View kill/death/killstreak/playtime/K-D stats | `onesmp.stats.use` |
| `/statstop <kills\|deaths\|killstreak\|playtime\|kd>` | Leaderboard GUI for a stat | `onesmp.stats.use` |
| `/statshologram <create\|remove\|list> ...` | Manage leaderboard holograms (requires FancyHolograms) | `onesmp.stats.hologram.admin` |
| `/help [category] [article]` | Browse help articles via a native dialog | `onesmp.help.use` |
| `/maintenance <on\|off\|status>` | Toggle maintenance mode (backed by the server whitelist) | `onesmp.maintenance.admin` |
| `/discord` | Print the Discord invite link | `onesmp.discord.use` |
| `/store` | Print the store link | `onesmp.store.use` |
| `/vote` | Show vote-site links and your vote count/streak | `onesmp.vote.use` |
| `/votetop` | Vote count leaderboard GUI | `onesmp.vote.use` |

Every command's own name always works regardless of configuration — the aliases above are just the shipped defaults. Both command aliases and per-subcommand aliases (e.g. `/world tp` for `/world teleport`) are editable via `lang/<language>/aliases.yml`/`subcommand-aliases.yml`; `subcommand-aliases.yml` changes apply on `/onesmp reload`, while `aliases.yml` needs a restart since those aliases are registered into Bukkit's command map once at enable.

## Permissions

<details>
<summary>Full permission node table</summary>

| Node | Default | Purpose |
|---|---|---|
| `onesmp.admin` | op | Grants every admin node below |
| `onesmp.economy.use` / `.admin` | true / op | Economy commands / `/eco` |
| `onesmp.shop.sell` | true | Sell menu access |
| `onesmp.market.use` | true | Browse, search, list, and buy on the market |
| `onesmp.crate.use` | true | Open a bound crate with a matching key |
| `onesmp.crate.admin` | op | `/crate` (create/remove/key) |
| `onesmp.home.use` | true | Homes |
| `onesmp.home.limit.<n>` | — | Raises that player's home limit to `n` |
| `onesmp.home.unlimited` | false | No home limit |
| `onesmp.warp.use` / `.admin` | true / op | Warp use / management |
| `onesmp.tpa.use` | true | TPA |
| `onesmp.rtp.use` / `.admin` | true / op | RTP / cooldown bypass |
| `onesmp.guild.use` / `.admin` | true / op | Guilds |
| `onesmp.chat.format` | false | Allows MiniMessage styling in chat messages and DMs |
| `onesmp.spawn.use` / `.admin` | true / op | `/spawn` / `/setspawn` |
| `onesmp.world.admin` | op | `/world` (create/list/delete/teleport) |
| `onesmp.moderation.spectate` | op | `/spectate` |
| `onesmp.maintenance.admin` | op | `/maintenance` |
| `onesmp.maintenance.bypass` | op | Join during maintenance without being whitelisted |
| `onesmp.msg.use` | true | Private messaging (`/msg`, `/reply`, `/ignore`) |
| `onesmp.msg.socialspy` | op | `/socialspy` |
| `onesmp.stats.use` | true | `/stats`, `/statstop` |
| `onesmp.stats.hologram.admin` | op | `/statshologram` (create/remove/list) |
| `onesmp.help.use` | true | `/help` |
| `onesmp.discord.use` | true | `/discord` |
| `onesmp.store.use` | true | `/store` |
| `onesmp.vote.use` | true | `/vote`, `/votetop` |

</details>

## Configuration

`plugins/OneSMP/` is populated on first start with `config.yml`, `sell-prices.yml`, a `lang/` folder (see [Localization](#localization) below), and `worlds.yml` once you first use `/world create`:

- **`config.yml`** — storage backend, economy, teleport warmup/TPA timeouts, home limits, RTP settings, guild rules, join/leave toggles, market listing cap, maintenance mode, spawn/void-world settings, nametag/tablist/scoreboard intervals, private-message cooldown, stats/hologram intervals, Discord/store links, the active `language`, cosmetic effect toggles, and the anti-spam action cooldown.
- **`sell-prices.yml`** — per-item `/sell` prices. Market listings aren't configured here — they're created in-game and stored in the database.
- **`lang/<language>/messages.yml`** — every message the plugin sends, in MiniMessage, plus the chat format, scoreboard title/lines, and the guild "no guild" placeholder. The shared `<prefix>` is defined once at the top.
- **`lang/<language>/help.yml`** — `/help` categories and their articles.
- **`lang/<language>/crates.yml`** — crate types: key appearance and a weighted reward pool per type.
- **`lang/<language>/aliases.yml`** — extra command aliases, registered into Bukkit's command map at enable. Changes need a restart.
- **`lang/<language>/subcommand-aliases.yml`** — extra aliases for individual subcommands (e.g. `/world tp`). Not real Bukkit commands, just labels checked in code; picked up by `/onesmp reload`.
- **`worlds.yml`** — one entry per `/world create`d world, storing its generator settings. Managed entirely by `/world` — no hand-editing needed.

On every startup, each file above except `worlds.yml` is checked against the version bundled in the jar, and any option missing on disk is added with its default value — existing settings are never touched. Since re-saving a YAML file drops hand-written comments, this only re-saves when something was actually missing, and always leaves a `<file>.bak` copy of the pre-update version first.

### Localization

Set `language` in `config.yml` to pick which `lang/<language>/` set of files gets loaded — shipped out of the box: `en_US`, `sv_SE`, `fi_FI`, `de_DE`, `ru_RU`, `es_ES`. An unrecognized code falls back to `en_US` with a warning logged. Switching languages and running `/onesmp reload` (or restarting) applies the change immediately, with one exception: the guild "no guild" placeholder text is cached once at startup, so that one specific string needs a full restart to pick up a new language.

Each language is upgraded independently the same way as any other config file — a plugin update that adds a new message key adds it (in that language) to every `lang/<language>/` file without touching your edits. A pre-existing root-level `messages.yml`/`help.yml`/`crates.yml`/`aliases.yml`/`subcommand-aliases.yml` from before this system existed is moved into `lang/en_US/` automatically the first time the plugin loads it, so upgrading servers don't lose their customizations.

<details>
<summary>Example: chat format</summary>

```yaml
chat:
  format: "<guild_segment><luckperms_prefix><player_name><gray>:</gray> <white><message>"
```

`<player_name>` isn't reset/re-colored after `<luckperms_prefix>` by default, so a prefix that doesn't close its own color tag (e.g. `<red>[Admin] `) carries that color onto the name too — add your own `<reset>` before `<player_name>` if that's unwanted. `<guild_segment>` carries its own trailing space (via `chat.guild-segment-format`) and renders to nothing for guildless players, so don't add a space after it yourself. `chat.guild-chat-format` (the guild-only channel toggled by `/guild chat`) can use `<guild_segment>` too, alongside `<guild_name>`/`<guild_tag>` directly.

</details>

<details>
<summary>Example: per-world RTP settings</summary>

```yaml
rtp:
  worlds:
    world:
      enabled: true
      cost: 0.0
    my_wilderness_world:
      enabled: true
      cost: 50.0
```

</details>

<details>
<summary>Example: guild-tag nametag</summary>

```yaml
nametag:
  guild-tag:
    format: "<dark_aqua>[<aqua><guild_tag></aqua>]</dark_aqua>"
    y-offset: 0.55
```

</details>

## Installation

1. Build the plugin (see [Building from Source](#building-from-source)) or grab a release jar, and drop it into `plugins/`.
2. Install [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) (required). Optionally install PacketEvents, LuckPerms + its MiniPlaceholders expansion, Vault, and FancyHolograms.
3. Start the server once to generate config files under `plugins/OneSMP/`, then edit them to taste.
4. Set `storage.type` to `MYSQL` in `config.yml` for a shared database instead of the default local SQLite file — see [Storage](#storage).

### Storage

Set `storage.type` to `MYSQL` or `SQLITE` in `config.yml`. MySQL connections pool through HikariCP; if the database is unreachable at startup, the plugin logs a warning and falls back to a local SQLite file (`plugins/OneSMP/data.db`) so the server still boots. The schema is created automatically on either backend.

## Building from Source

Requires JDK 25 and Maven. The shaded jar lands at `target/OneSMP-<version>.jar`.

```bash
mvn clean package
```

HikariCP, the MySQL driver, and the SQLite driver are shaded and relocated (`gg.nurmi.libs.*`) so they never collide with another plugin's copies. `paper-api`, MiniPlaceholders, VaultAPI, PacketEvents, the LuckPerms API, and FancyHolograms are all `provided` — supplied by the server/other plugins at runtime, not bundled.

## Notes & Limitations

- **Without PacketEvents**: overhead nametags and the tablist's reserved-slot filler are silently disabled (everything else still works); `/spectate` refuses to run at all.
- The guild-tag nametag line rides as a passenger entity; vanilla doesn't document the exact height a passenger renders at, so `nametag.guild-tag.y-offset` may need visual tuning.
- The Vault bridge is synchronous by contract, so third-party plugins calling it may block briefly on uncached balance lookups.
- Kill credit falls back from `PlayerDeathEvent#getKiller()` (direct melee only) to the last player who damaged the victim - directly, via a shot projectile, via TNT they lit, via an End Crystal they broke, or via a respawn anchor they triggered - within `stats.indirect-kill-window-seconds`. A bed exploding in the Nether/End is the same kind of block-only explosion as a respawn anchor but isn't attributed yet. A death with no player behind it at all (fall with no recent hit, natural lava, despawning) still only counts as a death, and always resets the victim's own killstreak regardless of cause.
- Player market listings are a straightforward "one listing, one buyer, all-or-nothing" design — there's no partial-quantity purchase of a bulk listing.
- The NuVotifier hook is entirely reflection-based (no compile-time dependency), so it keeps working across NuVotifier versions as long as `com.vexsoftware.votifier.model.VotifierEvent` and its `getVote()`/`getUsername()` methods stay put; a future NuVotifier rewrite that relocates or renames those would silently stop triggering rewards until updated.

---

<p align="center"><sub>Built directly against the <a href="https://papermc.io/software/paper">Paper API</a>'s region/entity scheduler, so it runs unmodified on both Paper and <a href="https://papermc.io/software/folia">Folia</a>. Seek no more, you've found it.</sub></p>
