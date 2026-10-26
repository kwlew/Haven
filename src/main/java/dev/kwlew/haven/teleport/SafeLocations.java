package dev.kwlew.haven.teleport;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether a player can stand at a block position and, if not, finds the nearest spot
 * where they can.
 * <p>
 * Everything works against {@link BlockView} rather than a {@link World}, so the search is plain
 * logic that tests can drive with an in-memory map. It is deliberately a coarse check on block
 * types - it does not model slabs, stairs or fluid levels - because the aim is to refuse lava,
 * void and suffocation, not to prove a spot is comfortable.
 */
public final class SafeLocations {

    /** Blocks that hurt or trap a player who stands in, or on, them. */
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

    /** Fractions of a block at or below this count as a whole-number Y. */
    private static final double PARTIAL_EPSILON = 1e-6;

    private SafeLocations() {
    }

    /**
     * A block position found by {@link #findSafe}.
     */
    public record Spot(int x, int y, int z) {
    }

    /**
     * Read-only access to the blocks around a destination.
     */
    public interface BlockView {

        Material type(int x, int y, int z);

        /** Lowest valid block Y. */
        int minY();

        /** Exclusive upper bound for block Y. */
        int maxY();

        /** True when the chunk holding this column is loaded. */
        boolean isLoaded(int x, int z);

        /**
         * Whether a material blocks movement. A hook so tests need not touch the server-backed
         * {@link Material#isSolid()}.
         */
        default boolean isSolid(Material material) {
            return material.isSolid();
        }
    }

    /**
     * A {@link BlockView} over a live world. Main thread only, and only for loaded chunks:
     * {@code getBlockAt} would otherwise load them synchronously.
     */
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

    /**
     * True when a player can stand with their feet in block {@code y}: room for feet and head,
     * nothing harmful in either, and firm (or water) ground below.
     */
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
     * Like {@link #isSafe}, but for an exact saved Y. A home saved on a partial-height block
     * (slab, bed, farmland, snow layer...) has a fractional Y inside that block, so the block at
     * {@code floor(y)} is the ground the player is standing on, not their feet. In that case the
     * spot is safe when the two blocks above it are free and harmless.
     * <p>
     * A hazardous block there (a campfire, say) never counts as ground, and whole-number Ys behave
     * exactly as {@link #isSafe}.
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
     * The original position if it is safe, otherwise the closest safe one: columns are tried ring
     * by ring outwards (Chebyshev distance, starting with the original column), and within a
     * column by smallest vertical offset, preferring up over down.
     * Columns in unloaded chunks are skipped.
     *
     * @param radius   horizontal search distance in blocks
     * @param vertical how far up and down to look in each column
     */
    public static Optional<Spot> findSafe(BlockView view, int x, int y, int z, int radius, int vertical) {
        // Ring 0 is the original column itself, so a home buried in a wall is lifted out of it
        // before any sideways move is considered.
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
