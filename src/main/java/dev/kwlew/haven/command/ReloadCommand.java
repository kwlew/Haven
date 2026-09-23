package dev.kwlew.haven.command;

import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.Sounds;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Re-reads {@code config.yml} and {@code messages.yml}.
 * <p>
 * Re-creating services would strand every live reference to them.
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

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            return false;
        }
        try {
            config.reload();
            messages.reload();
            sounds.reload();
            messages.send(sender, "admin.reloaded");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
            messages.send(sender, "admin.reload-failed");
        }
        return true;
    }
}
