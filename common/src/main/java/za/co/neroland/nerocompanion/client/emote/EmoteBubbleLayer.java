package za.co.neroland.nerocompanion.client.emote;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.FormattedCharSequence;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?}
//? if <1.21.9 {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
//? if <1.21.2 {
/*import net.minecraft.world.entity.Mob;
*///?}

import za.co.neroland.nerocompanion.client.CompanionRenderer;

/**
 * Draws the companion's emote bubble 0.55 blocks above its hitbox, camera-facing, with the trait-coloured border
 * ({@code 010} section 5.1). With the emote sheet it shows the line's icons; without it, the line's translated text
 * ({@link BubbleText}). Culled beyond {@link #RANGE} blocks and for invisible companions; nothing is drawn without a
 * live bubble. The layer API differs per line: {@code submit} on 1.21.9+, {@code render} with a render state on
 * 1.21.2-1.21.8, {@code render} with the mob on 1.21.1; all three share {@link #submitBubble}. Client only.
 */
//? if >=1.21.2 {
public class EmoteBubbleLayer extends RenderLayer<CompanionRenderer.State, EntityModel<LivingEntityRenderState>> {
//?} else {
/*public class EmoteBubbleLayer extends RenderLayer<Mob, EntityModel<Mob>> {
*///?}

    /** Bubbles further than this from the camera are not drawn. */
    public static final double RANGE = 24.0D;
    private static final float ABOVE = 0.55F;
    private static final int TEXT_COLOUR = 0xFFE8FCFA;
    private static final RenderType PANEL = RenderTypes.entityTranslucentEmissive(
            za.co.neroland.nerocompanion.client.render.Quads.WHITE);
    private static final RenderType ICONS = RenderTypes.entityTranslucentEmissive(BubbleArt.EMOTES);

    /** Draws one line of text in the current pose (font pixels, y down), the per-line part of text output. */
    @FunctionalInterface
    interface TextOut {
        void draw(PoseStack poseStack, FormattedCharSequence line, float x, float y, int argb);
    }

    //? if >=1.21.2 {
    public EmoteBubbleLayer(RenderLayerParent<CompanionRenderer.State, EntityModel<LivingEntityRenderState>> parent) {
        super(parent);
    }
    //?} else {
    /*public EmoteBubbleLayer(RenderLayerParent<Mob, EntityModel<Mob>> parent) {
        super(parent);
    }
    *///?}

    //? if >=1.21.9 {
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CompanionRenderer.State state,
            float yRot, float xRot) {
        if (state.isInvisible || state.distanceToCameraSq > RANGE * RANGE) {
            return;
        }
        submitBubble(poseStack, collector,
                (pose, line, x, y, argb) -> collector.order(2).submitText(pose, x, y, line, false,
                        Font.DisplayMode.NORMAL, BubbleArt.FULL_BRIGHT, argb, 0, 0),
                state.companion.entityId, state.boundingBoxHeight, state.companion.ageInTicks);
    }
    //?} else if >=1.21.2 {
    /*@Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, CompanionRenderer.State state,
            float yRot, float xRot) {
        if (state.isInvisible || state.distanceToCameraSq > RANGE * RANGE) {
            return;
        }
        submitBubble(poseStack, new SubmitNodeCollector(buffers), legacyText(buffers), state.companion.entityId,
                state.boundingBoxHeight, state.companion.ageInTicks);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Mob entity, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        net.minecraft.world.phys.Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (entity.distanceToSqr(camera) > RANGE * RANGE) {
            return;
        }
        submitBubble(poseStack, new SubmitNodeCollector(buffers), legacyText(buffers), entity.getId(),
                entity.getBbHeight(), entity.tickCount + partialTick);
    }
    *///?}

    //? if <1.21.9 {
    /*private static TextOut legacyText(MultiBufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        return (pose, line, x, y, argb) -> font.drawInBatch(line, x, y, argb, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, BubbleArt.FULL_BRIGHT);
    }
    *///?}

    /**
     * Draws the live bubble over {@code entityId}, if any. {@code poseStack} is in model space (as layers get it):
     * flipped, feet at y = 1.501, and scaled by the entity scale, the renderer's model scale and any pose hook. That
     * total scale is read back from the matrix so the anchor is {@code height + ABOVE} blocks above the feet for
     * every type; then the rotation is replaced by the camera's so the quad faces the viewer, in blocks.
     */
    static void submitBubble(PoseStack poseStack, SubmitNodeCollector collector, TextOut text, int entityId,
            float height, float ageInTicks) {
        EmoteBubbles.Bubble bubble = EmoteBubbles.active(entityId);
        if (bubble == null) {
            return;
        }
        float age = EmoteBubbles.age(bubble, ageInTicks - (float) Math.floor(ageInTicks));
        float scale = EmoteBubbles.scale(age);
        float alpha = EmoteBubbles.alpha(age);
        if (scale <= 0.0F || alpha <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        float modelSpace = Math.max(1.0E-3F, poseStack.last().pose().getScale(new Vector3f()).y);
        poseStack.translate(0.0F, 1.501F - (height + ABOVE) / modelSpace, 0.0F);
        PoseStack.Pose last = poseStack.last();
        Vector3f origin = last.pose().getTranslation(new Vector3f());
        //? if >=26.2 {
        Quaternionf camera = Minecraft.getInstance().gameRenderer.mainCamera().rotation();
        //?} else {
        /*Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        *///?}
        last.pose().identity().translate(origin).rotate(camera).scale(scale);
        last.normal().identity().rotate(camera);
        int border = bubble.trait().colour();
        if (BubbleArt.emotesPresent()) {
            boolean two = bubble.second() != null;
            float hw = BubbleArt.iconHalfWidth(two);
            float hh = BubbleArt.iconHalfHeight();
            float cy = BubbleArt.TAIL + hh;
            collector.order(0).submitCustomGeometry(poseStack, PANEL,
                    (pose, consumer) -> BubbleArt.panel(pose, consumer, hw, hh, cy, border, alpha));
            collector.order(1).submitCustomGeometry(poseStack, ICONS,
                    (pose, consumer) -> BubbleArt.icons(pose, consumer, bubble.first(), bubble.second(), cy, alpha));
        } else {
            BubbleText.Layout layout = bubble.text().get(bubble.key());
            float hw = (layout.widthPx() / 2.0F + 3.0F) * BubbleText.SCALE;
            float hh = (layout.heightPx() / 2.0F + 3.0F) * BubbleText.SCALE;
            float cy = BubbleArt.TAIL + hh;
            collector.order(0).submitCustomGeometry(poseStack, PANEL,
                    (pose, consumer) -> BubbleArt.panel(pose, consumer, hw, hh, cy, border, alpha));
            // Font alpha below ~0.1 is discarded by the shader, so stop drawing text at the very end of the fade.
            if (alpha > 0.1F) {
                int colour = BubbleArt.argb(TEXT_COLOUR, alpha);
                poseStack.pushPose();
                poseStack.translate(0.0F, cy + layout.heightPx() / 2.0F * BubbleText.SCALE, 0.003F);
                poseStack.scale(BubbleText.SCALE, -BubbleText.SCALE, BubbleText.SCALE);
                Font font = Minecraft.getInstance().font;
                int row = 0;
                for (FormattedCharSequence line : layout.lines()) {
                    text.draw(poseStack, line, -font.width(line) / 2.0F, row * font.lineHeight, colour);
                    row++;
                }
                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }
}
