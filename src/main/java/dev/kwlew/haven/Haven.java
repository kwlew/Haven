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
        saveDefaultConfig();

        try {
            bootstrap = new Bootstrap(this);
            bootstrap.init();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, COLORS.ANSI_RED
                    + "Haven failed to start and will be disabled." + COLORS.ANSI_RESET, t);

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logStartupTime();
    }

    @Override
    public void onDisable() {
        getLogger().info(COLORS.ANSI_CYAN + "Disabling Haven..." + COLORS.ANSI_RESET);

        if (bootstrap != null) {
            bootstrap.shutdown();
        }
    }

    private void logStartupTime() {
        long time = System.currentTimeMillis() - start;

        getLogger().info(COLORS.ANSI_CYAN + "Haven enabled! " + COLORS.ANSI_WHITE + "(Took "
                + COLORS.ANSI_GREEN + time + "ms" + COLORS.ANSI_WHITE + ")" + COLORS.ANSI_RESET);
    }
}
