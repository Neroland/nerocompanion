package za.co.neroland.nerocompanion.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.type.ValidationIssue;

/** Every shipped skin and hat file parses clean, and the shipped set is the 0.5.0 one (010 sections 6.2, 6.3). */
class ShippedLooksTest {

    private static final Set<Identifier> TYPES = Set.of(id("personal_drone"), id("scout_drone"), id("mining_bot"),
            id("carry_bot"), id("repair_bot"), id("combat_bot"), id("scrapling"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nerocompanion", path);
    }

    /** {@code common/src/main/resources/data/nerocompanion}, found by walking up from the working directory. */
    private static Path dataRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("common/src/main/resources/data/nerocompanion");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Look> load(Path root, LookKind kind, List<ValidationIssue> issues) throws IOException {
        List<Look> out = new ArrayList<>();
        Path dir = root.resolve(kind.directory());
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String relative = dir.relativize(file).toString().replace('\\', '/');
                Identifier lookId = Looks.idOf("nerocompanion", relative);
                LookParser.parse(lookId, kind, JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)),
                        TYPES, Looks.MODEL_OVERRIDES, issues).ifPresent(out::add);
            }
        }
        return out;
    }

    @Test
    void shippedLooksAreClean() throws IOException {
        Path root = dataRoot();
        Assumptions.assumeTrue(root != null, "source tree not reachable from the test working directory");
        List<ValidationIssue> issues = new ArrayList<>();
        List<Look> skins = load(root, LookKind.SKIN, issues);
        List<Look> hats = load(root, LookKind.HAT, issues);
        assertEquals(List.of(), issues);
        assertEquals(17, skins.size());
        assertEquals(10, hats.size());
        Set<String> withDefault = new HashSet<>();
        for (Look skin : skins) {
            if (skin.isDefault()) {
                withDefault.addAll(skin.types());
            }
        }
        assertEquals(7, withDefault.size());
        assertTrue(skins.stream().anyMatch(s -> s.family() && s.frames() == 8));
        assertTrue(skins.stream().anyMatch(s -> "scrapling/mossback".equals(s.modelOverride())));
    }
}
