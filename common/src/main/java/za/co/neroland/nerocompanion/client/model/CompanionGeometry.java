package za.co.neroland.nerocompanion.client.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The seven companion {@link LayerDefinition}s, the Scrapling's mossback model override and the ten hat models,
 * exactly as the geometry decision ({@code 019} section 1) tables them (part names, parents, pivots, box origins,
 * sizes, UVs, mirror flags). The cuboids are generated from the texture generator's {@code GEOMETRY} and
 * {@code HATS} tables, so the sheets and the models cannot drift apart: change a cube there, re-run the generator
 * and update the matching line here. Models are authored 1:1 against each type's hitbox. Client only.
 */
public final class CompanionGeometry {

    /** One part: its name and its parent's name ({@code root} for children of the mesh root). */
    public record Part(String name, String parent) {
    }

    /** Model override id of the Scrapling mossback skin ({@code nerocompanion:scrapling/mossback}). */
    public static final String MOSSBACK = "scrapling/mossback";

    private static final Map<String, List<Part>> PARTS = new HashMap<>();

    static {
        PARTS.put("personal_drone", List.of(
                new Part("body", "root"),
                new Part("fin_left", "body"),
                new Part("fin_right", "body"),
                new Part("nub", "body"),
                new Part("eyes_open", "body"),
                new Part("eyes_happy", "body"),
                new Part("eyes_low", "body"),
                new Part("eyes_closed", "body"),
                new Part("hat", "body")));
        PARTS.put("scout_drone", List.of(
                new Part("body", "root"),
                new Part("wing_left", "body"),
                new Part("wing_right", "body"),
                new Part("lens_open", "body"),
                new Part("lens_closed", "body"),
                new Part("hat", "body")));
        PARTS.put("mining_bot", List.of(
                new Part("chassis", "root"),
                new Part("tread_left_a", "root"),
                new Part("tread_left_b", "root"),
                new Part("tread_right_a", "root"),
                new Part("tread_right_b", "root"),
                new Part("head", "chassis"),
                new Part("eyes_open", "head"),
                new Part("eyes_happy", "head"),
                new Part("eyes_low", "head"),
                new Part("eyes_closed", "head"),
                new Part("drill_arm", "chassis"),
                new Part("drill_tip", "drill_arm"),
                new Part("hat", "head")));
        PARTS.put("carry_bot", List.of(
                new Part("tub", "root"),
                new Part("cab", "root"),
                new Part("eyes_open", "cab"),
                new Part("eyes_happy", "cab"),
                new Part("eyes_low", "cab"),
                new Part("eyes_closed", "cab"),
                new Part("handle_left", "tub"),
                new Part("handle_right", "tub"),
                new Part("cargo", "tub"),
                new Part("leg_fl", "root"),
                new Part("leg_fr", "root"),
                new Part("leg_rl", "root"),
                new Part("leg_rr", "root"),
                new Part("hat", "tub")));
        PARTS.put("repair_bot", List.of(
                new Part("column", "root"),
                new Part("head", "column"),
                new Part("eyes_open", "head"),
                new Part("eyes_happy", "head"),
                new Part("eyes_low", "head"),
                new Part("eyes_closed", "head"),
                new Part("arm_left", "column"),
                new Part("arm_right", "column"),
                new Part("tool_wrench", "arm_left"),
                new Part("tool_welder", "arm_right"),
                new Part("leg_front", "root"),
                new Part("leg_left", "root"),
                new Part("leg_right", "root"),
                new Part("hat", "head")));
        PARTS.put("combat_bot", List.of(
                new Part("torso", "root"),
                new Part("plate", "torso"),
                new Part("head", "torso"),
                new Part("eyes_open", "head"),
                new Part("eyes_happy", "head"),
                new Part("eyes_low", "head"),
                new Part("eyes_closed", "head"),
                new Part("emitter", "torso"),
                new Part("leg_front", "root"),
                new Part("leg_left", "root"),
                new Part("leg_right", "root"),
                new Part("hat", "torso")));
        PARTS.put("scrapling", List.of(
                new Part("body", "root"),
                new Part("head", "body"),
                new Part("eyes_open", "head"),
                new Part("eyes_happy", "head"),
                new Part("eyes_low", "head"),
                new Part("eyes_closed", "head"),
                new Part("tail", "body"),
                new Part("tail_tip", "tail"),
                new Part("leg_fl", "root"),
                new Part("leg_fr", "root"),
                new Part("leg_rl", "root"),
                new Part("leg_rr", "root"),
                new Part("hat", "head")));
        PARTS.put(MOSSBACK, List.of(
                new Part("body", "root"),
                new Part("head", "body"),
                new Part("eyes_open", "head"),
                new Part("eyes_happy", "head"),
                new Part("eyes_low", "head"),
                new Part("eyes_closed", "head"),
                new Part("tail", "body"),
                new Part("tail_tip", "tail"),
                new Part("leg_fl", "root"),
                new Part("leg_fr", "root"),
                new Part("leg_rl", "root"),
                new Part("leg_rr", "root"),
                new Part("hat", "head"),
                new Part("leaf", "tail_tip")));
    }

