package dev.kwlew.haven.command;

import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DelHomeCommand extends PlayerCommand {

    private final HomeManager homeManager;
    private final OverwriteConfirmations confirmations;
    private final Sounds sounds;

    public DelHomeCommand(Messages messages,
                          HomeManager homeManager,
                          OverwriteConfirmations confirmations,
                          Sounds sounds) {
        super(messages);
        this.homeManager = homeManager;
        this.confirmations = confirmations;
        this.sounds = sounds;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return false;
        }
        run(sender, args[0]);
        return true;
    }

    private void run(CommandSender sender, String rawName) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        String name = Home.normalize(rawName);
        PlayerHomes homes = homeManager.homesOf(player);
        Home removed = homes.remove(name);

        if (removed == null) {
            messages.send(player, "home.not-found", Placeholder.unparsed("name", name));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        // A pending overwrite for a home that no longer exists would be misleading.
        confirmations.clear(player.getUniqueId());
        homeManager.persist(homes);
        sounds.play(player, HavenSound.HOME_DELETED);

        // Echo the coordinates so an accidental deletion can be recreated from chat history.
        messages.send(player, "home.deleted",
                Placeholder.unparsed("name", removed.name()),
                Placeholder.unparsed("world", removed.world()),
                Placeholder.unparsed("x", Integer.toString((int) Math.floor(removed.x()))),
                Placeholder.unparsed("y", Integer.toString((int) Math.floor(removed.y()))),
                Placeholder.unparsed("z", Integer.toString((int) Math.floor(removed.z()))));

    }
}
