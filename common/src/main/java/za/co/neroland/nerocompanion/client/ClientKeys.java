package za.co.neroland.nerocompanion.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?}

/** NeroCompanion's key mappings: open the companion screen (K) and show / hide the strip (unbound). Client only. */
public final class ClientKeys {

    //? if >=1.21.9 {
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nerocompanion", "nerocompanion"));
    //?} else {
    /*public static final String CATEGORY = "key.categories.nerocompanion";
    *///?}

    public static final KeyMapping OPEN_COMPANION =
            new KeyMapping("key.nerocompanion.open_companion", InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping HUD_TOGGLE =
            new KeyMapping("key.nerocompanion.hud_toggle", InputConstants.UNKNOWN.getValue(), CATEGORY);

    private ClientKeys() {
    }

    public static List<KeyMapping> all() {
        return List.of(OPEN_COMPANION, HUD_TOGGLE);
    }
}
