package dev.kwlew.haven.command;

import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeLimits;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.home.WorldNames;
import dev.kwlew.haven.home.WorldRules;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.OptionalInt;

public class SetHomeCommand extends PlayerCommand {

    /** Name used when /sethome is run with no argument. */
    public static final String DEFAULT_HOME = "home";

    private final HavenConfig config;
    private final HomeManager homeManager;
    private final HomeLimits limits;
    private final WorldRules worldRules;
    private final OverwriteConfirmations confirmations;
    private final Sounds sounds;

    public SetHomeCommand(Messages messages,
                          HavenConfig config,
                          HomeManager homeManager,
                          HomeLimits limits,
                          WorldRules worldRules,
                          OverwriteConfirmations confirmations,
                          Sounds sounds) {
        super(messages);
        this.config = config;
        this.homeManager = homeManager;
        this.limits = limits;
        this.worldRules = worldRules;
        this.confirmations = confirmations;
        this.sounds = sounds;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 1) {
            return false;
        }
        run(sender, args.length == 0 ? DEFAULT_HOME : args[0]);
        return true;
    }

    private void run(CommandSender sender, String rawName) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        String name = Home.normalize(rawName);
        if (!Home.isValidName(name)) {
            messages.send(player, "home.invalid-name");
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        String world = player.getWorld().getName();

        if (!worldRules.sethomeAllowed(player, world)) {
            messages.send(player, "home.world-disabled",
                    Placeholder.unparsed("world", WorldNames.displayName(messages, world)));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        PlayerHomes homes = homeManager.homesOf(player);
        int limit = limits.limitFor(player);
        boolean exists = homes.has(name);

        // Only new homes count against the limit, so a player over a lowered cap can still move
        // the homes they already have.
        if (!exists && homes.count() >= limit) {
            messages.send(player, "home.limit-reached",
                    Placeholder.unparsed("count", Integer.toString(homes.count())),
                    Placeholder.unparsed("max", limits.describe(limit)));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        // Before the confirmation, so nobody is asked to confirm a move that is then refused. Runs
        // again on the confirming call, since the config can be reloaded in between.
        OptionalInt worldCap = worldRules.capFor(player, world);

        if (WorldRules.exceedsCap(worldCap, homes.all(), name, world)) {
            messages.send(player, "home.world-limit-reached",
                    Placeholder.unparsed("world", WorldNames.displayName(messages, world)),
                    Placeholder.unparsed("count",
                            Integer.toString(WorldRules.countIn(homes.all(), name, world))),
                    Placeholder.unparsed("max", Integer.toString(worldCap.getAsInt())));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        if (exists && !confirmations.confirm(player.getUniqueId(), name)) {
            messages.send(player, "home.overwrite-confirm",
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("seconds", Integer.toString(config.confirmTimeoutSeconds())));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        Home placed = Home.of(name, player.getLocation());
        Home existing = homes.get(name);

        // Moving a home keeps who it is shared with; the recipients' hints stay valid.
        homes.put(existing == null ? placed : existing.movedTo(placed));
        homeManager.persist(homes);
        sounds.play(player, HavenSound.HOME_SET);

        messages.send(player, exists ? "home.overwritten" : "home.set",
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("count", Integer.toString(homes.count())),
                Placeholder.unparsed("max", limits.describe(limit)));

    }
}
