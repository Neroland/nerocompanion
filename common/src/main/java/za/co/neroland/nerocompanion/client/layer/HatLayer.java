package za.co.neroland.nerocompanion.client.layer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
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

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.client.CompanionRenderInfo;
import za.co.neroland.nerocompanion.client.CompanionRenderer;
import za.co.neroland.nerocompanion.client.look.ClientLooks;
import za.co.neroland.nerocompanion.client.model.CompanionGeometry;
import za.co.neroland.nerocompanion.look.Look;

/**
 * Draws the worn hat ({@code 010} section 6.1, {@code 019} hat table): replays the model's transforms from the root
 * to the {@code hat} anchor part (so sleep, perch and head look carry the hat), scales by the type's
 * {@code hat_scale}, and draws the hat model with its texture (plus its glow sheet full-bright, for the halo ring
 * and the headlamp lens). Hat models are static, so they go out as custom geometry on every line (drawn at once
 * below 1.21.9 through the compat collector). Client only.
 */
//? if >=1.21.2 {
public class HatLayer extends RenderLayer<CompanionRenderer.State, EntityModel<LivingEntityRenderState>> {
//?} else {
/*public class HatLayer extends RenderLayer<net.minecraft.world.entity.Mob, EntityModel<net.minecraft.world.entity.Mob>> {
*///?}

    private static final Map<String, Optional<ModelPart>> MODELS = new HashMap<>();

    private final CompanionRenderer renderer;

    public HatLayer(CompanionRenderer renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    /** The baked hat model for a hat id path, or null for a hat without a model. */
    @Nullable
    public static synchronized ModelPart model(String path) {
        return MODELS.computeIfAbsent(path, key -> {
            LayerDefinition layer = CompanionGeometry.hat(key);
            return layer == null ? Optional.empty() : Optional.of(layer.bakeRoot());
        }).orElse(null);
    }

    //? if >=1.21.9 {
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CompanionRenderer.State state,
            float yRot, float xRot) {
        if (!state.isInvisible) {
            this.draw(poseStack, collector, light, state.companion);
        }
    }
    //?} else if >=1.21.2 {
    /*@Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, CompanionRenderer.State state,
            float yRot, float xRot) {
        if (!state.isInvisible) {
            this.draw(poseStack, new SubmitNodeCollector(buffers), light, state.companion);
        }
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, net.minecraft.world.entity.Mob entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        CompanionRenderInfo info = this.renderer.legacyInfo();
        if (!entity.isInvisible() && info.entityId == entity.getId()) {
            this.draw(poseStack, new SubmitNodeCollector(buffers), light, info);
        }
    }
    *///?}

    private void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, CompanionRenderInfo info) {
        if (!info.companion || info.hat.isEmpty()) {
            return;
        }
        Identifier id = Identifier.tryParse(info.hat);
        if (id == null) {
            return;
        }
        ModelPart hat = model(id.getPath());
        if (hat == null || this.renderer.current().base().hatChain().isEmpty()) {
            return;
        }
        Look look = ClientLooks.hat(info.hat);
        Identifier texture = look != null ? look.texture(this.renderer.type()).orElse(standard(id)) : standard(id);
        Identifier glow = look != null ? look.glow(this.renderer.type()).orElse(null) : null;
        poseStack.pushPose();
        for (ModelPart part : this.renderer.current().base().hatChain()) {
            part.translateAndRotate(poseStack);
        }
        float scale = CompanionGeometry.hatScale(this.renderer.type());
        poseStack.scale(scale, scale, scale);
        if ("halo_ring".equals(id.getPath())) {
            // A slow idle sway (010 section 6.3).
            float sway = Mth.sin(info.ageInTicks * 0.05F) * 0.02F;
            poseStack.last().pose().rotateY(sway);
            poseStack.last().normal().rotateY(sway);
        }
        RenderType base = this.getParentModel().renderType(texture);
        collector.order(0).submitCustomGeometry(poseStack, base,
                (pose, consumer) -> render(hat, pose, consumer, light, 0xFFFFFFFF));
        if (glow != null) {
            RenderType emissive = RenderTypes.entityTranslucentEmissive(glow);
            collector.order(1).submitCustomGeometry(poseStack, emissive,
                    (pose, consumer) -> render(hat, pose, consumer, GlowEyesLayer.FULL_BRIGHT, 0xFFFFFFFF));
        }
        poseStack.popPose();
    }

    private static Identifier standard(Identifier hat) {
        return Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID,
                "textures/entity/hat/" + hat.getPath() + ".png");
    }

    private static void render(ModelPart part, PoseStack.Pose pose, VertexConsumer consumer, int light, int colour) {
        PoseStack stack = new PoseStack();
        stack.last().pose().set(pose.pose());
        stack.last().normal().set(pose.normal());
        part.render(stack, consumer, light, OverlayTexture.NO_OVERLAY, colour);
    }
}
