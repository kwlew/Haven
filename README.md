# Haven

A homes plugin for Paper.

Players save named locations and teleport back to them, with per-rank home limits, a cooldown,
and a teleport warmup that cancels if they move or take damage.

- **Server:** Paper 1.17.1 through 26.3 (use the latest Paper build for each Minecraft version)
- **Java:** 17 for Paper 1.17.1 through 1.19, 21 for 1.20 through 1.21.11, 25 for 26.1 through 26.3
- **Optional:** PlaceholderAPI

## Install

1. Drop `Haven-<version>.jar` into `plugins/`.
2. Start the server. `config.yml` and `messages.yml` are generated on first run.
3. Grant any additional permissions you need. The basic commands are available to everyone by default.

## Commands

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/sethome [name]` | None | `haven.sethome` | Saves a home at your location. Defaults to `home`. Run it again to confirm an overwrite. |
| `/home [name]` | None | `haven.home` | Teleports to a home. With no name, it uses a home called `home`, your only home, or shows your home list. `/home <owner>:<name>` goes to a home another player shared with you. |
| `/delhome <name>` | `/removehome` | `haven.delhome` | Deletes a home and shows its coordinates. |
| `/renamehome <old> <new>` | None | `haven.renamehome` | Renames a home, keeping its location. |
| `/sharehome <home> [player]` | None | `haven.share` | Lets a player visit one of your homes. With no player, lists who it is shared with. |
| `/unsharehome <home> <player>` | None | `haven.share` | Stops sharing a home with a player. |
| `/homes [list]` | `/listhomes` | `haven.homes` | Lists your homes, grouped by world. Click a name to teleport. With `gui.enabled`, `/homes` opens the homes menu instead and `/homes list` is the chat list. |
| `/haven` | None | None | Opens the homes menu when `gui.enabled` is on and you have `haven.gui`; otherwise shows help. |
| `/haven help` | None | None | Lists the Haven commands you are allowed to use. Click one to fill it in. |
| `/haven version` | None | `haven.admin.version` | Shows the Haven and server versions, and whether an update is available. |
| `/haven reload` | None | `haven.admin.reload` | Reloads `config.yml` and `messages.yml`. |
| `/haven homes <player>` | None | `haven.admin.homes` | Lists a player's homes, online or offline. Click a name to teleport to it. |
| `/haven tp <player> <home>` | None | `haven.admin.tp` | Teleports you to a player's home. Skips warmup and cooldown, but still checks the destination is safe. |
| `/haven delhome <player> <home>` | None | `haven.admin.delhome` | Deletes a player's home and shows its coordinates. |

The `/haven homes`, `tp` and `delhome` subcommands work on players who are offline, as long as they
have joined the server before.

Home names must be 1 to 16 characters long and can contain English letters, digits, `-`, and `_`.
Names are case insensitive.

## Permissions

| Node | Default | Description |
|---|---|---|
| `haven.home` | everyone | Use `/home` |
| `haven.sethome` | everyone | Use `/sethome` |
| `haven.delhome` | everyone | Use `/delhome` |
| `haven.renamehome` | everyone | Use `/renamehome` |
| `haven.homes` | everyone | Use `/homes` |
| `haven.share` | everyone | Use `/sharehome` and `/unsharehome` |
| `haven.gui` | everyone | Open the homes menu from `/homes` and `/haven` (only when `gui.enabled` is on) |
| `haven.homes.<n>` | None | Sets the home limit to `<n>`. Highest granted value wins. |
| `haven.homes.unlimited` | op | Removes the home limit, including per-world limits |
| `haven.bypass.worlds` | op | Ignore `worlds.disabled`, `worlds.block-teleport` and `worlds.limits` |
| `haven.bypass.warmup` | op | Teleport instantly |
| `haven.bypass.cooldown` | op | Teleport without waiting |
| `haven.admin` | op | Grants every `haven.admin.*` permission below |
| `haven.admin.reload` | op | Use `/haven reload` |
| `haven.admin.update` | op | Receive update notifications |
| `haven.admin.homes` | op | Use `/haven homes` |
| `haven.admin.tp` | op | Use `/haven tp` |
| `haven.admin.delhome` | op | Use `/haven delhome` |
| `haven.admin.version` | op | Use `/haven version` |

For example, give members `haven.homes.5` and donors `haven.homes.15`. Players without a limit
permission use `homes.default-limit` from the config. If you lower a player's limit, their existing
homes remain available. They cannot create another home until they are below the new limit.

## Configuration

`config.yml` covers home limits, the warmup and cooldown, safe teleport, sharing, the homes menu, storage, update checks, and sounds. Every option is
commented in the generated file.

Safe teleport (`teleport.safe-teleport`) is on by default. After the warmup, Haven checks the saved
spot and refuses lava, fire, void, and suffocating blocks. If the spot is unsafe it moves the player
to the nearest safe spot within `search-radius` blocks sideways and `search-height` blocks up or
down, and says so. If none is found, the teleport is cancelled and the cooldown is not used. The saved
home is never changed. Creative and spectator players skip the check.

### Sharing homes

`/sharehome <home> <player>` lets one specific player visit that home; they use
`/home <your name>:<home>`. The recipient needs to have joined the server before and does not need to be
online. They are told when they are online, and shared homes appear after their own in `/homes`
(chat line `home.list-shared` and the menu, as player heads) and in `/home` tab completion.
`/unsharehome <home> <player>` revokes it. Deleting or renaming a home updates everyone it was
shared with.

Shared homes never count toward the recipient's home limit or world caps, and the owner can move
or rename a shared home without unsharing it. A visit uses the visitor's own warmup, cooldown and
safe-teleport check, and `worlds.block-teleport` applies to the visitor. Admins get no special
treatment here; use `/haven tp` instead.

```yaml
sharing:
  enabled: true      # false refuses sharing commands and shared teleports, and hides shared homes
  max-per-home: 10   # players one home can be shared with (1-100)
