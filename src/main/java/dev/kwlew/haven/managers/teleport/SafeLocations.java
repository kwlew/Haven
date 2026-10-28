package dev.kwlew.haven.managers.teleport;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the nearest standable spot. Works against {@link BlockView} so tests can use an in-memory map; the check
 * is coarse (block types only) and aims to refuse lava, void and suffocation.
 */
public final class SafeLocations {

    private static final Set<Material> HAZARDS = EnumSet.of(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.MAGMA_BLOCK,
            Material.CACTUS,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE,
            Material.POWDER_SNOW,
            Material.POINTED_DRIPSTONE,
            Material.END_PORTAL,
            Material.NETHER_PORTAL,
            Material.COBWEB);

    private static final double PARTIAL_EPSILON = 1e-6;

    private SafeLocations() {
    }

    public record Spot(int x, int y, int z) {
    }

    public interface BlockView {

        Material type(int x, int y, int z);

        int minY();

        int maxY();

        boolean isLoaded(int x, int z);

        /** Hook so tests need not touch the server-backed {@link Material#isSolid()}. */
        default boolean isSolid(Material material) {
            return material.isSolid();
        }
    }

    /** Main thread only, loaded chunks only: {@code getBlockAt} would otherwise load them synchronously. */
    public static BlockView of(World world) {
        return new BlockView() {
            @Override
            public Material type(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType();
            }

            @Override
            public int minY() {
                return world.getMinHeight();
            }

            @Override
            public int maxY() {
                return world.getMaxHeight();
            }

            @Override
            public boolean isLoaded(int x, int z) {
                return world.isChunkLoaded(x >> 4, z >> 4);
            }
        };
    }

    public static boolean isSafe(BlockView view, int x, int y, int z) {
        if (y - 1 < view.minY() || y + 1 >= view.maxY()) {
            return false;
        }

        Material feet = view.type(x, y, z);
        Material head = view.type(x, y + 1, z);
        Material ground = view.type(x, y - 1, z);

        if (!passable(view, feet) || !passable(view, head)) {
            return false;
        }
        if (head == Material.WATER) {
            return false;
        }

        return ground == Material.WATER || (view.isSolid(ground) && !HAZARDS.contains(ground));
    }

    /**
     * Like {@link #isSafe} for an exact saved Y: on a partial-height block (slab, bed...) the block at
     * {@code floor(y)} is the ground, not the feet.
     */
    public static boolean isSafeStanding(BlockView view, int x, double y, int z) {
        int block = (int) Math.floor(y);

        if (!view.isLoaded(x, z)) {
            return false;
        }

        Material inside = view.type(x, block, z);
        boolean onPartial = y - block > PARTIAL_EPSILON
                && view.isSolid(inside) && !HAZARDS.contains(inside);

        if (!onPartial) {
            return isSafe(view, x, block, z);
        }
        if (block < view.minY() || block + 2 >= view.maxY()) {
            return false;
        }

        Material body = view.type(x, block + 1, z);
        Material head = view.type(x, block + 2, z);

        return passable(view, body) && passable(view, head) && head != Material.WATER;
    }

    /**
     * Rings outward from the original column (Chebyshev distance), then smallest vertical offset, up before down.
     * Skips unloaded chunks.
     */
    public static Optional<Spot> findSafe(BlockView view, int x, int y, int z, int radius, int vertical) {
        // Ring 0 first, so a home buried in a wall is lifted out before any sideways move.
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }

                    Optional<Spot> found = searchColumn(view, x + dx, y, z + dz, vertical);
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<Spot> searchColumn(BlockView view, int x, int y, int z, int vertical) {
        if (!view.isLoaded(x, z)) {
            return Optional.empty();
        }

        if (isSafe(view, x, y, z)) {
            return Optional.of(new Spot(x, y, z));
        }

        for (int offset = 1; offset <= vertical; offset++) {
            if (isSafe(view, x, y + offset, z)) {
                return Optional.of(new Spot(x, y + offset, z));
            }
            if (isSafe(view, x, y - offset, z)) {
                return Optional.of(new Spot(x, y - offset, z));
            }
        }

        return Optional.empty();
    }

    private static boolean passable(BlockView view, Material material) {
        return !view.isSolid(material) && !HAZARDS.contains(material);
    }
}
