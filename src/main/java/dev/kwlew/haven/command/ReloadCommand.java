package dev.kwlew.haven.command;

import dev.kwlew.haven.managers.config.HavenConfig;
import dev.kwlew.haven.managers.gui.HomesMenus;
import dev.kwlew.haven.managers.home.HomeLimits;
import dev.kwlew.haven.managers.message.Messages;
import dev.kwlew.haven.managers.sound.Sounds;
import dev.kwlew.haven.managers.update.UpdateChecker;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class ReloadCommand {

    public static final String PERMISSION = "haven.admin.reload";

    private final JavaPlugin plugin;
    private final HavenConfig config;
    private final Messages messages;
    private final Sounds sounds;
    private final UpdateChecker updates;
    private final HomesMenus menus;
    private final HomeLimits limits;

    public ReloadCommand(JavaPlugin plugin, HavenConfig config, Messages messages, Sounds sounds,
                         UpdateChecker updates, HomesMenus menus, HomeLimits limits) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.sounds = sounds;
        this.updates = updates;
        this.menus = menus;
        this.limits = limits;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            return false;
        }
        try {
            config.reload();
            limits.clear();
            messages.reload();
            sounds.reload();
            updates.reload();
            // A menu just switched off must not stay open.
            menus.closeAllIfDisabled();
            messages.send(sender, "admin.reloaded");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
            messages.send(sender, "admin.reload-failed");
        }
        return true;
    }
}
