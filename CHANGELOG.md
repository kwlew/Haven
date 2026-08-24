## Haven 1.0.0

First release.

**Homes**
- `/sethome [name]` to save a spot, `/home [name]` to go back
- `/homes` lists everything grouped by world, click a name to teleport
- `/delhome <name>` deletes a home and prints its coordinates in case you want it back
- Asks for confirmation before overwriting a home you already have

**Limits and teleporting**
- Per rank home limits through `haven.homes.<n>`, or `haven.homes.unlimited`
- Optional warmup that cancels if you move or take damage
- Optional cooldown, only spent when a teleport actually goes through

**Making it yours**
- All text lives in `messages.yml` with full MiniMessage support
- Every sound is configurable, including volume, pitch and channel, or turn them off
- `/haven reload` picks up changes without a restart

**Good to know**
- Needs Paper 26.2 and Java 25
- PlaceholderAPI support is optional and needs version 2.12.3 or newer
- Homes are stored one file per player and written safely, so a crash will not lose them
