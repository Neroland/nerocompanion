package za.co.neroland.nerocompanion.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * A mining job's box, inclusive on both corners, never larger than the server's {@code mining.maxJobSize} cube.
 * Pure (plain-JVM safe). Blocks are visited top layer first so nothing is undermined, then row by row.
 */
public record MineArea(BlockPos min, BlockPos max) {

    /** Hard ceiling on {@code mining.maxJobSize}, whatever the config says. */
    public static final int HARD_MAX_EDGE = 16;

    /** Refuses any stored box with an edge over {@link #HARD_MAX_EDGE}: such a job reads as no job. */
    public static final Codec<MineArea> CODEC = RecordCodecBuilder.<MineArea>create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("min").forGetter(MineArea::min),
            BlockPos.CODEC.fieldOf("max").forGetter(MineArea::max)
    ).apply(instance, MineArea::new)).validate(area -> area.withinCap()
            ? com.mojang.serialization.DataResult.success(area)
            : com.mojang.serialization.DataResult.error(() -> "mining area edge over " + HARD_MAX_EDGE));

    public MineArea {
        BlockPos lo = new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()));
        BlockPos hi = new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ()));
        min = lo;
        max = hi;
    }

    /** The edge limit actually applied for a configured value: 1..{@link #HARD_MAX_EDGE}. */
    public static int clampEdge(int configured) {
        return Math.max(1, Math.min(HARD_MAX_EDGE, configured));
    }

    /**
     * The area spanned by two corners, anchored at the first: on any axis where the span is longer than
     * {@code maxEdge}, the second corner is pulled toward the first so that edge is exactly {@code maxEdge}.
     */
    public static MineArea of(BlockPos first, BlockPos second, int maxEdge) {
        int edge = clampEdge(maxEdge);
        return new MineArea(first.immutable(), new BlockPos(
                clampAxis(first.getX(), second.getX(), edge),
                clampAxis(first.getY(), second.getY(), edge),
                clampAxis(first.getZ(), second.getZ(), edge)));
    }

    private static int clampAxis(int anchor, int other, int edge) {
        int delta = other - anchor;
        if (Math.abs(delta) + 1 <= edge) {
            return other;
        }
        return anchor + Integer.signum(delta) * (edge - 1);
    }

    /** Whether {@link #of} had to shrink the span between these two corners. */
    public static boolean wouldClamp(BlockPos first, BlockPos second, int maxEdge) {
        int edge = clampEdge(maxEdge);
        return Math.abs(second.getX() - first.getX()) + 1 > edge || Math.abs(second.getY() - first.getY()) + 1 > edge
                || Math.abs(second.getZ() - first.getZ()) + 1 > edge;
    }

    /** Every edge is 1..{@link #HARD_MAX_EDGE}. */
    public boolean withinCap() {
        return (long) this.max.getX() - this.min.getX() < HARD_MAX_EDGE
                && (long) this.max.getY() - this.min.getY() < HARD_MAX_EDGE
                && (long) this.max.getZ() - this.min.getZ() < HARD_MAX_EDGE;
    }

    public int sizeX() {
        return this.max.getX() - this.min.getX() + 1;
    }

    public int sizeY() {
        return this.max.getY() - this.min.getY() + 1;
    }

    public int sizeZ() {
        return this.max.getZ() - this.min.getZ() + 1;
    }

    public int volume() {
        return this.sizeX() * this.sizeY() * this.sizeZ();
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.min.getX() && pos.getX() <= this.max.getX()
                && pos.getY() >= this.min.getY() && pos.getY() <= this.max.getY()
                && pos.getZ() >= this.min.getZ() && pos.getZ() <= this.max.getZ();
    }

    /** The {@code index}-th block in visiting order (top layer first); {@code 0 <= index < volume()}. */
    public BlockPos at(int index) {
        int layer = this.sizeX() * this.sizeZ();
        int y = this.max.getY() - index / layer;
        int rest = index % layer;
        int x = this.min.getX() + rest / this.sizeZ();
        int z = this.min.getZ() + rest % this.sizeZ();
        return new BlockPos(x, y, z);
    }

    /** Centre of the box, for "nearest bot" picks. */
    public BlockPos centre() {
        return new BlockPos((this.min.getX() + this.max.getX()) / 2, (this.min.getY() + this.max.getY()) / 2,
                (this.min.getZ() + this.max.getZ()) / 2);
    }
}
