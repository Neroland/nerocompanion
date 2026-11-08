package za.co.neroland.nerocompanion.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.type.ValidationIssue;

/** Catalogue validation: every bad look file is an issue (never an exception), and good ones keep their fields. */
class LookParserTest {

    private static final String AURORA = "{\"types\":[\"*\"],\"texture\":\"nerocompanion:entity/{type}/aurora\","
            + "\"glow\":\"nerocompanion:entity/{type}/aurora_glow\",\"unlock\":{\"rule\":\"hook\","
            + "\"hook\":\"nerocompanion:hook/skin/aurora\"},\"eye_color\":\"#7CFFB2\",\"animated_glow\":{\"frames\":8,"
            + "\"frametime\":4,\"frame_texture\":\"nerocompanion:entity/{type}/aurora_glow_{frame}\"}}";

    @Test
    void familySkinKeepsItsFieldsAndResolvesPerType() {
        Look look = LookTestSupport.look("aurora", LookKind.SKIN, AURORA);
        assertTrue(look.family());
        assertTrue(look.allows(LookTestSupport.SCOUT));
        assertEquals(0x7CFFB2, look.eyeColor());
        assertEquals(8, look.frames());
        assertEquals(UnlockRule.Kind.HOOK, look.unlock().kind());
        assertEquals("nerocompanion:textures/entity/scout_drone/aurora.png",
                look.texture("scout_drone").orElseThrow().toString());
        assertEquals("nerocompanion:textures/entity/scrapling/aurora_glow_3.png",
                look.glowFrame("scrapling", 11).orElseThrow().toString());
        assertEquals("skin.nerocompanion.aurora", look.nameKey());
    }

    @Test
    void typeSkinIsLimitedToItsTypeAndTakesTheMossbackOverride() {
        Look look = LookTestSupport.look("scrapling/mossback", LookKind.SKIN, "{\"types\":[\"nerocompanion:scrapling\"],"
                + "\"texture\":\"nerocompanion:entity/scrapling/mossback\",\"unlock\":{\"rule\":\"bond\",\"level\":3},"
                + "\"model_override\":\"nerocompanion:scrapling/mossback\"}");
        assertTrue(look.allows(LookTestSupport.SCRAPLING));
        assertFalse(look.allows(LookTestSupport.DRONE));
        assertEquals("scrapling/mossback", look.modelOverride());
        assertEquals(3, look.unlock().level());
        assertEquals("skin.nerocompanion.scrapling.mossback.unlock", look.unlockKey());
    }

    @Test
    void badFilesAreDroppedWithAnIssue() {
        String[] bad = {
            "[]",
            "{\"texture\":\"nerocompanion:entity/x\",\"unlock\":{\"rule\":\"default\"}}",
            "{\"types\":[\"nerocompanion:unknown\"],\"texture\":\"nerocompanion:entity/x\",\"unlock\":{\"rule\":\"default\"}}",
            "{\"types\":[\"*\"],\"texture\":\"nerocompanion:entity/x\",\"unlock\":{\"rule\":\"default\"}}",
            "{\"types\":[\"*\"],\"texture\":\"nerocompanion:entity/{type}/x\"}",
            "{\"types\":[\"*\"],\"texture\":\"nerocompanion:entity/{type}/x\",\"unlock\":{\"rule\":\"magic\"}}",
            "{\"types\":[\"*\"],\"texture\":\"nerocompanion:entity/{type}/x\",\"unlock\":{\"rule\":\"bond\",\"level\":9}}",
            "{\"types\":[\"*\"],\"texture\":\"nerocompanion:entity/{type}/x\",\"unlock\":{\"rule\":\"season\",\"season\":\"none\"}}",
            "{\"types\":[\"*\"],\"texture\":\"nerocompanion:entity/{type}/x\",\"unlock\":{\"rule\":\"item\"}}",
            "{\"types\":[\"*\"],\"texture\":\"nerocompanion:entity/{type}/x\",\"unlock\":{\"rule\":\"shop\",\"price\":-1}}",
        };
        for (String json : bad) {
            List<ValidationIssue> issues = new ArrayList<>();
            Optional<Look> look = LookTestSupport.parse("bad", LookKind.SKIN, json, issues);
            assertTrue(look.isEmpty(), json);
            assertTrue(issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.DROPPED), json);
        }
    }

    @Test
    void badOptionalFieldsFallBack() {
        List<ValidationIssue> issues = new ArrayList<>();
        Look look = LookTestSupport.parse("x", LookKind.SKIN, "{\"types\":[\"nerocompanion:personal_drone\"],"
                + "\"texture\":\"nerocompanion:entity/personal_drone/x\",\"unlock\":{\"rule\":\"default\"},"
                + "\"eye_color\":\"teal\",\"model_override\":\"nerocompanion:nope\",\"animated_glow\":{\"frames\":4,"
                + "\"frame_texture\":\"no_placeholder\"}}", issues).orElseThrow();
        assertEquals(-1, look.eyeColor());
        assertEquals("", look.modelOverride());
        assertEquals(0, look.frames());
        assertEquals(3, issues.size());
        assertTrue(issues.stream().allMatch(i -> i.severity() == ValidationIssue.Severity.FIELD_DEFAULTED));
    }

    @Test
    void lookIdsFollowTheFolders() {
        assertEquals("nerocompanion:personal_drone/graphite", Looks.idOf("nerocompanion", "personal_drone/graphite.json")
                .toString());
        assertEquals("nerocompanion:aurora", Looks.idOf("nerocompanion", "aurora.json").toString());
        assertEquals("nerocompanion:carry_bot/default", Looks.defaultSkin(LookTestSupport.id("carry_bot")).toString());
    }
}
