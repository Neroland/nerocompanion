package za.co.neroland.nerocompanion.compat.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.compat.CompanionPetHooks;

/**
 * The NeroCreatures SPI implementation: registered for ServiceLoader, constructible with no side effects ("with"
 * NeroCreatures), and unreachable from every other NeroCompanion class ("without" NeroCreatures nothing can load
 * it, so the mod never needs NeroCreatures at runtime).
 */
class CompanionPetHooksImplTest {

    private static final String SERVICE = "/META-INF/services/za.co.neroland.nerocreatures.compat.CompanionPetHooks";

    private static List<String> serviceLines() throws IOException {
        try (InputStream in = CompanionPetHooksImplTest.class.getResourceAsStream(SERVICE)) {
            assertNotNull(in, "service file is packaged with the main resources");
            List<String> out = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    String trimmed = line.strip();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        out.add(trimmed);
                    }
                }
            }
            return out;
        }
    }

    @Test
    void serviceFileNamesTheImplementation() throws Exception {
        assertEquals(List.of(CompanionPetHooksImpl.class.getName()), serviceLines());
        assertEquals(CreaturesCompat.HOOKS_IMPL, CompanionPetHooksImpl.class.getName());
        Class<?> named = Class.forName(serviceLines().get(0), false, getClass().getClassLoader());
        assertTrue(CompanionPetHooks.class.isAssignableFrom(named));
        assertTrue(Modifier.isPublic(named.getModifiers()));
        assertTrue(Modifier.isPublic(named.getConstructor().getModifiers()), "ServiceLoader needs a public no-arg ctor");
    }

    @Test
    void constructorIsSideEffectFree() throws Exception {
        CreaturePets.clear();
        CompanionPetHooks hooks = new CompanionPetHooksImpl();
        assertEquals(0, CreaturePets.size());
        for (Field field : CompanionPetHooksImpl.class.getDeclaredFields()) {
            assertTrue(field.isSynthetic() || !Modifier.isStatic(field.getModifiers()),
                    "no static state to initialise: " + field.getName());
        }
        assertEquals(0, CompanionPetHooksImpl.class.getDeclaredFields().length
                - (int) Stream.of(CompanionPetHooksImpl.class.getDeclaredFields()).filter(Field::isSynthetic).count(),
                "no instance state either");
        // Callbacks with nothing to act on are no-ops, and the idle behaviour stays NeroCreatures'.
        hooks.onPetTamed(null, null);
        hooks.onPetCommandChanged(null, "sit");
        hooks.onPetReleased(null);
        assertFalse(hooks.overridesIdleBehaviour(null));
        assertEquals(0, CreaturePets.size());
    }

    @Test
    void noOtherClassNamesNeroCreaturesOrTheImplementation() throws Exception {
        Path root = classesRoot();
        String self = CompanionPetHooksImpl.class.getName().replace('.', '/') + ".class";
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                scanned++;
                if (rel.equals(self)) {
                    continue;
                }
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                if (rel.startsWith("za/co/neroland/nerocreatures/")) {
                    offenders.add(rel + " (a NeroCreatures class is packaged)");
                } else if (bytes.contains("za/co/neroland/nerocreatures/")) {
                    offenders.add(rel + " (names a NeroCreatures type)");
                } else if (bytes.contains("za/co/neroland/nerocompanion/compat/creatures/CompanionPetHooksImpl")) {
                    offenders.add(rel + " (names the SPI implementation)");
                }
            }
        }
        assertTrue(scanned > 50, "scanned the main classes (" + scanned + ")");
        assertTrue(offenders.isEmpty(), offenders::toString);
    }

    private static Path classesRoot() throws URISyntaxException {
        Path location = Path.of(CompanionPetHooksImpl.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        assertTrue(Files.isDirectory(location), "tests run against the main classes directory: " + location);
        return location;
    }
}
