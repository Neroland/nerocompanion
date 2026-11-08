package za.co.neroland.nerocompanion.compat.nerofactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;

/** NeroFactions only ever objects to an attack on someone; everything else passes without touching NeroFactions. */
class NeroFactionsCompatTest {

    private static final UUID OWNER = new UUID(0L, 1L);

    @Test
    void onlyAttacksOnSomeoneAreChecked() {
        for (AuthorityAction action : AuthorityAction.values()) {
            // No target and no level: never reaches the bridge (which would fail here, NeroFactions being absent).
            assertEquals(Decision.ALLOW, NeroFactionsCompat.check(new AuthorityQuery(action, OWNER, null, null, null)),
                    action.name());
        }
        assertNull(NeroFactionsCompat.answersTo(null), "a mob or nothing answers to no player");
    }
}
