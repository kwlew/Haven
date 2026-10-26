package dev.kwlew.haven.command;

import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.share.HomeSharing;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RenameHomeCommand extends PlayerCommand {

    private final HomeManager homeManager;
    private final HomeSharing sharing;
    private final OverwriteConfirmations confirmations;
    private final Sounds sounds;

    public RenameHomeCommand(Messages messages,
                             HomeManager homeManager,
                             HomeSharing sharing,
                             OverwriteConfirmations confirmations,
                             Sounds sounds) {
        super(messages);
        this.homeManager = homeManager;
        this.sharing = sharing;
        this.confirmations = confirmations;
        this.sounds = sounds;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return false;
        }
        run(sender, args[0], args[1]);
        return true;
    }

    private void run(CommandSender sender, String rawOld, String rawNew) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        String oldName = Home.normalize(rawOld);
        String newName = Home.normalize(rawNew);
        if (!Home.isValidName(newName)) {
            messages.send(player, "home.invalid-name");
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        PlayerHomes homes = homeManager.homesOf(player);
        if (!homes.has(oldName)) {
            messages.send(player, "home.not-found", Placeholder.unparsed("name", oldName));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        if (oldName.equals(newName)) {
            messages.send(player, "home.rename-same");
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        // Taken before the rename: the old entry is what still names the recipients to update.
        Home before = homes.get(oldName);

        // Renaming never overwrites: replacing another home would silently destroy it.
        if (!homes.rename(oldName, newName)) {
            messages.send(player, "home.rename-exists", Placeholder.unparsed("name", newName));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        // A pending overwrite may reference the old name.
        confirmations.clear(player.getUniqueId());
        homeManager.persist(homes);
        sharing.renameHints(player.getUniqueId(), player.getName(), before, newName);
        sounds.play(player, HavenSound.HOME_SET);

        messages.send(player, "home.renamed",
                Placeholder.unparsed("old", oldName),
                Placeholder.unparsed("name", newName));
    }
}
