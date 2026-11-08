package za.co.neroland.nerocompanion.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.ai.combat.ChaseProgress;
import za.co.neroland.nerocompanion.ai.combat.CombatWork;
import za.co.neroland.nerocompanion.ai.combat.GuardRules;
import za.co.neroland.nerocompanion.ai.combat.GuardRules.Candidate;
import za.co.neroland.nerocompanion.ai.combat.GuardRules.Kind;
import za.co.neroland.nerocompanion.ai.repair.RepairMath;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityChain;
import za.co.neroland.nerocompanion.authority.AuthorityFacts;
import za.co.neroland.nerocompanion.authority.AuthorityProvider;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.AuthorityRules;
import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.energy.CompanionEnergy;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * The guard target filter (bot appetite + the owner-authority chain, with a fake facts adapter), the leash and
 * energy maths, and the fragility / energy numbers of both chassis against decision 003.
 */
class GuardRulesTest {

    /** A candidate as the authority chain sees it; everything allowed unless a field is flipped. */
    static final class Facts implements AuthorityFacts {
        final String name;
        TargetKind target = TargetKind.OTHER;
        boolean online = true;
        boolean targetInvulnerable;
        boolean canHarm = true;

        Facts(String name) {
            this.name = name;
        }

        Facts kind(TargetKind kind) {
            this.target = kind;
            return this;
        }

        @Override public boolean ownerOnline() { return online; }
        @Override public boolean ownerSameDimension() { return true; }
        @Override public OwnerMode ownerMode() { return OwnerMode.SURVIVAL; }
        @Override public boolean ownerMayBuild() { return true; }
        @Override public boolean spawnProtected() { return false; }
        @Override public boolean withinWorldBorder() { return true; }
        @Override public boolean ownerMayInteract() { return true; }
        @Override public float destroySpeed() { return 1.0F; }
        @Override public boolean unmineable() { return false; }
        @Override public boolean hasBlockEntity() { return false; }
        @Override public boolean containerLocked() { return false; }
        @Override public boolean gateOpen() { return true; }
        @Override public TargetKind targetKind() { return target; }
        @Override public boolean targetPlayerInvulnerable() { return targetInvulnerable; }
        @Override public boolean ownerCanHarmTarget() { return canHarm; }
    }

    private static final List<String> WARNINGS = new ArrayList<>();

    private static Decision authority(Facts facts, AuthorityRules rules, List<AuthorityProvider> providers) {
        return AuthorityChain.decide(AuthorityAction.ATTACK, facts, rules, providers,
                new AuthorityQuery(AuthorityAction.ATTACK, UUID.randomUUID(), null, null, null), WARNINGS::add);
    }

    private static Optional<Facts> pick(List<Candidate<Facts>> candidates, AuthorityRules rules,
            List<AuthorityProvider> providers) {
        return GuardRules.select(candidates, f -> authority(f, rules, providers));
    }

    private static Optional<Facts> pick(List<Candidate<Facts>> candidates) {
        return pick(candidates, AuthorityRules.DEFAULTS, List.of());
    }

    private static Candidate<Facts> c(Facts facts, Kind kind, boolean aggressor, double distanceSq) {
        return new Candidate<>(facts, kind, aggressor, true, distanceSq);
    }

    private static Candidate<Facts> outside(Facts facts, Kind kind, boolean aggressor, double distanceSq) {
        return new Candidate<>(facts, kind, aggressor, false, distanceSq);
    }

    @Test
    void takesTheNearestAllowedHostile() {
        Facts far = new Facts("far zombie");
        Facts near = new Facts("near skeleton");
        assertEquals("near skeleton", pick(List.of(c(far, Kind.HOSTILE, false, 64), c(near, Kind.HOSTILE, false, 9)))
                .orElseThrow().name);
    }

