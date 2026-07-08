# CanvasSuite

An all-in-one SMP plugin for [CanvasMC](https://canvasmc.io/) servers, bundling economy, shops, teleportation, random teleport, guilds, chat formatting, world creation, a void spawn world, moderator tools, private messaging, packet-driven nametags/tablist, a sidebar scoreboard, and world protection into a single jar — with a chest-GUI front end for every module that benefits from one.

CanvasMC is a fork of **Folia** (regionized multithreading — there is no main thread), and CanvasSuite is built natively for that model: every operation runs on the correct region/entity/async/global scheduler, and all teleports use `teleportAsync`. It is **not** intended for regular Paper/Spigot servers.

All player-facing text is rendered with [Adventure MiniMessage](https://docs.advntr.dev/minimessage/format.html) and supports [MiniPlaceholders](https://github.com/MiniPlaceholders/MiniPlaceholders) tags (global, audience, and relational placeholders) in every configurable message and chat format.

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| CanvasMC 1.21.8+ | ✅ | Or another up-to-date Folia-compatible server |
| Java 25 | ✅ | |
| [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) | ✅ | Hard dependency — the plugin will not load without it |
| [PacketEvents](https://www.spigotmc.org/resources/packetevents.80279/) | ➖ | Optional, installed as its own plugin — powers overhead nametags, moderator `/spectate`, and the tablist's reserved-slot filler. See [Notes & Limitations](#notes--limitations) for what degrades without it |
| [MiniPlaceholders-LuckPerms expansion](https://github.com/MiniPlaceholders/MiniPlaceholders) | ➖ | Optional — install alongside MiniPlaceholders if you want `<luckperms_prefix>`/`<luckperms_suffix>` to resolve in chat, nametags, and the scoreboard |
| [LuckPerms](https://luckperms.net/) | ➖ | Optional, soft-depended directly (not just through MiniPlaceholders) — used to sort the tablist by each player's effective group weight, highest first |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | ➖ | Optional — if present, CanvasSuite registers itself as the Vault economy provider so other plugins can use its currency |
| MySQL server | ➖ | Optional — falls back to a local SQLite file automatically |

## Features

### 💰 Economy
- Per-player balances stored in MySQL or SQLite, cached in memory for online players and persisted asynchronously.
- Configurable starting balance, maximum balance, and currency symbol/names.
- **Vault integration**: other plugins (job plugins, third-party shops, etc.) transparently use CanvasSuite's currency.
- `/baltop` opens a player-head leaderboard GUI of the richest players.

### 🛒 Shop
- Buy and sell items at **fixed, preconfigured prices** defined in `shop.yml` — no dynamic pricing surprises.
- Category-based chest GUI: pick a category (Blocks, Ores, Food, Farming, Combat, Misc out of the box), then browse a paginated item menu.
- Left-click to buy one, **shift-click to buy a stack**; right-click to sell one, **shift-right-click to sell everything** of that item in your inventory.
- Items can be buy-only or sell-only (set the other price to `-1`).
- Global `sell-price-multiplier` lets you tune the sell economy without editing every item.
- Overflow-safe: if your inventory fills mid-purchase, the undeliverable portion is refunded.

### 🏠 Teleportation
- **Homes** — `/sethome [name]`, `/home [name]`, `/delhome <name>`. Multiple named homes with per-rank limits via permission nodes (`canvassuite.home.limit.<n>`, or `canvassuite.home.unlimited`). Running `/home` with no arguments opens a GUI of all your homes (click to teleport, shift-click to delete).
- **Warps** — admin-defined server warps (`/setwarp`, `/delwarp`); `/warp` with no arguments opens a warp-browser GUI for everyone.
- **TPA** — `/tpa <player>` and `/tpahere <player>` requests with clickable **[Accept]/[Deny]** buttons in chat, automatic expiry, `/tpaccept`, `/tpdeny`.
- Configurable **teleport warmup** ("teleporting in 3s, don't move…") with cancel-on-move, applied consistently to homes, warps, and TPA.

### 🌐 Spawn & Void World
- A dedicated void world is created automatically on first enable (custom `ChunkGenerator`, no terrain, no bedrock/caves/structures/mobs) with a small starter platform so players never land in the void before an admin configures anything.
- `/setspawn` (admin) sets the server spawn point; `/spawn` teleports there instantly (no warmup, unlike homes/warps).
- First-time joiners are teleported to spawn automatically.
- Dying with no valid bed/respawn-anchor spawn sends the player back to spawn instead of the vanilla world spawn.
- Inside the void world, non-creative players can't interact with any block except doors, can't trample farmland, and take no damage or food loss — creative-mode players bypass every restriction. Falling out of the world (real void damage, not a fixed Y check) teleports the player straight back to spawn.

### 🌍 World Creation
- `/world create <name> [seed]` opens a GUI to configure a brand-new world before creating it: environment (Overworld/Nether/End), world type (Normal/Flat/Large Biomes/Amplified), generator (vanilla or the same void generator spawn uses), seed (typed up front or rerolled live in the GUI), structure generation, hardcore, difficulty, and PvP — each a one-click toggle/cycle, with a Create/Cancel step.
- `/world list` opens a paginated browser of every CanvasSuite-managed world; clicking one opens a detail view with teleport/delete actions.
- `/world tp <name>` teleports to a world's spawn (goes through the normal warmup path, unlike `/spawn`).
- `/world delete <name>` unloads the world and stops tracking it but **leaves the folder on disk** (recoverable); only `/world delete <name> wipe` permanently deletes the files — a deliberate extra step so a single misclick can't destroy a world.
- Every managed world's settings persist in `worlds.yml` and are reapplied consistently on every restart, so a void world stays void as new chunks generate.

### 🎲 Random Teleport
- `/rtp [world]` (aliases `/wild`, `/randomtp`) teleports you to a random safe spot within a configurable min/max radius. Running `/rtp` with no arguments opens a GUI listing every RTP-enabled world with its fee (or "Free").
- Random teleport is **opt-in per world** (`rtp.worlds.<name>.enabled`) — a world isn't offered anywhere until an admin turns it on — and each world can charge its **own fee** (`rtp.worlds.<name>.cost`), not just one global price.
- Safe-location search runs fully off-thread (async chunk loads + region-thread block checks) — no server stalls, Folia-safe. Avoids configured biomes (oceans and rivers by default) and hazardous landings (lava, cacti, water, magma).
- **Precache**: up to 15 already-found safe locations are kept ready per enabled world, topped off one at a time in the background — but only while the server's recent TPS is healthy, so `/rtp` usually teleports instantly instead of waiting on a fresh search, without adding load during a lag spike.
- Per-use cooldown; `canvassuite.rtp.admin` bypasses it.

### 🛡️ Guilds
- Create guilds with a name and chat tag: `/guild create <name> <tag>` (optional creation cost).
- Three roles — **Owner**, **Officer**, **Member** — gating invites, kicks, promotions, guild home, and disband.
- Invites with clickable accept, member limits, guild home teleport, and a DB-backed **guild bank**.
- **Guild chat**: `/guild chat` toggles your chat to guild-only messages.
- Management GUIs: `/guild` (or `/guild gui`) opens the main panel — info, member list (click to kick, shift-click to promote/demote), guild home, set-home, chat toggle, leave/disband.
- Guild tags appear in public chat (`<guild_segment>`, omitted entirely for guildless players) and as a second floating line above a guild member's overhead nametag — see Nametags below.

### 💬 Chat Formatting
- Every chat message is rendered through MiniMessage with full MiniPlaceholders support (e.g. `<player_name>`, plus relational placeholders between sender and each viewer).
- One format for everyone, configurable in `config.yml`.
- `<guild_segment>` placeholder built in — renders to nothing for players with no guild, so there's never a stray empty `[]`.
- **Injection-safe**: player-typed message content is inserted as plain text, so players can't smuggle `<click>`, `<hover>`, or color tags into chat — unless they hold `canvassuite.chat.format`, which unlocks MiniMessage styling in their messages. The same rule applies to private messages.
- **Join/leave messages** are MiniMessage/MiniPlaceholders templates too (`join-leave.join`/`join-leave.leave` in `messages.yml`), and can each be turned off independently via `join-leave.join-enabled`/`leave-enabled` in `config.yml` — disabled means no message at all, not just a blank one, replacing vanilla's default line either way.

### 🏷️ Nametags & Tablist
- Overhead nametag prefixes (e.g. from `<luckperms_prefix>`) are sent as per-player scoreboard **team packets only** — no real Bukkit `Scoreboard`/`Team` is ever registered. Vanilla's team system only lets the player's own name be colored via a single legacy `NamedTextColor` field (never full Component styling), so that field is derived from whatever color is still "active" at the end of the rendered prefix, rather than hardcoded — a prefix that doesn't close its own color tag carries that color onto the name here too, same as the tablist and chat format.
- Guild members get their guild tag on a genuine **second line** above their name — vanilla team prefixes/suffixes can only add text to the *same* line as a player's name, so this is rendered via an invisible `TEXT_DISPLAY` entity mounted as a passenger on the player, configurable via `nametag.guild-tag.format`/`y-offset`.
- The tablist stays **full-size at all times**: unused slots are filled with blank filler entries instead of the grid shrinking to fit the online player count, each showing a gray placeholder skin (`tablist.reserved-slot-skin` in `config.yml` — a Mojang-signed texture/signature pair from [mineskin.org](https://mineskin.org), swappable for any other skin the same way). Real players (sorted by LuckPerms group weight, highest first, then alphabetically within the same weight — weight is `0` for everyone if LuckPerms isn't installed, which just falls back to alphabetical) always occupy the grid starting from the top-left slot, reading left-to-right then wrapping to the next row, with filler entries pushed to fill whatever's left. Vanilla's own real-player tab entries don't reliably respond to a plugin's after-the-fact reordering, so this works by hiding them entirely and mirroring every online player's name/skin/ping/gamemode onto the plugin's own pre-allocated synthetic entries instead, the same technique the TAB plugin's "Layout" feature uses. Each mirror's displayed name is rendered from `tablist.name-format` (`messages.yml`, defaults to `<luckperms_prefix><player_name>`) as a single MiniMessage parse rather than gluing a prefix Component and a plain name Component together, so a prefix that doesn't explicitly close its color/formatting tag carries through onto the name too instead of the name rendering as a separate, unstyled sibling. Header/footer are configurable MiniMessage templates.
- All of the above requires the PacketEvents plugin; without it, nametags/reserved-slot filling are silently disabled (tablist header/footer still work — they use native Paper API, not packets).
- Prefixes/suffixes resolve purely through the MiniPlaceholders-LuckPerms expansion, so permission-group changes there are picked up on a periodic refresh, not instantly. The tablist's weight-based sorting is the one place CanvasSuite talks to the LuckPerms API directly (soft-depended, via `LuckPermsProvider`) — that's a live lookup on every join/quit, not cached.

### 📊 Scoreboard
- A real, private per-player sidebar scoreboard — configurable title and any number of lines in `config.yml`, each a MiniMessage template rendered relationally per viewer.
- Refreshes periodically without the flicker a naive rebuild-every-line implementation would cause: unchanged lines are never re-sent, and only the actual diff (added/removed/changed lines) touches the underlying scoreboard teams.

### 🕵️ Moderator Spectate
- `/spectate <player>` puts a moderator into spectator mode, teleports them to the target, and makes them **fully invisible** by intercepting the packets that would reveal them to other players — not the usual `hidePlayer`/`showPlayer` API.
- Running `/spectate` again exits: gamemode resets to Survival and the moderator is teleported back to spawn.
- Requires PacketEvents; the command refuses outright if it isn't installed rather than silently giving a moderator a false sense of being invisible.

### ✉️ Private Messaging
- `/msg <player> <message>` (aliases `/tell`, `/w`, `/pm`) and `/reply` (`/r`) for direct messages, with the same MiniMessage injection-safety rule as public chat.
- `/ignore <player>` blocks DMs from a specific player (persists across restarts).
- `/socialspy` (staff) mirrors every private message sent on the server to anyone with it enabled.

### 🌍 World Protection
- **Nether portal creation is blocked everywhere** — both lighting a frame with flint & steel and the auto-generated exit portal on the other side. Players get a configurable message; existing portals keep working for travel.
- **Stronghold generation is disabled** via a per-world datapack the plugin installs automatically (`canvassuite_no_stronghold`). The Bukkit API has no cancellable structure-generation event, so a datapack override of the stronghold structure set is the only reliable mechanism. It takes effect on the world's next load — restart the server after first install. Already-generated strongholds in visited chunks are unaffected.
- Both protections can be toggled in `config.yml`.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/balance [player]` (`/bal`, `/money`) | Check a balance | `canvassuite.economy.use` |
| `/pay <player> <amount>` | Send money | `canvassuite.economy.use` |
| `/baltop` | Rich-list GUI | `canvassuite.economy.use` |
| `/eco <give\|take\|set> <player> <amount>` | Admin economy control | `canvassuite.economy.admin` |
| `/shop` | Open the server shop GUI | `canvassuite.shop.use` |
| `/sethome [name]` / `/home [name]` / `/delhome <name>` | Manage & use homes | `canvassuite.home.use` |
| `/setwarp <name>` / `/delwarp <name>` | Manage warps | `canvassuite.warp.admin` |
| `/warp [name]` (`/warps`) | Warp browser / teleport | `canvassuite.warp.use` |
| `/tpa <player>` / `/tpahere <player>` | Teleport requests | `canvassuite.tpa.use` |
| `/tpaccept` / `/tpdeny` | Answer a request | `canvassuite.tpa.use` |
| `/rtp [world]` (`/wild`, `/randomtp`) | Random teleport, or open the world-select GUI | `canvassuite.rtp.use` |
| `/guild <sub>` (`/g`, `/clan`) | Guild management (`create`, `disband`, `invite`, `accept`, `kick`, `promote`, `demote`, `sethome`, `home`, `info`, `list`, `chat`, `leave`, `gui`) | `canvassuite.guild.use` |
| `/setspawn` | Set the server spawn point | `canvassuite.spawn.admin` |
| `/spawn` | Teleport to spawn | `canvassuite.spawn.use` |
| `/world <create\|list\|delete\|tp> ...` (`/worlds`) | Create and manage worlds | `canvassuite.world.admin` |
| `/spectate [player]` | Toggle moderator spectate/vanish | `canvassuite.moderation.spectate` |
| `/msg <player> <message>` (`/tell`, `/w`, `/pm`) | Send a private message | `canvassuite.msg.use` |
| `/reply <message>` (`/r`) | Reply to your last DM | `canvassuite.msg.use` |
| `/ignore <player>` | Toggle ignoring a player's DMs | `canvassuite.msg.use` |
| `/socialspy` | Toggle mirroring all DMs to yourself | `canvassuite.msg.socialspy` |

## Permissions

| Node | Default | Purpose |
|---|---|---|
| `canvassuite.admin` | op | Grants every admin node below |
| `canvassuite.economy.use` / `.admin` | true / op | Economy commands / `/eco` |
| `canvassuite.shop.use` | true | Shop access |
| `canvassuite.home.use` | true | Homes |
| `canvassuite.home.limit.<n>` | — | Raises that player's home limit to `n` (default limit set in config) |
| `canvassuite.home.unlimited` | false | No home limit |
| `canvassuite.warp.use` / `.admin` | true / op | Warp use / management |
| `canvassuite.tpa.use` | true | TPA |
| `canvassuite.rtp.use` / `.admin` | true / op | RTP / cooldown bypass |
| `canvassuite.guild.use` / `.admin` | true / op | Guilds |
| `canvassuite.chat.format` | false | Allows MiniMessage styling in chat messages and DMs |
| `canvassuite.spawn.use` / `.admin` | true / op | `/spawn` / `/setspawn` |
| `canvassuite.world.admin` | op | `/world` (create/list/delete/tp) |
| `canvassuite.moderation.spectate` | op | `/spectate` |
| `canvassuite.msg.use` | true | `/msg`, `/reply`, `/ignore` |
| `canvassuite.msg.socialspy` | op | `/socialspy` |

## Configuration

Six files are created in `plugins/CanvasSuite/` on first start (plus `worlds.yml`, written the first time `/world create` is used):

- **`config.yml`** — storage backend (MySQL credentials with automatic SQLite fallback), economy settings, teleport warmup/TPA timeouts, home limits, RTP radius/cooldown/per-world enable+fee/precache, guild rules and costs, chat format, join/leave message toggles, protection toggles, spawn/void-world settings, nametag/tablist/scoreboard settings, and private-message cooldown.
- **`messages.yml`** — every message the plugin sends, in MiniMessage. Change colors, add gradients, hover/click events, or MiniPlaceholders tags freely. The shared `<prefix>` is defined once at the top.
- **`shop.yml`** — shop categories and per-item `buy-price` / `sell-price` values.
- **`worlds.yml`** — one entry per world created via `/world create`, storing its generator settings so they're reapplied identically on every restart. Managed entirely by the `/world` command — hand-editing isn't necessary.
- **`aliases.yml`** — extra aliases for every command (e.g. `/bal` for `/balance`), applied at enable by registering the same command object under each configured alias in Bukkit's command map. Edit freely; a command's own name always works regardless of what's listed here. Changes take effect on the next restart.
- **`subcommand-aliases.yml`** — extra aliases for individual subcommands of `/world`, `/guild`, and `/eco` (e.g. `/world tp` for `/world teleport`). Unlike `aliases.yml` these aren't real Bukkit commands, just extra labels checked in code; a subcommand's own name always works regardless of what's listed here. Changes take effect on the next restart.

On every startup, each of the five files above is checked against the version bundled in the plugin jar and any option that's missing on disk (typically after upgrading to a version that added one) is added with its default value — existing settings are never touched. Since re-saving a YAML file with Bukkit's config API drops hand-written comments, this only re-saves a file when something was actually missing, and always leaves a `<file>.bak` copy of the pre-update version alongside it first.

Example chat format from `config.yml`:

```yaml
chat:
  format: "<guild_segment><luckperms_prefix><player_name><dark_gray>:</dark_gray> <gray><message>"
```

`<player_name>` isn't reset/re-colored after `<luckperms_prefix>` by default, so a prefix that doesn't close its own color tag (e.g. `<red>[Admin] `) carries that color onto the name too — add your own `<reset>` before `<player_name>` if you don't want that.

Example per-world RTP config from `config.yml`:

```yaml
rtp:
  worlds:
    world:
      enabled: true
      cost: 0.0
    resource_world:
      enabled: true
      cost: 250.0
```

Example shop entry from `shop.yml`:

```yaml
categories:
  ores:
    display-name: "<aqua>Ores & Ingots"
    icon: IRON_INGOT
    slot: 11
    items:
      DIAMOND: { buy-price: 120.0, sell-price: 60.0 }
      GOLDEN_APPLE: { buy-price: 200.0, sell-price: -1 }   # buy-only
```

## Storage

Set `storage.type` to `MYSQL` or `SQLITE` in `config.yml`. With MySQL selected, connections are pooled through HikariCP; if the database is unreachable at startup, the plugin logs a warning and **falls back to a local SQLite file** (`plugins/CanvasSuite/data.db`) so the server still boots. The schema (accounts, homes, warps, guilds, guild members, ignored players) is created automatically on either backend.

## Building from Source

```bash
mvn -DskipTests package
```

Requires JDK 25 and Maven. The shaded jar lands at `target/CanvasSuite-<version>.jar` with HikariCP and the MySQL/SQLite drivers relocated to `gg.nurmi.libs.*` to avoid conflicts with other plugins. PacketEvents is compiled against but never shaded in — it must be installed as its own plugin on the server.

## Installation

1. Drop `CanvasSuite-<version>.jar` into your server's `plugins/` folder.
2. Install [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) (required); optionally install PacketEvents (nametags/spectate/reserved-slot tablist), LuckPerms (tablist weight sorting) with the MiniPlaceholders-LuckPerms expansion (prefix/suffix placeholders), and Vault.
3. Start the server once to generate the config files, then adjust `config.yml`, `messages.yml`, and `shop.yml`.
4. If you use stronghold protection, restart once more so the auto-installed datapack takes effect in each world.

## Notes & Limitations

- **Without PacketEvents installed**: overhead nametags and the tablist's reserved-slot filler are silently disabled (everything else, including tablist header/footer, still works); `/spectate` refuses to run at all rather than offer a moderator a false sense of invisibility.
- The guild-tag second nametag line rides as a passenger entity on the player; vanilla doesn't document the exact height a passenger-mounted entity renders at, so `nametag.guild-tag.y-offset` may need visual tuning in-game.
- Random teleport is opt-in per world with configurable cost per use.
- Stronghold blocking only prevents **new** generation — strongholds in already-generated chunks remain, and the datapack needs one world reload/server restart after first install.
- Nether portal blocking prevents new portal **creation**; portals that existed before the plugin was installed remain usable.
- The Vault bridge is synchronous by contract (Vault's API returns plain doubles), so third-party plugins calling it from a region thread may block briefly on uncached (offline-player) balance lookups.
