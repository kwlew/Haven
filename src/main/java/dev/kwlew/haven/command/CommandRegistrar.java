package dev.kwlew.haven.command;

import org.bukkit.plugin.java.JavaPlugin;

/** Bridge between the Java 17 core and the Java 21 Brigadier adapter. */
public interface CommandRegistrar {
    void register(JavaPlugin plugin, SetHomeCommand setHome, HomeCommand home,
                  DelHomeCommand delHome, HomesCommand homes, ReloadCommand reload,
                  HomeSuggestions suggestions);
}
