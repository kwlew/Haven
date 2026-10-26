package dev.kwlew.haven.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpCommandTest {

    private static List<String> usages(Set<String> granted) {
        return HelpCommand.visible(HelpCommand.ENTRIES, granted::contains).stream()
                .map(HelpCommand.Entry::usage)
                .toList();
    }

    @Test
    void senderWithNoPermissionsOnlySeesHelp() {
        assertEquals(List.of("/haven help"), usages(Set.of()));
    }

    @Test
    void playerCommandsAppearWithTheirOwnNode() {
        List<String> shown = usages(Set.of("haven.home", "haven.renamehome"));

        assertTrue(shown.contains(CommandSpec.HOME.usage));
        assertTrue(shown.contains(CommandSpec.RENAME_HOME.usage));
        assertFalse(shown.contains(CommandSpec.SET_HOME.usage));
    }

    @Test
    void sharingCommandsShareOneNode() {
        List<String> shown = usages(Set.of("haven.share"));

        assertTrue(shown.contains(CommandSpec.SHARE_HOME.usage));
        assertTrue(shown.contains(CommandSpec.UNSHARE_HOME.usage));
        assertFalse(shown.contains(CommandSpec.HOME.usage));
    }

    @Test
    void adminSubcommandsAreGatedIndividually() {
        List<String> shown = usages(Set.of(AdminCommand.TP_PERMISSION));

        assertTrue(shown.contains("/haven tp <player> <home>"));
        assertFalse(shown.contains("/haven delhome <player> <home>"));
        assertFalse(shown.contains("/haven homes <player>"));
        assertFalse(shown.contains("/haven reload"));
        assertFalse(shown.contains("/haven version"));
    }

    @Test
    void everyPermissionShowsEveryEntry() {
        assertEquals(HelpCommand.ENTRIES.size(), HelpCommand.visible(HelpCommand.ENTRIES, node -> true).size());
    }

    @Test
    void anyOfPermissionNodesGrantsAnEntry() {
        HelpCommand.Entry entry = new HelpCommand.Entry("/x", "/x ", "x", "a.one;a.two");

        assertTrue(entry.visibleTo("a.two"::equals));
        assertFalse(entry.visibleTo("a.three"::equals));
    }

    @Test
    void suggestionsAreTheCommandWithoutItsArguments() {
        for (HelpCommand.Entry entry : HelpCommand.ENTRIES) {
            assertTrue(entry.usage().startsWith(entry.suggestion().trim()), entry.usage());
        }
    }

    @Test
    void rootIsOpenToEveryone() {
        assertEquals(null, CommandSpec.HAVEN.permission);
    }
}
