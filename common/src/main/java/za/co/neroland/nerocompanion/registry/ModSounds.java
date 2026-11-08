package za.co.neroland.nerocompanion.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.personality.Trait;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * NeroCompanion's 30 sound events ({@code 010} section 7). Every key has a synthesised original OGG under
 * {@code assets/nerocompanion/sounds/} and a subtitle {@code subtitles.nerocompanion.<key>} in {@code sounds.json};
 * the event id is the key ({@code nerocompanion:chirp.curious} ...). Callers add their own pitch jitter
 * ({@link #pitch}).
 */
public final class ModSounds {

    public static final RegistrationProvider<SoundEvent> SOUNDS =
            Services.REGISTRATION.provider(Registries.SOUND_EVENT, NeroCompanionCommon.MOD_ID);

    private static final List<String> KEYS = new ArrayList<>();

    public static final RegistryEntry<SoundEvent> CHIRP_CURIOUS = register("chirp.curious");
    public static final RegistryEntry<SoundEvent> CHIRP_LOYAL = register("chirp.loyal");
    public static final RegistryEntry<SoundEvent> CHIRP_GRUMPY = register("chirp.grumpy");
    public static final RegistryEntry<SoundEvent> CHIRP_SLEEPY = register("chirp.sleepy");
    public static final RegistryEntry<SoundEvent> CHIRP_SHOWOFF = register("chirp.showoff");
    public static final RegistryEntry<SoundEvent> CHIRP_SHY = register("chirp.shy");
    public static final RegistryEntry<SoundEvent> DRONE_HOVER = register("drone.hover");
    public static final RegistryEntry<SoundEvent> BOT_STEP = register("bot.step");
    public static final RegistryEntry<SoundEvent> BOT_SERVO = register("bot.servo");
    public static final RegistryEntry<SoundEvent> DEPLOY = register("deploy");
    public static final RegistryEntry<SoundEvent> RECALL = register("recall");
    public static final RegistryEntry<SoundEvent> CHARGE_HUM = register("charge.hum");
    public static final RegistryEntry<SoundEvent> CHARGE_SPARK = register("charge.spark");
    public static final RegistryEntry<SoundEvent> CHARGE_FULL = register("charge.full");
    public static final RegistryEntry<SoundEvent> CRANK_TURN = register("crank.turn");
    public static final RegistryEntry<SoundEvent> LOW_BATTERY = register("low_battery");
    public static final RegistryEntry<SoundEvent> DANGER_ALERT = register("danger_alert");
    public static final RegistryEntry<SoundEvent> SCOUT_PING = register("scout.ping");
    public static final RegistryEntry<SoundEvent> SALVAGE_CHIME = register("salvage.chime");
    public static final RegistryEntry<SoundEvent> SALVAGE_RETURN = register("salvage.return");
    public static final RegistryEntry<SoundEvent> PET_PURR = register("pet.purr");
    public static final RegistryEntry<SoundEvent> SLEEP_POWERDOWN = register("sleep.powerdown");
    public static final RegistryEntry<SoundEvent> WAKE = register("wake");
    public static final RegistryEntry<SoundEvent> FETCH_CATCH = register("fetch.catch");
    public static final RegistryEntry<SoundEvent> BOND_LEVELUP = register("bond.levelup");
    public static final RegistryEntry<SoundEvent> SCRAPLING_HATCH = register("scrapling.hatch");
    public static final RegistryEntry<SoundEvent> MINE_DRILL = register("mine.drill");
    public static final RegistryEntry<SoundEvent> REPAIR_WELD = register("repair.weld");
    public static final RegistryEntry<SoundEvent> COMBAT_LUNGE = register("combat.lunge");
    public static final RegistryEntry<SoundEvent> UI_CLICK = register("ui.click");

    private static RegistryEntry<SoundEvent> register(String key) {
        KEYS.add(key);
        return SOUNDS.register(key, id -> SoundEvent.createVariableRangeEvent(id.identifier()));
    }

    /** Every sound key, in registration order (the asset checker and the tests read it). */
    public static List<String> keys() {
        return Collections.unmodifiableList(KEYS);
    }

    /** The chirp of a trait ({@code Trait} order: curious, loyal, grumpy, sleepy, showoff, shy). */
    public static SoundEvent chirp(Trait trait) {
        return switch (trait) {
            case CURIOUS -> CHIRP_CURIOUS.get();
            case LOYAL -> CHIRP_LOYAL.get();
            case GRUMPY -> CHIRP_GRUMPY.get();
            case SLEEPY -> CHIRP_SLEEPY.get();
            case SHOWOFF -> CHIRP_SHOWOFF.get();
            case SHY -> CHIRP_SHY.get();
        };
    }

    /** Pitch with the {@code 010} per-play jitter: {@code 1.0 +- 0.04}. */
    public static float pitch(net.minecraft.util.RandomSource random) {
        return 0.96F + random.nextFloat() * 0.08F;
    }

    private ModSounds() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
