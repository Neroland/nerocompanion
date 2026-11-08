package za.co.neroland.nerocompanion.compat.nerosecurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;

/** NeroSecurity restricts opening, breaking and entering secured devices; nothing else, and never without a place. */
class NeroSecurityCompatTest {

    @Test
    void restrictsOpenBreakEnterOnly() {
        assertTrue(NeroSecurityCompat.restricts(AuthorityAction.OPEN));
        assertTrue(NeroSecurityCompat.restricts(AuthorityAction.BREAK));
        assertTrue(NeroSecurityCompat.restricts(AuthorityAction.ENTER));
        assertFalse(NeroSecurityCompat.restricts(AuthorityAction.ATTACK));
        assertFalse(NeroSecurityCompat.restricts(AuthorityAction.PLACE));
        assertEquals(Decision.ALLOW, NeroSecurityCompat.check(new AuthorityQuery(AuthorityAction.ATTACK,
                new UUID(0L, 1L), null, null, null)));
        assertEquals(Decision.ALLOW, NeroSecurityCompat.check(new AuthorityQuery(AuthorityAction.OPEN,
                new UUID(0L, 1L), null, null, null)), "no place: nothing to check");
    }
}
