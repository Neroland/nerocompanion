package za.co.neroland.nerocompanion.authority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The test list, against a fake rules adapter: offline owner denies; adventure owner denies break;
 * unmineable denies; owner/ally/pet/protected targets denied; pvpBots=false denies players; a provider deny wins;
 * a throwing provider denies. Plus the allow paths, so the denials are not vacuous.
 */
class AuthorityChainTest {

    /** Everything allowed unless a test flips a field. */
    static final class Facts implements AuthorityFacts {
        boolean online = true;
        boolean sameDimension = true;
        OwnerMode mode = OwnerMode.SURVIVAL;
        boolean mayBuild = true;
        boolean spawnProtected;
        boolean withinBorder = true;
        boolean mayInteract = true;
        float destroySpeed = 1.5F;
        boolean unmineable;
        boolean blockEntity;
        boolean locked;
        boolean gateOpen = true;
        TargetKind target = TargetKind.OTHER;
        boolean targetInvulnerable;
        boolean canHarm = true;

        @Override public boolean ownerOnline() { return online; }
        @Override public boolean ownerSameDimension() { return sameDimension; }
        @Override public OwnerMode ownerMode() { return mode; }
        @Override public boolean ownerMayBuild() { return mayBuild; }
        @Override public boolean spawnProtected() { return spawnProtected; }
        @Override public boolean withinWorldBorder() { return withinBorder; }
        @Override public boolean ownerMayInteract() { return mayInteract; }
        @Override public float destroySpeed() { return destroySpeed; }
        @Override public boolean unmineable() { return unmineable; }
        @Override public boolean hasBlockEntity() { return blockEntity; }
        @Override public boolean containerLocked() { return locked; }
        @Override public boolean gateOpen() { return gateOpen; }
        @Override public TargetKind targetKind() { return target; }
        @Override public boolean targetPlayerInvulnerable() { return targetInvulnerable; }
        @Override public boolean ownerCanHarmTarget() { return canHarm; }
    }

    private static final List<String> WARNINGS = new ArrayList<>();

    private static Decision decide(AuthorityAction action, Facts facts) {
        return decide(action, facts, AuthorityRules.DEFAULTS, List.of());
    }

    private static Decision decide(AuthorityAction action, Facts facts, AuthorityRules rules,
            List<AuthorityProvider> providers) {
        return AuthorityChain.decide(action, facts, rules, providers,
                new AuthorityQuery(action, UUID.randomUUID(), null, null, null), WARNINGS::add);
    }

    private static void assertDenied(String reason, Decision decision) {
        assertEquals("authority.nerocompanion." + reason, decision.reasonKey());
        assertTrue(decision.denied());
    }

    @Test
    void everythingAllowedForAnOrdinaryOnlineOwner() {
        for (AuthorityAction action : AuthorityAction.values()) {
            assertTrue(decide(action, new Facts()).allowed(), action.name());
        }
    }

    @Test
    void offlineOwnerDeniesEverything() {
        Facts facts = new Facts();
        facts.online = false;
        for (AuthorityAction action : AuthorityAction.values()) {
            assertDenied("owner_offline", decide(action, facts));
        }
    }

    @Test
    void ownerInAnotherDimensionDenies() {
        Facts facts = new Facts();
        facts.sameDimension = false;
        assertDenied("other_dimension", decide(AuthorityAction.BREAK, facts));
        assertDenied("other_dimension", decide(AuthorityAction.ATTACK, facts));
    }

    @Test
    void adventureOwnerDeniesBreakAndPlace() {
        Facts facts = new Facts();
        facts.mode = AuthorityFacts.OwnerMode.ADVENTURE;
        facts.mayBuild = false;
        assertDenied("cannot_build", decide(AuthorityAction.BREAK, facts));
        assertDenied("cannot_build", decide(AuthorityAction.PLACE, facts));
        assertTrue(decide(AuthorityAction.OPEN, facts).allowed());
    }

    @Test
    void spectatorOwnerDeniesEverything() {
        Facts facts = new Facts();
        facts.mode = AuthorityFacts.OwnerMode.SPECTATOR;
        for (AuthorityAction action : AuthorityAction.values()) {
            assertDenied("spectator", decide(action, facts));
        }
    }

