package za.co.neroland.nerocompanion.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerocompanion.alert.AlertText;

/**
 * The five notices: rich event (with {@code _notify}) first, Core alert second; 60-second coalescing; push opt-out;
 * data-minimised copy; and, through a copy of the bridge's push path, exactly one rich push per notice.
 */
class CompanionLinkEventsTest {

    private static final Identifier MINING_BOT = Identifier.fromNamespaceAndPath("nerocompanion", "mining_bot");
    private static final Pattern UUID_LIKE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-");

    private final UUID owner = UUID.randomUUID();
    private final UUID companion = UUID.randomUUID();
    private final CompanionLinkEvents.Pipeline pipeline = new CompanionLinkEvents.Pipeline();
    private final RecordingEnv env = new RecordingEnv();

    /** Records the order of calls; simulates Core's alerts event the way {@code LinkAlerts.publish} builds it. */
    private static final class RecordingEnv implements CompanionLinkEvents.Env {
        final AtomicLong clock = new AtomicLong(1_000_000L);
        final List<String> order = new ArrayList<>();
        final List<LinkEvent> bus = new ArrayList<>();
        final List<String[]> alerts = new ArrayList<>();
        final Set<UUID> optedOut = new HashSet<>();
        BridgePushPath bridge;

        @Override
        public long nowMillis() {
            return clock.get();
        }

        @Override
        public boolean pushAllowed(UUID owner) {
            return !optedOut.contains(owner);
        }

        @Override
        public void publish(LinkEvent event) {
            order.add("event");
            bus.add(event);
            if (bridge != null) {
                bridge.accept(event, clock.get());
            }
        }

        @Override
        public void raiseAlert(UUID owner, String id, String severity, String body) {
            order.add("alert");
            alerts.add(new String[] {id, severity, body});
            JsonObject delta = new JsonObject();
            delta.addProperty("id", id);
            delta.addProperty("module", "nerocompanion");
            delta.addProperty("severity", severity);
            delta.addProperty("text", body);
            delta.addProperty("at", clock.get());
            delta.addProperty("acked", false);
            delta.addProperty("event", "raised");
            LinkEvent event = LinkEvent.forPlayer("nerocompanion", "alerts", owner, delta);
            bus.add(event);
            if (bridge != null) {
                bridge.accept(event, clock.get() + 1L);
            }
        }
    }

    private CompanionLinkEvents.Notice notice(AlertText.Kind kind) {
        return new CompanionLinkEvents.Notice(kind, owner, companion, MINING_BOT, Optional.of("Digger"),
                kind == AlertText.Kind.SALVAGE_HELD ? OptionalInt.of(12) : OptionalInt.empty(),
                kind == AlertText.Kind.BOND_LEVEL ? OptionalInt.of(3) : OptionalInt.empty());
    }

    @Test
    void everyNoticeIsARichEventFirstThenTheAlert() {
        for (AlertText.Kind kind : AlertText.Kind.values()) {
            env.order.clear();
            env.bus.clear();
            assertTrue(pipeline.fire(env, notice(kind)), kind.name());
            assertEquals(List.of("event", "alert"), env.order, kind.name());
            LinkEvent event = env.bus.get(0);
            assertEquals("nerocompanion", event.moduleId());
            assertEquals(CompanionLinkEvents.TOPIC, event.topic());
            assertEquals(owner, event.playerId());
            assertFalse(event.isBroadcast());
            assertEquals(kind.name(), event.payload().get("type").getAsString());
            JsonObject notify = event.payload().getAsJsonObject("_notify");
            assertNotNull(notify, kind.name());
            assertFalse(notify.get("title").getAsString().isBlank());
            assertEquals("nerolink://server/{server}/nerocompanion/" + companion,
                    notify.get("deepLink").getAsString());
        }
    }

    @Test
    void pushBodiesAreTypeAndNameOnly() {
        for (AlertText.Kind kind : AlertText.Kind.values()) {
            String body = CompanionLinkEvents.body(notice(kind), "Mining Bot");
            assertTrue(body.contains("Mining Bot") && body.contains("Digger"), body);
            assertFalse(UUID_LIKE.matcher(body).find(), body);
            String withoutMinutes = body.replace("12 minutes", "");
            assertFalse(withoutMinutes.matches(".*\\d.*"), "no number but the minutes: " + body);
            assertFalse(body.toLowerCase().contains("overworld") || body.contains("minecraft:"), body);
            // Swap the name: nothing else changes, so no other input can be in it.
            String other = CompanionLinkEvents.body(new CompanionLinkEvents.Notice(kind, UUID.randomUUID(),
                    UUID.randomUUID(), MINING_BOT, Optional.of("Bolt"), notice(kind).minutesLeft(),
                    notice(kind).level()), "Mining Bot");
            assertEquals(body.replace("Digger", "#"), other.replace("Bolt", "#"));
        }
        assertTrue(CompanionLinkEvents.body(notice(AlertText.Kind.SALVAGE_HELD), "Mining Bot")
                .contains("12 minutes left"));
    }

