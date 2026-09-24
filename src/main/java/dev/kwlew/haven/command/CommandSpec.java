package dev.kwlew.haven.command;

import java.util.List;

/** Metadata shared by Bukkit and Brigadier command registration. */
enum CommandSpec {
    SET_HOME("sethome", "Save a home at your current location.", "/sethome [name]",
            "haven.sethome", List.of()),
    HOME("home", "Teleport to one of your homes.", "/home [name]",
            "haven.home", List.of()),
    DEL_HOME("delhome", "Delete one of your homes.", "/delhome <name>",
            "haven.delhome", List.of("removehome")),
    HOMES("homes", "List your homes.", "/homes",
            "haven.homes", List.of("listhomes")),
    HAVEN("haven", "Haven administration.", "/haven reload",
            "haven.admin.reload", List.of());

    final String name;
    final String description;
    final String usage;
    final String permission;
    final List<String> aliases;

    CommandSpec(String name, String description, String usage, String permission,
                List<String> aliases) {
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.permission = permission;
        this.aliases = aliases;
    }
}
