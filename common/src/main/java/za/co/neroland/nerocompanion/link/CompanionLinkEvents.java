package za.co.neroland.nerocompanion.link;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerocompanion.alert.AlertText;

/**
 * NeroCompanion's five notifying link events ({@code LOW_BATTERY}, {@code LOST}, {@code RECOVERED},
 * {@code SALVAGE_HELD}, {@code BOND_LEVEL}), topic {@value #TOPIC}, every one {@link LinkEvent#forPlayer
 * player-scoped} to the companion's stored owner UUID (never a broadcast, never a nearby player).
 *
 * <p><b>Order is the contract.</b> NeroLink 1.2.x turns every player-scoped event of an opted-in module into a phone
 * notification when the player is not watching live: a rich one when the payload carries {@value #NOTIFY}, a generic
 * "update" one when it does not. Core's {@code LinkAlerts.raise} publishes its own {@code alerts} event (no
 * {@value #NOTIFY}), and the bridge drops that generic push only inside two seconds after a rich push for the same
 * player and module. So each notice is published <b>with {@value #NOTIFY} first, then</b> the persisted Core alert is
 * raised: one rich push, no generic one. No roster deltas are ever published (the app polls).
 *
 * <p><b>Coalescing.</b> One notice per kind per companion per {@value #COOLDOWN_MS} ms: inside the window nothing is
 * published <em>and</em> the alert is not re-raised (its id is stable, so the stored alert from the first notice still
 * stands; re-raising it would produce exactly the generic push the window exists to avoid).
 *
 * <p><b>Opt-out.</b> When the owner turned push off ({@code /nscomp privacy push off}) neither the event is published
 * nor the alert raised: on bridge 1.2.x a raised alert is itself a generic push, so skipping only the event would not
 * honour the opt-out. Such an owner sees no NeroCompanion alerts in the app (the roster still shows the truth). With
 * push on, the app's category switch (off by default) still decides whether anything reaches the phone.
 *
 * <p><b>Data minimisation.</b> Notification text is the companion's type and its own filtered name, plus the minutes
 * left for {@code SALVAGE_HELD}; the deep link names only the companion id. Never a position, a dimension, a player
 * id or another player's name. The cooldown keys live in memory only and are forgotten on erasure.
 */
public final class CompanionLinkEvents {

    /** The topic every notice is published on. */
    public static final String TOPIC = "events";
    /** The payload member the bridge reads push copy from ({@code NotifyCopy.FIELD} in NeroLink). */
    public static final String NOTIFY = "_notify";
    /** One notice per kind per companion in this window. */
    public static final long COOLDOWN_MS = 60_000L;
    /** The bridge's caps ({@code NotifyCopy.TITLE_MAX/BODY_MAX/LINK_MAX}). */
    public static final int TITLE_MAX = 120;
    public static final int BODY_MAX = 240;
    public static final int LINK_MAX = 256;
    /** The bridge substitutes its relay id here. */
    public static final String SERVER_PLACEHOLDER = "{server}";
    /** The bridge's {@code NotifyCopy.DEEP_LINK}, checked here so a bad link never leaves the mod. */
    public static final Pattern DEEP_LINK = Pattern.compile(
            "^nerolink://server/[A-Za-z0-9_-]{1,64}/[a-z0-9_-]{1,32}(/[A-Za-z0-9_-]{1,64}){0,2}$");
    static final int MAX_KEYS = 8192;

    /** One thing worth telling an owner about. */
    public record Notice(AlertText.Kind kind, UUID owner, UUID companionId, Identifier type, Optional<String> name,
            OptionalInt minutesLeft, OptionalInt level) {

        public Notice {
            name = name == null ? Optional.empty() : name;
            minutesLeft = minutesLeft == null ? OptionalInt.empty() : minutesLeft;
            level = level == null ? OptionalInt.empty() : level;
        }
    }

    /** What the pipeline talks to (the live server, or a test). */
    public interface Env {
        long nowMillis();

        /** The owner's own push opt-out ({@code CompanionPrivacy.pushAllowed}). */
        boolean pushAllowed(UUID owner);

        /** Core's link event bus. */
        void publish(LinkEvent event);

        /** Core's persisted per-player alert. */
        void raiseAlert(UUID owner, String id, String severity, String body);
    }

    private static final Pipeline PIPELINE = new Pipeline();

    private CompanionLinkEvents() {
    }

    /** The shared pipeline (server thread). */
    public static Pipeline pipeline() {
        return PIPELINE;
    }

    /** Erasure: forget every cooldown key of {@code owner}. */
    public static void forget(UUID owner) {
        PIPELINE.forget(owner);
    }

    public static void clear() {
        PIPELINE.clear();
    }

    /** The coalescing, ordering and opt-out rules over an {@link Env}. */
    public static final class Pipeline {

        private final LinkedHashMap<String, Long> last = new LinkedHashMap<>();

