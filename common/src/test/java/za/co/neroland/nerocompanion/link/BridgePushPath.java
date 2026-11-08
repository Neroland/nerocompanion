package za.co.neroland.nerocompanion.link;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import za.co.neroland.nerolandcore.link.LinkEvent;

/**
 * A test copy of the NeroLink bridge's push path, so NeroCompanion can prove what reaches a phone without depending
 * on the bridge. Copied from NeroLink <b>1.2.2</b>: {@code za.co.neroland.nerolink.relay.NotifyCopy}
 * ({@code shouldPush}, {@code resolve}, {@code text}, {@code deepLink}, {@code RichPushShadow}, 2-second shadow) and
 * the order of checks in {@code RelayClient.NotifyForwarder.accept} (broadcast, player, category opt-in, live
 * connection, {@code shouldPush}, resolve, rich shadow). Re-copy if the bridge changes these.
 */
final class BridgePushPath {

    /** One push the relay would be sent. */
    record Push(UUID player, String category, String title, String body, String deepLink, boolean rich) {
    }

    record Copy(String title, String body, String deepLink, boolean rich) {
    }

    static final String FIELD = "_notify";
    static final int TITLE_MAX = 120;
    static final int BODY_MAX = 240;
    static final int LINK_MAX = 256;
    static final String SERVER_PLACEHOLDER = "{server}";
    static final Pattern DEEP_LINK = Pattern.compile(
            "^nerolink://server/[A-Za-z0-9_-]{1,64}/[a-z0-9_-]{1,32}(/[A-Za-z0-9_-]{1,64}){0,2}$");
    static final long RICH_SHADOW_MS = 2_000L;

    final String serverId;
    final Set<String> enabled = new HashSet<>();
    final Set<UUID> watchingLive = new HashSet<>();
    final List<Push> pushes = new ArrayList<>();
    private final Map<String, Long> lastRich = new HashMap<>();

    BridgePushPath(String serverId) {
        this.serverId = serverId;
    }

    void enable(UUID player, String category) {
        enabled.add(player + "|" + category);
    }

    /** {@code NotifyForwarder.accept} at wall-clock {@code now}. */
    void accept(LinkEvent event, long now) {
        if (event == null || event.isBroadcast()) {
            return;
        }
        UUID player = event.playerId();
        String category = event.moduleId();
        if (!enabled.contains(player + "|" + category)) {
            return;
        }
        if (watchingLive.contains(player)) {
            return;
        }
        if (!shouldPush(event)) {
            return;
        }
        Copy copy = resolve(event, serverId);
        String key = player + "|" + category;
        if (copy.rich()) {
            lastRich.put(key, now);
        } else {
            Long at = lastRich.get(key);
            if (at != null && now >= at && now - at < RICH_SHADOW_MS) {
                return;
            }
        }
        pushes.add(new Push(player, category, copy.title(), copy.body(), copy.deepLink(), copy.rich()));
    }

    static Copy fallback(LinkEvent e) {
        return new Copy("NeroLink — " + e.moduleId(), e.topic() + " update", null, false);
    }

    static Copy resolve(LinkEvent e, String serverId) {
        Copy base = fallback(e);
        JsonObject payload = e.payload();
        if (payload == null) {
            return base;
        }
        JsonElement raw = payload.get(FIELD);
        if (raw == null || !raw.isJsonObject()) {
            return base;
        }
        JsonObject n = raw.getAsJsonObject();
        String title = text(n.get("title"), TITLE_MAX);
        String body = text(n.get("body"), BODY_MAX);
        String link = deepLink(n.get("deepLink"), serverId);
        boolean rich = title != null || body != null;
        return new Copy(title != null ? title : base.title(), body != null ? body : base.body(), link, rich);
    }

    static boolean shouldPush(LinkEvent e) {
        if (!"alerts".equals(e.topic())) {
            return true;
        }
        JsonObject payload = e.payload();
        JsonElement verb = payload == null ? null : payload.get("event");
        if (verb == null || verb.isJsonNull()) {
            return true;
        }
        return isString(verb) && "raised".equals(verb.getAsString());
    }

    static String text(JsonElement el, int max) {
        if (!isString(el)) {
            return null;
        }
        String s = el.getAsString().trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int type = Character.getType(c);
            if (type != Character.CONTROL && type != Character.FORMAT) {
                sb.append(c);
            }
        }
        String out = sb.length() <= max ? sb.toString() : sb.substring(0,
                Character.isHighSurrogate(sb.charAt(max - 1)) ? max - 1 : max);
        return out.isBlank() ? null : out;
    }

    static String deepLink(JsonElement el, String serverId) {
        if (!isString(el)) {
            return null;
        }
        String link = el.getAsString();
        if (link.contains(SERVER_PLACEHOLDER)) {
            if (serverId == null || serverId.isBlank()) {
                return null;
            }
            link = link.replace(SERVER_PLACEHOLDER, serverId);
        }
        if (link.length() > LINK_MAX || !DEEP_LINK.matcher(link).matches()) {
            return null;
        }
        return link;
    }

    private static boolean isString(JsonElement el) {
        return el instanceof JsonPrimitive p && p.isString();
    }
}
