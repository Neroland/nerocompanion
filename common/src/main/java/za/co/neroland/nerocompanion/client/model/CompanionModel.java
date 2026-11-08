package za.co.neroland.nerocompanion.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?}
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.client.CompanionRenderInfo;
import za.co.neroland.nerocompanion.client.CompanionRenderer;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.personality.Trait;

/**
 * The companion model: one class for all seven types, built from a {@link CompanionGeometry} layer and driven by
 * the animation channels of {@code 019} section 4 - base (hover, flutter, walk, breathe,
 * head look), work loops (only while {@code WORKING} in the matching mode), the SLEEP / PERCH / CHARGE poses blended
 * by {@link AnimTracks}, additive impulses, energy modifiers, then the eye-part visibility of section 3.
 *
 * <p>Each renderer bakes three instances per model variant, one per {@link Pass}: the base pass (every cube, no eye
 * plates), the glow pass for bands and contacts (eye plates hidden), and the glow pass for the eyes (only the active
 * eye plate draws; every other part keeps its transform but skips its cubes). All three run the same channels from
 * the same render facts, so the passes always line up - also from 1.21.9, where a submitted model is posed again
 * when it is drawn. Client only.
 */
//? if >=1.21.2 {
public class CompanionModel extends EntityModel<LivingEntityRenderState> {
//?} else {
/*public class CompanionModel extends net.minecraft.client.model.HierarchicalModel<net.minecraft.world.entity.Mob> {
*///?}

    /** Which cubes an instance draws. */
    public enum Pass {
        BASE, GLOW_BODY, GLOW_EYES
    }

    private static final float PI = Mth.PI;
    private static final float TWO_PI = Mth.TWO_PI;
    private static final String[] EYES = {"eyes_open", "eyes_happy", "eyes_low", "eyes_closed"};
    private static final String[] LENSES = {"lens_open", "lens_closed"};

    private final String variant;
    private final String type;
    private final Pass pass;
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final List<ModelPart> all = new ArrayList<>();
    private final List<ModelPart> hatChain = new ArrayList<>();
    private final ModelPart rootPart;
    /** The eye plate drawn this frame (for the glow layer's tint), set by the last {@code setupAnim}. */
    private int activeEye;
    //? if <1.21.2 {
    /*private final CompanionRenderInfo legacyInfo = new CompanionRenderInfo();
    *///?}

    public CompanionModel(ModelPart root, String variant, Pass pass) {
        //? if >=1.21.2 {
        super(root);
        //?}
        this.rootPart = root;
        this.variant = variant;
        this.type = variant.contains("/") ? variant.substring(0, variant.indexOf('/')) : variant;
        this.pass = pass;
        this.parts.put("root", root);
        for (CompanionGeometry.Part part : CompanionGeometry.parts(variant)) {
            ModelPart parent = this.parts.get(part.parent());
            if (parent != null && parent.hasChild(part.name())) {
                ModelPart child = parent.getChild(part.name());
                this.parts.put(part.name(), child);
                this.all.add(child);
            }
        }
        // The hat anchor's chain, root first: HatLayer replays these transforms.
        List<ModelPart> chain = new ArrayList<>();
        String name = "hat";
        while (name != null && this.parts.containsKey(name)) {
            chain.add(0, this.parts.get(name));
            name = parentOf(variant, name);
        }
        this.hatChain.addAll(chain);
        for (ModelPart part : this.all) {
            // The eye pass keeps every transform but draws only the eye plates' cubes.
            part.skipDraw = pass == Pass.GLOW_EYES && !isEye(part);
        }
    }

    /** Bakes {@code variant} for {@code pass}. */
    public static CompanionModel bake(String variant, Pass pass) {
        return new CompanionModel(CompanionGeometry.layer(variant).bakeRoot(), variant, pass);
    }

    //? if <1.21.2 {
    /*@Override
    public ModelPart root() {
        return this.rootPart;
    }
    *///?}

    public String variant() {
        return this.variant;
    }

    public Pass pass() {
        return this.pass;
    }

    /** The parts from the model root to the {@code hat} anchor (empty when the variant has no anchor). */
    public List<ModelPart> hatChain() {
        return this.hatChain;
    }

    /** 0 open, 1 happy, 2 low, 3 closed (Scout: 0 open, 3 closed) - the plate the last pose chose. */
    public int activeEye() {
        return this.activeEye;
    }

    @Nullable
    private static String parentOf(String variant, String name) {
        for (CompanionGeometry.Part part : CompanionGeometry.parts(variant)) {
            if (part.name().equals(name)) {
                return "root".equals(part.parent()) ? "root" : part.parent();
            }
        }
        return null;
    }

