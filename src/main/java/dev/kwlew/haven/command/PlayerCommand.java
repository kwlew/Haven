package dev.kwlew.haven.command;

import dev.kwlew.haven.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shared plumbing for commands that only make sense for a player.
 */
public abstract class PlayerCommand {

    protected final Messages messages;

    protected PlayerCommand(Messages messages) {
        this.messages = messages;
    }

    /**
     * Returns the sender as a player, or {@code null} after messaging a non-player sender. Console
     * reaching these commands is expected, not exceptional - it must never throw.
     */
    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }

        messages.send(sender, "general.players-only");
        return null;
    }
}
