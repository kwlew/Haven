# Haven

A concise sethome plugin for PaperMC.

Players save named locations and teleport back to them, with per-rank home limits, a cooldown,
and a teleport warmup that cancels if they move or take damage.

- **Server:** Paper 26.2 (API `26.2`)
- **Java:** 25
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

All user-facing text lives in `messages.yml` in
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) format. Setting a message to `""`
silences it. Run `/haven reload` to apply changes without restarting.

## PlaceholderAPI

Available when PlaceholderAPI is installed — **2.12.3 or newer**, as earlier releases fail to load
on Minecraft 26.2. All placeholders require the player to be online.

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

Because homes record the world by **name**, renaming or deleting a world orphans the homes saved in
it. Those homes stay on disk, and `/home` reports the world as missing rather than sending the
player somewhere unexpected.

## Metrics

Haven reports anonymous usage data through [bStats](https://bstats.org/). Opt out for every plugin
by setting `enabled: false` in `plugins/bStats/config.yml`.

## Building

```bash
./gradlew build
```

The shaded jar lands in `build/libs/Haven-<version>-all.jar`. `./gradlew test` runs the unit tests,
and `./gradlew runServer` starts a Paper test server with the plugin loaded.
