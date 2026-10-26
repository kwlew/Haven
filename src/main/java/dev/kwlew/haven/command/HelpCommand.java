package dev.kwlew.haven.command;

import dev.kwlew.haven.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * {@code /haven help}: the commands the sender may actually run.
 * <p>
 * Open to everyone, so it can only ever list what the sender would be allowed to type - showing a
 * command that answers "no permission" is noise, and showing every admin command to every player
 * advertises tools they cannot use.
 */
public class HelpCommand {

    /**
     * One line of help.
     *
     * @param usage       shown as the command, e.g. {@code /home [name]}
     * @param suggestion  what clicking it puts in the chat box: the command up to its arguments
     * @param permission  node(s) separated by {@code ;}, any of which grants it; {@code null} for everyone
     */
    record Entry(String usage, String suggestion, String description, String permission) {

        static Entry of(CommandSpec spec) {
            return new Entry(spec.usage, "/" + spec.name + " ", spec.description, spec.permission);
        }

        boolean visibleTo(Predicate<String> has) {
            if (permission == null || permission.isEmpty()) {
                return true;
            }
            for (String node : permission.split(";")) {
                if (has.test(node)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Every Haven command in the order they are listed. The player commands take their wording from
     * {@link CommandSpec} so help can't drift from what is registered; the {@code /haven}
     * subcommands are not separate registrations, so they are described here.
     */
    static final List<Entry> ENTRIES = List.of(
            Entry.of(CommandSpec.HOME),
            Entry.of(CommandSpec.SET_HOME),
            Entry.of(CommandSpec.DEL_HOME),
            Entry.of(CommandSpec.RENAME_HOME),
            Entry.of(CommandSpec.SHARE_HOME),
            Entry.of(CommandSpec.UNSHARE_HOME),
            Entry.of(CommandSpec.HOMES),
            new Entry("/haven help", "/haven help", "Show this list.", null),
            new Entry("/haven version", "/haven version", "Show the Haven and server versions.",
                    AdminCommand.VERSION_PERMISSION),
            new Entry("/haven reload", "/haven reload", "Reload the configuration and messages.",
                    ReloadCommand.PERMISSION),
            new Entry("/haven homes <player>", "/haven homes ", "List a player's homes.",
                    AdminCommand.HOMES_PERMISSION),
            new Entry("/haven tp <player> <home>", "/haven tp ", "Teleport to a player's home.",
                    AdminCommand.TP_PERMISSION),
            new Entry("/haven delhome <player> <home>", "/haven delhome ", "Delete a player's home.",
                    AdminCommand.DELHOME_PERMISSION));

    /** The entries a holder of the given permissions may use, in listing order. */
    static List<Entry> visible(List<Entry> entries, Predicate<String> has) {
        List<Entry> shown = new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.visibleTo(has)) {
                shown.add(entry);
            }
        }

        return shown;
    }

    private final Messages messages;

    public HelpCommand(Messages messages) {
        this.messages = messages;
    }

    public void execute(CommandSender sender) {
        messages.send(sender, "help.header");

        for (Entry entry : visible(ENTRIES, sender::hasPermission)) {
            // The click is attached here, not in messages.yml: MiniMessage doesn't expand
            // placeholders inside a click tag's argument. Hover text stays configurable.
            messages.send(sender, "help.entry",
                    Placeholder.component("command", Component.text(entry.usage())
                            .clickEvent(ClickEvent.suggestCommand(entry.suggestion()))),
                    Placeholder.unparsed("description", entry.description()));
        }

        messages.send(sender, "help.footer");
    }
}
