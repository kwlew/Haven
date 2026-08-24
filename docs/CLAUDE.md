# Haven

A concise sethome plugin for PaperMC (Minecraft 26.2 / Java 25). Players save named
locations and teleport back to them, with per-rank home limits, a cooldown, and a
cancellable teleport warmup.

**⚠️ This file describes a moving target. Whenever you finish a code change,
re-read the affected packages and update the "Current structure" section
below so it stays accurate — don't let it drift into a stale description.**

## Stack

- Paper API via `paperweight-userdev`, targeting API version `26.2`
  (resolves `paper-api:26.2.build.112-stable`)
- Java 25 toolchain
- Shadow (fat-jar) + `run-paper` for local testing (`./gradlew runServer`)
- Commands via Paper's Brigadier API; user-facing text via Adventure MiniMessage
- bStats for metrics, PlaceholderAPI as an optional soft dependency.
  **PAPI must be 2.12.3 or newer** — 2.12.2 and earlier throw `NumberFormatException` in their
  static initializer on MC 26.2 and never load. `runServer` downloads a working copy automatically.
- Group: `dev.kwlew`, base package: `dev.kwlew.haven`

## Current structure

```
src/main/java/dev/kwlew/haven/
├── Haven.java                  # JavaPlugin entrypoint; owns Bootstrap, disables itself on a failed enable
├── COLORS.java                 # ANSI color code constants for console log formatting
├── globals/
│   └── BuildINFO.java          # Only what plugin.yml can't hold (bStats id, repo URL).
│                               # Name/version/authors come from plugin.getPluginMeta()
├── kernel/                     # Minimal custom DI + lifecycle framework
│   ├── Bootstrap.java          # Registers components by feature group, drives init() -> start(),
│   │                           # rolls back on failure, reverse-order shutdown()
│   ├── Registry.java           # Reflection-based DI container. Constructor injection via @Inject,
│   │                           # else the greediest constructor; detects circular deps; unwraps
│   │                           # constructor failures; iterates each instance once in creation
│   │                           # order even when bind() aliases it; seal() closes it after boot
│   ├── Inject.java             # @Inject marker annotation for constructor injection
│   └── LifecycleComponent.java # Interface with default init()/start()/shutdown() hooks
├── exceptions/
│   ├── CircularDependencyException.java
│   └── UnresolvedDependencyException.java
├── config/
│   └── HavenConfig.java        # Typed config.yml accessors; loads in ctor, read live (never cached)
├── message/
│   └── Messages.java           # messages.yml + MiniMessage; jar copy installed as defaults
├── home/
│   ├── Home.java               # record: name, world NAME, x/y/z, yaw/pitch, created. No Bukkit state
│   ├── PlayerHomes.java        # Live per-player container (main-thread only) + immutable Snapshot
│   ├── HomeStorage.java        # Persistence interface (load/save) extending LifecycleComponent
│   ├── YamlHomeStorage.java    # Per-player YAML on a private "Haven-IO" executor, atomic writes
│   ├── HomeManager.java        # Cache + pre-login parking; write-through on mutation
│   └── HomeLimits.java         # haven.homes.<n> permission scanning
├── sound/
│   ├── HavenSound.java         # Enum of sound events; value() is its config.yml key
│   └── Sounds.java             # Parses sounds once on load/reload, plays them to a player
├── teleport/
│   └── TeleportService.java    # Cooldown gate, tick-sampled warmup, async teleport
├── listener/
│   ├── ConnectionListener.java # Pre-login load / join install / quit flush + evict
│   └── WarmupListener.java     # Cancels warmups on damage and death
├── command/
│   ├── HavenCommands.java      # Registers the Brigadier COMMANDS handler in start()
│   ├── PlayerCommand.java      # Base class: resolves sender to a Player, messages console cleanly
│   ├── SetHomeCommand.java     # /sethome [name]
│   ├── HomeCommand.java        # /home [name]
│   ├── DelHomeCommand.java     # /delhome <name>  (alias /removehome)
│   ├── HomesCommand.java       # /homes           (alias /listhomes)
│   ├── ReloadCommand.java      # /haven reload    (config + messages only)
│   ├── HomeSuggestions.java    # Tab-completion from the cache; never blocks on disk
│   └── OverwriteConfirmations.java # Pending /sethome overwrite confirmations, keyed by home name
└── api/                        # Optional third-party integrations, wired as LifecycleComponents
    ├── bStats.java             # Metrics + custom charts, fed by a main-thread snapshot
    └── papi/
        ├── PlaceholderAPIHook.java  # Registers/unregisters the expansion if PAPI is present
        └── PlaceholderAPI.java      # %haven_homes_used% / _max% / _free% / _list%

src/test/java/dev/kwlew/haven/       # JUnit 5, no server or mocking framework
├── kernel/RegistryTest.java         # DI: caching, @Inject, cycles, bind aliasing, seal
├── home/HomeNameTest.java           # Locale-independent normalization, name validation
├── home/HomeLimitsTest.java         # Permission-based limits incl. negated/typo'd nodes
└── command/OverwriteConfirmationsTest.java

src/main/resources/
├── plugin.yml    # name/version/description templated from gradle.properties; declares permissions.
│                 # No `commands:` block on purpose — Brigadier handles registration.
├── config.yml    # Limits, warmup/cooldown, storage shutdown timeout, sounds
└── messages.yml  # All user-facing strings, MiniMessage format
```

