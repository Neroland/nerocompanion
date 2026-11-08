package za.co.neroland.nerocompanion.link;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.LedgerEntry.State;

/**
 * The write half of the link module (decision 013): one action, {@code companion}, with four ops.
 *
 * <ul>
 *   <li>{@code set_mode} {@code {mode}} - owner online, companion live and loaded, mode supported (the same
 *       validation as the in-game mode buttons);</li>
 *   <li>{@code recall} - companion live and loaded: parks it in its home Bay if a slot is free, else refuses
 *       ({@code no_bay}); cargo stays in the parked record, nothing is put into any inventory. Owner may be offline;</li>
 *   <li>{@code reclaim} - companion lost: restored parked in its home Bay if a slot is free (same ledger transition
 *       and optional cost as {@code /nscomp recover}), else {@code no_bay}. Owner may be offline;</li>
 *   <li>{@code rename} {@code {name}} - owner online (the server's text filter is asked of the live player), the same
 *       clean + 24-character cap + filter as in-game.</li>
 * </ul>
 *
 * <p>Every call: request body at most {@value #MAX_BODY_BYTES} bytes; one per-player bucket of
 * {@value za.co.neroland.nerocompanion.network.RateLimiter#LINK_PER_MINUTE} actions a minute in the in-game
 * limiter; the companion must be the requester's by the owner UUID in the Recovery Ledger. A companion that is not
 * theirs answers exactly like one that does not exist ({@code NOT_OWNER}), so the action cannot probe other players'
 * companions. Nothing is trusted from the request beyond the player UUID the bridge authenticated.
 */
public final class CompanionLinkActions implements LinkActionHandler {

    /** Largest accepted request body (UTF-8 bytes of the params object). */
    public static final int MAX_BODY_BYTES = 2048;
    /** Longest raw name accepted before cleaning (the stored name is capped at 24 characters). */
    static final int MAX_RAW_NAME = 128;

    public static final String OP_SET_MODE = "set_mode";
    public static final String OP_RECALL = "recall";
    public static final String OP_RECLAIM = "reclaim";
    public static final String OP_RENAME = "rename";

    /** The result of one op on the live server. */
    public record Outcome(boolean ok, String reason, boolean pending) {

        public static Outcome done() {
            return new Outcome(true, "", false);
        }

        public static Outcome later() {
            return new Outcome(true, "", true);
        }

        public static Outcome refuse(String reason) {
            return new Outcome(false, reason == null || reason.isBlank() ? "refused" : reason, false);
        }
    }

    /** What the handler acts through (the live server, or a test). Server thread. */
    public interface Ops {
        Optional<LedgerEntry> entry(UUID companionId);

        boolean online(UUID player);

        /** One action against the player's link bucket; false when over the limit. */
        boolean admit(UUID player);

        Outcome setMode(UUID owner, UUID companionId, CompanionMode mode);

        Outcome recall(UUID owner, UUID companionId);

        Outcome reclaim(UUID owner, UUID companionId);

        Outcome rename(UUID owner, UUID companionId, String requestedName);
    }

    private final Supplier<Ops> ops;

    public CompanionLinkActions(Supplier<Ops> ops) {
        this.ops = ops;
    }

    @Override
    public String moduleId() {
        return CompanionLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return CompanionLinkModule.ACTIONS;
    }

    /** {@code recall} and {@code reclaim} only move server-side state; the other ops refuse an offline owner. */
    @Override
    public boolean allowOffline(String actionId) {
        return CompanionLinkModule.ACTION_COMPANION.equals(actionId);
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        if (!CompanionLinkModule.ACTION_COMPANION.equals(actionId)) {
            return invalid("unknown_action");
        }
        if (playerId == null) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, "not_owner");
        }
        JsonObject body = params == null ? new JsonObject() : params;
        if (body.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            return invalid("too_large");
        }
        Ops world = this.ops.get();
        if (world == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "not_running");
        }
        if (!world.admit(playerId)) {
            return invalid("rate_limited");
        }
        Optional<UUID> companionId = uuid(string(body, "companionId"));
        if (companionId.isEmpty()) {
            return invalid("bad_companion");
        }
        Optional<LedgerEntry> entry = world.entry(companionId.get());
        if (entry.isEmpty() || !playerId.equals(entry.get().owner())) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, "not_owner");
        }
        String op = string(body, "op").orElse("");
        State state = entry.get().state();
        Outcome outcome;
        switch (op) {
            case OP_SET_MODE -> {
                if (!world.online(playerId)) {
                    return offline();
                }
                Optional<CompanionMode> mode = string(body, "mode").flatMap(CompanionMode::byId);
                if (mode.isEmpty()) {
                    return invalid("unknown_mode");
                }
                if (state != State.LIVE) {
                    return invalid("not_live");
                }
                outcome = world.setMode(playerId, companionId.get(), mode.get());
            }
            case OP_RECALL -> {
                if (state != State.LIVE) {
                    return invalid("not_live");
                }
                outcome = world.recall(playerId, companionId.get());
            }
            case OP_RECLAIM -> {
                if (state != State.LOST) {
                    return invalid("not_lost");
                }
                outcome = world.reclaim(playerId, companionId.get());
            }
            case OP_RENAME -> {
                if (!world.online(playerId)) {
                    return offline();
                }
                Optional<String> name = string(body, "name");
                if (name.isEmpty() || name.get().length() > MAX_RAW_NAME) {
                    return invalid("bad_name");
                }
                if (state != State.LIVE) {
                    return invalid("not_live");
                }
                outcome = world.rename(playerId, companionId.get(), name.get());
            }
            default -> {
                return invalid("unknown_op");
            }
        }
        if (outcome == null || !outcome.ok()) {
            return invalid(outcome == null ? "refused" : outcome.reason());
        }
        JsonObject answer = new JsonObject();
        answer.addProperty("companionId", companionId.get().toString());
        answer.addProperty("op", op);
        answer.addProperty("result", outcome.pending() ? "pending" : "done");
        return LinkActionResult.ok(answer);
    }

    private static LinkActionResult invalid(String reason) {
        return LinkActionResult.error(LinkActionResult.Error.VALIDATION, reason);
    }

    private static LinkActionResult offline() {
        return LinkActionResult.error(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED, "owner_offline");
    }

    private static Optional<String> string(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            return Optional.of(primitive.getAsString());
        }
        return Optional.empty();
    }

    private static Optional<UUID> uuid(Optional<String> raw) {
        if (raw.isEmpty() || raw.get().length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.get()));
        } catch (IllegalArgumentException bad) {
            return Optional.empty();
        }
    }
}
