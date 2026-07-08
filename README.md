<p align="center">
  <img src="assets/banner.svg" alt="CanvasSuite" width="100%">
</p>

<p align="center">
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-38bdf8?style=for-the-badge&logo=openjdk&logoColor=white">
  <img alt="Folia native" src="https://img.shields.io/badge/Folia-native-818cf8?style=for-the-badge">
  <img alt="Minecraft 1.21.8+" src="https://img.shields.io/badge/Minecraft-1.21.8%2B-38bdf8?style=for-the-badge&logo=minecraft&logoColor=white">
  <img alt="Build with Maven" src="https://img.shields.io/badge/build-Maven-818cf8?style=for-the-badge&logo=apachemaven&logoColor=white">
</p>

<p align="center">
An all-in-one SMP plugin for <a href="https://canvasmc.io/">CanvasMC</a> servers — economy, shops, crates, teleportation, random teleport, guilds, chat formatting, world creation, a void spawn world, moderator tools, private messaging, packet-driven nametags/tablist, a sidebar scoreboard, player stats with leaderboard holograms, and world protection, all in a single jar with a chest-GUI front end wherever one makes sense.
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-commands">Commands</a> •
  <a href="#-permissions">Permissions</a> •
  <a href="#-configuration">Configuration</a> •
  <a href="#-installation">Installation</a> •
  <a href="#-building-from-source">Building</a> •
  <a href="#-notes--limitations">Notes & Limitations</a>
</p>