    private CompanionGeometry() {
    }

    /** The parts of {@code variant} (a type path, or {@link #MOSSBACK}) in parent-before-child order. */
    public static List<Part> parts(String variant) {
        List<Part> parts = PARTS.get(variant);
        return parts == null ? List.of() : parts;
    }

    /** Whether {@code variant} names a companion model (a type path or a model override). */
    public static boolean known(String variant) {
        return PARTS.containsKey(variant);
    }

    /** The layer definition of {@code variant} (a type path, or {@link #MOSSBACK}); the Helper Drone when unknown. */
    public static LayerDefinition layer(String variant) {
        return switch (variant) {
            case "personal_drone" -> personalDrone(false);
            case "scout_drone" -> scoutDrone(false);
            case "mining_bot" -> miningBot(false);
            case "carry_bot" -> carryBot(false);
            case "repair_bot" -> repairBot(false);
            case "combat_bot" -> combatBot(false);
            case "scrapling" -> scrapling(false);
            case MOSSBACK -> scrapling(true);
            default -> personalDrone(false);
        };
    }

    /** Hat render scale on this type ({@code 019} shared numbers: Scout and Scrapling 0.75). */
    public static float hatScale(String type) {
        return "scout_drone".equals(type) || "scrapling".equals(type) ? 0.75F : 1.0F;
    }

    /** The ten hat models (cubes in {@code hat}-part space, growing upward), or null for an unknown hat. */
    public static LayerDefinition hat(String hat) {
        return switch (hat) {
            case "hard_hat" -> hardHat();
            case "bobble_beanie" -> bobbleBeanie();
            case "tiny_crown" -> tinyCrown();
            case "sprout" -> sprout();
            case "halo_ring" -> haloRing();
            case "sun_visor" -> sunVisor();
            case "party_cone" -> partyCone();
            case "scrap_bucket" -> scrapBucket();
            case "antenna_bow" -> antennaBow();
            case "headlamp" -> headlamp();
            default -> null;
        };
    }

    /** Every hat model id path (for the asset checker and the gallery). */
    public static final List<String> HATS = List.of("hard_hat", "bobble_beanie", "tiny_crown", "sprout", "halo_ring",
            "sun_visor", "party_cone", "scrap_bucket", "antenna_bow", "headlamp");

    private static void add(Map<String, PartDefinition> parts, String parent, String name, CubeListBuilder cubes,
            PartPose pose) {
        parts.put(name, parts.get(parent).addOrReplaceChild(name, cubes, pose));
    }

