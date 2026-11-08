package za.co.neroland.nerocompanion.authority;

/**
 * The world facts the decision chain reads, behind an adapter so the chain is unit-testable on the plain JVM.
 * {@link VanillaAuthorityFacts} answers them from the live server <em>as the owner</em> (resolved from the stored
 * owner UUID, never from proximity); tests answer them from fields. Implementations should compute lazily: the
 * chain stops at the first denial.
 */
public interface AuthorityFacts {

    /** How the owner is playing; spectators may do nothing, adventure players may not build. */
    enum OwnerMode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR
    }

    /** What an attack target is, relative to the owner. */
    enum TargetKind {
        NONE,
        OWNER,
        ALLY,
        OWNED_BY_SOMEONE,
        /**
         * Another player's companion that attacked the owner or the bot (it is the owner's or the bot's last attacker,
         * or is targeting either). Treated as {@link #OWNED_BY_SOMEONE} unless {@code pvpBots} is on; then it is
         * fair game like a player aggressor, judged by its own owner.
         */
        AGGRESSOR_COMPANION,
        PROTECTED_MOB,
        PLAYER,
        OTHER
    }

    boolean ownerOnline();

    boolean ownerSameDimension();

    OwnerMode ownerMode();

    /** The owner's own build permission ({@code Abilities.mayBuild}). */
    boolean ownerMayBuild();

    boolean spawnProtected();

    boolean withinWorldBorder();

    /** {@code level.mayInteract(owner, pos)}. */
    boolean ownerMayInteract();

    /** Block destroy speed; negative is unbreakable (bedrock-like). */
    float destroySpeed();

    /** In {@code #nerocompanion:unmineable}. */
    boolean unmineable();

    boolean hasBlockEntity();

    /** The container at the position is locked against the owner. */
    boolean containerLocked();

    /** The Core progression gate the type requires for the bot's current mode is open (true if none). */
    boolean gateOpen();

    TargetKind targetKind();

    /**
     * For a player target: in creative or spectator. For an {@link TargetKind#AGGRESSOR_COMPANION}: its owner is
     * offline, in creative or spectator (judged by that companion's stored owner UUID, never proximity).
     */
    boolean targetPlayerInvulnerable();

    /**
     * For a player target: {@code owner.canHarmPlayer(target)} (server PvP setting and teams). For an
     * {@link TargetKind#AGGRESSOR_COMPANION}: {@code owner.canHarmPlayer(thatCompanionsOwner)}.
     */
    boolean ownerCanHarmTarget();
}
