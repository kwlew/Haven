package dev.kwlew.haven.managers.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class YamlMergeTest {

    private static final String BUNDLED_CONFIG = resource("/config.yml");
    private static final String OLD_CONFIG = resource("/migrations/config-1.yml");
    private static final String BUNDLED_MESSAGES = resource("/messages.yml");
    private static final String OLD_MESSAGES = resource("/migrations/messages-1.yml");

    @Test
    void untouchedOldConfigBecomesTheBundledOneExactly() {
        assertEquals(BUNDLED_CONFIG, merge(BUNDLED_CONFIG, OLD_CONFIG, OLD_CONFIG).text());
    }

    @Test
    void untouchedOldMessagesBecomeTheBundledOnesExactly() {
        assertEquals(BUNDLED_MESSAGES, merge(BUNDLED_MESSAGES, OLD_MESSAGES, OLD_MESSAGES).text());
    }

    @Test
    void customConfigValuesSurviveAndNewKeysAndCommentsAppear() throws Exception {
        String admin = OLD_CONFIG
                .replace("default-limit: 3", "default-limit: 7")
                .replace("warmup-seconds: 3", "warmup-seconds: 0")
                .replace("key: minecraft:entity.enderman.teleport", "key: minecraft:entity.player.levelup");

        String merged = merge(BUNDLED_CONFIG, OLD_CONFIG, admin).text();
        YamlConfiguration parsed = parse(merged);

        assertEquals(7, parsed.getInt("homes.default-limit"));
        assertEquals(0, parsed.getInt("teleport.warmup-seconds"));
        assertEquals("minecraft:entity.player.levelup", parsed.getString("sounds.teleport-success.key"));
        // Untouched neighbours keep the new defaults.
        assertEquals(10, parsed.getInt("teleport.cooldown-seconds"));
        assertEquals("minecraft:entity.experience_orb.pickup", parsed.getString("sounds.home-set.key"));
        // Everything new in this version is there, with its comments.
        assertTrue(parsed.isSet("worlds.block-teleport"));
        assertTrue(parsed.isSet("sharing.max-per-home"));
        assertTrue(parsed.isSet("gui.enabled"));
        assertTrue(merged.contains("# Max homes per world, on top of the overall limit."));
        assertEquals(2, parsed.getInt("config-version"));
        // Whatever comment the bundled file puts above the marker comes along with it.
        String markerComment = BUNDLED_CONFIG.lines()
                .takeWhile(line -> !line.startsWith("config-version:"))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertTrue(merged.contains(markerComment + "\nconfig-version: 2"));
    }

    @Test
    void customisedMessageSurvivesWhileUntouchedOnesAreRedesigned() {
        String custom = "<green>Saved <name>!";
        String admin = OLD_MESSAGES.replace(
                "set: \"<prefix><gray>Home <white><name></white> set. <dark_gray>(<count>/<max>)\"",
                "set: \"" + custom + "\"");
        assertNotEquals(OLD_MESSAGES, admin, "fixture must actually customise home.set");

        YamlConfiguration parsed = parse(merge(BUNDLED_MESSAGES, OLD_MESSAGES, admin).text());
        YamlConfiguration bundled = parse(BUNDLED_MESSAGES);

        assertEquals(custom, parsed.getString("home.set"));
        assertEquals(bundled.getString("home.list-header"), parsed.getString("home.list-header"));
        assertNotEquals(parse(OLD_MESSAGES).getString("home.list-header"), parsed.getString("home.list-header"));
        assertEquals(bundled.getStringList("gui.home-lore"), parsed.getStringList("gui.home-lore"));
    }

    @Test
    void unknownAdminOptionsAreDroppedAndReported() {
        String admin = OLD_CONFIG + "\nmystery:\n  option: 5\nhomes-extra: true\n";

        YamlMerge.Result result = merge(BUNDLED_CONFIG, OLD_CONFIG, admin);

        assertEquals(List.of("mystery", "homes-extra"), result.dropped());
        assertFalse(result.text().contains("mystery"));
        assertEquals(BUNDLED_CONFIG, result.text());
    }

    @Test
    void unknownOptionInsideAKnownSectionIsReportedWithItsFullPath() {
        String admin = OLD_CONFIG.replace("  default-limit: 3", "  default-limit: 3\n  bonus: 1");

        assertEquals(List.of("homes.bonus"), merge(BUNDLED_CONFIG, OLD_CONFIG, admin).dropped());
    }

    @Test
    void untouchedOptionsRemovedByThisVersionAreNotReported() {
        // OLD_MESSAGES uses keys the new file may no longer have; an unedited one is no news.
        assertEquals(List.of(), merge(BUNDLED_MESSAGES, OLD_MESSAGES, OLD_MESSAGES).dropped());
    }

    @Test
    void trickyMessageTextRoundTripsExactly() {
        String tricky = "<prefix><red>It's \"quoted\": <hover:show_text:'a: b'>x</hover> # not a comment, 50% {ok} [ok] &c";
        String longer = "<prefix><gray>" + "A long message that the YAML dumper would wrap across lines. ".repeat(4) + "end";
        String admin = OLD_MESSAGES
                .replaceFirst("(?m)^  not-found: .*$", "  not-found: \"placeholder\"");
        YamlConfiguration adminYaml = parse(admin);
        adminYaml.set("home.not-found", tricky);
        adminYaml.set("home.limit-reached", longer);

        YamlMerge.Result result = YamlMerge.merge(BUNDLED_MESSAGES, parse(OLD_MESSAGES), adminYaml);
        YamlConfiguration parsed = parse(result.text());

        assertEquals(tricky, parsed.getString("home.not-found"));
        assertEquals(longer, parsed.getString("home.limit-reached"));
        assertEquals(1, result.text().lines().filter(line -> line.startsWith("  limit-reached:")).count());
        assertTrue(result.applied().containsKey("home.not-found"));
    }

    @Test
    void customisedBlockListIsWrittenInPlace() {
        YamlConfiguration adminYaml = parse(OLD_MESSAGES);
        List<String> lore = List.of("<gray>Custom: <world>", "", "<yellow>Go: 'now'");
        adminYaml.set("gui.home-lore", lore);

        String merged = YamlMerge.merge(BUNDLED_MESSAGES, parse(OLD_MESSAGES), adminYaml).text();
        YamlConfiguration parsed = parse(merged);
        YamlConfiguration bundled = parse(BUNDLED_MESSAGES);

        assertEquals(lore, parsed.getStringList("gui.home-lore"));
        // The keys around the list are untouched.
        assertEquals(bundled.getString("gui.home-name"), parsed.getString("gui.home-name"));
        assertEquals(bundled.getStringList("gui.shared-lore"), parsed.getStringList("gui.shared-lore"));
        assertTrue(merged.contains("  home-lore:\n    - "));
    }

    @Test
    void customisedMapsAndListsInConfigAreWritten() {
        YamlConfiguration adminYaml = parse(OLD_CONFIG);
        adminYaml.set("worlds.disabled", List.of("creative", "my world"));
        adminYaml.set("worlds.limits.world_nether", 1);
        adminYaml.set("worlds.limits.world_the_end", 0);

        YamlMerge.Result result = YamlMerge.merge(BUNDLED_CONFIG, parse(OLD_CONFIG), adminYaml);
        YamlConfiguration parsed = parse(result.text());

        assertEquals(List.of("creative", "my world"), parsed.getStringList("worlds.disabled"));
        assertEquals(1, parsed.getInt("worlds.limits.world_nether"));
        assertEquals(0, parsed.getInt("worlds.limits.world_the_end"));
        assertFalse(result.text().contains("limits: {}"));
    }

    @Test
    void anAdminsOwnConfigVersionIsNeverCopied() {
        YamlConfiguration adminYaml = parse(OLD_CONFIG);
        adminYaml.set("config-version", 1);

        assertEquals(BUNDLED_CONFIG, YamlMerge.merge(BUNDLED_CONFIG, parse(OLD_CONFIG), adminYaml).text());
    }

    @Test
    void layoutsItCannotReadAreRejectedRatherThanGuessedAt() {
        assertThrows(IllegalArgumentException.class, () -> YamlMerge.merge(
                "a: |\n  text\n", parse(""), parse("a: x")));
        assertThrows(IllegalArgumentException.class, () -> YamlMerge.merge(
                "- stray\n", parse(""), parse("a: x")));
    }

    private static YamlMerge.Result merge(String bundled, String old, String admin) {
        return YamlMerge.merge(bundled, parse(old), parse(admin));
    }

    private static YamlConfiguration parse(String text) {
        YamlConfiguration yaml = new YamlConfiguration();

        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError("not valid YAML:\n" + text, e);
        }

        return yaml;
    }

    private static String resource(String path) {
        try (InputStream stream = Objects.requireNonNull(YamlMergeTest.class.getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
