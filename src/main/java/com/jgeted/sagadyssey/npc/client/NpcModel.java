package com.jgeted.sagadyssey.npc.client;

import com.google.common.collect.ImmutableList;
import com.jgeted.sagadyssey.npc.entity.NpcBase;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * NPC 自定义模型 — 细手臂 + 全套 outer layer（hat/jacket/sleeves/pants）。
 * 基于 PlayerModel 的 slim 变体，但泛型适配 NpcBase。
 */
public class NpcModel extends HumanoidModel<NpcBase> {

    private final ModelPart jacket;
    private final ModelPart leftSleeve;
    private final ModelPart rightSleeve;
    private final ModelPart leftPants;
    private final ModelPart rightPants;

    public NpcModel(ModelPart root) {
        super(root);
        // HumanoidModel(1.21.1) 所有部件都是 root 的直接子部件
        this.jacket = root.getChild("jacket");
        this.leftSleeve = root.getChild("left_sleeve");
        this.rightSleeve = root.getChild("right_sleeve");
        this.leftPants = root.getChild("left_pants");
        this.rightPants = root.getChild("right_pants");
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg, hat,
                jacket, leftSleeve, rightSleeve, leftPants, rightPants);
    }

    /** 细手臂 + 全 outer layer 的 LayerDefinition。1.21.1 所有部件必须是 root 直接子部件。 */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeDeformation outer = new CubeDeformation(0.25F);
        CubeDeformation hatOuter = new CubeDeformation(0.5F);

        // Head + hat — 原点即可
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, hatOuter),
                PartPose.ZERO);

        // Body + jacket
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(16, 32)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, outer),
                PartPose.ZERO);

        // Left arm + sleeve (slim: 3px)
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(32, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_sleeve",
                CubeListBuilder.create().texOffs(48, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, outer),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        // Right arm + sleeve (slim: 3px)
        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_sleeve",
                CubeListBuilder.create().texOffs(40, 32)
                        .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, outer),
                PartPose.offset(-5.0F, 2.0F, 0.0F));

        // Left leg + pants
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(16, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_pants",
                CubeListBuilder.create().texOffs(0, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        // Right leg + pants
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("right_pants",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
                PartPose.offset(-1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    // 1.21.1 的 HumanoidMobRenderer 不会设置手臂姿态（getArmPose 已从渲染器移入 Player 等实体类），
    // 需自行在 setupAnim 之前赋值，否则 NPC 拉弓/持弩时手臂不抬起
    @Override
    public void prepareMobModel(NpcBase entity, float limbSwing, float limbSwingAmount, float partialTick) {
        HumanoidModel.ArmPose mainPose = armPoseFor(entity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = armPoseFor(entity, InteractionHand.OFF_HAND);
        if (mainPose.isTwoHanded()) {
            offPose = entity.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            this.rightArmPose = mainPose;
            this.leftArmPose = offPose;
        } else {
            this.rightArmPose = offPose;
            this.leftArmPose = mainPose;
        }
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
    }

    private static HumanoidModel.ArmPose armPoseFor(NpcBase entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0) {
            UseAnim useAnim = stack.getUseAnimation();
            if (useAnim == UseAnim.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (useAnim == UseAnim.CROSSBOW) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
        } else if (!entity.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public void setupAnim(NpcBase entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // outer layer 跟随 inner layer
        if (jacket != null) jacket.copyFrom(body);
        if (leftSleeve != null) leftSleeve.copyFrom(leftArm);
        if (rightSleeve != null) rightSleeve.copyFrom(rightArm);
        if (leftPants != null) leftPants.copyFrom(leftLeg);
        if (rightPants != null) rightPants.copyFrom(rightLeg);
    }
}
