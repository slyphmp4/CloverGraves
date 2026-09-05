# CloverGraves

[![Build CloverGraves](https://github.com/slyphmp4/CloverGraves/actions/workflows/build.yml/badge.svg?branch=rewrite)](https://github.com/slyphmp4/CloverGraves/actions/workflows/build.yml)
[![GitHub release](https://img.shields.io/github/v/release/slyphmp4/CloverGraves?display_name=tag)](https://github.com/slyphmp4/CloverGraves/releases/latest)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-3C8527)
![Java 25](https://img.shields.io/badge/Java-25-ED8B00)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

CloverGraves is a lightweight grave/death-chest plugin for Minecraft 26.2. It stores a player's items and experience after death, represents the grave with a protected player head and a native `TextDisplay` hologram, and provides configurable interaction, protection, persistence, history and teleport features.

The project is maintained by **slyph** and focuses on reliable Paper and Cardboard compatibility without runtime NMS, CraftBukkit internals or AxAPI dependencies.

## Features

- Stores inventory contents in a grave after death.
- Stores experience in the grave and removes the captured experience from the player on death to prevent duplication.
- Native Adventure `Component` and `TextDisplay` holograms.
- Protected player-head grave marker that cannot be taken or swapped by normal interaction.
- **Right-click** to open and inspect a grave.
- **Sneak + Right-click** for instant pickup.
- Configurable virtual interaction hitbox for easier grave targeting.
- Grave ownership and temporary protection for other players.
- Configurable safe grave placement with lava, solid-block and Nether-roof checks.
- Configurable grave lifetime, item dropping and dropped-item velocity.
- Persistent graves across server restarts.
- H2, SQLite and MySQL storage with JSON fallback and legacy-data migration support.
- Grave history and administrative restoration.
- Grave teleportation with warmup, cooldown, movement/damage cancellation and optional Vault economy cost.
- Optional PlaceholderAPI integration.
- Configurable messages, holograms, world names and HEX colors.
- Permission-aware commands and tab completion.
- No runtime AxAPI, NMS or CraftBukkit dependency.

## Requirements

| Requirement | Version / notes |
| --- | --- |
| Minecraft | **26.2** |
| Java | **25** |
| Paper API | **26.2.build.110-stable** target |
| Cardboard | Tested against [`slyphmp4/Cardboard`](https://github.com/slyphmp4/Cardboard) commit `5b62d59716f5a7d6dc0fa780ea7ca527e79272a5` |
| PlaceholderAPI | Optional, tested with **2.12.3** |
| Vault | Optional, used for economy-backed teleport costs |

CloverGraves is developed primarily for Minecraft/Paper/Cardboard 26.2. Cardboard users should use a build that includes working Bukkit `TextDisplay` support; the CI pipeline validates CloverGraves against the Cardboard revision listed above.

## Installation

1. Download `CloverGraves-2.0.0.jar` from the [latest release](https://github.com/slyphmp4/CloverGraves/releases/latest).
2. Place the JAR in your server's `plugins` directory.
3. Start the server once to generate the configuration files.
4. Configure `plugins/CloverGraves/config.yml` and `messages.yml` as needed.
5. Perform a full server restart after replacing the plugin JAR.

Using `/reload` is not recommended, especially on Cardboard servers.

## Grave interaction

By default:

- **Right-click** the grave to open its inventory.
- **Sneak + Right-click** the grave to instantly collect its contents.
- Left-clicking does not loot the grave.
- Once all stored items and XP are collected, the grave marker and hologram are removed.

CloverGraves also uses a configurable virtual interaction area so the player does not have to click a tiny ArmorStand hitbox precisely:

```yaml
interact-radius: 7.0
interaction-hitbox:
  width: 1.6
  height: 2.2
```

## Commands

The primary command is `/clovergraves`.

| Command | Description |
| --- | --- |
| `/clovergraves` | Show CloverGraves help. |
| `/clovergraves help` | Show command help. |
| `/clovergraves reload` | Reload CloverGraves configuration. |
| `/clovergraves list` | List accessible active graves. |
| `/clovergraves tp` | Teleport to your most recent grave. |
| `/clovergraves tp <world> <x> <y> <z>` | Teleport to a grave location or, with bypass permission, arbitrary coordinates. |
| `/clovergraves grave tp ...` | Compatibility form of the teleport command. |
| `/clovergraves history <player>` | Show stored grave history for a player. |
| `/clovergraves restore <player> <id>` | Restore a grave from history. |

Aliases: `/graves`, `/grave`, `/axgraves`, `/axgrave`, `/bibingka`.

## Permissions

Legacy `axgraves.*` permission nodes are intentionally retained for compatibility with existing permission setups.

| Permission | Default | Description |
| --- | --- | --- |
| `axgraves.help` | Everyone | Use help. |
| `axgraves.reload` | OP | Reload the plugin. |
| `axgraves.list` | Everyone | List accessible graves. |
| `axgraves.list.other` | OP | See other players' graves in the list. |
| `axgraves.tp` | Everyone | Use grave teleportation. |
| `axgraves.tp.bypass` | OP | Bypass grave-location restrictions for teleport coordinates. |
| `axgraves.allowgraves` | Everyone | Allow graves to be created for the player. |
| `axgraves.limit.1` | Disabled | Example grave-limit permission. |
| `axgraves.admin` | OP | Administrative bypasses. |
| `axgraves.update-notify` | OP | Receive update notifications. |
| `axgraves.protection.bypass` | OP | Bypass grave protection. |
| `axgraves.history` | OP | View grave history. |
| `axgraves.restore` | OP | Restore graves from history. |

## PlaceholderAPI

When PlaceholderAPI is installed, CloverGraves registers both the `clovergraves` namespace and the legacy `axgraves` namespace.

| Placeholder | Description |
| --- | --- |
| `%clovergraves_grave_count%` | Number of currently active graves on the server. |
| `%clovergraves_grave_limit%` | Grave limit for the current player, or `∞` when unlimited. |
| `%axgraves_grave_count%` | Legacy alias for grave count. |
| `%axgraves_grave_limit%` | Legacy alias for grave limit. |

## Storage

Set the storage backend in `config.yml`:

```yaml
storage:
  type: H2
```

Available SQL backends:

- `H2` — default local database.
- `SQLITE` — local SQLite database.
- `MYSQL` — remote MySQL-compatible database using the configured connection details.

If SQL initialization fails, CloverGraves can fall back to its JSON storage implementation. Existing supported legacy data is migrated where applicable.

## Colors and messages

User-facing text is configurable and processed through Adventure/MiniMessage. CloverGraves accepts common legacy-style color input and HEX forms, including:

```text
&c
&FF0000
&#FF0000
<#FF0000>
```

Invalid or unsupported input should not require direct section-sign color codes in Java source.

## Building from source

```bash
git clone https://github.com/slyphmp4/CloverGraves.git
cd CloverGraves
./gradlew clean build
```

The project uses the Gradle Wrapper and Java 25. The shaded release JAR is written to `build/libs/`.

## CI and Cardboard compatibility

Every release build runs the following checks before a release asset is published:

1. Source audit for forbidden AxAPI/NMS/CraftBukkit dependencies and removed hologram fallbacks.
2. `./gradlew clean build` and automated tests.
3. Audit of the produced shaded JAR.
4. Build of the pinned CloverGraves-compatible Cardboard 26.2 revision.
5. Fabric + Cardboard 26.2 runtime smoke test, including native `TextDisplay` behavior.
6. Upload of the verified CloverGraves JAR.

The release also includes `SHA256SUMS.txt` for verifying the published JAR.

## Bug reports and feature requests

Use the repository's [GitHub Issues](https://github.com/slyphmp4/CloverGraves/issues) page for reproducible bugs and feature requests. For Cardboard-specific problems, include the Cardboard build/commit, CloverGraves version and the relevant server log.

## Credits

CloverGraves is maintained by **slyph**.

The project started as a fork of [AxGraves](https://github.com/Artillex-Studios/AxGraves) and has since been substantially rewritten and modernized for CloverGraves, including its runtime architecture, storage, commands, interaction behavior and Cardboard compatibility work.

The project remains distributed under the [MIT License](LICENSE). The original Artillex-Studios copyright notice is preserved in the license as required.