    @Test
    void aBurstIsCoalescedByNotPublishing() {
        assertTrue(pipeline.fire(env, notice(AlertText.Kind.LOW_BATTERY)));
        env.clock.addAndGet(30_000L);
        assertFalse(pipeline.fire(env, notice(AlertText.Kind.LOW_BATTERY)));
        // Another kind, or another companion, is not held back.
        assertTrue(pipeline.fire(env, notice(AlertText.Kind.LOST)));
        assertTrue(pipeline.fire(env, new CompanionLinkEvents.Notice(AlertText.Kind.LOW_BATTERY, owner,
                UUID.randomUUID(), MINING_BOT, Optional.empty(), OptionalInt.empty(), OptionalInt.empty())));
        assertEquals(3, env.alerts.size());
        for (LinkEvent event : env.bus) {
            if (!"alerts".equals(event.topic())) {
                assertTrue(event.payload().has("_notify"), "never a _notify-less companion event");
            }
        }
        env.clock.addAndGet(CompanionLinkEvents.COOLDOWN_MS);
        assertTrue(pipeline.fire(env, notice(AlertText.Kind.LOW_BATTERY)));
    }

    @Test
    void theOwnersPushOptOutSendsNothingAtAll() {
        BridgePushPath bridge = new BridgePushPath("srv-1");
        bridge.enable(owner, "nerocompanion"); // the app category is on: the in-mod opt-out must still win
        env.bridge = bridge;
        env.optedOut.add(owner);
        for (AlertText.Kind kind : AlertText.Kind.values()) {
            assertFalse(pipeline.fire(env, notice(kind)), kind.name());
        }
        assertTrue(env.order.isEmpty(), "neither event nor alert: " + env.order);
        assertTrue(env.bus.isEmpty());
        assertTrue(env.alerts.isEmpty());
        assertTrue(bridge.pushes.isEmpty(), "no push of any kind: " + bridge.pushes);
        // An opted-out notice does not start a cooldown either.
        assertFalse(pipeline.holdsAnyFor(owner));
    }

    @Test
    void withPushOnTheAppCategoryIsTheMasterSwitch() {
        BridgePushPath bridge = new BridgePushPath("srv-1");
        env.bridge = bridge;
        assertTrue(pipeline.fire(env, notice(AlertText.Kind.LOST)));
        assertEquals(List.of("event", "alert"), env.order);
        assertEquals(1, env.alerts.size(), "the alert is kept for the app's list");
        assertTrue(bridge.pushes.isEmpty(), "category off: no push");
        bridge.enable(owner, "nerocompanion");
        assertTrue(pipeline.fire(env, notice(AlertText.Kind.RECOVERED)));
        assertEquals(1, bridge.pushes.size());
        assertTrue(bridge.pushes.get(0).rich());
    }

    @Test
    void erasureForgetsTheCooldownKeys() {
        pipeline.fire(env, notice(AlertText.Kind.LOW_BATTERY));
        assertTrue(pipeline.holdsAnyFor(owner));
        pipeline.forget(owner);
        assertFalse(pipeline.holdsAnyFor(owner));
    }

    @Test
    void lowBatteryReachesTheBridgePushPathOnceRichWithAPersistedAlert() {
        BridgePushPath bridge = new BridgePushPath("srv-1");
        bridge.enable(owner, "nerocompanion");
        env.bridge = bridge;

        assertTrue(pipeline.fire(env, notice(AlertText.Kind.LOW_BATTERY)));

        // Persisted: Core's alert store was written with the stable id and the minimised text.
        assertEquals(1, env.alerts.size());
        assertEquals(AlertText.id(AlertText.Kind.LOW_BATTERY, companion), env.alerts.get(0)[0]);
        assertEquals("WARN", env.alerts.get(0)[1]);
        // Exactly one push, the rich one; the alert's generic push fell inside the bridge's 2-second shadow.
        assertEquals(1, bridge.pushes.size(), bridge.pushes.toString());
        BridgePushPath.Push push = bridge.pushes.get(0);
        assertTrue(push.rich());
        assertEquals("nerocompanion", push.category());
        assertEquals("Low battery", push.title());
        assertEquals("Your Mining Bot Digger is low on battery.", push.body());
        assertEquals("nerolink://server/srv-1/nerocompanion/" + companion, push.deepLink());
    }

    @Test
    void theBridgeSendsNothingWhenTheCategoryIsOffOrThePlayerIsWatching() {
        BridgePushPath bridge = new BridgePushPath("srv-1");
        env.bridge = bridge;
        pipeline.fire(env, notice(AlertText.Kind.LOST));
        assertTrue(bridge.pushes.isEmpty(), "category off by default");

        bridge.enable(owner, "nerocompanion");
        bridge.watchingLive.add(owner);
        pipeline.fire(env, notice(AlertText.Kind.RECOVERED));
        assertTrue(bridge.pushes.isEmpty(), "watching live");
    }

    @Test
    void theDeepLinkPassesTheBridgesPattern() {
        String link = CompanionLinkEvents.deepLink(companion);
        assertNotNull(link);
        assertEquals("nerolink://server/abc/nerocompanion/" + companion,
                BridgePushPath.deepLink(new com.google.gson.JsonPrimitive(link), "abc"));
    }
}