```

The owner's file is the source of truth. A recipient's file keeps only a list of hints (owner, owner
name, home name) for listings and completion, and each visit re-checks the owner's data first. If a
hint is stale, the visit says the home isn't shared with them and removes the hint, so a failed
update never grants access. A missing home and an unshared one give the same message.

### World rules

The `worlds` section controls where homes may be set. Names are exact world folder names.

```yaml
worlds:
  disabled: [ world_the_end ]   # /sethome is refused here; existing homes are kept
  block-teleport: false         # true also refuses /home teleports into disabled worlds
  limits:
    world_nether: 1             # at most 1 home in the nether, on top of the overall limit
```

A limit of `0` allows no homes in that world; worlds not listed are uncapped. A world limit counts
a new home, or an existing home being moved in from another world. Overwriting a home that is
already in that world is never blocked. Players with `haven.bypass.worlds` ignore the disabled list
and the world limits; `haven.homes.unlimited` also ignores the world limits.

## Homes menu

Set `gui.enabled: true` to open a chest menu from `/homes` and `/haven` instead of the chat list
(off by default). It is a viewer and teleport launcher only: clicking a home teleports exactly as
`/home` does, with the same warmup, cooldown and safe-teleport checks, and nothing in the menu
edits, deletes or renames homes. Homes are ordered like the chat list (worlds by name, then homes
by name) and shown as grass, netherrack or end stone by dimension, or a barrier when the world is
not loaded. More than 28 homes are split into pages with previous/next arrows. Players need
`haven.gui` to see the menu and `haven.home` to teleport from it; without `haven.gui` they get the
chat list. `/homes list` always prints the chat list.

`/haven` is open to everyone, since `help` is; the menu needs `haven.gui` and each subcommand needs
its own permission. The menu text is the `gui:` section of `messages.yml` (MiniMessage). Turning
`gui.enabled` off and running `/haven reload` closes any menu that is open.

Each sound event has a namespaced `key`, `volume`, `pitch`, and `source`. Custom resource pack sounds
work too. Set a `key` to `""` to silence one event, or set `sounds.enabled: false` to turn off all
sounds. The events are `home-set`, `home-deleted`, `warmup-tick`, `teleport-success`,
`teleport-cancelled`, and `denied`.

Haven's command and teleport feedback lives in `messages.yml` in
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) format. Setting a message to `""`
silences it. Run `/haven reload` to apply changes without restarting. Paper handles command
permission denials before Haven receives the command.

`/homes` shows friendly world names: "Overworld", "The Nether" and "The End" for the main world's
dimensions, otherwise the folder name capitalised (`resource_world` becomes `Resource World`).
Override any of them with the `world-names` section in `messages.yml`. Overworld, Nether, End and
unloaded worlds each have their own line format (`home.list-world`, `-nether`, `-end`, `-missing`).

## Update notifications

Haven checks [Modrinth](https://modrinth.com/plugin/khaven) in the background at startup,
after `/haven reload`, and every six hours. It looks for a newer listed stable Paper release
that supports the server's exact Minecraft version. Beta/alpha releases are ignored; numeric
versions are compared so a development build ahead of the latest release is not told to downgrade.
Unrecognized local version formats skip checking with a console warning.

New updates are logged to the console and sent to online players with `haven.admin.update`
(ops by default). Joining admins receive the cached result without another network request.
The message includes a clickable release link and can be customized with `admin.update-available`
in `messages.yml`, using `<current>`, `<latest>`, and `<link>`.

Set `update-checker.enabled: false` to disable requests, change `interval-hours` (1–168) to adjust
frequency, or set `notify-admins: false` for console-only notices. Apply changes with `/haven reload`.
Failed requests retry at the normal interval and log once per outage. Checks never download or
install plugin jars and do not send player data.

## PlaceholderAPI

Install a PlaceholderAPI build compatible with your Paper version to use these placeholders.
The player must be online.

| Placeholder | Result |
|---|---|
| `%haven_homes_used%` | Homes currently saved |
| `%haven_homes_max%` | Their limit (`∞` when unlimited) |
| `%haven_homes_free%` | Remaining slots |
| `%haven_homes_list%` | Comma-separated home names |

## Upgrading

When you install a new version over an old one, Haven updates `config.yml` and `messages.yml` for you
on startup. The new options and comments are added, values you changed are kept, and anything you
never touched is upgraded to the new default (including redesigned messages). Your old files are saved
in `plugins/Haven/backups/` first. Options that no longer exist are not copied over and are listed in
the console, so check the backup if you need them.

If Haven can't update a file, it leaves it untouched, says so in the console, and tells admins with
`haven.admin.reload` when they join. Delete the file (or compare it with the default in the jar) and
restart to get the new options.

## Storage

Haven stores one YAML file per player in `plugins/Haven/homes/<uuid>.yml`. You can edit these files
while the server is stopped. Writes run on a background thread and are atomic, so a crash during
a save cannot truncate a player's homes.

Files are schema version 2, which added `shared-with` on each home and a `received` list per player.
Version 1 files load unchanged and are rewritten as version 2 on their next save. **Downgrading to an
older Haven after version 2 files exist is not supported**: older builds refuse to load them, so
players could not join until the files were restored from a backup.

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

Use JDK 21 or newer to build. The core emits Java 17 bytecode; the isolated Brigadier adapter
emits Java 21 bytecode. Both are packaged in one jar. Paper 1.20.6 and newer use Brigadier; older
supported versions use Bukkit commands.

```bash
./gradlew build
```

The release jar lands in `build/libs/Haven-<version>.jar`; it includes bStats and an isolated
MiniMessage/Adventure 4.10 bundle, so formatting also works on Paper 1.17.1. Chat messages cross
into Bukkit as JSON chat components, and menu titles and item text as legacy colour strings; sounds use Bukkit's namespaced sound API. `./gradlew test`
runs the unit tests. `./gradlew runServer` starts Paper 26.3 with PlaceholderAPI. Select another
server with `./gradlew runServer -PpaperVersion=1.17.1 -PpaperJavaVersion=17`. Install that Java
version locally first. Each Paper version has its own `run/<version>/` directory so worlds are
never opened by an older server.

Use `python3 scripts/smoke-paper.py 1.17.1 17` to check startup, command registration, reload,
and PlaceholderAPI. The compatibility workflow runs this check across the supported version range.
Before publishing, test `/sethome`, `/home`, `/delhome`, warmup cancellation, cooldown, safe teleport
(a home with lava or a block placed in it, and one with no safe spot nearby), and reconnect
persistence with a real player on both endpoint versions. With `gui.enabled: true`, also check the
homes menu on both endpoints: `/homes` and `/haven` open it, items cannot be taken or moved
(including shift-click, number keys and drag), clicking a home teleports with the usual warmup, the
arrows page when you have more than 28 homes, and `/homes list` still prints the chat list.
For world rules, with `worlds.disabled: [ world_nether ]`: `/sethome` in the nether is refused with
no confirmation started, and existing nether homes are kept and still teleport. Turn on
`worlds.block-teleport` and `/home` plus the homes menu refuse those homes too. Set
`worlds.limits: { world: 1 }` and check that a second overworld home is refused, that overwriting the
existing one still asks to confirm and works, and that moving a nether home into a full overworld is
refused on the first `/sethome` rather than after the confirmation. `haven.bypass.worlds` skips all of
it, and `/haven reload` applies changes.
For sharing, with two players: `/sharehome base <other>` notifies them with a clickable command, `/home <you>:base`
teleports them with their own warmup and cooldown, it shows in their `/homes` and menu and in tab completion,
`/unsharehome` and `/delhome` remove it for them (also while they are offline), and a stale entry answers "hasn't
shared" and disappears.
