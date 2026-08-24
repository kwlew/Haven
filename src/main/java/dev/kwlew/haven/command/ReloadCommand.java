package dev.kwlew.haven.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.Sounds;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Re-reads {@code config.yml} and {@code messages.yml}.
 * <p>
 * Only those two files: command nodes can't be re-registered outside enable, and re-creating
 * services would strand every live reference to them.
 */
public class ReloadCommand {

    private final JavaPlugin plugin;
    private final HavenConfig config;
    private final Messages messages;
    private final Sounds sounds;

    public ReloadCommand(JavaPlugin plugin, HavenConfig config, Messages messages, Sounds sounds) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.sounds = sounds;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("haven")
                .requires(source -> source.getSender().hasPermission("haven.admin.reload"))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            try {
                                // Config first: Messages and Sounds both read through it.
                                config.reload();
                                messages.reload();
                                sounds.reload();

                                messages.send(context.getSource().getSender(), "admin.reloaded");
                                return Command.SINGLE_SUCCESS;
                            } catch (RuntimeException e) {
                                plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
                                messages.send(context.getSource().getSender(), "admin.reload-failed");
                                return 0;
                            }
                        }))
                .build();
    }
}
