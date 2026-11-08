package za.co.neroland.nerocompanion.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.network.RateLimiter;
import za.co.neroland.nerocompanion.network.RenameRules;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;

/** The {@code companion} action: ownership against the ledger, online rules, rate limit, size cap, rename filter. */
class CompanionLinkActionsTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final LinkFixtures.World world = new LinkFixtures.World();
    private final FakeOps ops = new FakeOps();
    private final CompanionLinkActions actions = new CompanionLinkActions(() -> ops);

    private final class FakeOps implements CompanionLinkActions.Ops {
        final Set<UUID> online = new HashSet<>();
        final RateLimiter limiter = new RateLimiter();
        long tick = 1_000L;
        final List<String> done = new ArrayList<>();
        final Map<UUID, Optional<String>> names = new HashMap<>();
        final Set<String> blocked = Set.of("badword");

        @Override
        public Optional<LedgerEntry> entry(UUID companionId) {
            return world.book.get(companionId);
        }

        @Override
        public boolean online(UUID player) {
            return online.contains(player);
        }

        @Override
        public boolean admit(UUID player) {
            return limiter.allowLink(player, tick);
        }

        @Override
        public CompanionLinkActions.Outcome setMode(UUID owner, UUID companionId, CompanionMode mode) {
            done.add("set_mode:" + mode.id());
            return CompanionLinkActions.Outcome.done();
        }

        @Override
        public CompanionLinkActions.Outcome recall(UUID owner, UUID companionId) {
            done.add("recall");
            return CompanionLinkActions.Outcome.refuse("no_bay");
        }

        @Override
        public CompanionLinkActions.Outcome reclaim(UUID owner, UUID companionId) {
            done.add("reclaim");
            return CompanionLinkActions.Outcome.done();
        }

        /** The live rename's decision, over the same rules and a fake text filter. */
        @Override
        public CompanionLinkActions.Outcome rename(UUID owner, UUID companionId, String requestedName) {
            String cleaned = RenameRules.clean(requestedName);
            RenameRules.Result result = RenameRules.decide(names.getOrDefault(companionId, Optional.empty()),
                    cleaned, text -> blocked.stream().anyMatch(text.toLowerCase()::contains));
            done.add("rename:" + result.outcome());
            if (result.outcome() == RenameRules.Outcome.FILTERED) {
                return CompanionLinkActions.Outcome.refuse("filtered");
            }
            names.put(companionId, result.name());
            return CompanionLinkActions.Outcome.done();
        }
    }

    private CompanionRecord live(UUID owner) {
        return world.deploy(LinkFixtures.record(owner, "mining_bot", CompanionMode.FOLLOW, "Digger", Optional.empty()),
                new HomePos(LinkFixtures.OVERWORLD, new BlockPos(1, 64, 1)));
    }

    private static JsonObject op(UUID companionId, String op, String key, String value) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", UUID.randomUUID().toString());
        body.addProperty("companionId", companionId.toString());
        body.addProperty("op", op);
        if (key != null) {
            body.addProperty(key, value);
        }
        return body;
    }

    private LinkActionResult run(UUID who, JsonObject body) {
        ops.tick += 200; // well inside one minute for a handful of calls
        return actions.execute(who, "companion", body);
    }

    @Test
    void aNonOwnerIsRefusedExactlyLikeAMissingCompanion() {
        CompanionRecord mine = live(alice);
        ops.online.add(bob);
        LinkActionResult foreign = actions.execute(bob, "companion", op(mine.companionId(), "set_mode", "mode", "stay"));
        LinkActionResult missing = actions.execute(bob, "companion",
                op(UUID.randomUUID(), "set_mode", "mode", "stay"));
        assertFalse(foreign.ok());
        assertEquals(LinkActionResult.Error.NOT_OWNER, foreign.error());
        assertEquals(missing.error(), foreign.error());
        assertEquals(missing.message(), foreign.message());
        for (String o : List.of("recall", "reclaim", "rename")) {
            assertEquals(LinkActionResult.Error.NOT_OWNER,
                    actions.execute(bob, "companion", op(mine.companionId(), o, "name", "Mine")).error(), o);
        }
        assertTrue(ops.done.isEmpty(), "nothing ran: " + ops.done);
    }

    @Test
    void theOwnerCanSetModeOnlyWhileOnline() {
        CompanionRecord mine = live(alice);
        LinkActionResult offline = run(alice, op(mine.companionId(), "set_mode", "mode", "stay"));
        assertEquals(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED, offline.error());
        ops.online.add(alice);
        LinkActionResult ok = run(alice, op(mine.companionId(), "set_mode", "mode", "stay"));
        assertTrue(ok.ok(), ok.message());
        assertEquals("done", ok.state().get("result").getAsString());
        assertEquals(List.of("set_mode:stay"), ops.done);
        assertEquals("unknown_mode", run(alice, op(mine.companionId(), "set_mode", "mode", "dance")).message());
    }

    @Test
    void recallAndReclaimRunWithTheOwnerOfflineAndCheckTheState() {
        assertTrue(actions.allowOffline("companion"));
        CompanionRecord mine = live(alice);
        LinkActionResult recall = run(alice, op(mine.companionId(), "recall", null, null));
        assertEquals("no_bay", recall.message());
        assertEquals("not_lost", run(alice, op(mine.companionId(), "reclaim", null, null)).message());

        CompanionRecord lost = world.lose(LinkFixtures.record(alice, "carry_bot", CompanionMode.CARRY, "Rusty",
                Optional.of(LinkFixtures.BAY_A)), LinkFixtures.BAY_A);
        assertTrue(run(alice, op(lost.companionId(), "reclaim", null, null)).ok());
        assertEquals("not_live", run(alice, op(lost.companionId(), "recall", null, null)).message());
        assertEquals(List.of("recall", "reclaim"), ops.done);
    }

    @Test
    void renameGoesThroughTheSameFilterAndCapAndNeedsTheOwnerOnline() {
        CompanionRecord mine = live(alice);
        assertEquals(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED,
                run(alice, op(mine.companionId(), "rename", "name", "Rusty")).error());
        ops.online.add(alice);
        assertEquals("filtered", run(alice, op(mine.companionId(), "rename", "name", "BadWord bot")).message());
        assertTrue(run(alice, op(mine.companionId(), "rename", "name", "§cA very long name that goes past the cap"))
                .ok());
        String stored = ops.names.get(mine.companionId()).orElseThrow();
        assertTrue(stored.codePointCount(0, stored.length()) <= CompanionRecord.MAX_NAME_LENGTH, stored);
        assertFalse(stored.contains("§"), stored);
        assertEquals("bad_name", run(alice, op(mine.companionId(), "rename", "name", "x".repeat(200))).message());
    }

    @Test
    void actionsAreRateLimitedPerPlayer() {
        CompanionRecord mine = live(alice);
        CompanionRecord bobs = live(bob);
        ops.online.add(alice);
        ops.online.add(bob);
        int allowed = 0;
        for (int i = 0; i < 15; i++) {
            ops.tick += 1; // all within one minute
            if (!"rate_limited".equals(actions.execute(alice, "companion",
                    op(mine.companionId(), "set_mode", "mode", "stay")).message())) {
                allowed++;
            }
        }
        assertEquals(RateLimiter.LINK_PER_MINUTE, allowed);
        // Bob has his own bucket.
        assertTrue(actions.execute(bob, "companion", op(bobs.companionId(), "set_mode", "mode", "stay")).ok());
        // A minute later Alice may act again.
        ops.tick += 1_200;
        assertTrue(actions.execute(alice, "companion", op(mine.companionId(), "set_mode", "mode", "stay")).ok());
    }

    @Test
    void theBodyIsCappedAndMalformedRequestsAreRefused() {
        CompanionRecord mine = live(alice);
        ops.online.add(alice);
        JsonObject big = op(mine.companionId(), "set_mode", "mode", "stay");
        big.addProperty("pad", "x".repeat(CompanionLinkActions.MAX_BODY_BYTES));
        assertEquals("too_large", run(alice, big).message());
        assertEquals("bad_companion", run(alice, badId()).message());
        assertEquals("unknown_op", run(alice, op(mine.companionId(), "explode", null, null)).message());
        assertEquals("unknown_action", actions.execute(alice, "other", new JsonObject()).message());
        assertFalse(ops.done.contains("explode"));
    }

    private static JsonObject badId() {
        JsonObject body = new JsonObject();
        body.addProperty("companionId", "not-a-uuid");
        body.addProperty("op", "recall");
        return body;
    }

    @Test
    void noServerAnswersInternal() {
        CompanionLinkActions stopped = new CompanionLinkActions(() -> null);
        assertEquals(LinkActionResult.Error.INTERNAL,
                stopped.execute(alice, "companion", op(UUID.randomUUID(), "recall", null, null)).error());
    }
}
