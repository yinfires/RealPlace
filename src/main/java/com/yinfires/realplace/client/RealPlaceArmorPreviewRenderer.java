package com.yinfires.realplace.client;

import com.yinfires.realplace.server.RealPlaceObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

final class RealPlaceArmorPreviewRenderer {
    private static ArmorStand armorStand;
    private static Level armorStandLevel;

    private RealPlaceArmorPreviewRenderer() {
    }

    static boolean render(RenderLevelStageEvent event, MultiBufferSource bufferSource, RealPlaceObject object) {
        if (!isHumanoidArmorModel(object.stack(), object.modelMode())) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return true;
        }
        ArmorStand stand = armorStand(minecraft.level);
        configureStand(stand, object);

        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(object.position().x - camX, object.position().y - camY, object.position().z - camZ);
        poseStack.mulPose(new Quaternionf().rotationY((float)Math.toRadians(object.yaw())));
        poseStack.mulPose(new Quaternionf().rotationX((float)Math.toRadians(object.pitch())));
        poseStack.scale(object.scale(), object.scale(), object.scale());
        minecraft.getEntityRenderDispatcher().render(stand, 0.0D, -0.9D, 0.0D, 0.0F, 1.0F, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
        return true;
    }

    private static ArmorStand armorStand(Level level) {
        if (armorStand == null || armorStandLevel != level) {
            armorStand = new ArmorStand(level, 0.0D, 0.0D, 0.0D);
            armorStand.setNoBasePlate(true);
            armorStand.setShowArms(false);
            armorStand.setInvisible(true);
            armorStand.setNoGravity(true);
            armorStandLevel = level;
        }
        return armorStand;
    }

    private static void configureStand(ArmorStand stand, RealPlaceObject object) {
        stand.moveTo(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
        stand.setYBodyRot(0.0F);
        stand.setYHeadRot(0.0F);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                stand.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        ArmorItem armorItem = (ArmorItem)object.stack().getItem();
        stand.setItemSlot(armorItem.getEquipmentSlot(), object.stack());
    }

    private static boolean isHumanoidArmorModel(ItemStack stack, int modelMode) {
        return modelMode == 2
                && stack.getItem() instanceof ArmorItem armorItem
                && armorItem.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }
}
