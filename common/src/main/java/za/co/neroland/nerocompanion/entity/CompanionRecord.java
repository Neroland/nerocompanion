package za.co.neroland.nerocompanion.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Everything a companion <em>is</em>, as one immutable value: the single persistence shape.
 * The entity saves itself through it, and the Capsule item stores the same record (later also the Bay, the
 * Recovery Ledger and the export), so one codec and one round-trip test cover all of them.
 *
 * <ul>
 *   <li>{@code schema} - {@link #SCHEMA}; bumped on any shape change, and the decoder upgrades older ones.</li>
 *   <li>{@code companionId} - stable identity across recall, parking and reclaim. <b>Not</b> the entity UUID:
 *       every deploy creates a fresh entity.</li>
 *   <li>{@code ownerId} - the player who deployed it. {@link #NO_OWNER} only in the network copy (see
 *       {@link #STREAM_CODEC}) - an owner UUID is never sent to clients.</li>
 *   <li>{@code mode} - the mode the player chose. A server-forced SLEEP (owner offline, too far, flat battery)
 *       is never written here; a SLEEP the player chose is.</li>
 *   <li>{@code name} - already filtered and capped at {@link #MAX_NAME_LENGTH} characters when stored.</li>
 *   <li>{@code jobData} - per-type working state, owned and versioned by that type's own code.</li>
 *   <li>{@code health} - current health, so a hurt companion stays hurt through recall, parking and reclaim
 *       (schema 2). {@link #FULL_HEALTH} means "full"; schema-1 records upgrade to it, and a record written at zero
 *       health (a killed companion, for the recovery ledger) reads as full too. Clamped to the live max on apply.</li>
 * </ul>
 *
 * <p>Privacy: the owner UUID and the home position are personal data. They stay on the server: the item's
 * network codec strips both, so a capsule seen in someone's hand or an item frame tells other clients nothing.
 */
public record CompanionRecord(
        int schema,
        UUID companionId,
        UUID ownerId,
        Identifier type,
        CompanionMode mode,
        long energyFe,
        List<ChipStack> chips,
        int bondPoints,
        Optional<String> name,
        Optional<Identifier> skin,
        Optional<Identifier> hat,
        long personalitySeed,
        Optional<HomePos> home,
        CompoundTag jobData,
        float health) {

    /** Current shape version. 2 added {@code health}. */
    public static final int SCHEMA = 2;

    /** {@link #health} value meaning "at full health" (schema-1 records, fresh companions, killed companions). */
    public static final float FULL_HEALTH = -1.0F;

    /** Companion names are capped at this many characters (and filtered by the server before they get here). */
    public static final int MAX_NAME_LENGTH = 24;

    /** Owner placeholder used only in the stripped network copy. */
    public static final UUID NO_OWNER = new UUID(0L, 0L);

    public CompanionRecord {
        chips = List.copyOf(chips);
        name = name.map(CompanionRecord::capName).filter(value -> !value.isEmpty());
        jobData = jobData == null ? new CompoundTag() : jobData.copy();
        energyFe = Math.max(0L, energyFe);
        bondPoints = Math.max(0, bondPoints);
        health = Float.isNaN(health) || health <= 0.0F ? FULL_HEALTH : health;
    }

    /** A record at full health (the shape before schema 2). */
    public CompanionRecord(int schema, UUID companionId, UUID ownerId, Identifier type, CompanionMode mode,
            long energyFe, List<ChipStack> chips, int bondPoints, Optional<String> name, Optional<Identifier> skin,
            Optional<Identifier> hat, long personalitySeed, Optional<HomePos> home, CompoundTag jobData) {
        this(schema, companionId, ownerId, type, mode, energyFe, chips, bondPoints, name, skin, hat, personalitySeed,
                home, jobData, FULL_HEALTH);
    }

    /** Whether this record is at full health. */
    public boolean fullHealth() {
        return this.health == FULL_HEALTH;
    }

    /** A copy with {@code health} ({@code <= 0} or NaN reads as full). */
    public CompanionRecord withHealth(float health) {
        return new CompanionRecord(this.schema, this.companionId, this.ownerId, this.type, this.mode, this.energyFe,
                this.chips, this.bondPoints, this.name, this.skin, this.hat, this.personalitySeed, this.home,
                this.jobData, health);
    }

    /**
     * Drops control and Unicode format characters (zero-width, joiners, BOM, bidi overrides), collapses whitespace
     * runs, trims, and caps a name at {@link #MAX_NAME_LENGTH} code points. A name with no letter or digit left is
     * empty (no name).
     */
    public static String capName(String raw) {
        StringBuilder cleaned = new StringBuilder();
        boolean space = false;
        for (int cp : (raw == null ? "" : raw).codePoints().toArray()) {
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) {
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = true;
                continue;
            }
            if (space && cleaned.length() > 0) {
                cleaned.append(' ');
            }
            space = false;
            cleaned.appendCodePoint(cp);
        }
        StringBuilder out = new StringBuilder();
        cleaned.codePoints().limit(MAX_NAME_LENGTH).forEach(out::appendCodePoint);
        String capped = out.toString().strip();
        return capped.codePoints().anyMatch(Character::isLetterOrDigit) ? capped : "";
    }

    /** The per-type job data (a defensive copy; the record stays immutable). */
    @Override
    public CompoundTag jobData() {
        return this.jobData.copy();
    }

    public static final Codec<CompanionRecord> CODEC = RecordCodecBuilder.<CompanionRecord>create(instance -> instance.group(
            Codec.INT.fieldOf("schema").forGetter(CompanionRecord::schema),
            UUIDUtil.CODEC.fieldOf("companion_id").forGetter(CompanionRecord::companionId),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(CompanionRecord::ownerId),
            Identifier.CODEC.fieldOf("type").forGetter(CompanionRecord::type),
            CompanionMode.CODEC.fieldOf("mode").forGetter(CompanionRecord::mode),
            Codec.LONG.fieldOf("energy").forGetter(CompanionRecord::energyFe),
            ChipStack.CODEC.listOf().optionalFieldOf("chips", List.of()).forGetter(CompanionRecord::chips),
            Codec.INT.optionalFieldOf("bond", 0).forGetter(CompanionRecord::bondPoints),
            Codec.STRING.optionalFieldOf("name").forGetter(CompanionRecord::name),
            Identifier.CODEC.optionalFieldOf("skin").forGetter(CompanionRecord::skin),
            Identifier.CODEC.optionalFieldOf("hat").forGetter(CompanionRecord::hat),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(CompanionRecord::personalitySeed),
            HomePos.CODEC.optionalFieldOf("home").forGetter(CompanionRecord::home),
            CompoundTag.CODEC.optionalFieldOf("job").forGetter(r -> r.jobData.isEmpty()
                    ? Optional.empty() : Optional.of(r.jobData())),
            Codec.FLOAT.optionalFieldOf("health", FULL_HEALTH).forGetter(CompanionRecord::health)
    ).apply(instance, (schema, companionId, owner, type, mode, energy, chips, bond, name, skin, hat, seed, home, job,
            health) -> new CompanionRecord(schema, companionId, owner, type, mode, energy, chips, bond, name, skin, hat,
                    seed, home, job.orElseGet(CompoundTag::new), health)))
            .validate(CompanionRecord::upgrade);

    /**
     * Network form: the persistent codec over a stripped copy (no owner UUID, no home position, no job data). Used for the
     * Capsule's item component so tooltips work on the client without the owner or base location leaving the
     * server.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionRecord> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC).map(record -> record, CompanionRecord::forNetwork);

    /** Accepts every schema up to {@link #SCHEMA}; a newer one (a downgraded world) is an error, not a guess. */
    private static DataResult<CompanionRecord> upgrade(CompanionRecord record) {
        if (record.schema() < 1 || record.schema() > SCHEMA) {
            return DataResult.error(() -> "Unsupported companion record schema " + record.schema());
        }
        if (record.schema() == 1) {
            // Schema 1 had no health: it upgrades to full health (the field is absent, so it already decoded as
            // FULL_HEALTH; the explicit value documents the rule).
            return DataResult.success(new CompanionRecord(SCHEMA, record.companionId(), record.ownerId(),
                    record.type(), record.mode(), record.energyFe(), record.chips(), record.bondPoints(),
                    record.name(), record.skin(), record.hat(), record.personalitySeed(), record.home(),
                    record.jobData(), FULL_HEALTH));
        }
        return DataResult.success(record);
    }

    /**
     * Decodes a saved record. Empty on failure, with {@code onError} given a short reason (never the data
     * itself: it may contain the owner UUID).
     */
    public static Optional<CompanionRecord> tryDecode(net.minecraft.nbt.Tag tag,
            java.util.function.Consumer<String> onError) {
        DataResult<CompanionRecord> result = CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag);
        if (result.result().isPresent()) {
            return result.result();
        }
        int schema = Codec.INT.fieldOf("schema").codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result()
                .orElse(-1);
        onError.accept(schema > SCHEMA ? "saved by a newer version (schema " + schema + ")" : "unreadable record");
        return Optional.empty();
    }

    /** A brand-new companion of {@code type}, owned by {@code ownerId}, fully charged. */
    public static CompanionRecord fresh(Identifier type, UUID ownerId, CompanionMode mode, long energyFe, long seed) {
        return new CompanionRecord(SCHEMA, UUID.randomUUID(), ownerId, type, mode, energyFe, List.of(), 0,
                Optional.empty(), Optional.empty(), Optional.empty(), seed, Optional.empty(), new CompoundTag());
    }

    /** The copy sent to clients: owner, home and per-type job data (which may hold positions) removed. */
    public CompanionRecord forNetwork() {
        return new CompanionRecord(this.schema, this.companionId, NO_OWNER, this.type, this.mode, this.energyFe,
                this.chips, this.bondPoints, this.name, this.skin, this.hat, this.personalitySeed, Optional.empty(),
                new CompoundTag(), this.health);
    }

    /** The shareable record completed with its server-only part (owner, home, job data). */
    public CompanionRecord withPrivate(UUID owner, Optional<HomePos> home, CompoundTag job) {
        return new CompanionRecord(this.schema, this.companionId, owner, this.type, this.mode, this.energyFe,
                this.chips, this.bondPoints, this.name, this.skin, this.hat, this.personalitySeed, home, job,
                this.health);
    }

    /**
     * This record bound to {@code deployer} for a deploy. When the record names a different owner (a Capsule or Bay
     * record that changed hands) the previous owner's home and job data are dropped: they describe that owner's
     * base, never the new one's.
     */
    public CompanionRecord forDeployer(UUID deployer) {
        if (!NO_OWNER.equals(this.ownerId) && !this.ownerId.equals(deployer)) {
            return this.withPrivate(deployer, Optional.empty(), new CompoundTag());
        }
        return this.withOwner(deployer);
    }

    /** This record re-bound to a new owner (deploy binds the deployer). */
    public CompanionRecord withOwner(UUID owner) {
        return new CompanionRecord(this.schema, this.companionId, owner, this.type, this.mode, this.energyFe,
                this.chips, this.bondPoints, this.name, this.skin, this.hat, this.personalitySeed, this.home,
                this.jobData, this.health);
    }
}
