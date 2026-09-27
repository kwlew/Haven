package dev.kwlew.haven.update;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthClientTest {

    @Test
    void choosesHighestNumericVersionRegardlessOfApiOrder() {
        String json = "[" + release("1.9.0") + "," + release("1.11.0") + "," + release("1.10.0") + "]";
        var update = ModrinthClient.findUpdate(json, "1.8.0", "1.18.2").orElseThrow();
        assertEquals("1.11.0", update.version());
        assertEquals("https://modrinth.com/plugin/khaven/version/abc12345", update.url());
    }

    @Test
    void equalOlderAndEmptyResultsDoNotAnnounceUpdates() {
        assertTrue(ModrinthClient.findUpdate("[" + release("1.0.0") + "]", "1.0.0", "1.18.2").isEmpty());
        assertTrue(ModrinthClient.findUpdate("[" + release("1.0.0") + "]", "1.1.0-SNAPSHOT", "1.18.2").isEmpty());
        assertTrue(ModrinthClient.findUpdate("[]", "1.0.0", "1.18.2").isEmpty());
    }

    @Test
    void stableReleaseReplacesItsOwnPrerelease() {
        assertTrue(ModrinthClient.findUpdate("[" + release("1.0.0") + "]",
                "1.0.0-rc.1", "1.18.2").isPresent());
    }

    @Test
    void normalizesPrefixesMissingPatchAndBuildMetadata() {
        assertTrue(ModrinthClient.findUpdate("[" + release("v1.0") + "]",
                "1.0.0+build.12", "1.18.2").isEmpty());
        assertTrue(ModrinthClient.findUpdate("[" + release("v1.1.0+build.2") + "]",
                "V1.0", "1.18.2").isPresent());
    }

    @Test
    void ignoresPrereleasesAndNonListedReleases() {
        for (String candidate : new String[]{
                release("2.0.0").replace("release", "beta"),
                release("2.0.0").replace("release", "alpha"),
                release("2.0.0").replace("listed", "archived"),
                release("2.0.0").replace("listed", "unlisted"),
                release("2.0.0-rc.1")}) {
            assertTrue(ModrinthClient.findUpdate("[" + candidate + "]", "1.0.0", "1.18.2").isEmpty());
        }
    }

    @Test
    void requiresExactMinecraftVersionAndPaperLoader() {
        assertTrue(ModrinthClient.findUpdate("[" + release("2.0.0") + "]", "1.0.0", "1.18.1").isEmpty());
        assertTrue(ModrinthClient.findUpdate("[" + release("2.0.0").replace("paper", "fabric") + "]",
                "1.0.0", "1.18.2").isEmpty());
    }

    @Test
    void skipsMalformedEntriesWithoutHidingValidUpdates() {
        String json = "[null,42,{}," + release("latest") + ","
                + release("3.0.0").replace("abc12345", "../bad") + "," + release("1.1.0") + "]";
        assertEquals("1.1.0", ModrinthClient.findUpdate(json, "1.0.0", "1.18.2").orElseThrow().version());
    }

    @Test
    void invalidPayloadsAndUnknownLocalVersionsAreFailuresRatherThanUpToDate() {
        assertThrows(RuntimeException.class, () -> ModrinthClient.findUpdate("not json", "1.0.0", "1.18.2"));
        assertThrows(RuntimeException.class, () -> ModrinthClient.findUpdate("{}", "1.0.0", "1.18.2"));
        assertThrows(IllegalArgumentException.class, () -> ModrinthClient.findUpdate("[]", "dev", "1.18.2"));
    }

    @Test
    void requestsOnlyPaperAndTheRunningMinecraftVersionWithoutChangelogs() {
        var uri = ModrinthClient.versionsUri("26.3");
        assertEquals("api.modrinth.com", uri.getHost());
        assertEquals("/v2/project/UcDctwM0/version", uri.getPath());
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(query.contains("loaders=[\"paper\"]"));
        assertTrue(query.contains("game_versions=[\"26.3\"]"));
        assertTrue(query.contains("include_changelog=false"));
    }

    private static String release(String version) {
        return """
                {"id":"abc12345","version_number":"%s","version_type":"release",
                 "status":"listed","loaders":["paper"],"game_versions":["1.18.2","26.3"]}
                """.formatted(version);
    }
}
