package dev.kwlew.haven.command;

import dev.kwlew.haven.managers.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public abstract class PlayerCommand {

    protected final Messages messages;

    protected PlayerCommand(Messages messages) {
        this.messages = messages;
    }

    /** Null after messaging a non-player sender; console reaching these commands is expected. */
    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }

        messages.send(sender, "general.players-only");
        return null;
    }
}
