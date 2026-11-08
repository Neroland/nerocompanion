package za.co.neroland.nerocompanion.scout;

import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

/**
 * The Lamp Chip's light, as a pure state machine over an abstract position {@code P}: a drone owns <b>at most one</b>
 * light block, at (or a block behind) the block it occupies. Pure Java so the placement and cleanup rules are
 * unit-tested headless; the game side is {@code ai.drone.DroneLamp}, which supplies a {@link World} over the level.
 *
 * <p>Rules:
 * <ul>
 *   <li>The light is placed only into air, only in a loaded chunk, only where the owner's authority allows it.</li>
 *   <li><b>Hysteresis</b>: once lit, the light moves to the drone's new block only when the drone has stayed in that
 *       block for {@value #STABLE_TICKS} ticks or is {@value #JUMP_DISTANCE}+ blocks from the light, and never more
 *       often than once per {@value #MIN_INTERVAL_TICKS} ticks - a moving drone relights at most twice a second, not
 *       on every block it crosses. Turning the lamp off is immediate.</li>
 *   <li>The old light is removed <i>before</i> a new one is placed, so two never exist at once.</li>
 *   <li>Only a block that is still <i>our</i> light is ever removed; if a player replaced it, it is forgotten.</li>
 *   <li>A light whose chunk is <b>not loaded</b> is never forgotten: it is handed to {@link World#orphan} so the
 *       caller can remove it once the chunk is loaded again.</li>
 * </ul>
 */
public final class LampModel<P> {

    public static final int STABLE_TICKS = 10;
    public static final int MIN_INTERVAL_TICKS = 10;
    public static final int JUMP_DISTANCE = 2;
    private static final long NEVER = Long.MIN_VALUE / 4;

    /** The world operations the model needs. */
    public interface World<P> {
        /** The chunk holding {@code pos} is loaded (nothing here may load one). */
        boolean isLoaded(P pos);

        /** The block at {@code pos} is air (not water, not any other block). */
        boolean isAir(P pos);

        /** The block at {@code pos} is the lamp's own light (the light block at the lamp level). */
        boolean isOurLight(P pos);

        /** The owner's authority allows placing the light at {@code pos}. */
        boolean mayPlace(P pos);

        /** Places the light at {@code pos}; false if the world refused. */
        boolean place(P pos);

        /** Removes the light at {@code pos} (only called when loaded and {@link #isOurLight} holds). */
        void remove(P pos);

        /** Distance in blocks between two positions (Chebyshev). */
        int distance(P a, P b);

        /** {@code pos} may still hold our light but its chunk is not loaded: remember it for a later cleanup. */
        void orphan(P pos);
    }

    @Nullable
    private P lit;
    @Nullable
    private P candidate;
    private long candidateSince = NEVER;
    private long lastChange = NEVER;

    /** Where the light currently is, if this drone placed one. */
    public Optional<P> lit() {
        return Optional.ofNullable(this.lit);
    }

    /** Restores a remembered light position (from a save) so that it can be cleaned up. */
    public void remember(@Nullable P pos) {
        this.lit = pos;
    }

    /**
     * Moves the light toward {@code want} ({@code null} = lamp off) at game tick {@code now}.
     *
     * @return true if the remembered position changed (the caller persists it)
     */
    public boolean update(@Nullable P want, long now, World<P> world) {
        P before = this.lit;
        this.dropIfGone(world);
        if (want == null) {
            this.candidate = null;
            if (this.lit != null) {
                world.remove(this.lit);
                this.lit = null;
                this.lastChange = now;
            }
            return !Objects.equals(before, this.lit);
        }
        if (!want.equals(this.candidate)) {
            this.candidate = want;
            this.candidateSince = now;
        }
        if (want.equals(this.lit)) {
            return !Objects.equals(before, this.lit);
        }
        boolean due = now - this.lastChange >= MIN_INTERVAL_TICKS;
        boolean settled = now - this.candidateSince >= STABLE_TICKS;
        boolean far = this.lit == null || world.distance(this.lit, want) >= JUMP_DISTANCE;
        if (due && (settled || far)) {
            if (this.lit != null) {
                world.remove(this.lit);
                this.lit = null;
            }
            if (world.isLoaded(want) && world.isAir(want) && world.mayPlace(want) && world.place(want)) {
                this.lit = want;
            }
            this.lastChange = now;
        }
        return !Objects.equals(before, this.lit);
    }

    /**
     * Removes the light (if loaded and still ours; orphaned if unloaded) and forgets it.
     *
     * @return true if something was remembered
     */
    public boolean clear(World<P> world) {
        this.candidate = null;
        if (this.lit == null) {
            return false;
        }
        if (!world.isLoaded(this.lit)) {
            world.orphan(this.lit);
        } else if (world.isOurLight(this.lit)) {
            world.remove(this.lit);
        }
        this.lit = null;
        return true;
    }

    /** Unloaded: orphan it; loaded but no longer ours (a player replaced it): forget it. */
    private void dropIfGone(World<P> world) {
        if (this.lit == null) {
            return;
        }
        if (!world.isLoaded(this.lit)) {
            world.orphan(this.lit);
            this.lit = null;
        } else if (!world.isOurLight(this.lit)) {
            this.lit = null;
        }
    }
}