    private boolean isEye(ModelPart part) {
        for (String eye : EYES) {
            if (this.parts.get(eye) == part) {
                return true;
            }
        }
        for (String lens : LENSES) {
            if (this.parts.get(lens) == part) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private ModelPart p(String name) {
        return this.parts.get(name);
    }

    @Override
    //? if >=1.21.2 {
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        CompanionRenderInfo info = state instanceof CompanionRenderer.State s ? s.companion : null;
        if (info == null) {
            info = new CompanionRenderInfo();
            info.ageInTicks = state.ageInTicks;
            info.walkPos = state.walkAnimationPos;
            info.walkSpeed = state.walkAnimationSpeed;
        }
    //?} else {
    /*public void setupAnim(net.minecraft.world.entity.Mob entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        CompanionRenderInfo info = this.legacyInfo;
        info.fill(entity, ageInTicks);
        info.walkPos = limbSwing;
        info.walkSpeed = limbSwingAmount;
        info.headYaw = netHeadYaw * Mth.DEG_TO_RAD;
        info.headPitch = headPitch * Mth.DEG_TO_RAD;
    *///?}
        this.animate(info);
    }

    /** Runs every channel for {@code info}. Public for the Bay's parked display and the tests' pose checks. */
    public void animate(CompanionRenderInfo info) {
        this.rootPart.resetPose();
        for (ModelPart part : this.all) {
            part.resetPose();
        }
        float age = info.ageInTicks;
        float speed = Math.min(1.0F, info.walkSpeed);
        boolean low = info.energyPct <= 10 && !info.preview;
        if (low) {
            speed *= 0.7F;
        }
        boolean working = info.working() && !info.preview;
        CompanionMode mode = info.mode;
        boolean drone = isDrone();
        ModelPart root = this.rootPart;
        ModelPart body = p(bodyName());
        ModelPart look = drone ? p("body") : p("head");

        // --- base ---------------------------------------------------------------------------------------------
        float bobRate = info.chargeBlend > 0.0F ? Mth.lerp(info.chargeBlend, rate(), 0.05F) : rate();
        float bobAmp = "scout_drone".equals(this.type) ? 1.0F : 1.5F;
        if (drone) {
            bobAmp = Mth.lerp(info.perchBlend, bobAmp, 0.5F);
            bobAmp *= 1.0F - info.sleepBlend;
            if (low) {
                bobAmp *= 0.5F;
            }
            root.y += Mth.sin(age * bobRate) * bobAmp;
        }
        if ("personal_drone".equals(this.type)) {
            float flutter = Mth.sin(age * 0.12F + PI / 2.0F) * 0.08F;
            set(p("fin_left"), Axis.Z, flutter);
            set(p("fin_right"), Axis.Z, -flutter);
        } else if ("scout_drone".equals(this.type)) {
            float flutter = Mth.sin(age * 0.10F) * 0.05F;
            set(p("wing_left"), Axis.Z, flutter);
            set(p("wing_right"), Axis.Z, -flutter);
        }
        if (look != null) {
            look.yRot += Mth.sin(age * 0.04F) * 0.05F * (1.0F - speed);
            look.xRot += Mth.sin(age * 0.05F) * 0.03F * (1.0F - speed);
            boolean scanning = working && mode == CompanionMode.GUARD && "combat_bot".equals(this.type)
                    && !info.aggressive;
            if (!scanning && !info.preview) {
                look.yRot += Mth.clamp(info.headYaw, -0.9F, 0.9F);
                look.xRot += Mth.clamp(info.headPitch, -0.5F, 0.5F);
            }
        }
        this.walk(info.walkPos, speed, age);

        // --- work loops ---------------------------------------------------------------------------------------
        if (working) {
            this.work(info, mode, age);
        }

        // --- named poses ---------------------------------------------------------------------------------------
        if (info.sleepBlend > 0.0F) {
            this.sleepPose(info.sleepBlend, info.perched());
        }
        if (info.perchBlend > 0.0F) {
            this.perchPose(info.perchBlend);
        }
        if (info.chargeBlend > 0.0F && body != null) {
            body.y += info.chargeBlend;
        }

        // --- impulses ------------------------------------------------------------------------------------------
        if (info.impulse >= 0) {
            this.impulse(info, info.impulse, info.impulseT, age);
        }
        if ("combat_bot".equals(this.type) && info.lungeTicks > 0) {
            float t = 6 - info.lungeTicks;
            root.z -= 3.0F * Mth.sin(PI * t / 6.0F);
        }

        // --- energy --------------------------------------------------------------------------------------------
        if (low && body != null) {
            body.xRot += 0.06F;
        }

        // --- eyes and visibility -------------------------------------------------------------------------------
        this.activeEye = eye(info, working);
        this.showEyes();
        ModelPart cargo = p("cargo");
        if (cargo != null) {
            cargo.visible = working && mode == CompanionMode.CARRY;
        }
        if ("mining_bot".equals(this.type)) {
            boolean frameB = info.walkSpeed > 0.1F && ((int) (age / 2.0F)) % 2 == 1;
            setVisible("tread_left_a", !frameB);
            setVisible("tread_right_a", !frameB);
            setVisible("tread_left_b", frameB);
            setVisible("tread_right_b", frameB);
        }
    }

    private boolean isDrone() {
        return "personal_drone".equals(this.type) || "scout_drone".equals(this.type);
    }

    private float rate() {
        return "scout_drone".equals(this.type) ? 0.10F : 0.12F;
    }

    private String bodyName() {
        return switch (this.type) {
            case "mining_bot" -> "chassis";
            case "carry_bot" -> "tub";
            case "repair_bot" -> "column";
            case "combat_bot" -> "torso";
            default -> "body";
        };
    }

    private void walk(float walk, float speed, float age) {
        float a = Mth.cos(walk * 0.6662F);
        float b = Mth.cos(walk * 0.6662F + PI);
        switch (this.type) {
            case "carry_bot" -> {
                rot(p("leg_fl"), Axis.X, a * 0.45F * speed);
                rot(p("leg_rr"), Axis.X, a * 0.45F * speed);
                rot(p("leg_fr"), Axis.X, b * 0.45F * speed);
                rot(p("leg_rl"), Axis.X, b * 0.45F * speed);
            }
            case "repair_bot" -> {
                rot(p("leg_front"), Axis.X, a * 0.30F * speed);
                rot(p("leg_left"), Axis.X, b * 0.30F * speed);
                rot(p("leg_right"), Axis.X, b * 0.30F * speed);
                rot(p("column"), Axis.Z, Mth.sin(walk * 0.6662F) * 0.03F * speed);
            }
            case "combat_bot" -> {
                rot(p("leg_front"), Axis.X, a * 0.55F * speed);
                rot(p("leg_left"), Axis.X, b * 0.55F * speed);
                rot(p("leg_right"), Axis.X, b * 0.55F * speed);
            }
            case "scrapling" -> {
                rot(p("leg_fl"), Axis.X, a * 0.70F * speed);
                rot(p("leg_rr"), Axis.X, a * 0.70F * speed);
                rot(p("leg_fr"), Axis.X, b * 0.70F * speed);
                rot(p("leg_rl"), Axis.X, b * 0.70F * speed);
                rot(p("body"), Axis.Z, Mth.sin(walk * 0.6662F) * 0.06F * speed);
                set(p("tail"), Axis.X, -0.5F + Mth.sin(age * 0.15F) * 0.15F);
            }
            case "mining_bot" -> {
                ModelPart chassis = p("chassis");
                if (chassis != null) {
                    chassis.y += Mth.sin(walk * 1.3324F) * 0.3F * speed;
                }
            }
            default -> {
            }
        }
    }

    private void work(CompanionRenderInfo info, CompanionMode mode, float age) {
        switch (this.type) {
            case "mining_bot" -> {
                if (mode == CompanionMode.MINE) {
                    set(p("drill_tip"), Axis.Y, (age * 0.6F) % TWO_PI);
                    float phase = PI * (age % 16.0F) / 8.0F;
                    set(p("drill_arm"), Axis.X, -0.2F * (1.0F - Mth.cos(phase)) / 2.0F);
                    ModelPart chassis = p("chassis");
                    if (chassis != null) {
                        chassis.x += Mth.sin(age * 0.8F) * 0.5F;
                    }
                }
            }
            case "carry_bot" -> {
                if (mode == CompanionMode.CARRY) {
                    float h = Mth.sin(age * 0.15F) * 0.05F;
                    set(p("handle_left"), Axis.X, h);
                    set(p("handle_right"), Axis.X, h);
                }
            }
            case "personal_drone" -> {
                if (mode == CompanionMode.CARRY) {
                    set(p("fin_left"), Axis.Z, -0.3F);
                    set(p("fin_right"), Axis.Z, 0.3F);
                }
            }
            case "repair_bot" -> {
                if (mode == CompanionMode.REPAIR) {
                    set(p("arm_left"), Axis.X, -0.6F + 0.2F * Mth.sin(age * 0.15F));
                    set(p("arm_right"), Axis.X, -0.6F + 0.2F * Mth.sin(age * 0.15F + PI));
                }
            }
            case "combat_bot" -> {
                if (mode == CompanionMode.GUARD) {
                    if (info.aggressive) {
                        ModelPart plate = p("plate");
                        if (plate != null) {
                            plate.y -= 1.0F;
                        }
                    } else {
                        set(p("head"), Axis.Y, Mth.sin(age * 0.03F) * 0.9F);
                    }
                }
            }
            case "scout_drone" -> {
                if (mode == CompanionMode.SCOUT) {
                    set(p("body"), Axis.X, -0.2F);
                }
            }
            default -> {
            }
        }
    }

    private void sleepPose(float t, boolean perched) {
        switch (this.type) {
            case "personal_drone" -> {
                blend(p("fin_left"), Axis.Z, -0.9F, t);
                blend(p("fin_right"), Axis.Z, 0.9F, t);
                blend(p("body"), Axis.X, 0.15F, t);
                if (!perched) {
                    this.rootPart.y += 3.0F * t;
                }
            }
            case "scout_drone" -> {
                blend(p("wing_left"), Axis.Z, -0.5F, t);
                blend(p("wing_right"), Axis.Z, 0.5F, t);
                blend(p("body"), Axis.X, 0.15F, t);
                this.rootPart.y += 3.0F * t;
            }
            case "mining_bot" -> {
                blend(p("head"), Axis.X, 0.5F, t);
                blend(p("drill_arm"), Axis.X, 0.3F, t);
            }
            case "repair_bot" -> {
                blend(p("head"), Axis.X, 0.5F, t);
                blend(p("arm_left"), Axis.X, 0.4F, t);
                blend(p("arm_right"), Axis.X, 0.4F, t);
            }
            case "combat_bot" -> blend(p("head"), Axis.X, 0.35F, t);
            case "carry_bot" -> blend(p("cab"), Axis.X, 0.15F, t);
            case "scrapling" -> {
                blend(p("leg_fl"), Axis.X, -1.2F, t);
                blend(p("leg_fr"), Axis.X, -1.2F, t);
                blend(p("leg_rl"), Axis.X, 1.2F, t);
                blend(p("leg_rr"), Axis.X, 1.2F, t);
                blend(p("tail"), Axis.X, -1.4F, t);
                blend(p("head"), Axis.Y, 0.6F, t);
                ModelPart b = p("body");
                if (b != null) {
                    b.y += 2.0F * t;
                }
            }
            default -> {
            }
        }
    }

    private void perchPose(float t) {
        if ("personal_drone".equals(this.type)) {
            blend(p("fin_left"), Axis.Z, -0.6F, t);
            blend(p("fin_right"), Axis.Z, 0.6F, t);
            blend(p("body"), Axis.X, 0.1F, t);
        } else if ("scrapling".equals(this.type)) {
            blend(p("leg_rl"), Axis.X, -1.3F, t);
            blend(p("leg_rr"), Axis.X, -1.3F, t);
            blend(p("leg_fl"), Axis.X, -0.4F, t);
            blend(p("leg_fr"), Axis.X, -0.4F, t);
            blend(p("tail"), Axis.X, -0.8F, t);
            blend(p("tail"), Axis.Y, 0.9F, t);
        }
    }

    private void impulse(CompanionRenderInfo info, int impulse, float t, float age) {
        ModelPart root = this.rootPart;
        boolean bodyHead = isDrone() || "scrapling".equals(this.type);
        ModelPart nod = bodyHead ? p("body") : p("head");
        ModelPart body = p(bodyName());
        float s = Mth.sin(PI * Math.min(t, 10.0F) / 10.0F);
        Trait trait = Trait.byIndex(info.trait);
        switch (impulse) {
            case CompanionEntity.IMPULSE_EMOTE -> {
                if (trait == Trait.GRUMPY) {
                    root.yRot += 0.2F * Mth.sin(age * PI / 2.0F);
                } else {
                    root.y -= 2.0F * s;
                    rot(nod, Axis.Z, 0.3F * s);
                }
            }
            case CompanionEntity.IMPULSE_PET -> {
                rot(body, Axis.Y, 0.15F * Mth.sin(age * TWO_PI / 5.0F));
                if ("scrapling".equals(this.type)) {
                    rot(p("body"), Axis.Z, 0.25F * s);
                }
            }
            case CompanionEntity.IMPULSE_TRICK -> this.trick(trait, t, body, nod);
            case CompanionEntity.IMPULSE_STOMP -> root.y -= 3.0F * Mth.sin(PI * Math.min(t, 6.0F) / 6.0F);
            case CompanionEntity.IMPULSE_PICKUP -> {
                if ("carry_bot".equals(this.type)) {
                    root.y += 3.0F * s;
                } else {
                    root.y += 2.0F * Mth.sin(PI * Math.min(t, 6.0F) / 6.0F);
                }
            }
            default -> {
            }
        }
    }

    private void trick(Trait trait, float t, @Nullable ModelPart body, @Nullable ModelPart nod) {
        ModelPart root = this.rootPart;
        switch (trait) {
            case CURIOUS -> root.yRot += TWO_PI * Math.min(t, 20.0F) / 20.0F;
            case LOYAL -> {
                rot(nod, Axis.X, -0.3F);
                rot(p("fin_left"), Axis.Z, -0.6F);
                rot(p("fin_right"), Axis.Z, 0.6F);
                rot(p("arm_left"), Axis.X, -0.6F);
                rot(p("arm_right"), Axis.X, -0.6F);
            }
            case GRUMPY -> {
                float local = t % 6.0F;
                if (t < 18.0F && local < 3.0F) {
                    root.y -= 3.0F * Mth.sin(PI * local / 3.0F);
                }
            }
            case SLEEPY -> {
                float spin = TWO_PI * Math.min(t, 40.0F) / 40.0F;
                if ("scrapling".equals(this.type)) {
                    rot(p("body"), Axis.Z, spin);
                } else if (isDrone()) {
                    rot(p("body"), Axis.X, spin);
                } else {
                    root.yRot += spin;
                }
            }
            case SHOWOFF -> {
                float flip = TWO_PI * (t % 16.0F) / 16.0F;
                if (isDrone() || "scrapling".equals(this.type)) {
                    rot(p("body"), Axis.X, flip);
                } else {
                    root.yRot += flip;
                }
            }
            case SHY -> {
                if (t < 4.0F || (t >= 8.0F && t < 12.0F)) {
                    root.y -= 2.0F;
                }
            }
        }
    }

    /** 0 open, 1 happy, 2 low, 3 closed ({@code 019} sections 3, 4.2 and 4.5). */
    private static int eye(CompanionRenderInfo info, boolean working) {
        if (info.preview) {
            return 0;
        }
        boolean asleep = info.sleeping() && !info.charging();
        if (asleep || info.energyPct <= 0 || (info.napping() && info.napTicks >= 10.0F)) {
            return 3;
        }
        if (info.impulse == CompanionEntity.IMPULSE_PET) {
            return 1;
        }
        if (info.napping()) {
            return 2;
        }
        if (info.charging()) {
            return info.energyPct >= 50 ? 0 : 2;
        }
        if (working && info.mode == CompanionMode.REPAIR) {
            return 2;
        }
        if (Trait.byIndex(info.trait) == Trait.SLEEPY && !working && info.walkSpeed < 0.05F) {
            return 2;
        }
        return 0;
    }

    private void showEyes() {
        boolean eyesPass = this.pass == Pass.GLOW_EYES;
        if ("scout_drone".equals(this.type)) {
            setVisible("lens_open", eyesPass && this.activeEye != 3);
            setVisible("lens_closed", eyesPass && this.activeEye == 3);
            return;
        }
        for (int i = 0; i < EYES.length; i++) {
            setVisible(EYES[i], eyesPass && i == this.activeEye);
        }
    }

    private void setVisible(String name, boolean visible) {
        ModelPart part = p(name);
        if (part != null) {
            part.visible = visible;
        }
    }

    private enum Axis {
        X, Y, Z
    }

    private static void set(@Nullable ModelPart part, Axis axis, float value) {
        if (part == null) {
            return;
        }
        switch (axis) {
            case X -> part.xRot = value;
            case Y -> part.yRot = value;
            case Z -> part.zRot = value;
        }
    }

    private static void rot(@Nullable ModelPart part, Axis axis, float delta) {
        if (part == null) {
            return;
        }
        switch (axis) {
            case X -> part.xRot += delta;
            case Y -> part.yRot += delta;
            case Z -> part.zRot += delta;
        }
    }

    private static void blend(@Nullable ModelPart part, Axis axis, float target, float t) {
        if (part == null) {
            return;
        }
        switch (axis) {
            case X -> part.xRot = Mth.lerp(t, part.xRot, target);
            case Y -> part.yRot = Mth.lerp(t, part.yRot, target);
            case Z -> part.zRot = Mth.lerp(t, part.zRot, target);
        }
    }
}
