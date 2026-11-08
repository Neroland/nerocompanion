package za.co.neroland.nerocompanion.privacy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * Per-player privacy settings and the activity clock the inactive-owner retention sweep reads (overworld SavedData,
 * through Core's {@link SavedDataRecovery} guard). One row per player who has companion data or changed a setting:
 * the last time they were seen on this server (wall-clock millis, refreshed on login and while online) and three
 * opt-outs - names hidden from others, chatter off, push off. Nothing else: no name, no position.
 *
 * <p>Player data: the player's own. Exported by {@code /nscomp export}, erased by owner UUID (with a
 * {@code backupNow}), and removed by the retention sweep together with the rest of an inactive owner's data.
 */
public final class PrivacyPrefs extends SavedData {

    /**
     * One player's row.
     *
     * @param lastSeen   wall-clock millis the player was last seen (0 = not yet; the sweep starts the clock)
     * @param namesOff   their companions' names are hidden from other players
     * @param chatterOff no chatter from their companions, and none shown to them
     * @param pushOff    no push notifications about their companions (Stage 8 reads it)
     */
    public record Row(long lastSeen, boolean namesOff, boolean chatterOff, boolean pushOff) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("seen", 0L).forGetter(Row::lastSeen),
                Codec.BOOL.optionalFieldOf("names_off", false).forGetter(Row::namesOff),
                Codec.BOOL.optionalFieldOf("chatter_off", false).forGetter(Row::chatterOff),
                Codec.BOOL.optionalFieldOf("push_off", false).forGetter(Row::pushOff)
        ).apply(i, Row::new));

        public static final Row DEFAULT = new Row(0L, false, false, false);

        Row seen(long millis) {
            return new Row(millis, this.namesOff, this.chatterOff, this.pushOff);
        }
    }

    /** The three opt-outs. */
    public enum Setting {
        NAMES("names"),
        CHATTER("chatter"),
        PUSH("push");

        private final String id;

        Setting(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    private static final Codec<Map<UUID, Row>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Row.CODEC);
    private static final Codec<PrivacyPrefs> CODEC = MAP_CODEC.xmap(PrivacyPrefs::new, prefs -> prefs.rows);

    public static final SavedDataType<PrivacyPrefs> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "privacy"), PrivacyPrefs::new, CODEC);
    public static final String RECOVERY_NAME = "nerocompanion:privacy";

    private final Map<UUID, Row> rows = new HashMap<>();

    public PrivacyPrefs() {
        this(Map.of());
    }

    private PrivacyPrefs(Map<UUID, Row> loaded) {
        this.rows.putAll(loaded);
    }

    public static PrivacyPrefs get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, PrivacyPrefs::new, RECOVERY_NAME);
    }

    public Row row(UUID player) {
        return this.rows.getOrDefault(player, Row.DEFAULT);
    }

    public Optional<Row> find(UUID player) {
        return Optional.ofNullable(this.rows.get(player));
    }

    public boolean isOff(UUID player, Setting setting) {
        Row row = this.rows.get(player);
        if (row == null) {
            return false;
        }
        return switch (setting) {
            case NAMES -> row.namesOff();
            case CHATTER -> row.chatterOff();
            case PUSH -> row.pushOff();
        };
    }

    /** Turns {@code setting} on or off for {@code player} (stamps them as seen now). */
    public void set(UUID player, Setting setting, boolean off, long millis) {
        Row row = this.row(player);
        Row next = switch (setting) {
            case NAMES -> new Row(millis, off, row.chatterOff(), row.pushOff());
            case CHATTER -> new Row(millis, row.namesOff(), off, row.pushOff());
            case PUSH -> new Row(millis, row.namesOff(), row.chatterOff(), off);
        };
        this.rows.put(player, next);
        this.setDirty();
    }

    /** {@code player} was seen at {@code millis} (login, or online during a sweep). */
    public void seen(UUID player, long millis) {
        Row row = this.row(player);
        if (row.lastSeen() != millis) {
            this.rows.put(player, row.seen(millis));
            this.setDirty();
        }
    }

    /** Puts back the activity stamp a failed purge must keep, so the next sweep retries (Core 1.17.x semantics). */
    public void restoreSeen(UUID player, long millis) {
        this.rows.put(player, this.row(player).seen(millis));
        this.setDirty();
    }

    /** The last-seen stamp, or empty when the player has no row. */
    public Optional<Long> lastSeen(UUID player) {
        Row row = this.rows.get(player);
        return row == null ? Optional.empty() : Optional.of(row.lastSeen());
    }

    public Set<UUID> players() {
        return Set.copyOf(this.rows.keySet());
    }

    /** Erasure and retention: the player's row goes. */
    public boolean forget(UUID player) {
        boolean removed = this.rows.remove(player) != null;
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    public boolean holdsFor(UUID player) {
        return this.rows.containsKey(player);
    }

    public int size() {
        return this.rows.size();
    }
}
