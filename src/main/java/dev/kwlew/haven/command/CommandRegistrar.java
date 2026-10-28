package dev.kwlew.haven.command;

import org.bukkit.plugin.java.JavaPlugin;

public interface CommandRegistrar {
    void register(JavaPlugin plugin, SetHomeCommand setHome, HomeCommand home,
                  DelHomeCommand delHome, RenameHomeCommand renameHome,
                  ShareHomeCommand shareHome, UnshareHomeCommand unshareHome, HomesCommand homes,
                  HavenCommand haven, HomeSuggestions suggestions);
}
