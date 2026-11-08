package za.co.neroland.nerocompanion.ai.pet;

import java.util.Optional;

import za.co.neroland.nerocompanion.entity.HomePos;

/**
 * A pet that remembers the last Charging Pad that charged it (from the charger hook), so it can nap there even
 * when its home is a Bay. Server memory only: forgotten on unload, recall or parking, relearned on the next charge.
 */
public interface NapPadMemory {

    Optional<HomePos> lastNapPad();
}