---

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| CanvasMC 1.21.8+ | ✅ | Or another up-to-date Folia-compatible server |
| Java 25 | ✅ | |
| [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) | ✅ | Hard dependency — the plugin will not load without it |
| [PacketEvents](https://www.spigotmc.org/resources/packetevents.80279/) | ➖ | Optional, installed as its own plugin — powers overhead nametags, moderator `/spectate`, and the tablist's reserved-slot filler. See [Notes & Limitations](#-notes--limitations) for what degrades without it |
| [MiniPlaceholders-LuckPerms expansion](https://github.com/MiniPlaceholders/MiniPlaceholders) | ➖ | Optional — install alongside MiniPlaceholders if you want `<luckperms_prefix>`/`<luckperms_suffix>` to resolve in chat, nametags, and the scoreboard |
| [LuckPerms](https://luckperms.net/) | ➖ | Optional, soft-depended directly (not just through MiniPlaceholders) — used to sort the tablist by each player's effective group weight, highest first |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | ➖ | Optional — if present, CanvasSuite registers itself as the Vault economy provider so other plugins can use its currency |
| [FancyHolograms](https://modrinth.com/plugin/fancyholograms) | ➖ | Optional, soft-depended — powers `/statshologram` leaderboard holograms and the name hologram `/crate create` drops above a bound crate. Both degrade gracefully (a clear message / silent skip) if it's not installed |
| MySQL server | ➖ | Optional — falls back to a local SQLite file automatically |

All player-facing text is rendered with [Adventure MiniMessage](https://docs.advntr.dev/minimessage/format.html) and supports [MiniPlaceholders](https://github.com/MiniPlaceholders/MiniPlaceholders) tags (global, audience, and relational placeholders) in every configurable message and chat format.

## ✨ Features

### 💰 Economy
- Vault-compatible currency with configurable symbol/name, starting balance, and a max-balance cap.
- `/balance`, `/pay`, `/baltop` (paginated GUI), and admin-only `/eco give|take|set`.
- Automatically registers as the server's Vault economy provider when Vault is installed, so any other Vault-aware plugin can use CanvasSuite's currency without extra setup.

### 🛒 Shop
- `/buy` opens a category-browser GUI defined entirely in `shop.yml` — categories, display items, and per-item buy/sell prices.
- `/sell` opens an empty inventory to drop items into; it shows a running sell value as you fill it and pays out on confirm.
- A global `shop.sell-price-multiplier` in `config.yml` scales every sell price at once (e.g. `0.75` = sell for 75% of list price).
- Overflow-safe: if your inventory fills mid-purchase, the undeliverable portion is refunded; closing `/sell` with unsold items in it drops any that don't fit back in your inventory.

### 🎁 Crates
- Crate types (key appearance and a weighted reward pool of items/money/console commands) are defined entirely in `crates.yml`.
- `/crate create <type>` binds the block an admin is looking at as a crate of that type; `/crate remove` unbinds it. A crate block otherwise behaves like any other block until it's bound.
- `/crate key <type> <player> [amount]` gives an online player key items — ordinary items tagged with a hidden marker for that crate type, so they can't be duplicated by grabbing an item of the same material from elsewhere.
- Right-clicking a bound crate block with a matching key consumes one key and rolls a weighted-random reward from that crate's pool, then opens a chest GUI with a slot-machine-style reel that spins through the crate's possible rewards before landing on the one already rolled; rewards can optionally broadcast the win to the whole server. The reward is granted the moment the reel stops (or immediately if the player closes the menu early) - the animation is purely cosmetic and never re-rolls.
- If [FancyHolograms](https://modrinth.com/plugin/fancyholograms) is installed, `/crate create` also drops a floating text hologram above the block showing that crate type's display name (removed again by `/crate remove`); skipped silently if FancyHolograms isn't installed.

### 🏠 Teleportation
- **Homes** — `/sethome [name]`, `/home [name]`, `/delhome <name>`. Multiple named homes with per-rank limits via permission nodes (`canvassuite.home.limit.<n>`, or `canvassuite.home.unlimited`). Running `/home` with no arguments opens a GUI of all your homes (click to teleport, shift-click to delete).
- **Warps** — admin-defined server warps (`/setwarp`, `/delwarp`); `/warp` with no arguments opens a warp-browser GUI for everyone.
- **TPA** — `/tpa <player>` and `/tpahere <player>` requests with clickable **[Accept]/[Deny]** buttons in chat, automatic expiry, `/tpaccept`, `/tpdeny`.
- Configurable **teleport warmup** ("teleporting in 3s, don't move…") with cancel-on-move, applied consistently to homes, warps, and TPA.

### 🌐 Spawn & Void World
- A dedicated void world is created automatically on first enable (custom `ChunkGenerator`, no terrain, no bedrock/caves/structures/mobs) with a small starter platform so players never land in the void before an admin configures anything.
- `/setspawn` (admin) sets the server spawn point; `/spawn` teleports there through the same warmup as homes/warps.
- First-time joiners are teleported to spawn automatically.
- Dying with no valid bed/respawn-anchor spawn sends the player back to spawn instead of the vanilla world spawn.
- Inside the void world, non-creative players can't interact with any block except doors, can't trample farmland, and take no damage or food loss — creative-mode players bypass every restriction. Falling out of the world (real void damage, not a fixed Y check) teleports the player straight back to spawn.

### 🌍 World Creation
- `/world create <name> [seed]` opens a GUI to configure a brand-new world before creating it: environment (Overworld/Nether/End), world type (Normal/Flat/Large Biomes/Amplified), generator (vanilla or the same void generator spawn uses), seed (typed up front or rerolled live in the GUI), structure generation, hardcore, difficulty, and PvP — each a one-click toggle/cycle, with a Create/Cancel step.
- `/world list` opens a paginated browser of every CanvasSuite-managed world; clicking one opens a detail view with teleport/delete actions.
- `/world teleport <name>` (alias `tp`) teleports to a world's spawn, going through the same warmup path as `/spawn`.
- `/world delete <name>` unloads the world and stops tracking it but **leaves the folder on disk** (recoverable); only `/world delete <name> wipe` permanently deletes the files — a deliberate extra step so a single misclick can't destroy a world.
- Every managed world's settings persist in `worlds.yml` and are reapplied consistently on every restart, so a void world stays void as new chunks generate.

### 🎲 Random Teleport
- `/rtp [world]` (aliases `/wild`, `/randomtp`) teleports you to a random safe spot within a configurable min/max radius. Running `/rtp` with no arguments opens a GUI listing every RTP-enabled world with its fee (or "Free").
- Random teleport is opt-in per world with configurable cost per use.
- A background precache keeps a small pool of already-verified safe locations per world, topped off only while the server's TPS can handle it, so `/rtp` usually finds a destination instantly instead of searching live.
- Once a destination is found, the same teleport warmup used by homes/warps/TPA applies (`teleport.teleport-warmup-seconds`/`cancel-warmup-on-move` in `config.yml`) before you're actually moved.
- A configurable cooldown applies per player; `canvassuite.rtp.admin` bypasses it.
- Configurable biome avoid-list (oceans, rivers, the void, etc.) so you never land somewhere unusable.

### 🛡️ Guilds
- `/guild create <name> <tag>` (optional creation cost), `/guild disband`, `/guild invite`/`accept`, `/guild kick`, `/guild promote`/`demote` (Member/Officer/Owner roles), `/guild info`, `/guild list`, `/guild leave`.
- `/guild sethome`/`/guild home` for a shared guild teleport point.
- `/guild chat` toggles a guild-only chat channel; the guild tag renders on a genuine second nametag line in-world (see Nametags below).
- `/guild gui` (or just `/guild` with no arguments) opens a full management GUI — members list, roles, invites, home, chat toggle — everything the commands do, click-driven.
- Configurable name/tag length limits, member limit, and creation cost.

### 💬 Chat Formatting
- Every chat message is rendered through MiniMessage with full MiniPlaceholders support (e.g. `<player_name>`, plus relational placeholders between sender and each viewer).
- One format for everyone, configurable in `config.yml`. `<player_name>` isn't reset/re-colored after `<luckperms_prefix>` by default, so a prefix that doesn't close its own color tag (e.g. `<red>[Admin] `) intentionally carries that color onto the name too — add your own `<reset>` before `<player_name>` if you don't want that.
- `<guild_segment>` placeholder built in — renders to nothing for players with no guild, so there's never a stray empty `[]`.
- **Injection-safe**: player-typed message content is inserted as plain text, so players can't smuggle `<click>`, `<hover>`, or color tags into chat — unless they hold `canvassuite.chat.format`, which unlocks MiniMessage styling in their messages. The same rule applies to private messages.
- **Join/leave messages** are MiniMessage/MiniPlaceholders templates too (`join-leave.join`/`join-leave.leave` in `messages.yml`), and can each be turned off independently via `join-leave.join-enabled`/`leave-enabled` in `config.yml` — disabled means no message at all, not just a blank one, replacing vanilla's default line either way.

### 🏷️ Nametags & Tablist
- Overhead nametag prefixes (e.g. from `<luckperms_prefix>`) are sent as per-player scoreboard **team packets only** — no real Bukkit `Scoreboard`/`Team` is ever registered. Vanilla's team system only lets the player's own name be colored via a single legacy `NamedTextColor` field (never full Component styling), so that field is derived from whatever color is still "active" at the end of the rendered prefix, rather than hardcoded — a prefix that doesn't close its own color tag carries that color onto the name here too, same as the tablist and chat format.
- Guild members get their guild tag on a genuine **second line** above their name — vanilla team prefixes/suffixes can only add text to the *same* line as a player's name, so this is rendered via an invisible `TEXT_DISPLAY` entity mounted as a passenger on the player, configurable via `nametag.guild-tag.format`/`y-offset`.
- The tablist stays **full-size at all times**: unused slots are filled with blank filler entries instead of the grid shrinking to fit the online player count, each showing a gray placeholder skin (`tablist.reserved-slot-skin` in `config.yml` — a Mojang-signed texture/signature pair from [mineskin.org](https://mineskin.org), swappable for any other skin the same way).
- Real players (sorted by LuckPerms group weight, highest first, then alphabetically within the same weight — weight is `0` for everyone if LuckPerms isn't installed, which just falls back to alphabetical) always occupy the grid starting from the top-left slot, reading left-to-right then wrapping to the next row, with filler entries pushed to fill whatever's left. Vanilla's own real-player tab entries don't reliably respond to a plugin's after-the-fact reordering, so this works by hiding them entirely and mirroring every online player's name/skin/ping/gamemode onto the plugin's own pre-allocated synthetic entries instead, the same technique the TAB plugin's "Layout" feature uses.
- With more real players online than the grid has room for (more than 80, at the default `tablist.rows: 20`), the last slot becomes a `+N more players` summary (`tablist.overflow` in `messages.yml`) instead of individually mirroring one more player and leaving everyone past that shown natively and unbounded.
- Each mirror's displayed name is rendered from `tablist.name-format` (`messages.yml`, defaults to `<luckperms_prefix><player_name>`) as a single MiniMessage parse rather than gluing a prefix Component and a plain name Component together, so a prefix that doesn't explicitly close its color/formatting tag carries through onto the name too instead of the name rendering as a separate, unstyled sibling.
- Header/footer are configurable MiniMessage templates.
- All of the above requires the PacketEvents plugin; without it, nametags/reserved-slot filling are silently disabled (tablist header/footer still work — they use native Paper API, not packets).
- Prefixes/suffixes resolve purely through the MiniPlaceholders-LuckPerms expansion, so permission-group changes there are picked up on a periodic refresh, not instantly. The tablist's weight-based sorting is the one place CanvasSuite talks to the LuckPerms API directly (soft-depended, via `LuckPermsProvider`) — that's a live lookup on every join/quit, not cached.

### 📈 Player Stats
- Tracks kills, deaths, current & best killstreak, and playtime per player, persisted to the same storage backend as everything else.
- `/stats [player]` — kills, deaths, K/D ratio, current & best killstreak, and playtime for yourself or another player (online or offline).
- `/statstop <kills|deaths|killstreak|playtime>` opens a paginated leaderboard GUI for any of the four tracked stats.
- Reaching a configurable killstreak milestone (`stats.killstreak-broadcast-milestones` in `config.yml`, default `5, 10, 25, 50, 100`) triggers a server-wide broadcast.
- Playtime accrues live per session and is flushed to storage periodically (`stats.playtime-autosave-interval-seconds`) plus on quit and server shutdown, so a crash loses at most one autosave interval.
- Exposed as MiniPlaceholders tags usable anywhere MiniMessage is rendered — chat, tablist, nametags, the scoreboard below: `<stats_kills>`, `<stats_deaths>`, `<stats_kd>`, `<stats_killstreak>`, `<stats_best_killstreak>`, `<stats_playtime>`.

### 🏆 Leaderboard Holograms
- Optional integration with [FancyHolograms](https://modrinth.com/plugin/fancyholograms): `/statshologram create <kills|deaths|killstreak|playtime> <name> [limit]` drops a text hologram at your current location that's periodically repopulated with the live leaderboard for that stat (`limit` caps how many entries show, default 10, max 45).
- `/statshologram remove <name>` and `/statshologram list` manage existing leaderboard holograms.
- FancyHolograms owns the hologram entity itself — its location and persistence across restarts are handled entirely by FancyHolograms' own storage; CanvasSuite only remembers which stat each hologram displays and refreshes its text on an interval (`hologram.refresh-interval-seconds`, default 30s).
- Fully optional and soft-depended: if FancyHolograms isn't installed, `/statshologram` replies with a clear "not installed" message instead of erroring, and the rest of the plugin is unaffected.

### 📊 Scoreboard
- A fully MiniMessage/MiniPlaceholders-driven sidebar scoreboard, configurable title and lines, refreshed on an interval and rendered **relationally per viewer** (so relational placeholders resolve correctly for each viewer looking at each player).
- Toggleable entirely via `scoreboard.enabled`.
- In addition to MiniPlaceholders tags (`<player_name>`, `<luckperms_prefix>`, ...), three of CanvasSuite's own modules expose live per-viewer tags usable in any scoreboard line (or anywhere else MiniMessage is rendered): stats' `<stats_kills>`/`<stats_deaths>`/`<stats_kd>`/`<stats_killstreak>`/`<stats_best_killstreak>`/`<stats_playtime>` (see [Player Stats](#-player-stats) above), economy's `<economy_balance>`, and guilds' `<guild_own_name>`/`<guild_own_tag>` (render to a configurable `guild.no-guild-placeholder` string, default `"No Guild"`, for players not in a guild). These are deliberately separate from the `<guild_name>`/`<guild_tag>` placeholders used elsewhere for a *queried* guild (e.g. `/guild info <name>`), which refer to the guild being looked up rather than the viewer's own.

### 🕵️ Moderator Spectate
- `/spectate <player>` toggles a packet-driven vanish + spectator view of the target for moderators — the target never sees the moderator, and the moderator can freely fly through blocks to observe.
- Requires PacketEvents; refuses to run at all without it, rather than offering a false sense of invisibility.

### ✉️ Private Messaging
- `/msg`/`/tell`/`/w`/`/pm`, `/reply`, `/ignore`, and `/socialspy` (mirrors everyone's DMs to whoever has it enabled).
- Configurable cooldown between messages from the same sender.

### 🌍 World Protection
- Optional toggles to block new stronghold generation and new nether portal creation, independent of each other.

### ✨ Feedback & Effects
- Every command reply is automatically paired with a sound: messages.yml's own `<green>`/`<red>` color convention (success vs. denial/error) picks the sound, so this works for every command without per-command wiring.
- Teleporting (`/home`, `/warp`, `/tpa`, `/rtp`, `/world teleport`, `/spawn`) plays a particle/sound burst at both the origin and destination.
- Every chest-GUI menu (shop, sell, homes, warps, guild, world list, baltop, stats leaderboards, RTP world select, ...) plays a soft click on open.
- Joining plays a short chime and particle burst for the joining player.
- Each of the four categories above (and cosmetic effects as a whole) can be toggled independently under `effects` in `config.yml` - purely cosmetic, never affects whether an action actually succeeds.

## 🕹️ Commands

| Command | Description | Permission |
|---|---|---|
| `/balance [player]` (`/bal`, `/money`) | Check a balance | `canvassuite.economy.use` |
| `/pay <player> <amount>` | Send money | `canvassuite.economy.use` |
| `/baltop` (`/bt`) | Rich-list GUI | `canvassuite.economy.use` |
| `/eco <give\|take\|set> <player> <amount>` | Admin economy control | `canvassuite.economy.admin` |
| `/buy` | Open the server shop GUI (buy) | `canvassuite.shop.use` |
| `/sell` | Open the sell GUI | `canvassuite.shop.sell` |
| `/crate <create\|remove\|key> ...` | Bind/unbind crate blocks, give keys (admin) | `canvassuite.crate.admin` |
| `/sethome [name]` / `/home [name]` (`/homes`) / `/delhome <name>` | Manage & use homes | `canvassuite.home.use` |
| `/setwarp <name>` / `/delwarp <name>` | Manage warps | `canvassuite.warp.admin` |
| `/warp [name]` (`/warps`) | Warp browser / teleport | `canvassuite.warp.use` |
| `/tpa <player>` / `/tpahere <player>` | Teleport requests | `canvassuite.tpa.use` |
| `/tpaccept` / `/tpdeny` | Answer a request | `canvassuite.tpa.use` |
| `/rtp [world]` (`/wild`, `/wilderness`, `/randomtp`) | Random teleport, or open the world-select GUI | `canvassuite.rtp.use` |
| `/guild <sub>` (`/g`, `/clan`, `/team`, `/faction`) | Guild management (`create`, `disband`, `invite`, `accept`, `kick`, `promote`, `demote`, `sethome`, `home`, `info`, `list`, `chat`, `leave`, `gui`) | `canvassuite.guild.use` |
| `/setspawn` | Set the server spawn point | `canvassuite.spawn.admin` |
| `/spawn` | Teleport to spawn | `canvassuite.spawn.use` |
| `/world <create\|list\|delete\|teleport> ...` (`/worlds`) | Create and manage worlds | `canvassuite.world.admin` |
| `/spectate [player]` | Toggle moderator spectate/vanish | `canvassuite.moderation.spectate` |
| `/msg <player> <message>` (`/tell`, `/w`, `/pm`) | Send a private message | `canvassuite.msg.use` |
| `/reply <message>` (`/r`) | Reply to your last DM | `canvassuite.msg.use` |
| `/ignore <player>` | Toggle ignoring a player's DMs | `canvassuite.msg.use` |
| `/socialspy` | Toggle mirroring all DMs to yourself | `canvassuite.msg.socialspy` |
| `/stats [player]` | View kill/death/killstreak/playtime stats | `canvassuite.stats.use` |
| `/statstop <kills\|deaths\|killstreak\|playtime>` | Leaderboard GUI for a stat | `canvassuite.stats.use` |
| `/statshologram <create\|remove\|list> ...` | Manage leaderboard holograms (requires FancyHolograms) | `canvassuite.stats.hologram.admin` |

Every command's *own* name always works no matter what's configured — the aliases above are just the shipped defaults, and both they and each command's **subcommand** aliases (e.g. `/world tp` for `/world teleport`) are fully editable at runtime via `aliases.yml`/`subcommand-aliases.yml` — see [Configuration](#-configuration).

## 🔐 Permissions

<details>
<summary>Full permission node table</summary>

| Node | Default | Purpose |
|---|---|---|
| `canvassuite.admin` | op | Grants every admin node below |
| `canvassuite.economy.use` / `.admin` | true / op | Economy commands / `/eco` |
| `canvassuite.shop.use` | true | Shop access (buy) |
| `canvassuite.shop.sell` | true | Sell menu access |
| `canvassuite.crate.use` | true | Open a bound crate with a matching key |
| `canvassuite.crate.admin` | op | `/crate` (create/remove/key) |
| `canvassuite.home.use` | true | Homes |
| `canvassuite.home.limit.<n>` | — | Raises that player's home limit to `n` (default limit set in config) |
| `canvassuite.home.unlimited` | false | No home limit |
| `canvassuite.warp.use` / `.admin` | true / op | Warp use / management |
| `canvassuite.tpa.use` | true | TPA |
| `canvassuite.rtp.use` / `.admin` | true / op | RTP / cooldown bypass |
| `canvassuite.guild.use` / `.admin` | true / op | Guilds |
| `canvassuite.chat.format` | false | Allows MiniMessage styling in chat messages and DMs |
| `canvassuite.spawn.use` / `.admin` | true / op | `/spawn` / `/setspawn` |
| `canvassuite.world.admin` | op | `/world` (create/list/delete/teleport) |
| `canvassuite.moderation.spectate` | op | `/spectate` |
| `canvassuite.msg.use` | true | Private messaging (`/msg`, `/reply`, `/ignore`) |
| `canvassuite.msg.socialspy` | op | `/socialspy` |
| `canvassuite.stats.use` | true | `/stats`, `/statstop` |
| `canvassuite.stats.hologram.admin` | op | `/statshologram` (create/remove/list) |

</details>

## ⚙️ Configuration

Five files are created in `plugins/CanvasSuite/` on first start, plus `worlds.yml` once you first use `/world create`:

- **`config.yml`** — storage backend (MySQL credentials with automatic SQLite fallback), economy settings, teleport warmup/TPA timeouts, home limits, RTP radius/cooldown/per-world enable+fee/precache, guild rules/costs/no-guild placeholder string, chat format, join/leave message toggles, protection toggles, spawn/void-world settings, nametag/tablist/scoreboard settings, private-message cooldown, killstreak broadcast milestones/playtime autosave interval, leaderboard hologram refresh interval, and cosmetic sound/particle effect toggles.
- **`messages.yml`** — every message the plugin sends, in MiniMessage. Change colors, add gradients, hover/click events, or MiniPlaceholders tags freely. The shared `<prefix>` is defined once at the top.
- **`shop.yml`** — shop categories and per-item `buy-price` / `sell-price` values.
- **`crates.yml`** — crate types: key appearance and a weighted reward pool of items/money/console commands per type.
- **`aliases.yml`** — extra aliases for every command (e.g. `/bal` for `/balance`), applied at enable by registering the same command object under each configured alias in Bukkit's command map. Edit freely; a command's own name always works regardless of what's listed here. Changes take effect on the next restart.
- **`subcommand-aliases.yml`** — extra aliases for individual subcommands of `/world`, `/guild`, and `/eco` (e.g. `/world tp` for `/world teleport`). Unlike `aliases.yml` these aren't real Bukkit commands, just extra labels checked in code; a subcommand's own name always works regardless of what's listed here. Changes take effect on the next restart.
- **`worlds.yml`** — one entry per world created via `/world create`, storing its generator settings so they're reapplied identically on every restart. Managed entirely by the `/world` command — hand-editing isn't necessary. Not part of the auto-migration below since it has no bundled default to compare against.

On every startup, each of the five files above (everything except `worlds.yml`) is checked against the version bundled in the plugin jar, and any option that's missing on disk (typically after upgrading to a version that added one) is added with its default value — existing settings are never touched. Since re-saving a YAML file with Bukkit's config API drops hand-written comments, this only re-saves a file when something was actually missing, and always leaves a `<file>.bak` copy of the pre-update version alongside it first.

<details>
<summary>Example chat format from <code>config.yml</code></summary>

```yaml
chat:
  format: "<guild_segment><luckperms_prefix><player_name><dark_gray>:</dark_gray> <gray><message>"
```

`<player_name>` isn't reset/re-colored after `<luckperms_prefix>` by default, so a prefix that doesn't close its own color tag (e.g. `<red>[Admin] `) carries that color onto the name too — add your own `<reset>` before `<player_name>` if you don't want that.

</details>

<details>
<summary>Example per-world RTP config from <code>config.yml</code></summary>

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
<summary>Example guild-tag nametag config from <code>config.yml</code></summary>

```yaml
nametag:
  guild-tag:
    format: "<gray>[<tag>]</gray>"
    y-offset: 0.55
```

</details>

## 📦 Installation

1. Build the plugin (see [Building from Source](#-building-from-source)) or grab a release jar, and drop it into `plugins/`.
2. Install [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) (required); optionally install PacketEvents (nametags/spectate/reserved-slot tablist), LuckPerms (tablist weight sorting) with the MiniPlaceholders-LuckPerms expansion (prefix/suffix placeholders), Vault, and FancyHolograms (leaderboard holograms).
3. Start the server once to generate the default config files under `plugins/CanvasSuite/`, then edit `config.yml`/`messages.yml`/`shop.yml` to taste.
4. Set `storage.type` to `MYSQL` in `config.yml` if you want a shared database instead of the default local SQLite file — see [Storage](#storage).

### Storage

Set `storage.type` to `MYSQL` or `SQLITE` in `config.yml`. With MySQL selected, connections are pooled through HikariCP; if the database is unreachable at startup, the plugin logs a warning and **falls back to a local SQLite file** (`plugins/CanvasSuite/data.db`) so the server still boots. The schema (accounts, homes, warps, guilds, guild members, ignored players, player stats, leaderboard hologram registry) is created automatically on either backend.

## 🛠️ Building from Source

Requires JDK 25 and Maven. The shaded jar lands at `target/CanvasSuite-<version>.jar`.

```bash
mvn clean package
```

`HikariCP`, the MySQL driver, and the SQLite driver are shaded and relocated into the jar (`gg.nurmi.libs.*`) so they never collide with another plugin's copies on the same server. `canvas-api`, MiniPlaceholders, VaultAPI, PacketEvents, the LuckPerms API, and FancyHolograms are all `provided` — supplied by the server/other plugins at runtime, not bundled.

## 📝 Notes & Limitations

- **Without PacketEvents installed**: overhead nametags and the tablist's reserved-slot filler are silently disabled (everything else, including tablist header/footer, still works); `/spectate` refuses to run at all rather than offer a moderator a false sense of invisibility.
- The guild-tag second nametag line rides as a passenger entity on the player; vanilla doesn't document the exact height a passenger-mounted entity renders at, so `nametag.guild-tag.y-offset` may need visual tuning in-game.
- Random teleport is opt-in per world with configurable cost per use.
- Stronghold blocking only prevents **new** generation — strongholds in already-generated chunks remain, and the datapack needs one world reload/server restart after first install.
- Nether portal blocking prevents new portal **creation**; portals that existed before the plugin was installed remain usable.
- The Vault bridge is synchronous by contract (Vault's API returns plain doubles), so third-party plugins calling it from a region thread may block briefly on uncached (offline-player) balance lookups.
- Kills are credited via Bukkit's `PlayerDeathEvent#getKiller()`, which is only populated for direct player-vs-player damage — environmental deaths (fall, lava, void, etc.) always count as a death but never as anyone's kill, and any death resets the victim's killstreak regardless of cause.
- `/statshologram` and the MiniPlaceholders expansions (stats/economy/guild) were verified with a standalone SQL smoke test against a real SQLite engine and a clean `mvn package`, but not against a live FancyHolograms installation or in-game — no CanvasMC server was available in the environment these were built in.

---

<p align="center"><sub>Built for <a href="https://canvasmc.io/">CanvasMC</a> — a Folia fork. No single "main thread": every feature here is written against Folia's region/entity schedulers.</sub></p>
