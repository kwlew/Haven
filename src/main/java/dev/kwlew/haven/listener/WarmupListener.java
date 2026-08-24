package dev.kwlew.haven.listener;

import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.teleport.TeleportService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cancels a pending teleport when the player takes damage or dies.
 * <p>
 * Movement is <em>not</em> handled here - {@link TeleportService} samples position in its warmup
 * tick instead, which also covers vehicles.
 */
public class WarmupListener implements Listener, LifecycleComponent {

    private final JavaPlugin plugin;
    private final TeleportService teleportService;

    public WarmupListener(JavaPlugin plugin, TeleportService teleportService) {
        this.plugin = plugin;
        this.teleportService = teleportService;
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Damage fully absorbed by a shield or invulnerability shouldn't punish the player.
        if (event.getFinalDamage() <= 0.0D) {
            return;
        }

        teleportService.cancelForDamage(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        teleportService.cancelSilently(event.getEntity().getUniqueId());
    }
}
