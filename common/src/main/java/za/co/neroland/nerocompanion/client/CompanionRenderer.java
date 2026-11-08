package za.co.neroland.nerocompanion.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?}
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

import za.co.neroland.nerocompanion.client.look.ClientLooks;
import za.co.neroland.nerocompanion.client.model.CompanionGeometry;
import za.co.neroland.nerocompanion.client.model.CompanionModel;

/**
 * The companion renderer, one per type: the type's {@link CompanionModel} (plus any skin model override, e.g. the
 * Scrapling mossback) in its three passes, the skin's texture, the glow and hat layers, and two hook lists so
 * packages can add drawing without owning this class. Each frame picks the model variant and texture from the
 * synched skin. Perch position needs nothing here - the entity follows the owner's interpolated shoulder point on
 * the client; {@code PerchPose} (a {@link PoseHook}) tucks it in.
 *
 * <ul>
 *   <li>{@link #registerLayer}: called for every companion renderer as it is built (resource load), to add render
 *       layers with {@link #addCompanionLayer}. On 1.21.2+ layers render a {@link State} (read
 *       {@code state.companion}); on 1.21.1 they render the {@code Mob} (read {@link #legacyInfo()}).</li>
 *   <li>{@link #registerPoseHook}: called each frame after the base scale, with the companion facts and the pose
 *       stack (model space, before the model draws).</li>
 * </ul>
 * The draw path differs per line: {@code submit} from 1.21.9, {@code render(state)} on 1.21.2-1.21.8 and
 * {@code render(entity)} on 1.21.1; all three pick the variant first. Client only.
 */
//? if >=1.21.2 {
public class CompanionRenderer extends MobRenderer<Mob, CompanionRenderer.State, EntityModel<LivingEntityRenderState>> {
//?} else {
/*// 1.21.1 renderers read the entity directly; there is no render-state object yet.
public class CompanionRenderer extends MobRenderer<Mob, EntityModel<Mob>> {
*///?}

    /** Adds layers to one companion renderer as it is built. */
    @FunctionalInterface
    public interface LayerFactory {
        void attach(CompanionRenderer renderer);
    }

    /** Adjusts the model pose each frame. */
    @FunctionalInterface
    public interface PoseHook {
        void apply(CompanionRenderInfo info, PoseStack poseStack);
    }

    /** One model variant in its three passes. */
    public record Models(CompanionModel base, CompanionModel glowBody, CompanionModel glowEyes) {
        static Models bake(String variant) {
            return new Models(CompanionModel.bake(variant, CompanionModel.Pass.BASE),
                    CompanionModel.bake(variant, CompanionModel.Pass.GLOW_BODY),
                    CompanionModel.bake(variant, CompanionModel.Pass.GLOW_EYES));
        }
    }

    private static final List<LayerFactory> LAYERS = new CopyOnWriteArrayList<>();
    private static final List<PoseHook> POSE_HOOKS = new CopyOnWriteArrayList<>();

    public static void registerLayer(LayerFactory factory) {
        LAYERS.add(java.util.Objects.requireNonNull(factory));
    }

    public static void registerPoseHook(PoseHook hook) {
        POSE_HOOKS.add(java.util.Objects.requireNonNull(hook));
    }

    private final String type;
    private final Map<String, Models> variants = new HashMap<>();
    private Models current;

    // this-escape: the layer factories receive the renderer once it is fully built (the last constructor step).
    @SuppressWarnings("this-escape")
    public CompanionRenderer(EntityRendererProvider.Context context, String type, float shadow) {
        super(context, CompanionModel.bake(type, CompanionModel.Pass.BASE), shadow);
        this.type = type;
        Models standard = new Models((CompanionModel) this.model,
                CompanionModel.bake(type, CompanionModel.Pass.GLOW_BODY),
                CompanionModel.bake(type, CompanionModel.Pass.GLOW_EYES));
        this.variants.put(type, standard);
        if ("scrapling".equals(type)) {
            this.variants.put(CompanionGeometry.MOSSBACK, Models.bake(CompanionGeometry.MOSSBACK));
        }
        this.current = standard;
        this.addCompanionLayer(new za.co.neroland.nerocompanion.client.layer.GlowEyesLayer(this));
        this.addCompanionLayer(new za.co.neroland.nerocompanion.client.layer.HatLayer(this));
        for (LayerFactory factory : LAYERS) {
            factory.attach(this);
        }
    }

    /** The type path this renderer draws. */
    public String type() {
        return this.type;
    }

    /** The variant chosen for the companion being drawn (set at the start of each draw). */
    public Models current() {
        return this.current;
    }

    /** Picks the model variant for {@code info} and makes its base pass the drawn model. */
    private void select(CompanionRenderInfo info) {
        Models chosen = this.variants.get(ClientLooks.variant(this.type, info.skin));
        if (chosen == null) {
            chosen = this.variants.get(this.type);
        }
        this.current = chosen;
        this.model = chosen.base();
    }

    //? if >=1.21.2 {
    /** The companion render state: the living state plus {@link CompanionRenderInfo}. */
    public static class State extends LivingEntityRenderState {
        public final CompanionRenderInfo companion = new CompanionRenderInfo();
    }

    /** Adds a layer (from a {@link LayerFactory}). */
    public boolean addCompanionLayer(RenderLayer<State, EntityModel<LivingEntityRenderState>> layer) {
        return this.addLayer(layer);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Mob entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.companion.fill(entity, state.ageInTicks);
        state.companion.walkPos = state.walkAnimationPos;
        state.companion.walkSpeed = state.walkAnimationSpeed;
        state.companion.headYaw = state.yRot * Mth.DEG_TO_RAD;
        state.companion.headPitch = state.xRot * Mth.DEG_TO_RAD;
    }

    //? if >=1.21.9 {
    @Override
    public void submit(State state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
            net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        this.select(state.companion);
        super.submit(state, poseStack, collector, camera);
    }
    //?} else {
    /*@Override
    public void render(State state, PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light) {
        this.select(state.companion);
        super.render(state, poseStack, buffers, light);
    }
    *///?}

    @Override
    protected void scale(State state, PoseStack poseStack) {
        super.scale(state, poseStack);
        for (PoseHook hook : POSE_HOOKS) {
            hook.apply(state.companion, poseStack);
        }
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return ClientLooks.texture(this.type, state.companion.skin);
    }
    //?} else {
    /*private final CompanionRenderInfo info = new CompanionRenderInfo();

    // Adds a layer (from a LayerFactory).
    public boolean addCompanionLayer(RenderLayer<Mob, EntityModel<Mob>> layer) {
        return this.addLayer(layer);
    }

    // The facts of the companion being drawn (filled at the start of render).
    public CompanionRenderInfo legacyInfo() {
        return this.info;
    }

    @Override
    public void render(Mob entity, float yaw, float partialTick, PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        this.info.fill(entity, entity.tickCount + partialTick);
        this.select(this.info);
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    protected void scale(Mob entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        for (PoseHook hook : POSE_HOOKS) {
            hook.apply(this.info, poseStack);
        }
    }

    @Override
    public Identifier getTextureLocation(Mob entity) {
        return ClientLooks.texture(this.type, this.info.entityId == entity.getId() ? this.info.skin : "");
    }
    *///?}
}
