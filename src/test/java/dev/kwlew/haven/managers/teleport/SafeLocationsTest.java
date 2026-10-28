package dev.kwlew.haven.managers.teleport;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeLocationsTest {

    /** Stand-ins for {@link Material#isSolid()}, which needs a running server. */
    private static final Set<Material> SOLID = EnumSet.of(
            Material.STONE, Material.DIRT, Material.OBSIDIAN, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.OAK_SLAB, Material.CAMPFIRE);

    private FakeWorld world;

    @BeforeEach
    void setUp() {
        world = new FakeWorld();
        // Flat stone floor at y=63, so y=64 is the standing level.
        world.fill(-10, 10, 63, Material.STONE);
    }

    @Test
    void flatGroundIsSafe() {
        assertTrue(SafeLocations.isSafe(world, 0, 64, 0));
    }

    @Test
    void lavaAtFeetIsUnsafe() {
        world.set(0, 64, 0, Material.LAVA);

        assertFalse(SafeLocations.isSafe(world, 0, 64, 0));
    }

    @Test
    void voidBelowTheWorldIsUnsafe() {
        // Ground would be below minY.
        assertFalse(SafeLocations.isSafe(world, 0, world.minY(), 0));
        // And with nothing under the feet at all.
        assertFalse(SafeLocations.isSafe(world, 0, 20, 0));
    }

    @Test
    void solidBlockAtHeadSuffocates() {
        world.set(0, 65, 0, Material.STONE);

        assertFalse(SafeLocations.isSafe(world, 0, 64, 0));
    }

    @Test
    void lavaGroundIsUnsafe() {
        world.set(0, 63, 0, Material.LAVA);

        assertFalse(SafeLocations.isSafe(world, 0, 64, 0));
    }

    @Test
    void hazardousSolidGroundIsUnsafe() {
        world.set(0, 63, 0, Material.MAGMA_BLOCK);

        assertFalse(SafeLocations.isSafe(world, 0, 64, 0));
    }

    @Test
    void waterGroundIsOk() {
        world.set(0, 63, 0, Material.WATER);

        assertTrue(SafeLocations.isSafe(world, 0, 64, 0));
    }

    @Test
    void waterAtHeadIsUnsafe() {
        world.set(0, 65, 0, Material.WATER);

        assertFalse(SafeLocations.isSafe(world, 0, 64, 0));
    }

    @Test
    void safeOriginalIsReturnedUnchanged() {
        assertEquals(Optional.of(new SafeLocations.Spot(0, 64, 0)),
                SafeLocations.findSafe(world, 0, 64, 0, 3, 4));
    }

    @Test
    void findsTheNearestSpotOneBlockSideways() {
        world.set(0, 64, 0, Material.LAVA);

        Optional<SafeLocations.Spot> found = SafeLocations.findSafe(world, 0, 64, 0, 3, 4);

        assertTrue(found.isPresent());
        SafeLocations.Spot spot = found.get();
        assertEquals(64, spot.y());
        assertEquals(1, Math.max(Math.abs(spot.x()), Math.abs(spot.z())));
    }

    @Test
    void findsASpotAboveATwoBlockTallWall() {
        // Home inside a stone pillar at (0, 64..65) with a pillar around it as well.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.set(dx, 64, dz, Material.STONE);
                world.set(dx, 65, dz, Material.STONE);
            }
        }

        Optional<SafeLocations.Spot> found = SafeLocations.findSafe(world, 0, 64, 0, 1, 4);

        // The only safe cell within radius 1 is on top of the wall.
        assertEquals(Optional.of(new SafeLocations.Spot(0, 66, 0)), found);
    }

    @Test
    void emptyWhenNothingIsSafeWithinRadius() {
        world.fill(-10, 10, 63, Material.LAVA);

        assertTrue(SafeLocations.findSafe(world, 0, 64, 0, 3, 4).isEmpty());
    }

    @Test
    void radiusZeroOnlyChecksTheExactSpot() {
        world.set(0, 64, 0, Material.LAVA);

        assertTrue(SafeLocations.findSafe(world, 0, 64, 0, 0, 0).isEmpty());
    }

    @Test
    void skipsUnloadedColumns() {
        world.set(0, 64, 0, Material.LAVA);
        // Everything at distance 1 is unloaded except one column to the south-east.
        world.unload(-1, -1);
        world.unload(0, -1);
        world.unload(1, -1);
        world.unload(-1, 0);
        world.unload(1, 0);
        world.unload(-1, 1);
        world.unload(0, 1);

        Optional<SafeLocations.Spot> found = SafeLocations.findSafe(world, 0, 64, 0, 3, 4);

        assertEquals(Optional.of(new SafeLocations.Spot(1, 64, 1)), found);
    }

    @Test
    void allUnloadedMeansNothingFound() {
        world.unloadAll();

        assertTrue(SafeLocations.findSafe(world, 0, 64, 0, 3, 4).isEmpty());
    }

    @Test
    void homeOnAPartialBlockIsSafeAsSaved() {
        world.set(0, 64, 0, Material.OAK_SLAB);

        assertTrue(SafeLocations.isSafeStanding(world, 0, 64.5, 0));
    }

    @Test
    void homeOnAPartialBlockNeedsHeadRoom() {
        world.set(0, 64, 0, Material.OAK_SLAB);
        world.set(0, 66, 0, Material.STONE);

        assertFalse(SafeLocations.isSafeStanding(world, 0, 64.5, 0));
    }

    @Test
    void homeOnAPartialBlockStillRefusesHazardsAbove() {
        world.set(0, 64, 0, Material.OAK_SLAB);
        world.set(0, 65, 0, Material.LAVA);

        assertFalse(SafeLocations.isSafeStanding(world, 0, 64.5, 0));
    }

    @Test
    void hazardousPartialBlockIsUnsafe() {
        world.set(0, 64, 0, Material.CAMPFIRE);

        assertFalse(SafeLocations.isSafeStanding(world, 0, 64.4375, 0));
    }

    @Test
    void wholeNumberYBehavesLikeIsSafe() {
        assertTrue(SafeLocations.isSafeStanding(world, 0, 64.0, 0));

        world.set(0, 64, 0, Material.STONE);

        assertFalse(SafeLocations.isSafeStanding(world, 0, 64.0, 0));
    }

    private static final class FakeWorld implements SafeLocations.BlockView {

        private final Map<Pos, Material> blocks = new HashMap<>();
        private final Set<List<Integer>> unloaded = new HashSet<>();
        private boolean allUnloaded;

        void set(int x, int y, int z, Material material) {
            blocks.put(new Pos(x, y, z), material);
        }

        /** Fills a square of columns, from {@code min} to {@code max} on both axes, at one Y. */
        void fill(int min, int max, int y, Material material) {
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    set(x, y, z, material);
                }
            }
        }

        void unload(int x, int z) {
            unloaded.add(List.of(x, z));
        }

        void unloadAll() {
            allUnloaded = true;
        }

        @Override
        public Material type(int x, int y, int z) {
            return blocks.getOrDefault(new Pos(x, y, z), Material.AIR);
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }

        @Override
        public boolean isLoaded(int x, int z) {
            return !allUnloaded && !unloaded.contains(List.of(x, z));
        }

        @Override
        public boolean isSolid(Material material) {
            return SOLID.contains(material);
        }
    }

    private record Pos(int x, int y, int z) {
    }
}
