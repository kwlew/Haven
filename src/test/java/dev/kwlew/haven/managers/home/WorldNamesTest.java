package dev.kwlew.haven.managers.home;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldNamesTest {

    @Test
    void prettifiesWorldFolderNames() {
        assertEquals("Resource World", WorldNames.prettify("resource_world"));
        assertEquals("My World", WorldNames.prettify("my-world"));
        assertEquals("World", WorldNames.prettify("world"));
    }

    @Test
    void groupsByWorldNameThenHomeName() {
        PlayerHomes homes = new PlayerHomes(UUID.randomUUID(), "tester");
        homes.put(new Home("zeta", "world", 0, 0, 0, 0, 0, 0));
        homes.put(new Home("alpha", "world_nether", 0, 0, 0, 0, 0, 0));
        homes.put(new Home("beta", "world", 0, 0, 0, 0, 0, 0));

        Map<String, List<Home>> grouped = WorldNames.groupByWorld(homes);

        assertEquals(List.of("world", "world_nether"), List.copyOf(grouped.keySet()));
        assertEquals(List.of("beta", "zeta"), grouped.get("world").stream().map(Home::name).toList());
    }
}
