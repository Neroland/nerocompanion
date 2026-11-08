package za.co.neroland.nerocompanion.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityProvider;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;

/**
 * A sibling that is installed but whose bridge fails is replaced by a deny-only provider: the guarded actions are
 * refused everywhere, the rest (following, i.e. ENTER) still allowed.
 */
class FailClosedTest {

    private static Decision ask(AuthorityProvider provider, AuthorityAction action) {
        return provider.check(new AuthorityQuery(action, UUID.randomUUID(), null, null, null));
    }

    @Test
    void claimsFailClosedButFollowingStillWorks() {
        AuthorityProvider claims = SiblingCompat.denying(EnumSet.of(AuthorityAction.BREAK, AuthorityAction.PLACE,
                AuthorityAction.OPEN, AuthorityAction.ATTACK));
        for (AuthorityAction action : new AuthorityAction[] {AuthorityAction.BREAK, AuthorityAction.PLACE,
                AuthorityAction.OPEN, AuthorityAction.ATTACK}) {
            Decision d = ask(claims, action);
            assertTrue(d.denied(), action.name());
            assertEquals("authority.nerocompanion.sibling_unavailable", d.reasonKey());
        }
        assertFalse(ask(claims, AuthorityAction.ENTER).denied(), "following is never blocked");
    }

    @Test
    void anEmptySetDeniesNothing() {
        AuthorityProvider none = SiblingCompat.denying(EnumSet.noneOf(AuthorityAction.class));
        for (AuthorityAction action : AuthorityAction.values()) {
            assertFalse(ask(none, action).denied(), action.name());
        }
    }
}
