package com.yinfires.realplace;

import com.yinfires.realplace.server.RealPlaceObject;
import com.yinfires.realplace.server.RealPlaceObjectIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;

public final class RealPlaceClientState {
    private static boolean placementMode;
    private static ItemStack previewStack = ItemStack.EMPTY;
    private static float yaw;
    private static float pitch;
    private static float scale = 1.0F;
    private static int modelMode;
    private static final List<RealPlaceObject> objects = new ArrayList<>();
    private static final RealPlaceObjectIndex index = new RealPlaceObjectIndex();
    private static List<RealPlaceObject> objectSnapshot = List.of();
    private static long objectsVersion;

    private RealPlaceClientState() {
    }

    public static boolean placementMode() {
        return placementMode;
    }

    public static void setPlacementMode(boolean active) {
        if (active && !placementMode) {
            resetPlacementSession();
        }
        if (!active) {
            previewStack = ItemStack.EMPTY;
            resetPlacementSession();
        }
        placementMode = active;
    }

    public static ItemStack previewStack() {
        return previewStack;
    }

    public static void setPreviewStack(ItemStack stack) {
        if (stack.isEmpty()) {
            previewStack = ItemStack.EMPTY;
            modelMode = RealPlaceItemTransforms.clampModelMode(ItemStack.EMPTY, modelMode);
            return;
        }
        if (!previewStack.isEmpty() && ItemStack.isSameItemSameComponents(previewStack, stack)) {
            previewStack = stack.copy();
            modelMode = RealPlaceItemTransforms.clampModelMode(previewStack, modelMode);
            return;
        }
        previewStack = stack.copy();
        modelMode = RealPlaceItemTransforms.clampModelMode(previewStack, modelMode);
    }

    public static float yaw() {
        return yaw;
    }

    public static void addYaw(float delta) {
        yaw += delta;
    }

    public static float pitch() {
        return pitch;
    }

    public static void addPitch(float delta) {
        pitch += delta;
    }

    public static float scale() {
        return scale;
    }

    public static void addScale(float delta) {
        scale = RealPlaceItemTransforms.clampScale(scale + delta);
    }

    public static int modelMode() {
        return modelMode;
    }

    public static void toggleModelMode() {
        modelMode = RealPlaceItemTransforms.nextModelMode(previewStack, modelMode);
    }

    public static List<RealPlaceObject> objects() {
        return objectSnapshot;
    }

    public static void replaceObjects(List<RealPlaceObject> newObjects) {
        objects.clear();
        objects.addAll(newObjects);
        rebuildIndex();
        objectsVersion++;
    }

    public static void removeObject(UUID id) {
        if (objects.removeIf(object -> object.id().equals(id))) {
            rebuildIndex();
            objectsVersion++;
        }
    }

    public static List<RealPlaceObject> query(AABB box) {
        return index.query(box);
    }

    public static long objectsVersion() {
        return objectsVersion;
    }

    public static RealPlaceObject findAt(net.minecraft.core.BlockPos pos) {
        for (RealPlaceObject object : objects) {
            if (object.anchorPos().equals(pos)) {
                return object;
            }
        }
        return null;
    }

    public static RealPlaceObject find(UUID id) {
        for (RealPlaceObject object : objects) {
            if (object.id().equals(id)) {
                return object;
            }
        }
        return null;
    }

    private static void resetPlacementSession() {
        yaw = 0.0F;
        pitch = 0.0F;
        scale = 1.0F;
        modelMode = 0;
    }

    private static void rebuildIndex() {
        index.clear();
        for (RealPlaceObject object : objects) {
            index.add(object);
        }
        objectSnapshot = List.copyOf(objects);
    }
}
