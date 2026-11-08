package za.co.neroland.nerocompanion.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/** The unlock rules ({@code 010} section 6.1) and the server's ApplySkin checks built on them. */
class LookUnlocksTest {

    private static Look skin(String rule) {
        return LookTestSupport.look("personal_drone/test", LookKind.SKIN, "{\"types\":[\"nerocompanion:personal_drone\"],"
                + "\"texture\":\"nerocompanion:entity/personal_drone/test\",\"unlock\":" + rule + "}");
    }

    @Test
    void defaultIsAlwaysUnlocked() {
        assertTrue(LookUnlocks.unlocked(skin("{\"rule\":\"default\"}"), LookTestSupport.DRONE, new LookTestSupport.Ctx()));
    }

    @Test
    void bondNeedsTheLevel() {
        Look look = skin("{\"rule\":\"bond\",\"level\":3}");
        LookTestSupport.Ctx ctx = new LookTestSupport.Ctx();
        ctx.bond = 2;
        assertFalse(LookUnlocks.unlocked(look, LookTestSupport.DRONE, ctx));
        ctx.bond = 3;
        assertTrue(LookUnlocks.unlocked(look, LookTestSupport.DRONE, ctx));
        ctx.bond = 4;
        assertTrue(LookUnlocks.unlocked(look, LookTestSupport.DRONE, ctx));
    }

    @Test
    void advancementItemAndHookReadTheirIds() {
        LookTestSupport.Ctx ctx = new LookTestSupport.Ctx();
        Look adv = skin("{\"rule\":\"advancement\",\"id\":\"nerocompanion:pet_adopted\"}");
        Look item = skin("{\"rule\":\"item\",\"item\":\"nerocompanion:hazard_decal\"}");
        Look hook = skin("{\"rule\":\"hook\",\"hook\":\"nerocompanion:hook/skin/aurora\"}");
        assertFalse(LookUnlocks.unlocked(adv, LookTestSupport.DRONE, ctx));
        assertFalse(LookUnlocks.unlocked(item, LookTestSupport.DRONE, ctx));
        assertFalse(LookUnlocks.unlocked(hook, LookTestSupport.DRONE, ctx));
        ctx.done = Set.of(LookTestSupport.id("pet_adopted"));
        ctx.items = Set.of(LookTestSupport.id("hazard_decal"));
        ctx.hooks = Set.of(LookTestSupport.id("hook/skin/aurora"));
        assertTrue(LookUnlocks.unlocked(adv, LookTestSupport.DRONE, ctx));
        assertTrue(LookUnlocks.unlocked(item, LookTestSupport.DRONE, ctx));
        assertTrue(LookUnlocks.unlocked(hook, LookTestSupport.DRONE, ctx));
    }

    @Test
    void seasonMatchesTheConfigKeyOnly() {
        Look look = skin("{\"rule\":\"season\",\"season\":\"winter\"}");
        LookTestSupport.Ctx ctx = new LookTestSupport.Ctx();
        assertFalse(LookUnlocks.unlocked(look, LookTestSupport.DRONE, ctx));
        ctx.season = "festive";
        assertFalse(LookUnlocks.unlocked(look, LookTestSupport.DRONE, ctx));
        ctx.season = "winter";
        assertTrue(LookUnlocks.unlocked(look, LookTestSupport.DRONE, ctx));
    }

    @Test
    void shopIsLockedUntilAShopCanSell() {
        Look look = skin("{\"rule\":\"shop\",\"price\":250}");
        assertEquals(250L, look.unlock().price());
        assertFalse(LookUnlocks.unlocked(look, LookTestSupport.DRONE, new LookTestSupport.Ctx()));
        LookTestSupport.Ctx selling = new LookTestSupport.Ctx() {
            @Override
            public boolean shopCanSell(net.minecraft.resources.Identifier id, long price) {
                return price == 250L;
            }
        };
        assertTrue(LookUnlocks.unlocked(look, LookTestSupport.DRONE, selling));
    }

    @Test
    void anotherTypeIsNeverUnlocked() {
        assertFalse(LookUnlocks.unlocked(skin("{\"rule\":\"default\"}"), LookTestSupport.SCOUT, new LookTestSupport.Ctx()));
    }

    @Test
    void applyRejectsMissingWrongTypeAndLockedLooks() {
        LookTestSupport.Ctx ctx = new LookTestSupport.Ctx();
        Payloads.Feedback missing = LookService.check(null, LookTestSupport.DRONE, ctx);
        assertNotNull(missing);
        assertFalse(missing.ok());
        assertEquals("gui.nerocompanion.reason.no_such_look", missing.key());

        Payloads.Feedback wrongType = LookService.check(skin("{\"rule\":\"default\"}"), LookTestSupport.SCOUT, ctx);
        assertNotNull(wrongType);
        assertEquals("gui.nerocompanion.reason.look_type", wrongType.key());

        Payloads.Feedback locked = LookService.check(skin("{\"rule\":\"bond\",\"level\":4}"), LookTestSupport.DRONE, ctx);
        assertNotNull(locked);
        assertEquals("gui.nerocompanion.reason.look_locked", locked.key());

        Payloads.Feedback needItem = LookService.check(skin("{\"rule\":\"item\",\"item\":\"nerocompanion:hat_hard_hat\"}"),
                LookTestSupport.DRONE, ctx);
        assertNotNull(needItem);
        assertEquals("gui.nerocompanion.reason.need_item", needItem.key());
        assertEquals("@item.nerocompanion.hat_hard_hat", needItem.args().get(0));

        ctx.bond = 4;
        assertNull(LookService.check(skin("{\"rule\":\"bond\",\"level\":4}"), LookTestSupport.DRONE, ctx));
    }

    @Test
    void hatsDefaultToNoModelOverride() {
        Look hat = LookTestSupport.look("hard_hat", LookKind.HAT, "{\"types\":[\"*\"],\"texture\":"
                + "\"nerocompanion:entity/hat/hard_hat\",\"glow\":null,\"unlock\":{\"rule\":\"item\","
                + "\"item\":\"nerocompanion:hat_hard_hat\"},\"model_override\":\"nerocompanion:scrapling/mossback\"}");
        assertEquals("", hat.modelOverride());
        assertEquals("hat.nerocompanion.hard_hat", hat.nameKey());
        assertTrue(hat.glow("personal_drone").isEmpty());
    }
}
