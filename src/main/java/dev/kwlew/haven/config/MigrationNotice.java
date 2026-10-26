package dev.kwlew.haven.config;

import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tells admins in game when {@link FileMigrator} couldn't update a file, since the console
 * warning is easy to miss.
 * <p>
 * A separate component because {@link FileMigrator} has to run before {@link Messages} exists and
 * so can't depend on it. Each admin hears about it once per session rather than on every join.
 */
public class MigrationNotice implements LifecycleComponent, Listener {

    private final JavaPlugin plugin;
    private final FileMigrator migrator;
    private final Messages messages;
    private final Set<UUID> told = new HashSet<>();

    public MigrationNotice(JavaPlugin plugin, FileMigrator migrator, Messages messages) {
        this.plugin = plugin;
        this.migrator = migrator;
        this.messages = messages;
    }

    @Override
    public void start() {
        if (!migrator.failedFiles().isEmpty()) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("haven.admin.reload") || !told.add(player.getUniqueId())) {
            return;
        }

        for (String file : migrator.failedFiles()) {
            messages.send(player, "admin.migration-failed", Placeholder.unparsed("file", file));
        }
    }

    @Override
    public void shutdown() {
        HandlerList.unregisterAll(this);
        told.clear();
    }
}
