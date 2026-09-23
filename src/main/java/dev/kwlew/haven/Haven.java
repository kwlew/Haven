package dev.kwlew.haven;

import dev.kwlew.haven.kernel.Bootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class Haven extends JavaPlugin {

    private long start;
    private Bootstrap bootstrap;

    @Override
    public void onEnable() {
        start = System.currentTimeMillis();
        if (!VersionSupport.supports(getServer().getBukkitVersion())) {
            getLogger().severe("Haven requires Paper 1.18.2 or newer; found "
                    + getServer().getBukkitVersion());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();

        try {
            bootstrap = new Bootstrap(this);
            bootstrap.init();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Haven failed to start and will be disabled.", t);

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logStartupTime();
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling Haven...");

        if (bootstrap != null) {
            bootstrap.shutdown();
        }
    }

    private void logStartupTime() {
        long time = System.currentTimeMillis() - start;

        getLogger().info("Haven enabled! (Took " + time + "ms)");
    }
}
