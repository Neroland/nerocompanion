package za.co.neroland.nerocompanion.compat.nerocolonies;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;

/** The NeroColonies provider has nothing to say about a query without a place (and never reaches NeroColonies). */
class NeroColoniesCompatTest {

    @Test
    void noPlaceNoObjection() {
        for (AuthorityAction action : AuthorityAction.values()) {
            assertEquals(Decision.ALLOW, NeroColoniesCompat.check(new AuthorityQuery(action, new UUID(0L, 1L), null,
                    null, null)), action.name());
        }
    }
}
