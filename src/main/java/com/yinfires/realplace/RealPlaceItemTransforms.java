package com.yinfires.realplace;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class RealPlaceItemTransforms {
    private static final String TAG_KEY = "RealPlace";

    private RealPlaceItemTransforms() {
    }

    public static void clear(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.remove(TAG_KEY));
    }

    public static float clampScale(float scale) {
        return Math.max(0.1F, Math.min(6.0F, scale));
    }

    public static int clampModelMode(int modelMode) {
        return Math.max(0, Math.min(2, modelMode));
    }

    public static int clampModelMode(ItemStack stack, int modelMode) {
        int requested = clampModelMode(modelMode);
        int[] validModes = validModelModes(stack);
        for (int validMode : validModes) {
            if (validMode == requested) {
                return requested;
            }
        }
        return validModes[0];
    }

    public static int nextModelMode(ItemStack stack, int currentModelMode) {
        int[] validModes = validModelModes(stack);
        int current = clampModelMode(stack, currentModelMode);
        for (int i = 0; i < validModes.length; i++) {
            if (validModes[i] == current) {
                return validModes[(i + 1) % validModes.length];
            }
        }
        return validModes[0];
    }

    public static int[] validModelModes(ItemStack stack) {
        if (stack.isEmpty()) {
            return new int[]{0};
        }
        if (stack.getItem() instanceof BlockItem) {
            return new int[]{0, 1};
        }
        if (isHumanoidArmor(stack)) {
            return new int[]{0, 2};
        }
        if (stack.is(Items.TRIDENT)) {
            return new int[]{0, 1};
        }
        return new int[]{0, 1};
    }

    private static boolean isHumanoidArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armorItem
                && armorItem.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }
}
