package com.yinfires.realplace.server;

import com.yinfires.realplace.RealPlaceItemTransforms;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RealPlaceObject {
    private final UUID id;
    private final Vec3 position;
    private final ItemStack stack;
    private final float yaw;
    private final float pitch;
    private final float scale;
    private final int modelMode;
    private final RealPlaceShape shape;
    private AABB bounds;

    public RealPlaceObject(UUID id, Vec3 position, ItemStack stack, float yaw, float pitch, float scale, int modelMode, RealPlaceShape shape) {
        this.id = id;
        this.position = position;
        this.stack = stack;
        this.yaw = yaw;
        this.pitch = pitch;
        this.scale = scale;
        this.modelMode = RealPlaceItemTransforms.clampModelMode(stack, modelMode);
        this.shape = shape;
    }

    public UUID id() {
        return id;
    }

    public Vec3 position() {
        return position;
    }

    public ItemStack stack() {
        return stack;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float scale() {
        return scale;
    }

    public int modelMode() {
        return modelMode;
    }

    public RealPlaceShape shape() {
        return shape;
    }

    public BlockPos anchorPos() {
        return BlockPos.containing(position);
    }

    public AABB bounds() {
        if (bounds == null) {
            bounds = shape.bounds(position, yaw, pitch, scale);
        }
        return bounds;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("Scale", scale);
        tag.putInt("ModelMode", modelMode);
        ItemStack.OPTIONAL_CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack)
                .result()
                .ifPresent(itemTag -> tag.put("Item", itemTag));
        tag.put("Shape", shape.save());
        return tag;
    }

    public static RealPlaceObject fromTag(CompoundTag tag, HolderLookup.Provider provider) {
        UUID id = tag.getUUID("Id");
        Vec3 position;
        if (tag.contains("X")) {
            position = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        } else {
            position = BlockPos.of(tag.getLong("Pos")).getCenter();
        }
        ItemStack stack = ItemStack.parseOptional(provider, tag.getCompound("Item"));
        float yaw = tag.getFloat("Yaw");
        float pitch = tag.getFloat("Pitch");
        float scale = tag.getFloat("Scale");
        int modelMode = tag.getInt("ModelMode");
        RealPlaceShape shape = RealPlaceShape.load(tag, stack, modelMode);
        return new RealPlaceObject(id, position, stack, yaw, pitch, scale, modelMode, shape);
    }
}
