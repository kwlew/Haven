# Haven

A concise sethome plugin for PaperMC.

Players save named locations and teleport back to them, with per-rank home limits, a cooldown,
and a teleport warmup that cancels if they move or take damage.

- **Server:** Paper 1.18.2 through 26.3
- **Java:** 17 for Paper 1.18–1.19, 21 for 1.20–1.21.11, 25 for 26.1–26.3
- **Optional:** PlaceholderAPI

## Install

1. Drop `Haven-<version>.jar` into `plugins/`.
2. Start the server. `config.yml` and `messages.yml` are generated on first run.
3. Grant permissions (see below) — the basics are on by default.

## Commands

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/sethome [name]` | — | `haven.sethome` | Saves a home at your location. Defaults to `home`. Re-run to confirm an overwrite. |
| `/home [name]` | — | `haven.home` | Teleports to a home. With no name: uses one called `home`, else your only home, else lists them. |
| `/delhome <name>` | `/removehome` | `haven.delhome` | Deletes a home and echoes its coordinates. |
| `/homes` | `/listhomes` | `haven.homes` | Lists your homes, grouped by world. Click a name to teleport. |
| `/haven reload` | — | `haven.admin.reload` | Reloads `config.yml` and `messages.yml`. |

Home names are 1–16 characters of `a-z`, `0-9`, `-` and `_`, and are case-insensitive.

## Permissions

| Node | Default | Description |
|---|---|---|
| `haven.home` | everyone | Use `/home` |
| `haven.sethome` | everyone | Use `/sethome` |
| `haven.delhome` | everyone | Use `/delhome` |
| `haven.homes` | everyone | Use `/homes` |
| `haven.homes.<n>` | — | Sets the home limit to `<n>`. Highest granted value wins. |
| `haven.homes.unlimited` | op | Removes the home limit |
| `haven.bypass.warmup` | op | Teleport instantly |
| `haven.bypass.cooldown` | op | Teleport without waiting |
| `haven.admin.reload` | op | Use `/haven reload` |

Grant limits per rank, e.g. `haven.homes.5` for members and `haven.homes.15` for donors. Players
with no such node get `homes.default-limit` from the config. Lowering someone's limit never deletes
homes they already have — it only stops them making new ones.

## Configuration

`config.yml` covers home limits, the warmup and cooldown, storage, and sounds. Every option is
commented in the generated file.

Sounds are fully customizable — each event takes a namespaced `key` (resource-pack sounds work),
`volume`, `pitch`, and `source`. Set a `key` to `""` to silence one event, or `sounds.enabled: false`
for all of them. Configurable events: `home-set`, `home-deleted`, `warmup-tick`,
`teleport-success`, `teleport-cancelled`, `denied`.

Haven's command and teleport feedback lives in `messages.yml` in
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) format. Setting a message to `""`
silences it. Run `/haven reload` to apply changes without restarting. Bukkit handles command
permission denials before Haven receives the command.

## PlaceholderAPI

Available when PlaceholderAPI is installed. Haven's test server uses PlaceholderAPI 2.12.3;
use a PlaceholderAPI build compatible with your Paper version. All placeholders require the
player to be online.

| Placeholder | Result |
|---|---|
| `%haven_homes_used%` | Homes currently saved |
| `%haven_homes_max%` | Their limit (`∞` when unlimited) |
| `%haven_homes_free%` | Remaining slots |
| `%haven_homes_list%` | Comma-separated home names |

## Storage

Homes are stored as YAML, one file per player, in `plugins/Haven/homes/<uuid>.yml`. The files are
meant to be readable and hand-editable. Writes happen on a background thread and are atomic, so a
crash mid-save cannot truncate a player's homes.

Reads share the write queue, so a fast reconnect sees the player's latest queued save. If a file
cannot be parsed, Haven refuses the login and leaves it untouched for an administrator to repair.
If a valid temporary file can recover it, Haven saves the damaged main file with a
`.corrupt-<id>` suffix before restoring the temporary file. A file with a newer schema is also
left untouched rather than being rewritten by an older Haven build.

Because homes record the world by **name**, renaming or deleting a world orphans the homes saved in
it. Those homes stay on disk, and `/home` reports the world as missing rather than sending the
player somewhere unexpected.

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

Run a startup, command-registration, reload, and PlaceholderAPI smoke test with
`python3 scripts/smoke-paper.py 1.18.2 17`. The compatibility workflow runs this check across
the supported version range. Before publishing, test `/sethome`, `/home`, `/delhome`, warmup
cancellation, cooldown, and reconnect persistence with a real player on both endpoint versions.
