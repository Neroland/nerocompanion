package za.co.neroland.nerocompanion.client.perch;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.client.CompanionRenderInfo;
import za.co.neroland.nerocompanion.client.CompanionRenderer;
import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Shoulder-perch rendering ({@code 001} section 6), through the renderer's pose hook - no player render layer, so
 * nothing per loader. The entity already follows the owner's shoulder on the client each tick; this hook makes the
 * frame exact: it finds the owner whose shoulder point the companion is pinned to (no owner id or UUID is needed -
 * the shoulder formula identifies it), works out the owner's shoulder at this frame's partial tick, and shifts the
 * model by the difference, so a perched companion never lags or jitters behind the owner whichever entity ticked
 * first. Then it tucks in (80 %, a slight forward lean). Works the same on every line. Client only.
 */
public final class PerchPose {

    /** Perched companions draw at this scale. */
    public static final float PERCHED_SCALE = 0.8F;
    private static final float LEAN_RADIANS = 0.1F;
    /** Matching tolerance (blocks) between the companion and a candidate owner's shoulder point. */
    private static final double MATCH = 1.0E-3D;
    /** Corrections larger than this are a teleport, not lag; leave them alone. */
    private static final double MAX_SHIFT = 2.0D;

    private PerchPose() {
    }

    /** Client init (integration agent: {@code PerchPose.register();} in {@code ClientCompanionContent}). */
    public static void register() {
        CompanionRenderer.registerPoseHook(PerchPose::apply);
    }

    static void apply(CompanionRenderInfo info, PoseStack poseStack) {
        if (!info.companion || !info.perched()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(info.entityId);
        if (entity instanceof CompanionEntity companion) {
            correct(level, companion, info.ageInTicks, poseStack);
        }
        poseStack.scale(PERCHED_SCALE, PERCHED_SCALE, PERCHED_SCALE);
        poseStack.last().pose().rotateX(LEAN_RADIANS);
        poseStack.last().normal().rotateX(LEAN_RADIANS);
    }

    private static void correct(ClientLevel level, CompanionEntity companion, float ageInTicks, PoseStack poseStack) {
        float partial = Mth.clamp(ageInTicks - companion.tickCount, 0.0F, 1.0F);
        Player owner = findOwner(level, companion);
        if (owner == null) {
            return;
        }
        Vec3 exact = CompanionEntity.shoulderPoint(Mth.lerp(partial, owner.xOld, owner.getX()),
                Mth.lerp(partial, owner.yOld, owner.getY()), Mth.lerp(partial, owner.zOld, owner.getZ()),
                Mth.rotLerp(partial, owner.yBodyRotO, owner.yBodyRot), owner.getBbHeight());
        Vec3 drawn = new Vec3(Mth.lerp(partial, companion.xo, companion.getX()),
                Mth.lerp(partial, companion.yo, companion.getY()), Mth.lerp(partial, companion.zo, companion.getZ()));
        Vec3 shift = exact.subtract(drawn);
        double length = shift.length();
        if (length < 1.0E-4D || length > MAX_SHIFT) {
            return;
        }
        float bodyYaw = Mth.rotLerp(partial, companion.yBodyRotO, companion.yBodyRot);
        // Total scale applied so far (entity scale x the renderer's model scale x earlier hooks), read from the matrix.
        float modelSpace = Math.max(1.0E-3F, poseStack.last().pose().getScale(new Vector3f()).y);
        Vector3f local = modelShift(shift, bodyYaw, modelSpace);
        poseStack.translate(local.x, local.y, local.z);
    }

    /**
     * The pose-hook-space translation that moves the model by {@code worldShift}: the hook runs after
     * {@code scale(entity)}, {@code rotY(180 - bodyYaw)}, the {@code (-1, -1, 1)} flip and the renderer's model
     * scale, so undo those in reverse; {@code totalScale} is their product.
     */
    static Vector3f modelShift(Vec3 worldShift, float bodyYaw, float totalScale) {
        Vector3f v = new Vector3f((float) worldShift.x, (float) worldShift.y, (float) worldShift.z);
        new Quaternionf().rotationY(-(180.0F - bodyYaw) * Mth.DEG_TO_RAD).transform(v);
        return new Vector3f(-v.x / totalScale, -v.y / totalScale, v.z / totalScale);
    }

    /** The player whose shoulder point (this tick or last) the companion sits on, or null. */
    @Nullable
    private static Player findOwner(ClientLevel level, CompanionEntity companion) {
        for (Player player : level.players()) {
            if (player.distanceToSqr(companion) > 9.0D) {
                continue;
            }
            Vec3 now = CompanionEntity.shoulderPoint(player.getX(), player.getY(), player.getZ(), player.yBodyRot,
                    player.getBbHeight());
            Vec3 before = CompanionEntity.shoulderPoint(player.xOld, player.yOld, player.zOld, player.yBodyRotO,
                    player.getBbHeight());
            if (matches(now, companion) || matches(before, companion)) {
                return player;
            }
        }
        return null;
    }

    private static boolean matches(Vec3 point, Entity companion) {
        return Math.abs(point.x - companion.getX()) < MATCH && Math.abs(point.y - companion.getY()) < MATCH
                && Math.abs(point.z - companion.getZ()) < MATCH;
    }
}