    @Test
    void neverPicksTheOwnerTeamPetsCompanionsOrProtectedMobs() {
        // Each of these attacked the owner (aggressor) and is right next to the bot: the chain still says no.
        List<Candidate<Facts>> all = List.of(
                c(new Facts("owner").kind(AuthorityFacts.TargetKind.OWNER), Kind.PLAYER, true, 1),
                c(new Facts("teammate").kind(AuthorityFacts.TargetKind.ALLY), Kind.PLAYER, true, 1),
                c(new Facts("someone's wolf").kind(AuthorityFacts.TargetKind.OWNED_BY_SOMEONE), Kind.OTHER, true, 1),
                c(new Facts("another companion").kind(AuthorityFacts.TargetKind.OWNED_BY_SOMEONE), Kind.OTHER, true, 1),
                c(new Facts("villager").kind(AuthorityFacts.TargetKind.PROTECTED_MOB), Kind.OTHER, true, 1),
                c(new Facts("tagged mob").kind(AuthorityFacts.TargetKind.PROTECTED_MOB), Kind.HOSTILE, true, 1));
        assertEquals(Optional.empty(), pick(all));
        Facts zombie = new Facts("zombie");
        List<Candidate<Facts>> withHostile = new ArrayList<>(all);
        withHostile.add(c(zombie, Kind.HOSTILE, false, 100));
        assertEquals("zombie", pick(withHostile).orElseThrow().name);
    }

    @Test
    void playersAreDeniedWithPvpBotsOff() {
        Facts attacker = new Facts("attacking player").kind(AuthorityFacts.TargetKind.PLAYER);
        assertEquals(Optional.empty(), pick(List.of(c(attacker, Kind.PLAYER, true, 4))));
        assertEquals("authority.nerocompanion.pvp_off",
                authority(attacker, AuthorityRules.DEFAULTS, List.of()).reasonKey());
    }

    @Test
    void playersOnlyAsAggressorsAndOnlyWithEveryPvpPermission() {
        AuthorityRules pvp = new AuthorityRules(true, true, true);
        Facts bystander = new Facts("bystander").kind(AuthorityFacts.TargetKind.PLAYER);
        assertEquals(Optional.empty(), pick(List.of(c(bystander, Kind.PLAYER, false, 4)), pvp, List.of()));
        Facts attacker = new Facts("attacker").kind(AuthorityFacts.TargetKind.PLAYER);
        assertEquals("attacker", pick(List.of(c(attacker, Kind.PLAYER, true, 4)), pvp, List.of()).orElseThrow().name);
        attacker.canHarm = false; // server PvP off, or the owner's own team rules
        assertEquals(Optional.empty(), pick(List.of(c(attacker, Kind.PLAYER, true, 4)), pvp, List.of()));
        attacker.canHarm = true;
        attacker.targetInvulnerable = true; // creative / spectator
        assertEquals(Optional.empty(), pick(List.of(c(attacker, Kind.PLAYER, true, 4)), pvp, List.of()));
    }

    @Test
    void peacefulCreaturesOnlyWhenTheyAttacked() {
        Facts cow = new Facts("cow");
        assertEquals(Optional.empty(), pick(List.of(c(cow, Kind.OTHER, false, 4))));
        Facts wolf = new Facts("wild wolf");
        assertEquals("wild wolf", pick(List.of(c(wolf, Kind.OTHER, true, 4))).orElseThrow().name);
        assertFalse(GuardRules.eligible(Kind.PLAYER, false, true));
        assertTrue(GuardRules.eligible(Kind.HOSTILE, false, true));
    }

    @Test
    void neutralMobsOnlyWhenAngryOrAggressing() {
        // Zombified piglins and endermen are both Enemy and NeutralMob: neutral wins.
        assertEquals(Kind.OTHER, GuardRules.kindOf(true, true, false));
        assertEquals(Kind.HOSTILE, GuardRules.kindOf(true, false, false));
        assertEquals(Kind.OTHER, GuardRules.kindOf(false, false, false));
        assertEquals(Kind.PLAYER, GuardRules.kindOf(false, false, true));
        Facts piglin = new Facts("calm zombified piglin");
        assertEquals(Optional.empty(), pick(List.of(c(piglin, GuardRules.kindOf(true, true, false), false, 4))));
        Facts angry = new Facts("angry zombified piglin");
        assertEquals("angry zombified piglin",
                pick(List.of(c(angry, GuardRules.kindOf(true, true, false), true, 4))).orElseThrow().name);
    }

    @Test
    void aggressorsCountInTheLeashHostilesOnlyInTheRadius() {
        Facts skeleton = new Facts("skeleton at 15 blocks, shooting the owner");
        assertEquals("skeleton at 15 blocks, shooting the owner",
                pick(List.of(outside(skeleton, Kind.HOSTILE, true, 225))).orElseThrow().name);
        Facts wanderer = new Facts("zombie at 15 blocks, minding its own business");
        assertEquals(Optional.empty(), pick(List.of(outside(wanderer, Kind.HOSTILE, false, 225))));
        assertFalse(GuardRules.eligible(Kind.HOSTILE, false, false));
        assertTrue(GuardRules.eligible(Kind.OTHER, true, false));
    }

