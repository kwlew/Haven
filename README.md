# Haven

A homes plugin for Paper.

Players save named locations and teleport back to them, with per-rank home limits, a cooldown,
and a teleport warmup that cancels if they move or take damage.

- **Server:** Paper 1.18.2 through 26.3
- **Java:** 17 for Paper 1.18 through 1.19, 21 for 1.20 through 1.21.11, 25 for 26.1 through 26.3
- **Optional:** PlaceholderAPI

## Install

1. Drop `Haven-<version>.jar` into `plugins/`.
2. Start the server. `config.yml` and `messages.yml` are generated on first run.
3. Grant any additional permissions you need. The basic commands are available to everyone by default.

## Commands

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/sethome [name]` | None | `haven.sethome` | Saves a home at your location. Defaults to `home`. Run it again to confirm an overwrite. |
| `/home [name]` | None | `haven.home` | Teleports to a home. With no name, it uses a home called `home`, your only home, or shows your home list. |
| `/delhome <name>` | `/removehome` | `haven.delhome` | Deletes a home and shows its coordinates. |
| `/homes` | `/listhomes` | `haven.homes` | Lists your homes, grouped by world. Click a name to teleport. |
| `/haven reload` | None | `haven.admin.reload` | Reloads `config.yml` and `messages.yml`. |

Home names must be 1 to 16 characters long and can contain English letters, digits, `-`, and `_`.
Names are case insensitive.

## Permissions

| Node | Default | Description |
|---|---|---|
| `haven.home` | everyone | Use `/home` |
| `haven.sethome` | everyone | Use `/sethome` |
| `haven.delhome` | everyone | Use `/delhome` |
| `haven.homes` | everyone | Use `/homes` |
| `haven.homes.<n>` | None | Sets the home limit to `<n>`. Highest granted value wins. |
| `haven.homes.unlimited` | op | Removes the home limit |
| `haven.bypass.warmup` | op | Teleport instantly |
| `haven.bypass.cooldown` | op | Teleport without waiting |
| `haven.admin.reload` | op | Use `/haven reload` |

For example, give members `haven.homes.5` and donors `haven.homes.15`. Players without a limit
permission use `homes.default-limit` from the config. If you lower a player's limit, their existing
homes remain available. They cannot create another home until they are below the new limit.

## Configuration

`config.yml` covers home limits, the warmup and cooldown, storage, and sounds. Every option is
commented in the generated file.

Each sound event has a namespaced `key`, `volume`, `pitch`, and `source`. Custom resource pack sounds
work too. Set a `key` to `""` to silence one event, or set `sounds.enabled: false` to turn off all
sounds. The events are `home-set`, `home-deleted`, `warmup-tick`, `teleport-success`,
`teleport-cancelled`, and `denied`.

Haven's command and teleport feedback lives in `messages.yml` in
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) format. Setting a message to `""`
silences it. Run `/haven reload` to apply changes without restarting. Bukkit handles command
permission denials before Haven receives the command.

## PlaceholderAPI

Install a PlaceholderAPI build compatible with your Paper version to use these placeholders.
The player must be online.

| Placeholder | Result |
|---|---|
| `%haven_homes_used%` | Homes currently saved |
| `%haven_homes_max%` | Their limit (`∞` when unlimited) |
| `%haven_homes_free%` | Remaining slots |
| `%haven_homes_list%` | Comma-separated home names |

## Storage

Haven stores one YAML file per player in `plugins/Haven/homes/<uuid>.yml`. You can edit these files
while the server is stopped. Writes run on a background thread and are atomic, so a crash during
a save cannot truncate a player's homes.

Reads share the write queue, so a fast reconnect sees the player's latest queued save. If a file
cannot be parsed, Haven refuses the login and leaves it untouched for an administrator to repair.
If a valid temporary file can recover it, Haven saves the damaged main file with a
`.corrupt-<id>` suffix before restoring the temporary file. Haven also leaves files with a newer
schema untouched.

Homes record the world by **name**. If you rename or delete a world, homes saved there stay on disk.
Players trying to use those homes see a message that the world is unavailable.

## Metrics

Haven reports anonymous usage data through [bStats](https://bstats.org/). Opt out for every plugin
by setting `enabled: false` in `plugins/bStats/config.yml`.

## Building

Use JDK 17 or newer; the build emits Java 17 bytecode.

```bash
./gradlew build
```

The release jar lands in `build/libs/Haven-<version>.jar`; it includes bStats. `./gradlew test`
runs the unit tests. `./gradlew runServer` starts Paper 26.3 with PlaceholderAPI. Select another
server with `./gradlew runServer -PpaperVersion=1.18.2 -PpaperJavaVersion=17`. Install that Java
version locally first. Each Paper version has its own `run/<version>/` directory so worlds are
never opened by an older server.

Use `python3 scripts/smoke-paper.py 1.18.2 17` to check startup, command registration, reload,
and PlaceholderAPI. The compatibility workflow runs this check across the supported version range.
Before publishing, test `/sethome`, `/home`, `/delhome`, warmup cancellation, cooldown, and reconnect
persistence with a real player on both endpoint versions.
