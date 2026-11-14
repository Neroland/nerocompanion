package za.co.neroland.nerocompanion.compat.nerofactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;

/** NeroFactions only ever objects to an attack on someone; everything else passes without touching NeroFactions. */
class NeroFactionsCompatTest {

    private static final UUID OWNER = new UUID(0L, 1L);
    private static final UUID OTHER = new UUID(0L, 2L);

    @Test
    void onlyAttacksOnSomeoneAreChecked() {
        for (AuthorityAction action : AuthorityAction.values()) {
            // No target and no level: never reaches the bridge (which would fail here, NeroFactions being absent).
            assertEquals(Decision.ALLOW, NeroFactionsCompat.check(new AuthorityQuery(action, OWNER, null, null, null)),
                    action.name());
        }
        assertNull(NeroFactionsCompat.answersTo(null), "a mob or nothing answers to no player");
    }

    @Test
    void sharedFactionMatches() {
        assertTrue(FactionOverlap.shareFaction(Set.of("nerofactions:pioneers"),
                Set.of("nerofactions:miners", "nerofactions:pioneers")), "one faction in common is enough");
    }

    @Test
    void distinctFactionsDoNotMatch() {
        assertFalse(FactionOverlap.shareFaction(Set.of("nerofactions:pioneers"), Set.of("nerofactions:miners")));
    }

    @Test
    void noMembershipNeverMatches() {
        // NeroFactionsApi.membershipOf answers an empty set when membership is unavailable.
        assertFalse(FactionOverlap.shareFaction(Set.of(), Set.of("nerofactions:pioneers")), "attacker has none");
        assertFalse(FactionOverlap.shareFaction(Set.of("nerofactions:pioneers"), Set.of()), "target has none");
        assertFalse(FactionOverlap.shareFaction(Set.of(), Set.of()), "neither has one");
        assertFalse(FactionOverlap.shareFaction(null, Set.of("nerofactions:pioneers")), "null is treated as none");
    }

    @Test
    void absentNeroFactionsRegistersNothing() {
        NeroFactionsCompat.init(false);
        assertFalse(NeroFactionsCompat.isActive(), "not installed: no provider, no objection");
    }

    @Test
    void missingApiFailsTheProbeAndTheFallbackFailsClosed() {
        // This classpath has no NeroFactions at all, exactly like an older NeroFactions without NeroFactionsApi:
        // the probe throws a LinkageError, which init() catches and answers with the fail-closed provider.
        assertThrows(LinkageError.class, NeroFactionsBridge::probe);
        assertTrue(NeroFactionsCompat.failClosed(OWNER, OTHER).denied(), "no attacks on another player");
        assertEquals("authority.nerocompanion.sibling_unavailable",
                NeroFactionsCompat.failClosed(OWNER, OTHER).reasonKey());
        assertFalse(NeroFactionsCompat.failClosed(OWNER, OWNER).denied(), "its own owner is never a target");
        assertFalse(NeroFactionsCompat.failClosed(OWNER, null).denied(), "mobs are not the sibling's concern");
        for (AuthorityAction action : AuthorityAction.values()) {
            if (action != AuthorityAction.ATTACK) {
                assertFalse(NeroFactionsCompat.failClosed(new AuthorityQuery(action, OWNER, null, null, null))
                        .denied(), action.name());
            }
        }
    }
}
