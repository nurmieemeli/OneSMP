# CanvasSuite

An all-in-one SMP plugin for [CanvasMC](https://canvasmc.io/) servers, bundling economy, shops, teleportation, random teleport, guilds, chat formatting, and world protection into a single jar — with a chest-GUI front end for every module.

CanvasMC is a fork of **Folia** (regionized multithreading — there is no main thread), and CanvasSuite is built natively for that model: every operation runs on the correct region/entity/async scheduler, and all teleports use `teleportAsync`. It is **not** intended for regular Paper/Spigot servers.

All player-facing text is rendered with [Adventure MiniMessage](https://docs.advntr.dev/minimessage/format.html) and supports [MiniPlaceholders](https://github.com/MiniPlaceholders/MiniPlaceholders) tags (global, audience, and relational placeholders) in every configurable message and chat format.

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| CanvasMC 1.21.8+ | ✅ | Or another up-to-date Folia-compatible server |
| Java 25 | ✅ | |
| [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) | ✅ | Hard dependency — the plugin will not load without it |
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
- **/back** — return to your previous location after any plugin teleport or after dying.
- Configurable **teleport warmup** ("teleporting in 3s, don't move…") with cancel-on-move, applied consistently to homes, warps, TPA, and /back.

### 🎲 Random Teleport
- `/rtp [world]` (aliases `/wild`, `/randomtp`) teleports you to a random safe spot within a configurable min/max radius.
- Safe-location search runs fully off-thread (async chunk loads + region-thread block checks) — no server stalls, Folia-safe.
- Avoids configured biomes (oceans and rivers by default) and hazardous landings (lava, cacti, water, magma).
- Per-use cooldown, optional economy cost, and a per-world blacklist. `canvassuite.rtp.admin` bypasses the cooldown.

### 🛡️ Guilds
- Create guilds with a name and chat tag: `/guild create <name> <tag>` (optional creation cost).
- Three roles — **Owner**, **Officer**, **Member** — gating invites, kicks, promotions, guild home, and disband.
- Invites with clickable accept, member limits, guild home teleport, and a DB-backed **guild bank**.
- **Guild chat**: `/guild chat` toggles your chat to guild-only messages.
- Management GUIs: `/guild` (or `/guild gui`) opens the main panel — info, member list (click to kick, shift-click to promote/demote), guild home, set-home, chat toggle, leave/disband.
- Guild tags appear in public chat via the `<guild_tag>` placeholder.

### 💬 Chat Formatting
- Every chat message is rendered through MiniMessage with full MiniPlaceholders support (e.g. `<player_name>`, plus relational placeholders between sender and each viewer).
- **Permission-tiered formats**: define any number of formats in `config.yml`, evaluated top-to-bottom — first format whose permission the sender holds wins. Ship different looks for admins, donors, and defaults.
- `<guild_tag>` placeholder built in.
- **Injection-safe**: player-typed message content is inserted as plain text, so players can't smuggle `<click>`, `<hover>`, or color tags into chat — unless they hold `canvassuite.chat.format`, which unlocks MiniMessage styling in their messages.

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
| `/back` | Return to previous location | `canvassuite.tpa.use` |
| `/rtp [world]` (`/wild`, `/randomtp`) | Random teleport | `canvassuite.rtp.use` |
| `/guild <sub>` (`/g`, `/clan`) | Guild management (`create`, `disband`, `invite`, `accept`, `kick`, `promote`, `demote`, `sethome`, `home`, `info`, `list`, `chat`, `leave`, `gui`) | `canvassuite.guild.use` |

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
| `canvassuite.tpa.use` | true | TPA and /back |
| `canvassuite.rtp.use` / `.admin` | true / op | RTP / cooldown bypass |
| `canvassuite.guild.use` / `.admin` | true / op | Guilds |
| `canvassuite.chat.format` | false | Allows MiniMessage styling in chat messages |
| `canvassuite.chat.admin` | op | Uses the admin chat format tier |

## Configuration

Three files are created in `plugins/CanvasSuite/` on first start:

- **`config.yml`** — storage backend (MySQL credentials with automatic SQLite fallback), economy settings, teleport warmup/TPA timeouts, home limits, RTP radius/cooldown/cost/biome blacklist, guild rules and costs, chat format tiers, and protection toggles.
- **`messages.yml`** — every message the plugin sends, in MiniMessage. Change colors, add gradients, hover/click events, or MiniPlaceholders tags freely. The shared `<prefix>` is defined once at the top.
- **`shop.yml`** — shop categories and per-item `buy-price` / `sell-price` values.

Example chat format tier from `config.yml`:

```yaml
chat:
  formats:
    - permission: canvassuite.chat.admin
      format: "<red><bold>ADMIN</bold></red> <gray>[<guild_tag>]</gray> <white><player_name></white><dark_gray>:</dark_gray> <message>"
    - permission: ""   # fallback for everyone else
      format: "<gray>[<guild_tag>]</gray> <white><player_name></white><dark_gray>:</dark_gray> <gray><message>"
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

Set `storage.type` to `MYSQL` or `SQLITE` in `config.yml`. With MySQL selected, connections are pooled through HikariCP; if the database is unreachable at startup, the plugin logs a warning and **falls back to a local SQLite file** (`plugins/CanvasSuite/data.db`) so the server still boots. The schema (accounts, homes, warps, guilds, guild members) is created automatically on either backend.

## Building from Source

```bash
mvn -DskipTests package
```

Requires JDK 25 and Maven. The shaded jar lands at `target/CanvasSuite-<version>.jar` with HikariCP and the MySQL/SQLite drivers relocated to `gg.nurmi.libs.*` to avoid conflicts with other plugins.

## Installation

1. Drop `CanvasSuite-<version>.jar` into your server's `plugins/` folder.
2. Install [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) (required) and optionally Vault.
3. Start the server once to generate the config files, then adjust `config.yml`, `messages.yml`, and `shop.yml`.
4. If you use stronghold protection, restart once more so the auto-installed datapack takes effect in each world.

## Notes & Limitations

- Stronghold blocking only prevents **new** generation — strongholds in already-generated chunks remain, and the datapack needs one world reload/server restart after first install.
- Nether portal blocking prevents new portal **creation**; portals that existed before the plugin was installed remain usable.
- The Vault bridge is synchronous by contract (Vault's API returns plain doubles), so third-party plugins calling it from a region thread may block briefly on uncached (offline-player) balance lookups.
