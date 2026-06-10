package com.yinfires.realplace.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public record RealPlaceShape(List<Box> boxes, Transform modelTransform, boolean placeable) {
    public static final int MAX_BOXES = 512;
    private static final double MAX_LOCAL_EXTENT = 3.0D;
    private static final double MIN_SIZE = 1.0E-5D;
    private static final double MERGE_EPSILON = 1.0E-6D;
    private static final double SAT_EPSILON = 1.0E-8D;
    private static final double CONTACT_EPSILON = 1.0E-7D;
    private static final double SWEEP_STEP = 0.03125D;
    private static final double PLACEMENT_SURFACE_EPSILON = 1.0E-4D;
    private static final double SEPARATION_EPSILON = 1.0E-5D;
    private static final int BINARY_SEARCH_STEPS = 8;
    private static final Box FALLBACK_ITEM_BOX = new Box(-0.3D, -0.06D, -0.3D, 0.3D, 0.06D, 0.3D);
    private static final Box FALLBACK_ARMOR_BOX = new Box(-0.35D, -0.9D, -0.35D, 0.35D, 0.9D, 0.35D);

    public RealPlaceShape {
        boxes = List.copyOf(mergeAdjacentBoxes(boxes));
        modelTransform = modelTransform == null ? Transform.IDENTITY : modelTransform;
    }

    public RealPlaceShape(List<Box> boxes, Transform modelTransform) {
        this(boxes, modelTransform, true);
    }

    public RealPlaceShape(List<Box> boxes) {
        this(boxes, Transform.IDENTITY, true);
    }

    public RealPlaceShape withTransform(Transform transform) {
        return new RealPlaceShape(boxes, transform, placeable);
    }

    public static RealPlaceShape fallback(ItemStack stack, int modelMode) {
        return new RealPlaceShape(List.of(modelMode == 2 ? FALLBACK_ARMOR_BOX : FALLBACK_ITEM_BOX));
    }

    public static RealPlaceShape single(AABB box) {
        return new RealPlaceShape(List.of(Box.from(box)));
    }

    public static RealPlaceShape unplaceable(AABB outlineBox) {
        return new RealPlaceShape(List.of(Box.from(outlineBox)), Transform.IDENTITY, false);
    }

    public boolean isValidForServer(float scale) {
        if (!placeable || boxes.isEmpty() || boxes.size() > MAX_BOXES || !isFinite(scale) || scale < 0.1F || scale > 6.0F || !modelTransform.isValid()) {
            return false;
        }
        AABB localBounds = localBounds();
        if (localBounds.getXsize() > MAX_LOCAL_EXTENT
                || localBounds.getYsize() > MAX_LOCAL_EXTENT
                || localBounds.getZsize() > MAX_LOCAL_EXTENT
                || Math.abs(localBounds.minX) > MAX_LOCAL_EXTENT
                || Math.abs(localBounds.maxX) > MAX_LOCAL_EXTENT
                || Math.abs(localBounds.minY) > MAX_LOCAL_EXTENT
                || Math.abs(localBounds.maxY) > MAX_LOCAL_EXTENT
                || Math.abs(localBounds.minZ) > MAX_LOCAL_EXTENT
                || Math.abs(localBounds.maxZ) > MAX_LOCAL_EXTENT) {
            return false;
        }
        AABB transformedBounds = bounds(Vec3.ZERO, 0.0F, 0.0F, 1.0F);
        return transformedBounds.getXsize() <= MAX_LOCAL_EXTENT
                && transformedBounds.getYsize() <= MAX_LOCAL_EXTENT
                && transformedBounds.getZsize() <= MAX_LOCAL_EXTENT
                && Math.abs(transformedBounds.minX) <= MAX_LOCAL_EXTENT
                && Math.abs(transformedBounds.maxX) <= MAX_LOCAL_EXTENT
                && Math.abs(transformedBounds.minY) <= MAX_LOCAL_EXTENT
                && Math.abs(transformedBounds.maxY) <= MAX_LOCAL_EXTENT
                && Math.abs(transformedBounds.minZ) <= MAX_LOCAL_EXTENT
                && Math.abs(transformedBounds.maxZ) <= MAX_LOCAL_EXTENT;
    }

    public AABB localBounds() {
        AABB bounds = null;
        for (Box box : boxes) {
            if (!box.isValid()) {
                continue;
            }
            bounds = bounds == null ? box.toAabb() : bounds.minmax(box.toAabb());
        }
        return bounds == null ? FALLBACK_ITEM_BOX.toAabb() : bounds;
    }

    public AABB bounds(Vec3 position, float yaw, float pitch, float scale) {
        AABB bounds = null;
        for (OrientedBox box : worldOrientedBoxes(position, yaw, pitch, scale)) {
            AABB boxBounds = box.bounds();
            bounds = bounds == null ? boxBounds : bounds.minmax(boxBounds);
        }
        return bounds == null ? new AABB(position, position) : bounds;
    }

    public List<AABB> worldBoxes(Vec3 position, float yaw, float pitch, float scale, double deflate) {
        List<AABB> transformed = new ArrayList<>(boxes.size());
        for (OrientedBox box : worldOrientedBoxes(position, yaw, pitch, scale)) {
            AABB bounds = box.bounds();
            if (deflate != 0.0D) {
                bounds = bounds.inflate(-deflate);
            }
            if (bounds.getXsize() > MIN_SIZE && bounds.getYsize() > MIN_SIZE && bounds.getZsize() > MIN_SIZE) {
                transformed.add(bounds);
            }
        }
        return transformed;
    }

    public VoxelShape toVoxelShape(Vec3 position, float yaw, float pitch, float scale, boolean collisionShape) {
        if (collisionShape && !placeable) {
            return Shapes.empty();
        }
        VoxelShape result = Shapes.empty();
        List<Box> sourceBoxes = collisionShape ? collisionBoxes() : boxes;
        for (Box box : sourceBoxes) {
            AABB bounds = orientedBox(box, position, yaw, pitch, scale).bounds();
            result = Shapes.joinUnoptimized(result, Shapes.create(bounds), BooleanOp.OR);
        }
        return result.optimize();
    }

    public Optional<ShapeHit> clip(Vec3 position, float yaw, float pitch, float scale, Vec3 start, Vec3 end) {
        return clipExact(position, yaw, pitch, scale, start, end);
    }

    public Optional<ShapeHit> clipExact(Vec3 position, float yaw, float pitch, float scale, Vec3 start, Vec3 end) {
        Vec3 localStart = inverseTransformPoint(start, position, yaw, pitch, scale);
        Vec3 localEnd = inverseTransformPoint(end, position, yaw, pitch, scale);
        return boxes.stream()
                .map(box -> box.toAabb().clip(localStart, localEnd).map(localHit -> {
                    Vec3 worldHit = transformPoint(localHit.x, localHit.y, localHit.z, position, yaw, pitch, scale);
                    Direction localFace = faceForHit(box.toAabb(), localHit);
                    return new ShapeHit(worldHit, worldDirection(localFace, yaw, pitch), start.distanceToSqr(worldHit));
                }))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(ShapeHit::distance));
    }

    public List<Line> outlineLines(Vec3 position, float yaw, float pitch, float scale) {
        VoxelShape localShape = Shapes.empty();
        for (Box box : boxes) {
            localShape = Shapes.joinUnoptimized(localShape, Shapes.create(box.toAabb()), BooleanOp.OR);
        }
        List<Line> result = new ArrayList<>();
        localShape.optimize().forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> result.add(new Line(
                transformPoint(minX, minY, minZ, position, yaw, pitch, scale),
                transformPoint(maxX, maxY, maxZ, position, yaw, pitch, scale))));
        return result;
    }

    public boolean intersectsAabb(Vec3 position, float yaw, float pitch, float scale, AABB box) {
        return pushOutEntityAabb(position, yaw, pitch, scale, box);
    }

    public boolean pushOutEntityAabb(Vec3 position, float yaw, float pitch, float scale, AABB box) {
        if (!placeable) {
            return false;
        }
        OrientedBox aabb = OrientedBox.fromAabb(box);
        for (OrientedBox objectBox : worldOrientedBoxes(position, yaw, pitch, scale)) {
            if (objectBox.intersects(aabb)) {
                return true;
            }
        }
        return false;
    }

    public Vec3 separationVectorEntityAabb(Vec3 position, float yaw, float pitch, float scale, AABB box) {
        if (!placeable || !pushOutEntityAabb(position, yaw, pitch, scale, box)) {
            return Vec3.ZERO;
        }
        double maxDistance = maxEscapeDistance(position, yaw, pitch, scale, box);
        Vec3 best = Vec3.ZERO;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double positive = escapeDistance(position, yaw, pitch, scale, box, axis, true, maxDistance);
            if (Double.isFinite(positive) && positive < bestDistance) {
                bestDistance = positive;
                best = axisVector(axis, positive);
            }
            double negative = escapeDistance(position, yaw, pitch, scale, box, axis, false, maxDistance);
            if (Double.isFinite(negative) && Math.abs(negative) < bestDistance) {
                bestDistance = Math.abs(negative);
                best = axisVector(axis, negative);
            }
        }
        return best;
    }

    public double sweepEntityAabb(Vec3 position, float yaw, float pitch, float scale, AABB entityBox, Direction.Axis axis, double movement) {
        if (!placeable || movement == 0.0D) {
            return movement;
        }
        int steps = Math.max(1, (int)Math.ceil(Math.abs(movement) / SWEEP_STEP));
        double previous = 0.0D;
        for (int i = 1; i <= steps; i++) {
            double candidate = movement * (double)i / (double)steps;
            if (pushOutEntityAabb(position, yaw, pitch, scale, move(entityBox, axis, candidate))) {
                return refineSweep(position, yaw, pitch, scale, entityBox, axis, previous, candidate, movement > 0.0D);
            }
            previous = candidate;
        }
        return movement;
    }

    public boolean intersectsVoxelShape(Vec3 position, float yaw, float pitch, float scale, VoxelShape voxelShape) {
        if (!placeable || voxelShape.isEmpty()) {
            return false;
        }
        for (AABB box : voxelShape.toAabbs()) {
            if (intersectsAabb(position, yaw, pitch, scale, box)) {
                return true;
            }
        }
        return false;
    }

    public boolean intersectsShape(Vec3 position, float yaw, float pitch, float scale, RealPlaceShape other, Vec3 otherPosition, float otherYaw, float otherPitch, float otherScale) {
        if (!placeable || !other.placeable) {
            return false;
        }
        List<OrientedBox> first = worldOrientedBoxes(position, yaw, pitch, scale);
        List<OrientedBox> second = other.worldOrientedBoxes(otherPosition, otherYaw, otherPitch, otherScale);
        for (OrientedBox left : first) {
            for (OrientedBox right : second) {
                if (left.intersects(right)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Vec3 placementPosition(Vec3 hitLocation, Direction direction, float yaw, float pitch, float scale) {
        AABB boundsAtOrigin = bounds(Vec3.ZERO, yaw, pitch, scale);
        Direction.Axis axis = direction.getAxis();
        double side = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? boundsAtOrigin.min(axis) : boundsAtOrigin.max(axis);
        double coordinate = axis.choose(hitLocation.x, hitLocation.y, hitLocation.z) - side
                + direction.getAxisDirection().getStep() * PLACEMENT_SURFACE_EPSILON;
        return switch (axis) {
            case X -> new Vec3(coordinate, hitLocation.y, hitLocation.z);
            case Y -> new Vec3(hitLocation.x, coordinate, hitLocation.z);
            case Z -> new Vec3(hitLocation.x, hitLocation.y, coordinate);
        };
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(boxes.size());
        for (Box box : boxes) {
            box.write(buf);
        }
        modelTransform.write(buf);
        buf.writeBoolean(placeable);
    }

    public static RealPlaceShape read(FriendlyByteBuf buf) {
        int encodedSize = buf.readVarInt();
        if (encodedSize < 0 || encodedSize > MAX_BOXES) {
            throw new IllegalArgumentException("Invalid RealPlace shape box count: " + encodedSize);
        }
        int keptSize = Math.min(encodedSize, MAX_BOXES);
        List<Box> boxes = new ArrayList<>(keptSize);
        for (int i = 0; i < encodedSize; i++) {
            Box box = Box.read(buf);
            if (i < keptSize) {
                boxes.add(box);
            }
        }
        Transform transform = Transform.read(buf);
        boolean placeable = buf.readBoolean();
        return new RealPlaceShape(boxes, transform, placeable);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Box box : boxes) {
            list.add(box.save());
        }
        tag.put("Boxes", list);
        tag.put("Transform", modelTransform.save());
        tag.putBoolean("Placeable", placeable);
        return tag;
    }

    public static RealPlaceShape load(CompoundTag tag, ItemStack stack, int modelMode) {
        if (!tag.contains("Shape", Tag.TAG_COMPOUND)) {
            return unplaceable(fallback(stack, modelMode).localBounds());
        }
        CompoundTag shapeTag = tag.getCompound("Shape");
        ListTag list = shapeTag.getList("Boxes", Tag.TAG_COMPOUND);
        List<Box> boxes = new ArrayList<>(Math.min(list.size(), MAX_BOXES));
        for (int i = 0; i < list.size() && boxes.size() < MAX_BOXES; i++) {
            boxes.add(Box.load(list.getCompound(i)));
        }
        Transform transform = shapeTag.contains("Transform", Tag.TAG_COMPOUND) ? Transform.load(shapeTag.getCompound("Transform")) : Transform.IDENTITY;
        boolean placeable = !shapeTag.contains("Placeable", Tag.TAG_BYTE) || shapeTag.getBoolean("Placeable");
        RealPlaceShape shape = new RealPlaceShape(boxes, transform, placeable);
        if (shape.isValidForServer(1.0F)) {
            return shape;
        }
        AABB outline = boxes.isEmpty() ? fallback(stack, modelMode).localBounds() : shape.localBounds();
        return unplaceable(outline);
    }

    public static boolean intersects(VoxelShape first, VoxelShape second) {
        return Shapes.joinIsNotEmpty(first, second, BooleanOp.AND);
    }

    private List<OrientedBox> worldOrientedBoxes(Vec3 position, float yaw, float pitch, float scale) {
        List<OrientedBox> result = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            if (box.isValid()) {
                result.add(orientedBox(box, position, yaw, pitch, scale));
            }
        }
        return result;
    }

    private OrientedBox orientedBox(Box box, Vec3 position, float yaw, float pitch, float scale) {
        double hx = (box.maxX - box.minX) * 0.5D;
        double hy = (box.maxY - box.minY) * 0.5D;
        double hz = (box.maxZ - box.minZ) * 0.5D;
        double cx = (box.minX + box.maxX) * 0.5D;
        double cy = (box.minY + box.maxY) * 0.5D;
        double cz = (box.minZ + box.maxZ) * 0.5D;
        return new OrientedBox(
                transformPoint(cx, cy, cz, position, yaw, pitch, scale),
                transformVector(hx, 0.0D, 0.0D, yaw, pitch, scale),
                transformVector(0.0D, hy, 0.0D, yaw, pitch, scale),
                transformVector(0.0D, 0.0D, hz, yaw, pitch, scale));
    }

    private Vec3 transformPoint(double x, double y, double z, Vec3 position, float yaw, float pitch, float scale) {
        Vec3 transformed = modelTransform.apply(x, y, z);
        return objectTransformPoint(transformed.x, transformed.y, transformed.z, position, yaw, pitch, scale);
    }

    private Vec3 transformVector(double x, double y, double z, float yaw, float pitch, float scale) {
        Vec3 transformed = modelTransform.applyVector(x, y, z);
        return objectTransformVector(transformed.x, transformed.y, transformed.z, yaw, pitch, scale);
    }

    private Vec3 inverseTransformPoint(Vec3 point, Vec3 position, float yaw, float pitch, float scale) {
        Vec3 objectLocal = inverseObjectTransformPoint(point, position, yaw, pitch, scale);
        return modelTransform.inverse(objectLocal);
    }

    private Direction worldDirection(Direction direction, float yaw, float pitch) {
        Vec3 transformed = transformVector(direction.getStepX(), direction.getStepY(), direction.getStepZ(), yaw, pitch, 1.0F);
        double absX = Math.abs(transformed.x);
        double absY = Math.abs(transformed.y);
        double absZ = Math.abs(transformed.z);
        if (absY >= absX && absY >= absZ) {
            return transformed.y >= 0.0D ? Direction.UP : Direction.DOWN;
        }
        if (absX >= absZ) {
            return transformed.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return transformed.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private List<Box> collisionBoxes() {
        return mergeAdjacentBoxes(boxes);
    }

    private static List<Box> mergeAdjacentBoxes(List<Box> sourceBoxes) {
        List<Box> merged = new ArrayList<>();
        for (Box box : sourceBoxes) {
            if (box.isValid()) {
                merged.add(box);
            }
        }
        boolean changed;
        do {
            changed = false;
            outer:
            for (int i = 0; i < merged.size(); i++) {
                for (int j = i + 1; j < merged.size(); j++) {
                    Box joined = tryMerge(merged.get(i), merged.get(j));
                    if (joined != null) {
                        merged.set(i, joined);
                        merged.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed);
        return merged;
    }

    private static Box tryMerge(Box first, Box second) {
        if (sameRange(first.minY, first.maxY, second.minY, second.maxY) && sameRange(first.minZ, first.maxZ, second.minZ, second.maxZ)
                && touchesOrOverlaps(first.minX, first.maxX, second.minX, second.maxX)) {
            return new Box(Math.min(first.minX, second.minX), first.minY, first.minZ, Math.max(first.maxX, second.maxX), first.maxY, first.maxZ);
        }
        if (sameRange(first.minX, first.maxX, second.minX, second.maxX) && sameRange(first.minZ, first.maxZ, second.minZ, second.maxZ)
                && touchesOrOverlaps(first.minY, first.maxY, second.minY, second.maxY)) {
            return new Box(first.minX, Math.min(first.minY, second.minY), first.minZ, first.maxX, Math.max(first.maxY, second.maxY), first.maxZ);
        }
        if (sameRange(first.minX, first.maxX, second.minX, second.maxX) && sameRange(first.minY, first.maxY, second.minY, second.maxY)
                && touchesOrOverlaps(first.minZ, first.maxZ, second.minZ, second.maxZ)) {
            return new Box(first.minX, first.minY, Math.min(first.minZ, second.minZ), first.maxX, first.maxY, Math.max(first.maxZ, second.maxZ));
        }
        return null;
    }

    private static boolean sameRange(double firstMin, double firstMax, double secondMin, double secondMax) {
        return nearlyEqual(firstMin, secondMin) && nearlyEqual(firstMax, secondMax);
    }

    private static boolean touchesOrOverlaps(double firstMin, double firstMax, double secondMin, double secondMax) {
        return firstMax + MERGE_EPSILON >= secondMin && secondMax + MERGE_EPSILON >= firstMin;
    }

    private static boolean nearlyEqual(double first, double second) {
        return Math.abs(first - second) <= MERGE_EPSILON;
    }

    private static Vec3 objectTransformPoint(double x, double y, double z, Vec3 position, float yaw, float pitch, float scale) {
        Vec3 transformed = objectTransformVector(x, y, z, yaw, pitch, scale);
        return new Vec3(position.x + transformed.x, position.y + transformed.y, position.z + transformed.z);
    }

    private static Vec3 objectTransformVector(double x, double y, double z, float yaw, float pitch, float scale) {
        x *= scale;
        y *= scale;
        z *= scale;
        double pitchRad = Math.toRadians(pitch);
        double pitchCos = Math.cos(pitchRad);
        double pitchSin = Math.sin(pitchRad);
        double pitchedY = y * pitchCos - z * pitchSin;
        double pitchedZ = y * pitchSin + z * pitchCos;
        y = pitchedY;
        z = pitchedZ;
        double yawRad = Math.toRadians(yaw);
        double yawCos = Math.cos(yawRad);
        double yawSin = Math.sin(yawRad);
        double yawedX = x * yawCos + z * yawSin;
        double yawedZ = -x * yawSin + z * yawCos;
        return new Vec3(yawedX, y, yawedZ);
    }

    private static Vec3 inverseObjectTransformPoint(Vec3 point, Vec3 position, float yaw, float pitch, float scale) {
        double x = point.x - position.x;
        double y = point.y - position.y;
        double z = point.z - position.z;
        double yawRad = Math.toRadians(yaw);
        double yawCos = Math.cos(yawRad);
        double yawSin = Math.sin(yawRad);
        double localX = x * yawCos - z * yawSin;
        double localZ = x * yawSin + z * yawCos;
        x = localX;
        z = localZ;
        double pitchRad = Math.toRadians(pitch);
        double pitchCos = Math.cos(pitchRad);
        double pitchSin = Math.sin(pitchRad);
        double localY = y * pitchCos + z * pitchSin;
        localZ = -y * pitchSin + z * pitchCos;
        double safeScale = Math.max(scale, 1.0E-4F);
        return new Vec3(x / safeScale, localY / safeScale, localZ / safeScale);
    }

    private static Direction faceForHit(AABB box, Vec3 hit) {
        Direction direction = Direction.UP;
        double nearest = Math.abs(hit.y - box.maxY);
        double distance = Math.abs(hit.y - box.minY);
        if (distance < nearest) {
            nearest = distance;
            direction = Direction.DOWN;
        }
        distance = Math.abs(hit.x - box.minX);
        if (distance < nearest) {
            nearest = distance;
            direction = Direction.WEST;
        }
        distance = Math.abs(hit.x - box.maxX);
        if (distance < nearest) {
            nearest = distance;
            direction = Direction.EAST;
        }
        distance = Math.abs(hit.z - box.minZ);
        if (distance < nearest) {
            nearest = distance;
            direction = Direction.NORTH;
        }
        distance = Math.abs(hit.z - box.maxZ);
        if (distance < nearest) {
            direction = Direction.SOUTH;
        }
        return direction;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private double refineSweep(Vec3 position, float yaw, float pitch, float scale, AABB entityBox, Direction.Axis axis, double safe, double blocked, boolean positive) {
        double low = safe;
        double high = blocked;
        for (int i = 0; i < BINARY_SEARCH_STEPS; i++) {
            double mid = (low + high) * 0.5D;
            if (pushOutEntityAabb(position, yaw, pitch, scale, move(entityBox, axis, mid))) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return positive ? Math.max(0.0D, low - SEPARATION_EPSILON) : Math.min(0.0D, low + SEPARATION_EPSILON);
    }

    private double escapeDistance(Vec3 position, float yaw, float pitch, float scale, AABB entityBox, Direction.Axis axis, boolean positive, double maxDistance) {
        double direction = positive ? 1.0D : -1.0D;
        double low = 0.0D;
        double high = SWEEP_STEP;
        while (high <= maxDistance) {
            if (!pushOutEntityAabb(position, yaw, pitch, scale, move(entityBox, axis, direction * high))) {
                for (int i = 0; i < BINARY_SEARCH_STEPS + 2; i++) {
                    double mid = (low + high) * 0.5D;
                    if (pushOutEntityAabb(position, yaw, pitch, scale, move(entityBox, axis, direction * mid))) {
                        low = mid;
                    } else {
                        high = mid;
                    }
                }
                return direction * (high + SEPARATION_EPSILON);
            }
            low = high;
            high *= 2.0D;
        }
        return Double.POSITIVE_INFINITY;
    }

    private double maxEscapeDistance(Vec3 position, float yaw, float pitch, float scale, AABB entityBox) {
        AABB shapeBounds = bounds(position, yaw, pitch, scale);
        double shapeSpan = Math.max(shapeBounds.getXsize(), Math.max(shapeBounds.getYsize(), shapeBounds.getZsize()));
        double entitySpan = Math.max(entityBox.getXsize(), Math.max(entityBox.getYsize(), entityBox.getZsize()));
        return Math.max(SWEEP_STEP * 2.0D, shapeSpan + entitySpan + 1.0D);
    }

    private static Vec3 axisVector(Direction.Axis axis, double distance) {
        return switch (axis) {
            case X -> new Vec3(distance, 0.0D, 0.0D);
            case Y -> new Vec3(0.0D, distance, 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, distance);
        };
    }

    private static AABB move(AABB box, Direction.Axis axis, double distance) {
        return switch (axis) {
            case X -> box.move(distance, 0.0D, 0.0D);
            case Y -> box.move(0.0D, distance, 0.0D);
            case Z -> box.move(0.0D, 0.0D, distance);
        };
    }

    public record ShapeHit(Vec3 location, Direction direction, double distance) {
    }

    public record Line(Vec3 start, Vec3 end) {
    }

    public record Transform(
            double m00,
            double m01,
            double m02,
            double m03,
            double m10,
            double m11,
            double m12,
            double m13,
            double m20,
            double m21,
            double m22,
            double m23) {
        public static final Transform IDENTITY = new Transform(
                1.0D, 0.0D, 0.0D, 0.0D,
                0.0D, 1.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 1.0D, 0.0D);

        public static Transform fromMatrix(Matrix4f matrix) {
            return fromMatrix(matrix, Vec3.ZERO);
        }

        public static Transform fromRenderMatrix(Matrix4f matrix) {
            return fromMatrix(matrix, new Vec3(-0.5D, -0.5D, -0.5D));
        }

        public static Transform renderIdentity() {
            return new Transform(
                    1.0D, 0.0D, 0.0D, -0.5D,
                    0.0D, 1.0D, 0.0D, -0.5D,
                    0.0D, 0.0D, 1.0D, -0.5D);
        }

        private static Transform fromMatrix(Matrix4f matrix, Vec3 renderOffset) {
            Vec3 origin = transform(matrix, 0.0D, 0.0D, 0.0D);
            Vec3 xAxis = transform(matrix, 1.0D, 0.0D, 0.0D).subtract(origin);
            Vec3 yAxis = transform(matrix, 0.0D, 1.0D, 0.0D).subtract(origin);
            Vec3 zAxis = transform(matrix, 0.0D, 0.0D, 1.0D).subtract(origin);
            origin = origin.add(xAxis.scale(renderOffset.x)).add(yAxis.scale(renderOffset.y)).add(zAxis.scale(renderOffset.z));
            return new Transform(
                    xAxis.x, yAxis.x, zAxis.x, origin.x,
                    xAxis.y, yAxis.y, zAxis.y, origin.y,
                    xAxis.z, yAxis.z, zAxis.z, origin.z);
        }

        private static Vec3 transform(Matrix4f matrix, double x, double y, double z) {
            Vector4f vector = new Vector4f((float)x, (float)y, (float)z, 1.0F);
            vector.mul(matrix);
            return new Vec3(vector.x(), vector.y(), vector.z());
        }

        public Vec3 apply(double x, double y, double z) {
            return new Vec3(
                    m00 * x + m01 * y + m02 * z + m03,
                    m10 * x + m11 * y + m12 * z + m13,
                    m20 * x + m21 * y + m22 * z + m23);
        }

        public Vec3 applyVector(double x, double y, double z) {
            return new Vec3(
                    m00 * x + m01 * y + m02 * z,
                    m10 * x + m11 * y + m12 * z,
                    m20 * x + m21 * y + m22 * z);
        }

        public Vec3 inverse(Vec3 point) {
            double x = point.x - m03;
            double y = point.y - m13;
            double z = point.z - m23;
            double det = determinant();
            if (Math.abs(det) < 1.0E-10D) {
                return point;
            }
            double inv00 = (m11 * m22 - m12 * m21) / det;
            double inv01 = (m02 * m21 - m01 * m22) / det;
            double inv02 = (m01 * m12 - m02 * m11) / det;
            double inv10 = (m12 * m20 - m10 * m22) / det;
            double inv11 = (m00 * m22 - m02 * m20) / det;
            double inv12 = (m02 * m10 - m00 * m12) / det;
            double inv20 = (m10 * m21 - m11 * m20) / det;
            double inv21 = (m01 * m20 - m00 * m21) / det;
            double inv22 = (m00 * m11 - m01 * m10) / det;
            return new Vec3(
                    inv00 * x + inv01 * y + inv02 * z,
                    inv10 * x + inv11 * y + inv12 * z,
                    inv20 * x + inv21 * y + inv22 * z);
        }

        public boolean isValid() {
            return isFinite(m00) && isFinite(m01) && isFinite(m02) && isFinite(m03)
                    && isFinite(m10) && isFinite(m11) && isFinite(m12) && isFinite(m13)
                    && isFinite(m20) && isFinite(m21) && isFinite(m22) && isFinite(m23)
                    && Math.abs(determinant()) > 1.0E-10D;
        }

        private double determinant() {
            return m00 * (m11 * m22 - m12 * m21)
                    - m01 * (m10 * m22 - m12 * m20)
                    + m02 * (m10 * m21 - m11 * m20);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeDouble(m00);
            buf.writeDouble(m01);
            buf.writeDouble(m02);
            buf.writeDouble(m03);
            buf.writeDouble(m10);
            buf.writeDouble(m11);
            buf.writeDouble(m12);
            buf.writeDouble(m13);
            buf.writeDouble(m20);
            buf.writeDouble(m21);
            buf.writeDouble(m22);
            buf.writeDouble(m23);
        }

        public static Transform read(FriendlyByteBuf buf) {
            return new Transform(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("M00", m00);
            tag.putDouble("M01", m01);
            tag.putDouble("M02", m02);
            tag.putDouble("M03", m03);
            tag.putDouble("M10", m10);
            tag.putDouble("M11", m11);
            tag.putDouble("M12", m12);
            tag.putDouble("M13", m13);
            tag.putDouble("M20", m20);
            tag.putDouble("M21", m21);
            tag.putDouble("M22", m22);
            tag.putDouble("M23", m23);
            return tag;
        }

        public static Transform load(CompoundTag tag) {
            return new Transform(
                    tag.getDouble("M00"),
                    tag.getDouble("M01"),
                    tag.getDouble("M02"),
                    tag.getDouble("M03"),
                    tag.getDouble("M10"),
                    tag.getDouble("M11"),
                    tag.getDouble("M12"),
                    tag.getDouble("M13"),
                    tag.getDouble("M20"),
                    tag.getDouble("M21"),
                    tag.getDouble("M22"),
                    tag.getDouble("M23"));
        }
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            double a = Math.min(minX, maxX);
            double b = Math.min(minY, maxY);
            double c = Math.min(minZ, maxZ);
            double d = Math.max(minX, maxX);
            double e = Math.max(minY, maxY);
            double f = Math.max(minZ, maxZ);
            minX = a;
            minY = b;
            minZ = c;
            maxX = d;
            maxY = e;
            maxZ = f;
        }

        public static Box from(AABB box) {
            return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }

        public boolean isValid() {
            return isFinite(minX) && isFinite(minY) && isFinite(minZ)
                    && isFinite(maxX) && isFinite(maxY) && isFinite(maxZ)
                    && maxX - minX >= MIN_SIZE
                    && maxY - minY >= MIN_SIZE
                    && maxZ - minZ >= MIN_SIZE;
        }

        public AABB toAabb() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("MinX", minX);
            tag.putDouble("MinY", minY);
            tag.putDouble("MinZ", minZ);
            tag.putDouble("MaxX", maxX);
            tag.putDouble("MaxY", maxY);
            tag.putDouble("MaxZ", maxZ);
            return tag;
        }

        public static Box load(CompoundTag tag) {
            return new Box(
                    tag.getDouble("MinX"),
                    tag.getDouble("MinY"),
                    tag.getDouble("MinZ"),
                    tag.getDouble("MaxX"),
                    tag.getDouble("MaxY"),
                    tag.getDouble("MaxZ"));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeDouble(minX);
            buf.writeDouble(minY);
            buf.writeDouble(minZ);
            buf.writeDouble(maxX);
            buf.writeDouble(maxY);
            buf.writeDouble(maxZ);
        }

        public static Box read(FriendlyByteBuf buf) {
            return new Box(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
    }

    private record OrientedBox(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ) {
        static OrientedBox fromAabb(AABB box) {
            Vec3 center = new Vec3((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
            return new OrientedBox(
                    center,
                    new Vec3((box.maxX - box.minX) * 0.5D, 0.0D, 0.0D),
                    new Vec3(0.0D, (box.maxY - box.minY) * 0.5D, 0.0D),
                    new Vec3(0.0D, 0.0D, (box.maxZ - box.minZ) * 0.5D));
        }

        AABB bounds() {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int x = -1; x <= 1; x += 2) {
                for (int y = -1; y <= 1; y += 2) {
                    for (int z = -1; z <= 1; z += 2) {
                        Vec3 corner = center.add(axisX.scale(x)).add(axisY.scale(y)).add(axisZ.scale(z));
                        minX = Math.min(minX, corner.x);
                        minY = Math.min(minY, corner.y);
                        minZ = Math.min(minZ, corner.z);
                        maxX = Math.max(maxX, corner.x);
                        maxY = Math.max(maxY, corner.y);
                        maxZ = Math.max(maxZ, corner.z);
                    }
                }
            }
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        boolean intersects(OrientedBox other) {
            Vec3[] aUnit = unitAxes();
            Vec3[] bUnit = other.unitAxes();
            double[] aExtent = extents();
            double[] bExtent = other.extents();
            double[][] r = new double[3][3];
            double[][] absR = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    r[i][j] = aUnit[i].dot(bUnit[j]);
                    absR[i][j] = Math.abs(r[i][j]) + SAT_EPSILON;
                }
            }

            Vec3 tVec = other.center.subtract(center);
            double[] t = new double[]{tVec.dot(aUnit[0]), tVec.dot(aUnit[1]), tVec.dot(aUnit[2])};

            for (int i = 0; i < 3; i++) {
                double ra = aExtent[i];
                double rb = bExtent[0] * absR[i][0] + bExtent[1] * absR[i][1] + bExtent[2] * absR[i][2];
                if (Math.abs(t[i]) > ra + rb + CONTACT_EPSILON) {
                    return false;
                }
            }

            for (int j = 0; j < 3; j++) {
                double ra = aExtent[0] * absR[0][j] + aExtent[1] * absR[1][j] + aExtent[2] * absR[2][j];
                double rb = bExtent[j];
                double distance = Math.abs(t[0] * r[0][j] + t[1] * r[1][j] + t[2] * r[2][j]);
                if (distance > ra + rb + CONTACT_EPSILON) {
                    return false;
                }
            }

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    int i1 = (i + 1) % 3;
                    int i2 = (i + 2) % 3;
                    int j1 = (j + 1) % 3;
                    int j2 = (j + 2) % 3;
                    double ra = aExtent[i1] * absR[i2][j] + aExtent[i2] * absR[i1][j];
                    double rb = bExtent[j1] * absR[i][j2] + bExtent[j2] * absR[i][j1];
                    double distance = Math.abs(t[i2] * r[i1][j] - t[i1] * r[i2][j]);
                    if (distance > ra + rb + CONTACT_EPSILON) {
                        return false;
                    }
                }
            }
            return true;
        }

        private Vec3[] unitAxes() {
            return new Vec3[]{unit(axisX), unit(axisY), unit(axisZ)};
        }

        private double[] extents() {
            return new double[]{axisX.length(), axisY.length(), axisZ.length()};
        }

        private static Vec3 unit(Vec3 axis) {
            double length = axis.length();
            return length < MIN_SIZE ? new Vec3(1.0D, 0.0D, 0.0D) : axis.scale(1.0D / length);
        }
    }
}