        /**
         * Sends {@code notice}: its rich event first, then the alert.
         *
         * @return false when the owner opted out of push or the notice fell inside its cooldown window: nothing sent
         */
        public boolean fire(Env env, Notice notice) {
            if (env == null || notice == null || notice.owner() == null || notice.companionId() == null) {
                return false;
            }
            long now = env.nowMillis();
            boolean push;
            try {
                push = env.pushAllowed(notice.owner());
            } catch (RuntimeException failure) {
                push = false; // when in doubt, nothing
            }
            if (!push) {
                // The owner's opt-out covers every push from NeroCompanion: on bridge 1.2.x the Core alert would
                // itself become a generic push, so it is not raised either (decision 014 MAJOR-1, option b).
                return false;
            }
            if (!this.admit(notice, now)) {
                return false;
            }
            String typeName = AlertText.typeName(notice.type() == null ? "" : notice.type().getPath());
            try {
                env.publish(LinkEvent.forPlayer(CompanionLinkModule.MODULE_ID, TOPIC, notice.owner(),
                        payload(notice, typeName, now)));
            } catch (RuntimeException | LinkageError failure) {
                // An event is never worth losing the alert over.
            }
            env.raiseAlert(notice.owner(), AlertText.id(notice.kind(), notice.companionId()),
                    AlertText.severity(notice.kind()), AlertText.body(notice.kind(), typeName, notice.name()));
            return true;
        }

        private synchronized boolean admit(Notice notice, long now) {
            String key = notice.owner() + "|" + notice.kind().id() + "|" + notice.companionId();
            Long at = this.last.get(key);
            if (at != null && now >= at && now - at < COOLDOWN_MS) {
                return false;
            }
            if (this.last.size() >= MAX_KEYS) {
                this.last.values().removeIf(t -> now - t >= COOLDOWN_MS || now < t);
                Iterator<String> oldest = this.last.keySet().iterator();
                while (this.last.size() >= MAX_KEYS && oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            this.last.remove(key);
            this.last.put(key, now);
            return true;
        }

        public synchronized void forget(UUID owner) {
            String prefix = owner + "|";
            this.last.keySet().removeIf(k -> k.startsWith(prefix));
        }

        public synchronized boolean holdsAnyFor(UUID owner) {
            String prefix = owner + "|";
            return this.last.keySet().stream().anyMatch(k -> k.startsWith(prefix));
        }

        public synchronized void clear() {
            this.last.clear();
        }
    }

    // =================================================================================================
    // Payload and push copy (the only builder of notification text)
    // =================================================================================================

    /** The event payload: {@code type}, {@code at}, {@code companionId}, {@code chassis}, extras, {@code _notify}. */
    static JsonObject payload(Notice notice, String typeName, long now) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", notice.kind().name());
        payload.addProperty("at", now);
        payload.addProperty("companionId", notice.companionId().toString());
        payload.addProperty("chassis", notice.type() == null ? "" : notice.type().toString());
        notice.minutesLeft().ifPresent(m -> payload.addProperty("minutesLeft", Math.max(0, m)));
        notice.level().ifPresent(l -> payload.addProperty("level", l));
        payload.add(NOTIFY, notify(notice, typeName));
        return payload;
    }

    /** {@code {title, body, deepLink}} for the bridge's {@code NotifyCopy.resolve}. */
    static JsonObject notify(Notice notice, String typeName) {
        JsonObject out = new JsonObject();
        out.addProperty("title", cap(title(notice.kind()), TITLE_MAX));
        out.addProperty("body", cap(body(notice, typeName), BODY_MAX));
        String link = deepLink(notice.companionId());
        if (link != null) {
            out.addProperty("deepLink", link);
        }
        return out;
    }

    static String title(AlertText.Kind kind) {
        return switch (kind) {
            case LOW_BATTERY -> "Low battery";
            case LOST -> "Companion lost";
            case RECOVERED -> "Companion back";
            case SALVAGE_HELD -> "Your things are safe";
            case BOND_LEVEL -> "Bond grew";
        };
    }

    /** Type and filtered name; for salvage also the minutes left. Nothing else can reach this text. */
    static String body(Notice notice, String typeName) {
        if (notice.kind() == AlertText.Kind.SALVAGE_HELD && notice.minutesLeft().isPresent()) {
            int minutes = Math.max(0, notice.minutesLeft().getAsInt());
            String who = notice.name().filter(n -> !n.isBlank()).map(n -> "Your " + typeName + " " + n)
                    .orElse("Your " + typeName);
            return who + " is holding your things: " + minutes + (minutes == 1 ? " minute" : " minutes") + " left.";
        }
        return AlertText.body(notice.kind(), typeName, notice.name());
    }

    /** {@code nerolink://server/{server}/nerocompanion/<companionId>}, or null if it would not pass the bridge. */
    @Nullable
    static String deepLink(UUID companionId) {
        if (companionId == null) {
            return null;
        }
        String link = "nerolink://server/" + SERVER_PLACEHOLDER + "/" + CompanionLinkModule.MODULE_ID + "/"
                + companionId;
        String probe = link.replace(SERVER_PLACEHOLDER, "x");
        return link.length() <= LINK_MAX && DEEP_LINK.matcher(probe).matches() ? link : null;
    }

    /** Control and format characters removed, trimmed, cut to {@code max} without splitting a surrogate pair. */
    static String cap(String text, int max) {
        StringBuilder out = new StringBuilder();
        (text == null ? "" : text).codePoints().filter(cp -> {
            int type = Character.getType(cp);
            return type != Character.CONTROL && type != Character.FORMAT && type != Character.LINE_SEPARATOR
                    && type != Character.PARAGRAPH_SEPARATOR;
        }).forEach(out::appendCodePoint);
        String clean = out.toString().trim();
        if (clean.length() <= max) {
            return clean;
        }
        int end = max;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) {
            end--;
        }
        return clean.substring(0, end).trim();
    }
}
