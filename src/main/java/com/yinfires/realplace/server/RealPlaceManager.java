package com.yinfires.realplace.server;

import com.yinfires.realplace.network.SyncRealObjectsPayload;
import com.yinfires.realplace.RealPlaceItemTransforms;
import com.yinfires.realplace.network.RealPlaceNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RealPlaceManager {
    private static final double SYNC_RADIUS = 96.0D;
    private static final Set<UUID> PLACEMENT_MODE_PLAYERS = new HashSet<>();
    private static final Map<UUID, SyncState> SYNC_STATES = new HashMap<>();

    private RealPlaceManager() {
    }

    public static List<RealPlaceObject> all(ServerLevel level) {
        return RealPlaceSavedData.get(level).all();
    }

    public static List<RealPlaceObject> query(ServerLevel level, AABB box) {
        return RealPlaceSavedData.get(level).query(box);
    }

    public static List<RealPlaceObject> nearby(ServerPlayer player) {
        return RealPlaceSavedData.get(player.serverLevel()).nearby(player.position(), SYNC_RADIUS);
    }

    public static boolean place(ServerPlayer player, InteractionHand hand, Vec3 position, float yaw, float pitch, float scale, int modelMode, ItemStack stack, RealPlaceShape shape) {
        if (!isPlacementMode(player) || stack.isEmpty()) {
            return false;
        }
        scale = RealPlaceItemTransforms.clampScale(scale);
        int clampedModelMode = RealPlaceItemTransforms.clampModelMode(stack, modelMode);
        if (modelMode != clampedModelMode) {
            return false;
        }
        modelMode = clampedModelMode;
        if (shape == null || !shape.isValidForServer(scale)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        AABB bounds = shape.bounds(position, yaw, pitch, scale);
        Vec3 expectedPosition = expectedPlacementPosition(player, yaw, pitch, scale, shape);
        if (expectedPosition == null || expectedPosition.distanceToSqr(position) > 0.04D) {
            return false;
        }
        if (!canReach(player, bounds)) {
            return false;
        }
        for (net.minecraft.world.phys.shapes.VoxelShape blockShape : level.getBlockCollisions(null, bounds)) {
            if (shape.intersectsVoxelShape(position, yaw, pitch, scale, blockShape)) {
                return false;
            }
        }
        for (RealPlaceObject object : RealPlaceSavedData.get(level).query(bounds)) {
            if (shape.intersectsShape(position, yaw, pitch, scale, object.shape(), object.position(), object.yaw(), object.pitch(), object.scale())) {
                return false;
            }
        }
        ItemStack stored = stack.copy();
        stored.setCount(1);
        RealPlaceItemTransforms.clear(stored);
        RealPlaceObject object = new RealPlaceObject(UUID.randomUUID(), position, stored, yaw, pitch, scale, modelMode, shape);
        RealPlaceSavedData.get(level).put(object);
        if (!player.getAbilities().instabuild) {
            ItemStack inHand = player.getItemInHand(hand);
            inHand.shrink(1);
            if (inHand.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
        sync(level);
        return true;
    }

    public static RealPlaceObject pickup(ServerPlayer player, UUID id, InteractionHand hand) {
        if (isPlacementMode(player)) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        RealPlaceObject object = RealPlaceSavedData.get(level).get(id);
        if (object == null) {
            return null;
        }
        RaycastHit lookedAt = raycastHit(player);
        if (lookedAt == null || !lookedAt.object().id().equals(id)) {
            return null;
        }
        ItemStack stack = object.stack().copy();
        RealPlaceItemTransforms.clear(stack);
        RealPlaceSavedData.get(level).remove(object.id());
        ItemStack current = player.getItemInHand(hand);
        if (current.isEmpty()) {
            player.setItemInHand(hand, stack);
        } else if (!player.getInventory().add(stack)) {
            ItemEntity entity = player.drop(stack, false);
            if (entity != null) {
                entity.setPos(player.getX(), player.getY() - 0.2D, player.getZ());
            }
        }
        sync(level);
        return object;
    }

    public static RealPlaceObject raycast(ServerPlayer player) {
        RaycastHit hit = raycastHit(player);
        return hit == null ? null : hit.object();
    }

    public static RaycastHit raycastHit(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(player.getBlockReach()));
        ServerLevel level = player.serverLevel();
        RaycastHit objectHit = raycastHit(level, start, end);
        if (objectHit == null) {
            return null;
        }
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.BLOCK && start.distanceToSqr(blockHit.getLocation()) + 1.0E-5D < objectHit.distance()) {
            return null;
        }
        return objectHit;
    }

    public static RealPlaceObject raycast(ServerLevel level, Vec3 start, Vec3 end) {
        RaycastHit hit = raycastHit(level, start, end);
        return hit == null ? null : hit.object();
    }

    public static RaycastHit raycastHit(ServerLevel level, Vec3 start, Vec3 end) {
        RealPlaceObject nearest = null;
        Vec3 nearestLocation = null;
        Direction nearestDirection = Direction.UP;
        double nearestDistance = Double.MAX_VALUE;
        AABB searchBox = new AABB(start, end).inflate(1.0E-4D);
        for (RealPlaceObject object : RealPlaceSavedData.get(level).query(searchBox)) {
            java.util.Optional<RealPlaceShape.ShapeHit> hit = object.shape().clipExact(object.position(), object.yaw(), object.pitch(), object.scale(), start, end);
            if (hit.isPresent()) {
                double distance = hit.get().distance();
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = object;
                    nearestLocation = hit.get().location();
                    nearestDirection = hit.get().direction();
                }
            }
        }
        return nearest == null ? null : new RaycastHit(nearest, nearestLocation, nearestDirection, nearestDistance);
    }

    private static Vec3 expectedPlacementPosition(ServerPlayer player, float yaw, float pitch, float scale, RealPlaceShape shape) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(player.getBlockReach()));
        ServerLevel level = player.serverLevel();
        PreviewHit nearest = null;
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            nearest = new PreviewHit(blockHit.getLocation(), blockHit.getDirection(), start.distanceToSqr(blockHit.getLocation()));
        }
        RaycastHit objectHit = raycastHit(level, start, end);
        if (objectHit != null && (nearest == null || objectHit.distance() < nearest.distance())) {
            nearest = new PreviewHit(objectHit.location(), objectHit.direction(), objectHit.distance());
        }
        if (nearest == null) {
            return null;
        }
        return shape.placementPosition(nearest.location(), nearest.direction(), yaw, pitch, scale);
    }

    public static void sync(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            syncIfChanged(player);
        }
    }

    public static void sync(ServerPlayer player) {
        sendSync(player, nearby(player));
    }

    public static void syncIfChanged(ServerPlayer player) {
        RealPlaceSavedData data = RealPlaceSavedData.get(player.serverLevel());
        List<RealPlaceObject> nearby = data.nearby(player.position(), SYNC_RADIUS);
        SyncState previous = SYNC_STATES.get(player.getUUID());
        if (previous != null && previous.matches(player, data.version(), nearby)) {
            return;
        }
        sendSync(player, nearby);
    }

    public static void setPlacementMode(ServerPlayer player, boolean active) {
        if (active) {
            PLACEMENT_MODE_PLAYERS.add(player.getUUID());
        } else {
            PLACEMENT_MODE_PLAYERS.remove(player.getUUID());
        }
    }

    public static boolean isPlacementMode(ServerPlayer player) {
        return PLACEMENT_MODE_PLAYERS.contains(player.getUUID());
    }

    private static boolean canReach(ServerPlayer player, AABB bounds) {
        double reach = player.getBlockReach() + 1.0D;
        return player.getEyePosition().distanceToSqr(bounds.getCenter()) <= reach * reach;
    }

    public static void clearPlacementMode(ServerPlayer player) {
        PLACEMENT_MODE_PLAYERS.remove(player.getUUID());
        SYNC_STATES.remove(player.getUUID());
    }

    public record RaycastHit(RealPlaceObject object, Vec3 location, Direction direction, double distance) {
    }

    private record PreviewHit(Vec3 location, Direction direction, double distance) {
    }

    private static void sendSync(ServerPlayer player, List<RealPlaceObject> objects) {
        SYNC_STATES.put(player.getUUID(), SyncState.capture(player, RealPlaceSavedData.get(player.serverLevel()).version(), objects));
        RealPlaceNetworking.sendToPlayer(player, new SyncRealObjectsPayload(objects));
    }

    private record SyncState(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, long dataVersion, int sectionX, int sectionY, int sectionZ, List<UUID> objectIds) {
        private static SyncState capture(ServerPlayer player, long dataVersion, List<RealPlaceObject> objects) {
            List<UUID> ids = new ArrayList<>(objects.size());
            for (RealPlaceObject object : objects) {
                ids.add(object.id());
            }
            return new SyncState(
                    player.level().dimension(),
                    dataVersion,
                    sectionCoordinate(player.getX()),
                    sectionCoordinate(player.getY()),
                    sectionCoordinate(player.getZ()),
                    List.copyOf(ids));
        }

        private boolean matches(ServerPlayer player, long currentDataVersion, List<RealPlaceObject> objects) {
            if (dataVersion != currentDataVersion
                    || !dimension.equals(player.level().dimension())
                    || sectionX != sectionCoordinate(player.getX())
                    || sectionY != sectionCoordinate(player.getY())
                    || sectionZ != sectionCoordinate(player.getZ())
                    || objectIds.size() != objects.size()) {
                return false;
            }
            for (int i = 0; i < objects.size(); i++) {
                if (!objectIds.get(i).equals(objects.get(i).id())) {
                    return false;
                }
            }
            return true;
        }

        private static int sectionCoordinate(double coordinate) {
            return Math.floorDiv((int)Math.floor(coordinate), 16);
        }
    }
}