    @Test
    void anAttackingCompanionIsFairGameOnlyWithPvpBots() {
        Facts rival = new Facts("rival's combat bot").kind(AuthorityFacts.TargetKind.AGGRESSOR_COMPANION);
        // pvpBots=false (default): companion immunity holds, nothing changes.
        assertEquals("authority.nerocompanion.target_owned",
                authority(rival, AuthorityRules.DEFAULTS, List.of()).reasonKey());
        assertEquals(Optional.empty(), pick(List.of(c(rival, Kind.OTHER, true, 4))));
        AuthorityRules pvp = new AuthorityRules(true, true, true);
        assertEquals("rival's combat bot", pick(List.of(c(rival, Kind.OTHER, true, 4)), pvp, List.of())
                .orElseThrow().name);
        // Still judged on its owner: no PvP with them, or they are offline / creative -> denied.
        rival.canHarm = false;
        assertEquals(Optional.empty(), pick(List.of(c(rival, Kind.OTHER, true, 4)), pvp, List.of()));
        rival.canHarm = true;
        rival.targetInvulnerable = true;
        assertEquals(Optional.empty(), pick(List.of(c(rival, Kind.OTHER, true, 4)), pvp, List.of()));
        // A companion that did not attack stays protected even with pvpBots on.
        Facts bystander = new Facts("passing companion").kind(AuthorityFacts.TargetKind.OWNED_BY_SOMEONE);
        assertEquals(Optional.empty(), pick(List.of(c(bystander, Kind.OTHER, true, 4)), pvp, List.of()));
    }

    @Test
    void guardLoopPlaysWhileEngagedInGuardAndStay() {
        assertTrue(GuardRules.playsGuardLoop(CompanionMode.GUARD, true));
        assertTrue(GuardRules.playsGuardLoop(CompanionMode.STAY, true));
        assertFalse(GuardRules.playsGuardLoop(CompanionMode.GUARD, false));
        assertFalse(GuardRules.playsGuardLoop(CompanionMode.FOLLOW, true));
    }

    @Test
    void drainIsActiveOnlyWhileEngagedOrRepairing() {
        for (CompanionMode mode : List.of(
                CompanionMode.GUARD,
                CompanionMode.REPAIR,
                CompanionMode.MINE,
                CompanionMode.CARRY)) {
            assertEquals(CompanionEnergy.Activity.IDLE, CompanionEnergy.activityFor(mode, false, false));
            assertEquals(CompanionEnergy.Activity.ACTIVE, CompanionEnergy.activityFor(mode, false, true));
        }
        assertEquals(CompanionEnergy.Activity.ACTIVE, CompanionEnergy.activityFor(
                CompanionMode.SCOUT, false, false));
    }

    @Test
    void unreachableTargetsAreGivenUp() {
        ChaseProgress chase = new ChaseProgress();
        assertFalse(chase.update(100.0D));
        for (int i = 1; i < GuardRules.GIVE_UP_TICKS; i++) {
            assertFalse(chase.update(100.0D), "tick " + i);
        }
        assertTrue(chase.update(100.0D));
        chase.reset();
        double distance = 400.0D;
        for (int i = 0; i < 200; i++) {
            distance -= 2.0D;
            assertFalse(chase.update(distance));
        }
    }

    @Test
    void aggressorsComeFirst() {
        Facts near = new Facts("near hostile");
        Facts attacker = new Facts("far attacker");
        assertEquals("far attacker", pick(List.of(c(near, Kind.HOSTILE, false, 4), c(attacker, Kind.HOSTILE, true, 100)))
                .orElseThrow().name);
    }

    @Test
    void aClaimProviderDenialBlocksTheTarget() {
        AuthorityProvider claims = query -> Decision.deny("claim");
        Facts zombie = new Facts("zombie in a claim");
        assertEquals(Optional.empty(), pick(List.of(c(zombie, Kind.HOSTILE, false, 4)), AuthorityRules.DEFAULTS,
                List.of(claims)));
        AuthorityProvider broken = query -> {
            throw new IllegalStateException("provider down");
        };
        assertEquals(Optional.empty(), pick(List.of(c(zombie, Kind.HOSTILE, false, 4)), AuthorityRules.DEFAULTS,
                List.of(broken)));
    }