    @Test
    void blockRules() {
        Facts spawn = new Facts();
        spawn.spawnProtected = true;
        assertDenied("spawn_protection", decide(AuthorityAction.BREAK, spawn));
        Facts border = new Facts();
        border.withinBorder = false;
        assertDenied("world_border", decide(AuthorityAction.BREAK, border));
        assertDenied("world_border", decide(AuthorityAction.ENTER, border));
        Facts interact = new Facts();
        interact.mayInteract = false;
        assertDenied("no_interact", decide(AuthorityAction.BREAK, interact));
        assertDenied("no_interact", decide(AuthorityAction.OPEN, interact));
        Facts bedrock = new Facts();
        bedrock.destroySpeed = -1.0F;
        assertDenied("unbreakable", decide(AuthorityAction.BREAK, bedrock));
        Facts unmineable = new Facts();
        unmineable.unmineable = true;
        assertDenied("unmineable", decide(AuthorityAction.BREAK, unmineable));
        Facts machine = new Facts();
        machine.blockEntity = true;
        assertDenied("block_entity", decide(AuthorityAction.BREAK, machine));
        Facts locked = new Facts();
        locked.locked = true;
        assertDenied("locked", decide(AuthorityAction.OPEN, locked));
    }

    @Test
    void attackTargetRules() {
        for (AuthorityFacts.TargetKind kind : List.of(AuthorityFacts.TargetKind.OWNER,
                AuthorityFacts.TargetKind.ALLY, AuthorityFacts.TargetKind.OWNED_BY_SOMEONE,
                AuthorityFacts.TargetKind.PROTECTED_MOB, AuthorityFacts.TargetKind.NONE)) {
            Facts facts = new Facts();
            facts.target = kind;
            assertTrue(decide(AuthorityAction.ATTACK, facts).denied(), kind.name());
        }
        Facts owner = new Facts();
        owner.target = AuthorityFacts.TargetKind.OWNER;
        assertDenied("target_owner", decide(AuthorityAction.ATTACK, owner));
    }

    @Test
    void playersOnlyWithPvpBotsAndTheOwnersOwnPermission() {
        Facts player = new Facts();
        player.target = AuthorityFacts.TargetKind.PLAYER;
        assertDenied("pvp_off", decide(AuthorityAction.ATTACK, player));
        AuthorityRules pvp = new AuthorityRules(true, true, true);
        assertTrue(decide(AuthorityAction.ATTACK, player, pvp, List.of()).allowed());
        player.canHarm = false;
        assertDenied("pvp_off", decide(AuthorityAction.ATTACK, player, pvp, List.of()));
        player.canHarm = true;
        player.targetInvulnerable = true;
        assertDenied("target_invulnerable", decide(AuthorityAction.ATTACK, player, pvp, List.of()));
    }

    @Test
    void configSwitchesDeny() {
        AuthorityRules off = new AuthorityRules(false, false, false);
        assertDenied("mining_disabled", decide(AuthorityAction.BREAK, new Facts(), off, List.of()));
        assertDenied("combat_disabled", decide(AuthorityAction.ATTACK, new Facts(), off, List.of()));
    }

    @Test
    void closedGateDenies() {
        Facts facts = new Facts();
        facts.gateOpen = false;
        assertDenied("gate", decide(AuthorityAction.BREAK, facts));
    }

    @Test
    void providerDenyWins() {
        AuthorityProvider claims = query -> Decision.deny("claimed");
        assertDenied("claimed", decide(AuthorityAction.BREAK, new Facts(), AuthorityRules.DEFAULTS,
                List.of(query -> Decision.ALLOW, claims)));
    }

    @Test
    void providerCannotGrantPastVanilla() {
        Facts facts = new Facts();
        facts.unmineable = true;
        assertDenied("unmineable", decide(AuthorityAction.BREAK, facts, AuthorityRules.DEFAULTS,
                List.of(query -> Decision.ALLOW)));
    }

    @Test
    void throwingProviderDeniesAndWarnsOnce() {
        WARNINGS.clear();
        AuthorityProvider broken = new AuthorityProvider() {
            @Override
            public Decision check(AuthorityQuery query) {
                throw new NoClassDefFoundError("sibling missing");
            }
        };
        assertDenied("provider_error", decide(AuthorityAction.OPEN, new Facts(), AuthorityRules.DEFAULTS,
                List.of(broken)));
        assertDenied("provider_error", decide(AuthorityAction.OPEN, new Facts(), AuthorityRules.DEFAULTS,
                List.of(broken)));
        assertEquals(1, WARNINGS.size());
        AuthorityProvider runtime = query -> {
            throw new IllegalStateException("boom");
        };
        assertDenied("provider_error", decide(AuthorityAction.ENTER, new Facts(), AuthorityRules.DEFAULTS,
                List.of(runtime)));
    }

    @Test
    void reasonKeysCarryNoPlayerData() {
        Facts facts = new Facts();
        facts.online = false;
        String key = decide(AuthorityAction.BREAK, facts).reasonKey();
        assertTrue(key.matches("authority\\.nerocompanion\\.[a-z_]+"), key);
    }
}
