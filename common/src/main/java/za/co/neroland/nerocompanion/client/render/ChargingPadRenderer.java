package za.co.neroland.nerocompanion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.charge.ChargingPadBlock;
import za.co.neroland.nerocompanion.charge.ChargingPadBlockEntity;

/**
 * The Charging Pad's charge ring: a 10x10-px glowing square ring on the plate, pulsing, drawn only while the pad is
 * charging. Culled - never a blanket true: nothing is drawn unless {@code charging}, within 32 blocks, and NeoForge
 * also frustum-tests {@link #getRenderBoundingBox}. Placeholder geometry until the art pass. Client only.
 */
public class ChargingPadRenderer implements BlockEntityRenderer<ChargingPadBlockEntity, ChargingPadRenderer.State> {

    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(Quads.WHITE);
    private static final int RANGE = 32;

    public static class State extends BlockEntityRenderState {
        public boolean charging;
        public float pulse;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return RANGE;
    }

    /** NeoForge frustum hook (inert elsewhere, so no {@code @Override}). */
    public AABB getRenderBoundingBox(ChargingPadBlockEntity be) {
        return new AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRender(ChargingPadBlockEntity be, Vec3 camera) {
        if (!be.getBlockState().getValue(ChargingPadBlock.CHARGING)) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        double dx = camera.x - pos.getX() - 0.5D;
        double dy = camera.y - pos.getY() - 0.5D;
        double dz = camera.z - pos.getZ() - 0.5D;
        return dx * dx + dy * dy + dz * dz <= RANGE * RANGE;
    }

    @Override
    public void extractRenderState(ChargingPadBlockEntity be, State state, float partialTick, Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, camera, breaking);
        state.charging = be.getBlockState().getValue(ChargingPadBlock.CHARGING);
        long time = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        state.pulse = 0.6F + 0.4F * Mth.sin((time % 628L + partialTick) * 0.15F);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.charging) {
            return;
        }
        int alpha = (int) (Math.max(0.2F, Math.min(1.0F, state.pulse)) * 255.0F);
        int colour = (alpha << 24) | 0x7FE8FF;
        collector.order(0).submitCustomGeometry(pose, GLOW, (p, c) -> {
            float y = 2.05F;
            Quads.floor(c, p, 0, 3, 3, 13, 4, y, colour, Quads.FULL_BRIGHT);
            Quads.floor(c, p, 0, 3, 12, 13, 13, y, colour, Quads.FULL_BRIGHT);
            Quads.floor(c, p, 0, 3, 4, 4, 12, y, colour, Quads.FULL_BRIGHT);
            Quads.floor(c, p, 0, 12, 4, 13, 12, y, colour, Quads.FULL_BRIGHT);
        });
    }
}
