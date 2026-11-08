package za.co.neroland.nerocompanion.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.compat.neroeconomy.NeroEconomyCompat;
import za.co.neroland.nerocompanion.compat.nerocolonies.NeroColoniesCompat;
import za.co.neroland.nerocompanion.compat.nerofactions.NeroFactionsCompat;
import za.co.neroland.nerocompanion.compat.nerologistics.NeroLogisticsCompat;
import za.co.neroland.nerocompanion.compat.nerosecurity.NeroSecurityCompat;
import za.co.neroland.nerocompanion.compat.nerospace.NerospaceCompat;
import za.co.neroland.nerocompanion.compat.nerospace.OffWorld;
import za.co.neroland.nerocompanion.compat.nerotech.NerotechCompat;
import za.co.neroland.nerocompanion.economy.RequestKeys;

/**
 * Core-only safety for every sibling façade (decision 020). The test classpath carries no sibling class at all (the
 * artifacts are compileOnly and the stubs are main-compile only), so this is a Core-only server: every façade's
 * {@code init()} must find nothing, register nothing, and every no-op path must answer without loading a sibling
 * class (a load would throw {@code NoClassDefFoundError} here). Then the compiled classes are scanned: only each
 * sibling's bridge names that sibling, only its façade names the bridge, and no sibling class is packaged.
 */
class FacadeIsolationTest {

    /** Sibling package -> the one class allowed to name it -> the classes allowed to name that bridge. */
    private static final Map<String, List<String>> BRIDGES = Map.of(
            "za/co/neroland/nerocolonies/", List.of("compat/nerocolonies/NeroColoniesBridge",
                    "compat/nerocolonies/NeroColoniesCompat"),
            "za/co/neroland/nerofactions/", List.of("compat/nerofactions/NeroFactionsBridge",
                    "compat/nerofactions/NeroFactionsCompat"),
            "za/co/neroland/nerosecurity/", List.of("compat/nerosecurity/NeroSecurityBridge",
                    "compat/nerosecurity/NeroSecurityCompat"),
            "za/co/neroland/nerologistics/", List.of("compat/nerologistics/NeroLogisticsBridge",
                    "compat/nerologistics/NeroLogisticsCompat"),
            "za/co/neroland/nerospace/", List.of("compat/nerospace/NerospaceBridge",
                    "compat/nerospace/NerospaceCompat"),
            "za/co/neroland/neroeconomy/", List.of("economy/NeroEconomyBridge", "economy/CompanionEconomy"),
            // Nerotech: a documented no-op seam, nothing may name it at all.
            "za/co/neroland/nerotech/", List.of("compat/nerotech/-none-", "compat/nerotech/-none-"));

    private static final String OURS = "za/co/neroland/nerocompanion/";

    @Test
    void coreOnlyInitFindsNothingAndRegistersNoProvider() {
        int before = OwnerAuthority.providerCount();
        NeroColoniesCompat.init();
        NeroSecurityCompat.init();
        NeroFactionsCompat.init();
        NeroLogisticsCompat.init();
        NerospaceCompat.init();
        assertFalse(NeroColoniesCompat.isActive());
        assertFalse(NeroSecurityCompat.isActive());
        assertFalse(NeroFactionsCompat.isActive());
        assertFalse(NeroLogisticsCompat.isActive());
        assertFalse(NerospaceCompat.isActive());
        assertEquals(before, OwnerAuthority.providerCount(), "authority providers only when the sibling is loaded");
        assertFalse(SiblingCompat.isLoaded("nerocolonies"), "no platform here: nothing reads as installed");
    }

    @Test
    void noOpPathsAnswerWithoutTheSibling() {
        assertNull(NeroLogisticsCompat.driveBayInlet(null, null, null), "no drive bay without NeroLogistics");
        assertFalse(NeroLogisticsCompat.registerEndpoint(null), "endpoint registration is the 0.7.0 seam");
        assertFalse(NerotechCompat.wirelessChargingNode(), "Nerotech has no wireless charging node");
        assertFalse(NerotechCompat.isLoaded());
        assertEquals(OffWorld.EARTH, NerospaceCompat.environment(null, null), "Core-only: every level is Earth");
        assertFalse(NeroEconomyCompat.shopOpen(), "no shop without NeroEconomy");
        assertEquals(0L, NeroEconomyCompat.reclaimCost(25), "no real currency provider: reclaims are free");
        assertEquals(0L, NeroEconomyCompat.reclaimCost(0));
    }

    @Test
    void purchaseRecordsAndKeysNameTheLookNeverThePlayer() {
        Identifier look = Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot/courier");
        assertEquals("nerocompanion:purchase/nerocompanion/carry_bot/courier",
                NeroEconomyCompat.purchaseGate(look).toString());
        for (long nonce : new long[] {0L, 1L, -1L, Long.MAX_VALUE, 0x1234_5678_9ABC_DEF0L}) {
            String key = NeroEconomyCompat.requestKey(look, nonce);
            assertTrue(RequestKeys.isValid(key), key);
            assertTrue(key.startsWith("look/nerocompanion:carry_bot/courier#"), key);
        }
        Identifier longLook = Identifier.fromNamespaceAndPath("some_pack", "a/very/long/path/to/a/look/that/goes/on");
        assertTrue(RequestKeys.isValid(NeroEconomyCompat.requestKey(longLook, 42L)), "falls back when too long");
    }

    @Test
    void onlyTheBridgeNamesASiblingAndOnlyTheFacadeNamesTheBridge() throws Exception {
        Path root = Path.of(OwnerAuthority.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "tests run against the main classes directory: " + root);
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                scanned++;
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                for (Map.Entry<String, List<String>> entry : BRIDGES.entrySet()) {
                    String sibling = entry.getKey();
                    String bridge = OURS + entry.getValue().get(0);
                    String facade = OURS + entry.getValue().get(1);
                    if (rel.startsWith(sibling)) {
                        offenders.add(rel + " (a sibling class is packaged)");
                    }
                    boolean isBridge = rel.equals(bridge + ".class") || rel.startsWith(bridge + "$");
                    if (bytes.contains(sibling) && !isBridge) {
                        offenders.add(rel + " names " + sibling);
                    }
                    boolean mayNameBridge = isBridge || rel.equals(facade + ".class") || rel.startsWith(facade + "$");
                    if (bytes.contains(bridge) && !mayNameBridge) {
                        offenders.add(rel + " names " + bridge);
                    }
                }
            }
        }
        assertTrue(scanned > 100, "scanned the main classes (" + scanned + ")");
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }
}