    private static LayerDefinition personalDrone(boolean mossback) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        add(parts, "root", "body", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -3F, -4F, 8F, 6F, 8F),
                PartPose.offset(0F, 21F, 0F));
        add(parts, "body", "fin_left", CubeListBuilder.create().texOffs(0, 16).addBox(-3F, -0.5F, -3F, 3F, 1F, 6F),
                PartPose.offset(-4F, 0F, 0F));
        add(parts, "body", "fin_right", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(0F, -0.5F, -3F, 3F, 1F, 6F),
                PartPose.offset(4F, 0F, 0F));
        add(parts, "body", "nub", CubeListBuilder.create().texOffs(0, 24).addBox(-1F, -1F, -1F, 2F, 1F, 2F),
                PartPose.offset(0F, -3F, 0F));
        add(parts, "body", "eyes_open", CubeListBuilder.create().texOffs(18, 16).addBox(-3F, -2F, -4.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "body", "eyes_happy", CubeListBuilder.create().texOffs(18, 20).addBox(-3F, -2F, -4.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "body", "eyes_low", CubeListBuilder.create().texOffs(18, 24).addBox(-3F, -2F, -4.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "body", "eyes_closed", CubeListBuilder.create().texOffs(18, 28).addBox(-3F, -2F, -4.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "body", "hat", CubeListBuilder.create(),
                PartPose.offset(0F, -4F, 0F));
        if (mossback) {
            throw new IllegalArgumentException("only the scrapling has a mossback override");
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    private static LayerDefinition scoutDrone(boolean mossback) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        add(parts, "root", "body", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -3F, -6F, 8F, 4F, 6F),
                PartPose.offset(0F, 21F, 0F));
        add(parts, "body", "wing_left", CubeListBuilder.create().texOffs(0, 10).addBox(-5F, -0.5F, -4F, 5F, 3F, 10F),
                PartPose.offset(-4F, -1F, 0F));
        add(parts, "body", "wing_right", CubeListBuilder.create().texOffs(0, 10).mirror().addBox(0F, -0.5F, -4F, 5F, 3F, 10F),
                PartPose.offset(4F, -1F, 0F));
        add(parts, "body", "lens_open", CubeListBuilder.create().texOffs(0, 23).addBox(-4F, -2F, -6.5F, 8F, 2F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "body", "lens_closed", CubeListBuilder.create().texOffs(0, 26).addBox(-4F, -2F, -6.5F, 8F, 2F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "body", "hat", CubeListBuilder.create(),
                PartPose.offset(0F, -3F, -2F));
        if (mossback) {
            throw new IllegalArgumentException("only the scrapling has a mossback override");
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    private static LayerDefinition miningBot(boolean mossback) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        add(parts, "root", "chassis", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -14F, -6F, 12F, 10F, 12F),
                PartPose.offset(0F, 24F, 0F));
        add(parts, "root", "tread_left_a", CubeListBuilder.create().texOffs(0, 34).addBox(-7F, -4F, -7F, 3F, 4F, 14F),
                PartPose.offset(0F, 24F, 0F));
        add(parts, "root", "tread_left_b", CubeListBuilder.create().texOffs(2, 34).addBox(-7F, -4F, -7F, 3F, 4F, 14F),
                PartPose.offset(0F, 24F, 0F));
        add(parts, "root", "tread_right_a", CubeListBuilder.create().texOffs(0, 34).addBox(4F, -4F, -7F, 3F, 4F, 14F),
                PartPose.offset(0F, 24F, 0F));
        add(parts, "root", "tread_right_b", CubeListBuilder.create().texOffs(2, 34).addBox(4F, -4F, -7F, 3F, 4F, 14F),
                PartPose.offset(0F, 24F, 0F));
        add(parts, "chassis", "head", CubeListBuilder.create().texOffs(0, 22).addBox(-4F, -4F, -3F, 8F, 4F, 6F),
                PartPose.offset(0F, -14F, -1F));
        add(parts, "head", "eyes_open", CubeListBuilder.create().texOffs(48, 0).addBox(-3F, -3F, -3.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_happy", CubeListBuilder.create().texOffs(48, 4).addBox(-3F, -3F, -3.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_low", CubeListBuilder.create().texOffs(48, 8).addBox(-3F, -3F, -3.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_closed", CubeListBuilder.create().texOffs(48, 12).addBox(-3F, -3F, -3.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "chassis", "drill_arm", CubeListBuilder.create().texOffs(28, 22).addBox(-2F, -2F, -8F, 4F, 4F, 8F),
                PartPose.offset(0F, -9F, -6F));
        add(parts, "drill_arm", "drill_tip", CubeListBuilder.create().texOffs(52, 22).addBox(-1F, -1F, -4F, 2F, 2F, 4F),
                PartPose.offset(0F, 0F, -8F));
        add(parts, "head", "hat", CubeListBuilder.create(),
                PartPose.offset(0F, -4F, 0F));
        if (mossback) {
            throw new IllegalArgumentException("only the scrapling has a mossback override");
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition carryBot(boolean mossback) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        add(parts, "root", "tub", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -10F, -5F, 12F, 10F, 10F),
                PartPose.offset(0F, 15F, 0F));
        add(parts, "root", "cab", CubeListBuilder.create().texOffs(0, 20).addBox(-5F, -5F, -4F, 10F, 5F, 8F),
                PartPose.offset(0F, 20F, 0F));
        add(parts, "cab", "eyes_open", CubeListBuilder.create().texOffs(36, 20).addBox(-3F, -4F, -4.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "cab", "eyes_happy", CubeListBuilder.create().texOffs(50, 20).addBox(-3F, -4F, -4.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "cab", "eyes_low", CubeListBuilder.create().texOffs(36, 24).addBox(-3F, -4F, -4.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "cab", "eyes_closed", CubeListBuilder.create().texOffs(50, 24).addBox(-3F, -4F, -4.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "tub", "handle_left", CubeListBuilder.create().texOffs(12, 33).addBox(-2F, -1F, -3F, 2F, 2F, 6F),
                PartPose.offset(-6F, -5F, 0F));
        add(parts, "tub", "handle_right", CubeListBuilder.create().texOffs(12, 33).mirror().addBox(0F, -1F, -3F, 2F, 2F, 6F),
                PartPose.offset(6F, -5F, 0F));
        add(parts, "tub", "cargo", CubeListBuilder.create().texOffs(28, 33).addBox(-4F, -2F, -3F, 8F, 3F, 6F),
                PartPose.offset(0F, -10F, 0F));
        add(parts, "root", "leg_fl", CubeListBuilder.create().texOffs(0, 33).addBox(-1.5F, 0F, -1.5F, 3F, 4F, 3F),
                PartPose.offset(-3.5F, 20F, -2.5F));
        add(parts, "root", "leg_fr", CubeListBuilder.create().texOffs(0, 33).mirror().addBox(-1.5F, 0F, -1.5F, 3F, 4F, 3F),
                PartPose.offset(3.5F, 20F, -2.5F));
        add(parts, "root", "leg_rl", CubeListBuilder.create().texOffs(0, 33).addBox(-1.5F, 0F, -1.5F, 3F, 4F, 3F),
                PartPose.offset(-3.5F, 20F, 2.5F));
        add(parts, "root", "leg_rr", CubeListBuilder.create().texOffs(0, 33).mirror().addBox(-1.5F, 0F, -1.5F, 3F, 4F, 3F),
                PartPose.offset(3.5F, 20F, 2.5F));
        add(parts, "tub", "hat", CubeListBuilder.create(),
                PartPose.offset(0F, -10F, 0F));
        if (mossback) {
            throw new IllegalArgumentException("only the scrapling has a mossback override");
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition repairBot(boolean mossback) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        add(parts, "root", "column", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -10F, -4F, 8F, 10F, 8F),
                PartPose.offset(0F, 20F, 0F));
        add(parts, "column", "head", CubeListBuilder.create().texOffs(32, 0).addBox(-3.5F, -5F, -3.5F, 7F, 5F, 7F),
                PartPose.offset(0F, -10F, 0F));
        add(parts, "head", "eyes_open", CubeListBuilder.create().texOffs(0, 18).addBox(-2.5F, -4F, -4F, 5F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_happy", CubeListBuilder.create().texOffs(12, 18).addBox(-2.5F, -4F, -4F, 5F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_low", CubeListBuilder.create().texOffs(0, 22).addBox(-2.5F, -4F, -4F, 5F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_closed", CubeListBuilder.create().texOffs(12, 22).addBox(-2.5F, -4F, -4F, 5F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "column", "arm_left", CubeListBuilder.create().texOffs(0, 40).addBox(-2F, -7F, -1F, 2F, 7F, 2F),
                PartPose.offset(-4F, -9F, 0F));
        add(parts, "column", "arm_right", CubeListBuilder.create().texOffs(0, 40).mirror().addBox(0F, -7F, -1F, 2F, 7F, 2F),
                PartPose.offset(4F, -9F, 0F));
        add(parts, "arm_left", "tool_wrench", CubeListBuilder.create().texOffs(24, 40).addBox(-1.5F, -2F, -1.5F, 3F, 2F, 3F),
                PartPose.offset(-1F, -7F, 0F));
        add(parts, "arm_right", "tool_welder", CubeListBuilder.create().texOffs(36, 40).addBox(-1.5F, -2F, -1.5F, 3F, 2F, 3F),
                PartPose.offset(1F, -7F, 0F));
        add(parts, "root", "leg_front", CubeListBuilder.create().texOffs(28, 18).addBox(-1F, 0F, -1F, 2F, 4F, 2F),
                PartPose.offset(0F, 20F, -3F));
        add(parts, "root", "leg_left", CubeListBuilder.create().texOffs(28, 18).addBox(-1F, 0F, -1F, 2F, 4F, 2F),
                PartPose.offset(-3F, 20F, 2F));
        add(parts, "root", "leg_right", CubeListBuilder.create().texOffs(28, 18).addBox(-1F, 0F, -1F, 2F, 4F, 2F),
                PartPose.offset(3F, 20F, 2F));
        add(parts, "head", "hat", CubeListBuilder.create(),
                PartPose.offset(0F, -5F, 0F));
        if (mossback) {
            throw new IllegalArgumentException("only the scrapling has a mossback override");
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition combatBot(boolean mossback) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        add(parts, "root", "torso", CubeListBuilder.create().texOffs(24, 11).addBox(-4F, -9F, -3.5F, 8F, 9F, 7F),
                PartPose.offset(0F, 16F, 0F));
        add(parts, "torso", "plate", CubeListBuilder.create().texOffs(0, 0).addBox(-8F, -7F, -4F, 16F, 3F, 8F),
                PartPose.offset(0F, -9F, 0F));
        add(parts, "torso", "head", CubeListBuilder.create().texOffs(0, 11).addBox(-3F, -4F, -3F, 6F, 5F, 6F),
                PartPose.offset(0F, -9F, 0F));
        add(parts, "head", "eyes_open", CubeListBuilder.create().texOffs(0, 30).addBox(-3F, -3F, -3.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_happy", CubeListBuilder.create().texOffs(14, 30).addBox(-3F, -3F, -3.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_low", CubeListBuilder.create().texOffs(0, 34).addBox(-3F, -3F, -3.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_closed", CubeListBuilder.create().texOffs(14, 34).addBox(-3F, -3F, -3.5F, 6F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "torso", "emitter", CubeListBuilder.create().texOffs(0, 22).addBox(2F, -4F, -4F, 4F, 2F, 6F),
                PartPose.offset(0F, -9F, 0F));
        add(parts, "root", "leg_front", CubeListBuilder.create().texOffs(0, 44).addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F),
                PartPose.offset(0F, 16F, -2F));
        add(parts, "root", "leg_left", CubeListBuilder.create().texOffs(0, 44).addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F),
                PartPose.offset(-2.5F, 16F, 2F));
        add(parts, "root", "leg_right", CubeListBuilder.create().texOffs(0, 44).mirror().addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F),
                PartPose.offset(2.5F, 16F, 2F));
        add(parts, "torso", "hat", CubeListBuilder.create(),
                PartPose.offset(0F, -16F, 0F));
        if (mossback) {
            throw new IllegalArgumentException("only the scrapling has a mossback override");
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition scrapling(boolean mossback) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        add(parts, "root", "body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -5F, -4F, 7F, 5F, 8F),
                PartPose.offset(0F, 21F, 0F));
        add(parts, "body", "head", CubeListBuilder.create().texOffs(30, 0).addBox(-2.5F, -3F, -5F, 5F, 4F, 5F),
                PartPose.offset(0F, -4F, -4F));
        add(parts, "head", "eyes_open", CubeListBuilder.create().texOffs(0, 13).addBox(-2.5F, -2F, -5.5F, 5F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_happy", CubeListBuilder.create().texOffs(12, 13).addBox(-2.5F, -2F, -5.5F, 5F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_low", CubeListBuilder.create().texOffs(24, 13).addBox(-2.5F, -2F, -5.5F, 5F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "head", "eyes_closed", CubeListBuilder.create().texOffs(36, 13).addBox(-2.5F, -2F, -5.5F, 5F, 3F, 1F),
                PartPose.offset(0F, 0F, 0F));
        add(parts, "body", "tail", CubeListBuilder.create().texOffs(40, 20).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 5F),
                PartPose.offset(0F, -4F, 4F));
        add(parts, "tail", "tail_tip", CubeListBuilder.create().texOffs(52, 20).addBox(-0.5F, -2F, -0.5F, 1F, 2F, 1F),
                PartPose.offset(0F, 0F, 5F));
        add(parts, "root", "leg_fl", CubeListBuilder.create().texOffs(0, 20).addBox(-1F, 0F, -1F, 2F, 3F, 2F),
                PartPose.offset(-2.5F, 21F, -2F));
        add(parts, "root", "leg_fr", CubeListBuilder.create().texOffs(8, 20).addBox(-1F, 0F, -1F, 2F, 3F, 2F),
                PartPose.offset(2.5F, 21F, -2F));
        add(parts, "root", "leg_rl", CubeListBuilder.create().texOffs(16, 20).addBox(-1F, 0F, -1F, 2F, 3F, 2F),
                PartPose.offset(-2.5F, 21F, 2F));
        add(parts, "root", "leg_rr", CubeListBuilder.create().texOffs(24, 20).addBox(-1F, 0F, -1F, 2F, 3F, 2F),
                PartPose.offset(2.5F, 21F, 2F));
        add(parts, "head", "hat", CubeListBuilder.create(),
                PartPose.offset(0F, -3F, -1.5F));
        if (mossback) {
            add(parts, "tail_tip", "leaf", CubeListBuilder.create().texOffs(56, 20).addBox(-1F, -1F, -1F, 2F, 1F, 2F),
                    PartPose.offset(0F, -2F, 0F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition hardHat() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -5F, -4F, 8F, 4F, 8F),
                PartPose.ZERO);
        root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 12).addBox(-5F, -1F, -5F, 10F, 1F, 10F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition bobbleBeanie() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -5F, -4F, 8F, 5F, 8F),
                PartPose.ZERO);
        root.addOrReplaceChild("bobble", CubeListBuilder.create().texOffs(32, 0).addBox(-1F, -7F, -1F, 2F, 2F, 2F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition tinyCrown() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3F, -3F, 6F, 3F, 6F),
                PartPose.ZERO);
        root.addOrReplaceChild("point_fl", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -4F, -3F, 1F, 1F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("point_fr", CubeListBuilder.create().texOffs(24, 0).addBox(2F, -4F, -3F, 1F, 1F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("point_bl", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -4F, 2F, 1F, 1F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("point_br", CubeListBuilder.create().texOffs(24, 0).addBox(2F, -4F, 2F, 1F, 1F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("jewel", CubeListBuilder.create().texOffs(28, 0).addBox(-0.5F, -2F, -3.5F, 1F, 1F, 1F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition sprout() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("stem", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("leaf_left", CubeListBuilder.create().texOffs(4, 0).addBox(-2.5F, -4F, -0.5F, 2F, 1F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("leaf_right", CubeListBuilder.create().texOffs(4, 0).addBox(0.5F, -3F, -0.5F, 2F, 1F, 1F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition haloRing() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("ring_front", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -3F, -4F, 8F, 1F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("ring_back", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -3F, 3F, 8F, 1F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("ring_left", CubeListBuilder.create().texOffs(0, 2).addBox(-4F, -3F, -3F, 1F, 1F, 6F),
                PartPose.ZERO);
        root.addOrReplaceChild("ring_right", CubeListBuilder.create().texOffs(0, 2).addBox(3F, -3F, -3F, 1F, 1F, 6F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition sunVisor() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -1F, -7F, 10F, 1F, 6F),
                PartPose.ZERO);
        root.addOrReplaceChild("glass", CubeListBuilder.create().texOffs(0, 7).addBox(-4F, 0F, -7F, 8F, 2F, 1F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition partyCone() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("cone_base", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3F, -3F, 6F, 3F, 6F),
                PartPose.ZERO);
        root.addOrReplaceChild("cone_mid", CubeListBuilder.create().texOffs(0, 9).addBox(-2F, -6F, -2F, 4F, 3F, 4F),
                PartPose.ZERO);
        root.addOrReplaceChild("pom", CubeListBuilder.create().texOffs(16, 9).addBox(-1F, -8F, -1F, 2F, 2F, 2F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition scrapBucket() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -6F, -3.5F, 7F, 6F, 7F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition antennaBow() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -3F, -0.5F, 1F, 3F, 1F),
                PartPose.ZERO);
        root.addOrReplaceChild("bow", CubeListBuilder.create().texOffs(4, 0).addBox(-2F, -5F, -0.5F, 4F, 2F, 1F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition headlamp() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("housing", CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -3F, -4F, 4F, 3F, 3F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
}