## Invariants worth preserving

These are load-bearing; breaking one causes a subtle bug rather than a compile error.

- **Threading.** `PlayerHomes` instances in `HomeManager`'s cache are **main-thread only**.
  The storage thread only ever receives immutable `PlayerHomes.Snapshot`s and must never
  touch the Bukkit API. That's why `Home` stores the world as a *name* — it keeps `Home`
  serializable off-thread and the YAML hand-editable. Cost: renaming a world orphans its
  homes (documented in `config.yml`).
- **Storage I/O never uses the Bukkit scheduler.** Bukkit cancels queued tasks at disable,
  which would silently drop the final save. `YamlHomeStorage` owns a single-threaded
  executor and drains it in `shutdown()`.
- **Writes are atomic** (temp file + `ATOMIC_MOVE`), and write-through on every mutation.
  There is deliberately no dirty-set or periodic flush.
- **Config is never cached in fields.** All constructors run before any `init()`, so a
  cached value would be read from unloaded state — and `/haven reload` would not take effect.
  `Messages` and `Sounds` are the deliberate exceptions: they cache *parsed* forms and expose
  `reload()`, which `ReloadCommand` calls in order after `config.reload()`.
- **Never pass an explicit default to a Bukkit config getter.** `getInt(path, def)` *shadows*
  the defaults chain, so a key absent from an upgraded server's `config.yml` silently returns
  your literal instead of the jar's value. `HavenConfig.defined()` checks the file and the
  bundled defaults before reading. `getConfigurationSection` doesn't fall through at all —
  read scalar paths instead. This silently disabled every sound on upgraded installs once.
- **Teardown is keyed off what was *constructed*, not what was initialised.** A constructor can
  already own a resource (`YamlHomeStorage` starts its non-daemon I/O thread there), so if a
  later component fails to construct, that thread still has to be stopped or the JVM never
  exits and the server hangs on `/stop`.
- **MiniMessage does not expand placeholders inside a click tag's argument.** `<click:run_command:
  '/home <name>'>` keeps `<name>` verbatim. Hover arguments *are* re-parsed as components, so
  they're fine. Attach click actions in code (see `HomesCommand#chip`).
- **bStats chart callbacks run on bStats' own thread.** They must only read `volatile` snapshots
  refreshed by the main-thread task in `bStats#snapshot`, never `HomeManager`'s cache or the
  loaded config directly.
- **Pure logic stays extractable.** `HomeLimits.resolve(Map, int)` and
  `OverwriteConfirmations#confirmAt(uuid, name, now)` exist as parameterised entry points so the
  rules can be tested without a running server. Keep new decision logic similarly separable.
- **Command nodes are rebuilt inside the COMMANDS callback**, never cached: the registrar
  event fires again on `/minecraft:reload`. The handler itself must be registered during
  `onEnable` (i.e. in `start()`), the only phase Paper accepts it.
- **Cooldown is stamped only on a successful teleport**, never at command time, so a
  cancelled warmup costs the player nothing.
- **Home names are normalized with `Locale.ROOT`.** A bare `toLowerCase()` maps `I` to `ı`
  under `tr_TR` and desyncs the write path from the lookup path.

## Conventions worth preserving

- New cross-cutting features should be `LifecycleComponent`s registered/resolved through
  `Bootstrap`/`Registry` rather than wired ad hoc in `Haven.java`. Add them to the matching
  `initCore`/`initGameplay`/`initCommands`/`initListeners` group.
- `Registry` cannot construct an interface — bind it to an implementation with
  `registry.bind(Iface.class, Impl.class)` before anything depending on it resolves.
- Components declare dependencies via an `@Inject`-annotated constructor when there's more
  than one constructor to disambiguate.
- Listeners take `JavaPlugin` by constructor injection and self-register in `start()`.
- Every `shutdown()` must tolerate never having been `init()`ed — the rollback path calls it
  on components whose own `init()` threw.
- Console log lines use `COLORS` for the `[HAVEN]` prefix style established in `Haven.java`.
- User-facing text goes in `messages.yml` and is sent via `Messages#send`, never hardcoded.

## Testing

`./gradlew test` runs the suite (46 tests, no server required). `./gradlew runServer` starts a
Paper test server with the plugin loaded — console commands work over stdin, which is enough to
verify registration, console-sender handling and reload, but **not** anything player-facing.

Tests deliberately avoid a mocking framework, so anything requiring `Player`, `JavaPlugin` or the
Bukkit scheduler is verified by running the server instead. When adding logic worth testing,
extract it into a plain-data entry point rather than reaching for Mockito.

## Not built yet

GUI home list, home sharing/invites, `/back`, admin commands beyond reload, SQL backends,
per-world restrictions, teleport particles. The `HomeStorage` seam leaves room for these.