    @Test
    void offlineOwnerOrCombatDisabledDeniesEverything() {
        Facts zombie = new Facts("zombie");
        zombie.online = false;
        assertEquals(Optional.empty(), pick(List.of(c(zombie, Kind.HOSTILE, false, 4))));
        zombie.online = true;
        assertEquals(Optional.empty(), pick(List.of(c(zombie, Kind.HOSTILE, false, 4)),
                new AuthorityRules(true, false, false), List.of()));
    }

    @Test
    void leashIsGuardRadiusPlusEight() {
        assertEquals(12, GuardRules.guardRadius(0));
        assertEquals(20, GuardRules.leashRadius(0));
        assertEquals(26, GuardRules.leashRadius(6));
        assertTrue(GuardRules.withinLeash(20 * 20, 0));
        assertFalse(GuardRules.withinLeash(20 * 20 + 1, 0));
    }

    @Test
    void aHitCosts150FeAndNeverDipsBelowTheReserve() {
        assertEquals(150L, GuardRules.FE_PER_HIT);
        long capacity = 40_000L;
        assertEquals(4_000L, GuardRules.reserve(capacity));
        assertTrue(GuardRules.canAffordHit(4_150L, capacity));
        assertFalse(GuardRules.canAffordHit(4_149L, capacity));
        assertEquals(240, (capacity - GuardRules.reserve(capacity)) / GuardRules.FE_PER_HIT);
    }

    @Test
    void meleeTiers() {
        assertEquals(1, GuardRules.meleeTier());
        assertEquals(4.0F, GuardRules.damage(4.0D, 1));
        assertEquals(6.0F, GuardRules.damage(4.0D, 2));
        assertEquals(6.0F, GuardRules.damage(4.0D, 9));
        assertEquals(20, GuardRules.cooldownTicks(1));
        assertEquals(15, GuardRules.cooldownTicks(2));
    }

    @Test
    void noRegenerationExceptWhileCharging() {
        assertEquals(0.0F, GuardRules.healPerSecond(false));
        assertTrue(GuardRules.healPerSecond(true) > 0.0F);
    }

    private static JsonObject type(String id) throws Exception {
        try (InputStream in = GuardRulesTest.class.getClassLoader()
                .getResourceAsStream("data/nerocompanion/companion_types/" + id + ".json")) {
            assertTrue(in != null, id + ".json on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertEnergy(JsonObject type, long capacity, long idle, long active) {
        JsonObject energy = type.getAsJsonObject("energy");
        assertEquals(capacity, energy.get("capacity").getAsLong());
        assertEquals(idle, energy.get("drain_idle_per_second").getAsLong());
        assertEquals(active, energy.get("drain_active_per_second").getAsLong());
    }

    @Test
    void combatBotIsFragileAndMatches003() throws Exception {
        JsonObject combat = type("combat_bot");
        assertEnergy(combat, 40_000L, 8L, 80L);
        double maxHealth = combat.get("max_health").getAsDouble();
        assertEquals(GuardRules.FRAGILE_MAX_HEALTH, maxHealth);
        assertTrue(maxHealth < 20.0D, "lower than the ground chassis default");
        assertTrue(maxHealth < type("repair_bot").get("max_health").getAsDouble(), "frailer than the repair bot");
        assertEquals(3, combat.get("chip_slots").getAsInt());
        assertTrue(combat.getAsJsonArray("work_goals").toString().contains(CombatWork.GUARD_TARGET.toString()));
        assertTrue(combat.getAsJsonArray("work_goals").toString().contains(CombatWork.GUARD_MELEE.toString()));
    }

    @Test
    void repairBotMatches003() throws Exception {
        JsonObject repair = type("repair_bot");
        assertEnergy(repair, 36_000L, 8L, 30L);
        assertEquals(2L, RepairMath.FE_PER_POINT);
        assertEquals(3, repair.get("chip_slots").getAsInt());
    }

    @Test
    void registersBothGuardGoalsOnceTheTargetOneInTheTargetSelector() {
        CombatWork.register();
        CombatWork.register();
        assertTrue(WorkGoals.get(CombatWork.GUARD_TARGET).orElseThrow().target());
        assertFalse(WorkGoals.get(CombatWork.GUARD_MELEE).orElseThrow().target());
    }
}
