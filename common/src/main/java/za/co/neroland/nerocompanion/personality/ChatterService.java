package za.co.neroland.nerocompanion.personality;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.privacy.CompanionPrivacy;

/**
 * Server side chatter: picks a lang-keyed line from the companion's trait pool for an event, throttled per
 * companion ({@link ChatterCadence}, Chatterbox / Hush / Silent Running chips), and sends a {@code Chatter}
 * payload (entity id, key, trait - no text, no player data) to the players who can see the companion: same level,
 * within {@link #MAX_RANGE} blocks and the owner's render distance, with line of sight. When the companion's name
 * is hidden from others ({@code NAME_HIDDEN}) only the owner gets it. A player who turned chatter off
 * ({@code /nscomp privacy chatter off}) gets none, and their own companions say nothing.
 *
 * <p>Other packages call {@link #say} for their events (scout ore marks, mining), e.g.
 * {@code ChatterService.say(companion, ChatterEvent.ORE_FOUND)}.
 */
public final class ChatterService {

    /** Bubbles are culled beyond 24 blocks on the client; nobody further out needs the packet. */
    public static final double MAX_RANGE = 32.0D;

    private ChatterService() {
    }

    /** The companion's chatter volume from its chips. */
    public static ChatterVolume volume(CompanionEntity companion) {
        List<ChipStack> chips = companion.activeChips();
        return ChatterVolume.of(ChipEffects.has(chips, ChipKinds.SILENT_RUNNING),
                ChipEffects.has(chips, ChipKinds.CHATTERBOX), ChipEffects.has(chips, ChipKinds.HUSH));
    }

    /** Says a line for {@code event} if the cooldowns allow. Server only; returns whether a line was sent. */
    public static boolean say(CompanionEntity companion, ChatterEvent event) {
        if (!(companion.level() instanceof ServerLevel level) || companion.isRemoved()
                || companion.isSleepingCompanion()) {
            return false;
        }
        Mood mood = Mood.of(companion);
        ChatterVolume volume = volume(companion);
        long now = level.getGameTime();
        if (!ChatterCadence.ready(event, volume, now, mood.lastChatter, mood.lastFor(event))) {
            return false;
        }
        if (!CompanionPrivacy.chatterAllowed(level.getServer(), companion.ownerId())) {
            return false; // the owner turned chatter off (/nscomp privacy chatter off)
        }
        Trait trait = Trait.byIndex(companion.trait());
        ChatterLines.Line line = ChatterLines.pick(trait, event, companion.getRandom().nextInt(1 << 16));
        mood.lastChatter = now;
        mood.lastByEvent.put(event, now);
        send(level, companion, new Payloads.Chatter(companion.getId(), line.key(), trait.ordinal()));
        return true;
    }

    private static void send(ServerLevel level, CompanionEntity companion, Payloads.Chatter payload) {
        ServerPlayer owner = companion.owner();
        double range = MAX_RANGE;
        if (owner != null) {
            range = Math.min(range, Math.max(2, owner.clientInformation().viewDistance()) * 16.0D);
        }
        double rangeSq = range * range;
        boolean nameHidden = (companion.syncedFlags() & CompanionEntity.FLAG_NAME_HIDDEN) != 0;
        for (ServerPlayer player : level.players()) {
            boolean isOwner = companion.isOwnedBy(player);
            if (nameHidden && !isOwner) {
                continue;
            }
            if (player.distanceToSqr(companion) > rangeSq) {
                continue;
            }
            if (!isOwner && !player.hasLineOfSight(companion)) {
                continue;
            }
            if (!isOwner && !CompanionPrivacy.chatterAllowed(level.getServer(), player.getUUID())) {
                continue; // this player turned chatter off
            }
            Services.NETWORK.sendToPlayer(player, payload);
        }
    }
}
