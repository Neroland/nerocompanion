package za.co.neroland.nerocompanion.client.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.charge.CompanionBayBlock;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.client.CompanionColours;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.registry.ModDataComponents;
import za.co.neroland.nerocompanion.registry.ModItems;

/**
 * The Companion Bay's parked companions: each occupied port on the front face shows its type's filled Capsule item
 * icon (a half-scale model does not fit a 6x6 px port), on a dark port plate, plus its status LED (resting /
 * charging / full). Culled - never a blanket true: nothing is drawn with every port empty or beyond 24 blocks, and
 * NeoForge frustum-tests {@link #getRenderBoundingBox}. Up to 4 icons and 4 LEDs. Client only.
 */
public class CompanionBayRenderer implements BlockEntityRenderer<CompanionBayBlockEntity, CompanionBayRenderer.State> {

    private static final RenderType SOLID = RenderTypes.entityTranslucentEmissive(Quads.WHITE);
    private static final int RANGE = 24;
    /** Port origins (pixels, north-facing layout): 2x2 grid of 6x6 ports. */
    private static final int[][] PORTS = {{2, 9}, {9, 9}, {2, 2}, {9, 2}};
    /** Icon size in pixels (the port is 6, the LED takes its top-right pixel). */
    private static final float ICON_PX = 4.5F;
    /** One filled Capsule per companion type, built once (client only). */
    private static final Map<String, ItemStack> CAPSULES = new HashMap<>();

    public static class State extends BlockEntityRenderState {
        public int turns;
        public List<CompanionBayBlockEntity.DisplaySlot> slots = List.of();
        public final ItemStackRenderState[] icons = {new ItemStackRenderState(), new ItemStackRenderState(),
                new ItemStackRenderState(), new ItemStackRenderState()};
        public final boolean[] iconReady = new boolean[4];
    }

    /** The filled Capsule a parked companion of {@code type} shows (its blueprint picks the per-type icon). */
    static ItemStack capsuleFor(String type) {
        return CAPSULES.computeIfAbsent(type, key -> {
            ItemStack stack = new ItemStack(ModItems.CAPSULE.get());
            Identifier id = Identifier.tryParse(key);
            if (id != null) {
                stack.set(ModDataComponents.BLUEPRINT.get(), id);
            }
            return stack;
        });
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
    public AABB getRenderBoundingBox(CompanionBayBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.1D);
    }

    @Override
    public boolean shouldRender(CompanionBayBlockEntity be, Vec3 camera) {
        boolean any = false;
        for (CompanionBayBlockEntity.DisplaySlot slot : be.display()) {
            any |= slot.occupied();
        }
        if (!any) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        double dx = camera.x - pos.getX() - 0.5D;
        double dy = camera.y - pos.getY() - 0.5D;
        double dz = camera.z - pos.getZ() - 0.5D;
        return dx * dx + dy * dy + dz * dz <= RANGE * RANGE;
    }

    @Override
    public void extractRenderState(CompanionBayBlockEntity be, State state, float partialTick, Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, camera, breaking);
        Direction facing = be.getBlockState().hasProperty(CompanionBayBlock.FACING)
                ? be.getBlockState().getValue(CompanionBayBlock.FACING) : Direction.NORTH;
        state.turns = Quads.quarterTurns(facing);
        state.slots = be.display();
        Level level = be.getLevel();
        int seed = (int) be.getBlockPos().asLong();
        for (int i = 0; i < state.icons.length; i++) {
            boolean occupied = i < state.slots.size() && state.slots.get(i).occupied() && level != null;
            state.iconReady[i] = occupied;
            if (!occupied) {
                continue;
            }
            ItemStack capsule = capsuleFor(state.slots.get(i).type());
            //? if >=1.21.9 {
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.icons[i], capsule,
                    ItemDisplayContext.FIXED, level, null, seed + i);
            //?} else {
            /*state.icons[i].update(capsule, ItemDisplayContext.FIXED, level, seed + i);
            *///?}
        }
    }


    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        List<CompanionBayBlockEntity.DisplaySlot> slots = state.slots;
        int turns = state.turns;
        collector.order(0).submitCustomGeometry(pose, SOLID, (p, c) -> {
            for (int i = 0; i < slots.size() && i < PORTS.length; i++) {
                CompanionBayBlockEntity.DisplaySlot slot = slots.get(i);
                if (!slot.occupied()) {
                    continue;
                }
                int x = PORTS[i][0];
                int y = PORTS[i][1];
                // the dark port plate the Capsule icon sits on
                Quads.wall(c, p, turns, -0.05F, x + 0.25F, x + 5.25F, y + 0.25F, y + 5.25F, CompanionColours.INK,
                        Quads.FULL_BRIGHT);
                int led = slot.state() == Payloads.BaySlotView.STATE_FULL ? CompanionColours.OK_GREEN
                        : slot.state() == Payloads.BaySlotView.STATE_CHARGING ? CompanionColours.COMPANION_TEAL
                                : CompanionColours.TEAL_DIM;
                Quads.wall(c, p, turns, -0.65F, x + 5.0F, x + 6.0F, y + 5.0F, y + 6.0F, led, Quads.FULL_BRIGHT);
            }
        });
        // The type's filled Capsule icon in each occupied port, facing out of the front face.
        for (int i = 0; i < slots.size() && i < PORTS.length; i++) {
            if (!state.iconReady[i]) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5F, 0.0F, 0.5F);
            //? if >=26.3 {
            /*pose.rotate(Axis.YP.rotationDegrees(-90.0F * turns));
            *///?} else {
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F * turns));
            //?}
            pose.translate(-0.5F, 0.0F, -0.5F);
            float half = ICON_PX / 2.0F;
            pose.translate((PORTS[i][0] + 0.5F + half) / 16.0F, (PORTS[i][1] + 0.5F + half) / 16.0F, -0.4F / 16.0F);
            float scale = ICON_PX / 16.0F;
            pose.scale(scale, scale, scale);
            state.icons[i].submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
