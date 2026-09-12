<div align="center">

# CloverGraves

**A persistent grave system for Minecraft servers with safe placement, protected loot, history, teleportation and multiple storage backends.**

[![Build](https://github.com/slyphmp4/CloverGraves/actions/workflows/build.yml/badge.svg?branch=rewrite)](https://github.com/slyphmp4/CloverGraves/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/slyphmp4/CloverGraves?style=flat-square)](https://github.com/slyphmp4/CloverGraves/releases)
[![Java](https://img.shields.io/badge/Java-25-555?style=flat-square)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-26.2-555?style=flat-square)](https://papermc.io/)
[![License](https://img.shields.io/badge/license-MIT-555?style=flat-square)](LICENSE)

[Releases](https://github.com/slyphmp4/CloverGraves/releases) · [Builds](https://github.com/slyphmp4/CloverGraves/actions) · [Issues](https://github.com/slyphmp4/CloverGraves/issues)

</div>

---

## Overview

CloverGraves keeps a death recoverable without turning it into a permanent keep-inventory mechanic.

When a player dies, configured inventory contents and experience are captured into a persistent grave. The grave is represented by a protected player-head marker and a native `TextDisplay` hologram, can survive server restarts, and disappears once its contents are recovered or its lifetime expires.

The project is designed around Paper 26.2 and Cardboard compatibility without runtime NMS, CraftBukkit internals or an AxAPI dependency.

### At a glance

| Area | What CloverGraves provides |
| --- | --- |
| Death handling | Item and XP capture with duplication-safe XP removal |
| Grave marker | Protected player head and native `TextDisplay` hologram |
| Interaction | Right-click inventory, sneak-right-click instant pickup |
| Placement | Safe-location search around lava, solid blocks and Nether roof |
| Protection | Ownership rules and temporary protection from other players |
| Persistence | H2, SQLite, MySQL and JSON fallback/migration support |
| Recovery | Grave list, history, restore and teleport commands |
| Economy | Optional Vault-backed teleport cost |
| Integration | PlaceholderAPI and public grave events |

---

## Requirements

| Component | Version / notes |
| --- | --- |
| Minecraft / Paper build target | **26.2** |
| Java | **25** |
| Paper API build | `26.2.build.110-stable` |
| Bukkit `api-version` | `26.2` |
| PlaceholderAPI | Optional, built against **2.12.3** |
| Vault | Optional, used for teleport costs |

CloverGraves is compiled, declared and tested for Minecraft/Paper/Cardboard 26.2. The plugin does not lower `api-version` to claim unsupported backwards compatibility.

The CI pipeline also includes Cardboard-oriented compatibility checks for the modern runtime features CloverGraves uses, including `TextDisplay` behavior.

---

## Installation

1. Download `CloverGraves-2.1.1.jar` from [Releases](https://github.com/slyphmp4/CloverGraves/releases), or build the project yourself.
2. Put the JAR into the server's `plugins/` directory.
3. Start the server once to generate `config.yml` and `messages.yml`.
4. Review grave lifetime, storage, protection and teleport settings.
5. Restart the server normally after replacing the plugin JAR.

A full restart is preferred over Bukkit's global `/reload`, particularly on compatibility layers such as Cardboard.

---

## Grave lifecycle

The normal flow is intentionally simple:

```text
Player dies
    ↓
Configured items and XP are captured
    ↓
CloverGraves finds a safe nearby position
    ↓
A protected grave marker and hologram appear
    ↓
Player opens or instantly collects the grave
    ↓
Remaining contents stay persistent
    ↓
Empty or expired grave is removed
```

Experience captured into the grave is removed from the normal death drop path so the same XP is not awarded twice.

---

## Interaction

By default:

- **Right-click** opens the grave inventory.
- **Sneak + Right-click** performs instant pickup.
- Left-clicking does not loot the grave.
- A grave is removed once all stored items and XP have been collected.

Because a single skull can be annoying to target, CloverGraves also uses a configurable virtual interaction area:

```yaml
interact-radius: 7.0
interaction-hitbox:
  width: 1.6
  height: 2.2
```

Instant pickup can be disabled independently or limited to the grave owner.

---

## Safe placement

A death location is not always a usable grave location. CloverGraves can search nearby for a safer position instead of placing the marker directly inside a hazard.

```yaml
safe-placement:
  enabled: true
  avoid-lava: true
  avoid-solid: true
  avoid-nether-roof: true
  nether-roof-y: 125
  require-ground-support: true
  max-horizontal-radius: 16
  max-vertical-distance: 128
  notify-owner: true
```

World-specific height limits can also be configured for the Overworld, Nether and End.

---

## Protection and lifetime

The default grave lifetime is 30 minutes:

```yaml
despawn-time-seconds: 1800
```

Other players are temporarily blocked by the protection window:

```yaml
protection:
  seconds: 30
  message-cooldown-seconds: 3
```

Ownership restrictions can be tightened further with `interact-only-own`, `instant-pickup-only-own` and permission-based administrative bypasses.

When a grave expires, item dropping and dropped-item velocity are configurable rather than hard-coded.

---

## Items and experience

Core capture settings are independent:

```yaml
xp-keep-percentage: 1.0
store-items: true
store-xp: true
```

The order used when reconstructing a grave inventory can also be controlled:

```yaml
grave-item-order:
  - "ARMOR"
  - "HAND"
  - "OFFHAND"
```

Armor can be auto-equipped again during collection, and individual items can be excluded through the blacklist configuration.

`override-keep-inventory` is disabled by default, so server/world keep-inventory behavior is not silently overridden unless explicitly requested.

---

## Commands

The main command is `/clovergraves`.

| Command | Description |
| --- | --- |
| `/clovergraves` | Show command help |
| `/clovergraves help` | Show help explicitly |
| `/clovergraves reload` | Reload configuration |
| `/clovergraves list` | List accessible active graves |
| `/clovergraves tp` | Teleport to the most recent accessible grave |
| `/clovergraves tp <world> <x> <y> <z>` | Teleport to a grave location; bypass permission can allow arbitrary coordinates |
| `/clovergraves history <player>` | View stored grave history |
| `/clovergraves restore <player> <id>` | Restore a grave from history |

Command aliases retained for compatibility:

```text
/axgraves
/axgrave
/graves
/grave
/bibingka
```

Command aliases are registered from `plugin.yml`. The legacy `command-aliases`
configuration key is currently not read by the command registration code.

---

## Permissions

Legacy `axgraves.*` nodes are intentionally retained so an existing permissions setup does not need to be rewritten during migration.

| Permission | Default | Purpose |
| --- | --- | --- |
| `axgraves.help` | Everyone | View help |
| `axgraves.reload` | OP | Reload CloverGraves |
| `axgraves.list` | Everyone | List accessible graves |
| `axgraves.list.other` | OP | Include graves owned by other players |
| `axgraves.tp` | Everyone | Use grave teleportation |
| `axgraves.tp.bypass` | OP | Bypass grave-location teleport restrictions |
| `axgraves.allowgraves` | Everyone | Allow graves to be created for the player |
| `axgraves.limit.1` | Disabled | Example grave-limit permission |
| `axgraves.admin` | OP | Administrative bypasses |
| `axgraves.update-notify` | OP | Receive update notifications |
| `axgraves.protection.bypass` | OP | Bypass grave protection |
| `axgraves.history` | OP | View grave history |
| `axgraves.restore` | OP | Restore historical graves |

---

## Teleportation

Players can return to a grave without making teleportation instant or consequence-free.

Defaults:

```yaml
teleport:
  cooldown-seconds: 60
  warmup-seconds: 5
  cancel-on-move: true
  cancel-on-damage: true
  cost: 0
  currency-symbol: "₱"
  confirmation-timeout-seconds: 15
```

If a positive `cost` is configured and Vault is available with an economy provider, the teleport can charge the player. With a zero cost, Vault is not required for ordinary grave operation.

---

## History and restore

CloverGraves can keep a bounded history after graves are removed:

```yaml
history:
  enabled: true
  keep-per-player: 5
  keep-days: 14
```

Staff with the appropriate permissions can inspect this history and restore a selected record. This is useful for support cases without turning the active-grave database into an indefinite archive.

---

## Storage

The default backend is local H2:

```yaml
storage:
  type: H2
  table-prefix: "axgraves_"
  flush-interval-seconds: 15
```

Supported persistent backends include:

| Backend | Use case |
| --- | --- |
| `H2` | Default local SQL database |
| `SQLITE` | Alternative local SQLite database |
| `MYSQL` | Remote/shared MySQL-compatible database |
| JSON fallback | Recovery/fallback path when applicable |

MySQL connection-pool settings are configurable under `storage.mysql.pool`.

CloverGraves also contains migration support for compatible legacy grave data so storage modernization does not require discarding existing records.

---

## PlaceholderAPI

When PlaceholderAPI is installed, CloverGraves registers the `clovergraves` namespace and keeps the legacy `axgraves` aliases.

| Placeholder | Result |
| --- | --- |
| `%clovergraves_grave_count%` | Number of currently active graves |
| `%clovergraves_grave_limit%` | Grave limit for the current player, or `∞` when unlimited |
| `%axgraves_grave_count%` | Legacy alias for grave count |
| `%axgraves_grave_limit%` | Legacy alias for grave limit |

---

## Messages and colors

Player-facing text is kept in `messages.yml`, while technical behavior stays in `config.yml`.

CloverGraves accepts common legacy and HEX color forms, including:

```text
&c
&FF0000
&#FF0000
<#FF0000>
```

Hologram presentation is separately configurable:

```yaml
holograms:
  background-color: "00000000"
  alignment: center
  billboard: vertical
  see-through: false
  shadow: true
```

---

## Public API and events

The plugin exposes a small API surface and grave lifecycle events for integrations.

Available event classes include:

```text
GravePreSpawnEvent
GraveSpawnEvent
GraveInteractEvent
GraveOpenEvent
```

This allows another plugin to observe or influence grave behavior without depending on internal implementation packages.

---

## Configuration files

```text
plugins/CloverGraves/
├── config.yml
├── messages.yml
└── <storage files created by the selected backend>
```

`config.yml` controls placement, interaction, protection, grave limits, item/XP capture, storage, history, teleportation, world limits, blacklists, holograms and update notifications.

`messages.yml` contains the editable player-facing text.

---

## Building from source

CloverGraves uses Gradle Kotlin DSL, Java 25 and Shadow.

```bash
git clone https://github.com/slyphmp4/CloverGraves.git
cd CloverGraves
git checkout rewrite
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

The shaded release JAR is written to `build/libs/`.

The build bundles and relocates the runtime libraries that belong inside CloverGraves while keeping Vault, PlaceholderAPI and Paper as external APIs.

---

## Compatibility notes

CloverGraves avoids runtime NMS and CraftBukkit internals. Modern holograms use Bukkit's native `TextDisplay` API, and Cardboard-specific checks are isolated in compatibility code and CI rather than mixed into the core grave implementation.

The build workflow performs source/JAR checks and runtime-oriented compatibility validation before publishing artifacts.

For Cardboard-specific bug reports, include the CloverGraves version, Cardboard build or commit and the relevant server log.

---

## Credits and license

CloverGraves is maintained by **slyph**.

The project began as a fork of [AxGraves](https://github.com/Artillex-Studios/AxGraves) and has since been substantially rewritten for the CloverGraves codebase, including storage, placement, commands, persistence and compatibility work.

CloverGraves is distributed under the [MIT License](LICENSE). The original copyright notice is preserved there as required.
