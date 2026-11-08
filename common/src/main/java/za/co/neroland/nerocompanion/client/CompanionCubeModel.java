package za.co.neroland.nerocompanion.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?}
import net.minecraft.util.Mth;

/**
 * Stage 1 placeholder model: a cube body with four rotor arms that spin slowly. Programmer art; the Stage 5 art
 * pass replaces it.
 */
//? if >=1.21.2 {
public class CompanionCubeModel extends EntityModel<LivingEntityRenderState> {
//?} else {
/*public class CompanionCubeModel extends net.minecraft.client.model.HierarchicalModel<net.minecraft.world.entity.Mob> {
*///?}

    private final ModelPart rotors;
    //? if <1.21.2 {
    /*private final ModelPart rootPart;
    *///?}

    public CompanionCubeModel(ModelPart root) {
        //? if >=1.21.2 {
        super(root);
        //?} else {
        /*this.rootPart = root;
        *///?}
        this.rotors = root.getChild("rotors");
    }

    //? if <1.21.2 {
    /*@Override
    public ModelPart root() {
        return this.rootPart;
    }
    *///?}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -4F, -4F, 8F, 6F, 8F)
                        // Sensor eye.
                        .texOffs(0, 14).addBox(-1.5F, -2F, -5F, 3F, 2F, 1F),
                PartPose.offset(0F, 20F, 0F));
        root.addOrReplaceChild("rotors",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-8F, -0.5F, -1F, 16F, 1F, 2F)
                        .addBox(-1F, -0.5F, -8F, 2F, 1F, 16F),
                PartPose.offset(0F, 15.5F, 0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    //? if >=1.21.2 {
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        float ageInTicks = state.ageInTicks;
    //?} else {
    /*public void setupAnim(net.minecraft.world.entity.Mob entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
    *///?}
        this.rotors.yRot = (ageInTicks * 0.6F) % Mth.TWO_PI;
    }
}
