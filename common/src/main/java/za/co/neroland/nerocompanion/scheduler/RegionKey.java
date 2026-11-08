package za.co.neroland.nerocompanion.scheduler;

/**
 * An 8x8-chunk region in one dimension, the unit {@code maxActiveBotsPerChunkRegion} is counted in.
 *
 * @param dimension the dimension id as a string
 */
public record RegionKey(String dimension, int regionX, int regionZ) {

    /** Chunks per region edge. */
    public static final int CHUNKS = 8;

    public static RegionKey ofBlock(String dimension, int blockX, int blockZ) {
        return new RegionKey(dimension, Math.floorDiv(blockX >> 4, CHUNKS), Math.floorDiv(blockZ >> 4, CHUNKS));
    }
}
