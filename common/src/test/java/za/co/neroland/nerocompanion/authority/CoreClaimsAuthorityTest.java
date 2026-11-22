package za.co.neroland.nerocompanion.authority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.protection.ClaimCheck;
import za.co.neroland.nerolandcore.protection.ClaimChecks;

/** Core land claims join the authority chain only while a claims mod registered a check, and only for block actions. */
class CoreClaimsAuthorityTest {

    @AfterEach
    void reset() {
        ClaimChecks.clearForTests();
    }

    @Test
    void onlyBlockActionsAreRestricted() {
        assertTrue(CoreClaimsAuthority.restricts(AuthorityAction.BREAK));
        assertTrue(CoreClaimsAuthority.restricts(AuthorityAction.PLACE));
        assertTrue(CoreClaimsAuthority.restricts(AuthorityAction.OPEN));
        assertFalse(CoreClaimsAuthority.restricts(AuthorityAction.ATTACK));
        assertFalse(CoreClaimsAuthority.restricts(AuthorityAction.ENTER));
    }

    @Test
    void queriesWithoutAPlaceAreAllowed() {
        UUID owner = UUID.randomUUID();
        assertSame(Decision.ALLOW,
                CoreClaimsAuthority.check(new AuthorityQuery(AuthorityAction.BREAK, owner, null, null, null)));
        assertSame(Decision.ALLOW,
                CoreClaimsAuthority.check(new AuthorityQuery(AuthorityAction.ATTACK, owner, null, null, null)));
    }

    @Test
    void claimsStepIsAddedOnlyWhileAClaimsModIsPresent() {
        int sibling = OwnerAuthority.providerCount();
        assertEquals(sibling, OwnerAuthority.providers().size(), "Core-only: no claims step");
        ClaimChecks.register(new ClaimCheck() {
            @Override
            public boolean isProtected(ServerLevel level, BlockPos pos) {
                return false;
            }

            @Override
            public boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
                return true;
            }
        });
        assertEquals(sibling + 1, OwnerAuthority.providers().size());
        assertEquals(sibling, OwnerAuthority.providerCount(), "the façade count is unchanged");
    }
}
