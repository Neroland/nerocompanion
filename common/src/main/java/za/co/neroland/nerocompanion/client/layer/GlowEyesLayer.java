package za.co.neroland.nerocompanion.client.layer;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?}
//? if <1.21.9 {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import za.co.neroland.nerocompanion.client.CompanionRenderInfo;
import za.co.neroland.nerocompanion.client.CompanionRenderer;
import za.co.neroland.nerocompanion.client.look.ClientLooks;
import za.co.neroland.nerocompanion.client.model.CompanionModel;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * The companion's emissive layer (ported from the family's {@code GlowEyesLayer}), drawn full-bright from the
 * active skin's {@code *_glow.png} in the two passes of {@code 019} section 4.5: the bands, contacts, thrusters and
 * seams (eye plates hidden) tinted by the energy band, then only the active eye plate tinted by the eye colour. The
 * glow sheet's eyes are white, so the tint is the colour. The aurora skin swaps in its animated glow frame. The
 * three draw paths differ per line ({@code submit} from 1.21.9, {@code render} with a state on 1.21.2-1.21.8,
 * {@code render} with the mob on 1.21.1); all share {@link #bandColour} and {@link #eyeColour}. Client only.
 */
//? if >=1.21.2 {
public class GlowEyesLayer extends RenderLayer<CompanionRenderer.State, EntityModel<LivingEntityRenderState>> {
//?} else {
/*public class GlowEyesLayer extends RenderLayer<net.minecraft.world.entity.Mob, EntityModel<net.minecraft.world.entity.Mob>> {
*///?}

    public static final int FULL_BRIGHT = 0xF000F0;
    public static final int COMPANION_TEAL = 0x56CCC4;
    public static final int TEAL_DIM = 0x2F6C68;
    public static final int TEAL_BRIGHT = 0xA8F0EA;
    public static final int EYE_WHITE = 0xE8FCFA;
    public static final int WARN_AMBER = 0xFFB347;
    public static final int LOW_RED = 0xFF5C5C;
    public static final int OK_GREEN = 0x58E07A;

    private final CompanionRenderer renderer;
    private final Map<Identifier, RenderType> types = new HashMap<>();

    public GlowEyesLayer(CompanionRenderer renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    private RenderType type(Identifier texture) {
        return this.types.computeIfAbsent(texture, t -> RenderTypes.entityTranslucentEmissive(t));
    }

    //? if >=1.21.9 {
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CompanionRenderer.State state,
            float yRot, float xRot) {
        CompanionRenderInfo info = state.companion;
        if (state.isInvisible || !info.companion) {
            return;
        }
        CompanionRenderer.Models models = this.renderer.current();
        RenderType type = this.type(ClientLooks.glow(this.renderer.type(), info.skin, info.ageInTicks));
        int band = bandColour(info);
        if ((band >>> 24) != 0) {
            submit(collector, models.glowBody(), state, poseStack, type, band);
        }
        // The eye pass is posed when it draws; the base model chose the plate this frame from the same facts.
        submit(collector, models.glowEyes(), state, poseStack, type, eyeColour(info, models.base().activeEye()));
    }

    private static void submit(SubmitNodeCollector collector, CompanionModel model, CompanionRenderer.State state,
            PoseStack poseStack, RenderType type, int argb) {
        //? if >=26.3 {
        /*collector.order(1).submitModel(model, state, poseStack, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
        *///?} else {
        collector.order(1).submitModel(model, state, poseStack, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb,
                null);
        //?}
    }
    //?} else if >=1.21.2 {
    /*@Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, CompanionRenderer.State state,
            float yRot, float xRot) {
        CompanionRenderInfo info = state.companion;
        if (state.isInvisible || !info.companion) {
            return;
        }
        CompanionRenderer.Models models = this.renderer.current();
        RenderType type = this.type(ClientLooks.glow(this.renderer.type(), info.skin, info.ageInTicks));
        int band = bandColour(info);
        if ((band >>> 24) != 0) {
            models.glowBody().setupAnim(state);
            models.glowBody().renderToBuffer(poseStack, buffers.getBuffer(type), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    band);
        }
        models.glowEyes().setupAnim(state);
        models.glowEyes().renderToBuffer(poseStack, buffers.getBuffer(type), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                eyeColour(info, models.glowEyes().activeEye()));
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, net.minecraft.world.entity.Mob entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        CompanionRenderInfo info = this.renderer.legacyInfo();
        if (entity.isInvisible() || !info.companion || info.entityId != entity.getId()) {
            return;
        }
        CompanionRenderer.Models models = this.renderer.current();
        RenderType type = this.type(ClientLooks.glow(this.renderer.type(), info.skin, info.ageInTicks));
        int band = bandColour(info);
        if ((band >>> 24) != 0) {
            models.glowBody().setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            models.glowBody().renderToBuffer(poseStack, buffers.getBuffer(type), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    band);
        }
        models.glowEyes().setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        models.glowEyes().renderToBuffer(poseStack, buffers.getBuffer(type), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                eyeColour(info, models.glowEyes().activeEye()));
    }
    *///?}

    /**
     * Band / contact tint ({@code 019} 4.5): the role colours are painted on the sheet, so healthy is white at full
     * alpha; 11-25 % blinks at 1 Hz; asleep (or flat) is the dim teal; charging pulses at 2 Hz.
     */
    public static int bandColour(CompanionRenderInfo info) {
        float age = info.ageInTicks;
        if (info.preview) {
            return argb(1.0F, 0xFFFFFF);
        }
        if (info.charging()) {
            return argb(0.6F + 0.4F * Mth.sin(age * 0.63F), 0xFFFFFF);
        }
        if (info.sleeping() || info.energyPct <= 0) {
            return argb(1.0F, TEAL_DIM);
        }
        if (info.energyPct <= 25) {
            return argb(Mth.sin(age * 0.314F) > 0.0F ? 1.0F : 0.5F, 0xFFFFFF);
        }
        if (info.working() && info.mode == CompanionMode.GUARD && "combat_bot".equals(info.type)) {
            return argb(info.aggressive ? 1.0F : 0.75F, 0xFFFFFF);
        }
        return argb(1.0F, 0xFFFFFF);
    }

    /** Eye tint ({@code 019} 4.2 / 4.3 / 4.5) for the plate {@code eye} (3 = closed). */
    public static int eyeColour(CompanionRenderInfo info, int eye) {
        float age = info.ageInTicks;
        int healthy = ClientLooks.eyeColour(info.skin, COMPANION_TEAL);
        if (info.preview) {
            return argb(1.0F, healthy);
        }
        if (info.dangerFlash) {
            return argb(1.0F, Mth.sin(age * 1.26F) > 0.0F ? LOW_RED : healthy);
        }
        if (info.chargeFlash > 0.0F) {
            return argb(1.0F, OK_GREEN);
        }
        if (eye == 3) {
            // Standby blink: EYE_WHITE for 2 ticks in every 60.
            return argb(1.0F, ((int) age) % 60 < 2 ? EYE_WHITE : TEAL_DIM);
        }
        int colour;
        float alpha = 1.0F;
        if (info.energyPct > 50 || info.charging()) {
            colour = healthy;
        } else if (info.energyPct > 25) {
            colour = WARN_AMBER;
        } else if (info.energyPct > 10) {
            colour = LOW_RED;
        } else {
            colour = LOW_RED;
            alpha = 0.6F;
        }
        if (info.working() && info.mode == CompanionMode.SCOUT && "scout_drone".equals(info.type)) {
            alpha *= 0.5F + 0.5F * Mth.sin(age * 0.4F);
        }
        return argb(alpha, colour);
    }

    static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
