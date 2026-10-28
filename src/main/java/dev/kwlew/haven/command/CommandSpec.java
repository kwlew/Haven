package dev.kwlew.haven.command;

import org.bukkit.command.CommandSender;

import java.util.List;

enum CommandSpec {
    SET_HOME("sethome", "Save a home at your current location.", "/sethome [name]",
            "haven.sethome", List.of()),
    HOME("home", "Teleport to one of your homes, or one shared with you.", "/home [name]",
            "haven.home", List.of()),
    DEL_HOME("delhome", "Delete one of your homes.", "/delhome <name>",
            "haven.delhome", List.of("removehome")),
    RENAME_HOME("renamehome", "Rename one of your homes.", "/renamehome <old> <new>",
            "haven.renamehome", List.of()),
    SHARE_HOME("sharehome", "Share a home with another player, or list who it is shared with.",
            "/sharehome <home> [player]", "haven.share", List.of()),
    UNSHARE_HOME("unsharehome", "Stop sharing a home with a player.", "/unsharehome <home> <player>",
            "haven.share", List.of()),
    HOMES("homes", "Open or list your homes.", "/homes [list]",
            "haven.homes", List.of("listhomes")),
    // No permission: help is open to everyone, so the root can't be gated; subcommands check their own nodes.
    HAVEN("haven", "Open your homes menu, get help, or use Haven's admin tools.",
            "/haven [help|version|reload|homes|tp|delhome]", null, List.of());

    final String name;
    final String description;
    final String usage;
    /** Nodes separated by {@code ;}, any of which grants access; null for everyone. */
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

    boolean permits(CommandSender sender) {
        if (permission == null) {
            return true;
        }
        for (String node : permission.split(";")) {
            if (sender.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
