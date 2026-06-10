package com.yinfires.realplace.client;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

public final class RealPlaceBlockModeModels {
    private static final Vec3 ZERO = Vec3.ZERO;
    private static final Vec3 BED_HEAD_OFFSET = new Vec3(0.0D, 0.0D, 0.5D);
    private static final Vec3 BED_FOOT_OFFSET = new Vec3(0.0D, 0.0D, -0.5D);
    private static final Vec3 DOOR_UPPER_OFFSET = new Vec3(0.0D, 1.0D, 0.0D);

    private RealPlaceBlockModeModels() {
    }

    public static Model resolve(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return Model.empty();
        }
        Block block = blockItem.getBlock();
        BlockState state = normalizedState(block.defaultBlockState());
        if (usesFixedItemFallback(block, state)) {
            return Model.fixedItemFallbackModel();
        }
        if (block instanceof BellBlock) {
            BlockState bell = setIfPresent(state, BellBlock.FACING, Direction.NORTH);
            bell = setIfPresent(bell, BellBlock.ATTACHMENT, BellAttachType.FLOOR);
            bell = setIfPresent(bell, BellBlock.POWERED, false);
            return new Model(List.of(new Part(bell, ZERO)), List.of(new Part(bell, ZERO)), false, true);
        }
        if (block instanceof BedBlock) {
            BlockState bed = setIfPresent(state, HorizontalDirectionalBlock.FACING, Direction.SOUTH);
            bed = setIfPresent(bed, BedBlock.OCCUPIED, false);
            BlockState head = setIfPresent(bed, BedBlock.PART, BedPart.HEAD);
            BlockState foot = setIfPresent(bed, BedBlock.PART, BedPart.FOOT);
            return new Model(
                    List.of(new Part(head, BED_HEAD_OFFSET)),
                    List.of(new Part(head, BED_HEAD_OFFSET), new Part(foot, BED_FOOT_OFFSET)),
                    false,
                    false);
        }
        if (block instanceof DoorBlock) {
            BlockState door = setIfPresent(state, DoorBlock.FACING, Direction.NORTH);
            door = setIfPresent(door, DoorBlock.OPEN, false);
            door = setIfPresent(door, DoorBlock.HINGE, DoorHingeSide.LEFT);
            door = setIfPresent(door, DoorBlock.POWERED, false);
            BlockState lower = setIfPresent(door, DoorBlock.HALF, DoubleBlockHalf.LOWER);
            BlockState upper = setIfPresent(door, DoorBlock.HALF, DoubleBlockHalf.UPPER);
            List<Part> parts = List.of(new Part(lower, ZERO), new Part(upper, DOOR_UPPER_OFFSET));
            return new Model(parts, parts, false, false);
        }
        return new Model(List.of(new Part(state, ZERO)), List.of(new Part(state, ZERO)), false, false);
    }

    public static boolean useFixedItemFallback(ItemStack stack) {
        return resolve(stack).fixedItemFallback();
    }

    public static boolean isBed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BedBlock;
    }

    private static BlockState normalizedState(BlockState state) {
        state = setIfPresent(state, HorizontalDirectionalBlock.FACING, Direction.NORTH);
        if (state.hasProperty(BellBlock.ATTACHMENT)) {
            state = state.setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR);
        }
        state = setIfPresent(state, BellBlock.POWERED, false);
        return state;
    }

    private static boolean usesFixedItemFallback(Block block, BlockState state) {
        return block instanceof AbstractSkullBlock
                || state.getRenderShape() == RenderShape.INVISIBLE;
    }

    private static <T extends Comparable<T>> BlockState setIfPresent(BlockState state, Property<T> property, T value) {
        return state.hasProperty(property) ? state.setValue(property, value) : state;
    }

    public record Part(BlockState state, Vec3 offset) {
    }

    public record Model(List<Part> renderParts, List<Part> collisionParts, boolean fixedItemFallback, boolean renderBellBody) {
        private static Model empty() {
            return new Model(List.of(), List.of(), false, false);
        }

        private static Model fixedItemFallbackModel() {
            return new Model(List.of(), List.of(), true, false);
        }
    }
}
