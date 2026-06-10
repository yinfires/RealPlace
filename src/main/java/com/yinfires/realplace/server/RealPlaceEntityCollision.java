package com.yinfires.realplace.server;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RealPlaceEntityCollision {
    private RealPlaceEntityCollision() {
    }

    public static Vec3 collide(AABB entityBox, Vec3 movement, Collection<RealPlaceObject> objects) {
        if (objects.isEmpty()) {
            return movement;
        }
        if (movement.lengthSqr() < 1.0E-12D) {
            Vec3 separation = resolveEmbedded(entityBox, objects);
            return separation.lengthSqr() < 1.0E-12D ? movement : separation.add(movement);
        }
        Vec3 separation = resolveEmbedded(entityBox, objects);
        if (separation.lengthSqr() >= 1.0E-12D) {
            entityBox = entityBox.move(separation);
        }
        double x = movement.x;
        double y = movement.y;
        double z = movement.z;

        if (y != 0.0D) {
            y = clipAxis(entityBox, Direction.Axis.Y, y, objects);
            if (y != 0.0D) {
                entityBox = entityBox.move(0.0D, y, 0.0D);
            }
        }

        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst && z != 0.0D) {
            z = clipAxis(entityBox, Direction.Axis.Z, z, objects);
            if (z != 0.0D) {
                entityBox = entityBox.move(0.0D, 0.0D, z);
            }
        }

        if (x != 0.0D) {
            x = clipAxis(entityBox, Direction.Axis.X, x, objects);
            if (!zFirst && x != 0.0D) {
                entityBox = entityBox.move(x, 0.0D, 0.0D);
            }
        }

        if (!zFirst && z != 0.0D) {
            z = clipAxis(entityBox, Direction.Axis.Z, z, objects);
        }

        return new Vec3(clean(separation.x + x), clean(separation.y + y), clean(separation.z + z));
    }

    public static boolean intersects(AABB box, Collection<RealPlaceObject> objects) {
        for (RealPlaceObject object : objects) {
            if (object.shape().placeable()
                    && object.bounds().intersects(box)
                    && object.shape().pushOutEntityAabb(object.position(), object.yaw(), object.pitch(), object.scale(), box)) {
                return true;
            }
        }
        return false;
    }

    public static List<RealPlaceObject> filter(Collection<RealPlaceObject> objects, AABB searchBox) {
        return objects.stream()
                .filter(object -> object.shape().placeable() && object.bounds().intersects(searchBox))
                .toList();
    }

    private static double clipAxis(AABB entityBox, Direction.Axis axis, double movement, Collection<RealPlaceObject> objects) {
        double clipped = movement;
        for (RealPlaceObject object : objects) {
            if (object.shape().placeable()) {
                clipped = object.shape().sweepEntityAabb(object.position(), object.yaw(), object.pitch(), object.scale(), entityBox, axis, clipped);
            }
        }
        return clipped;
    }

    private static double clean(double value) {
        return Math.abs(value) < 1.0E-7D ? 0.0D : value;
    }

    private static Vec3 resolveEmbedded(AABB entityBox, Collection<RealPlaceObject> objects) {
        Vec3 total = Vec3.ZERO;
        AABB current = entityBox;
        for (int iteration = 0; iteration < 4; iteration++) {
            Vec3 bestStep = Vec3.ZERO;
            double bestDistanceSqr = Double.POSITIVE_INFINITY;
            for (RealPlaceObject object : objects) {
                if (!object.shape().placeable() || !object.bounds().intersects(current)) {
                    continue;
                }
                Vec3 step = object.shape().separationVectorEntityAabb(object.position(), object.yaw(), object.pitch(), object.scale(), current);
                double lengthSqr = step.lengthSqr();
                if (lengthSqr >= 1.0E-12D && lengthSqr < bestDistanceSqr) {
                    bestDistanceSqr = lengthSqr;
                    bestStep = step;
                }
            }
            if (bestDistanceSqr == Double.POSITIVE_INFINITY) {
                break;
            }
            total = total.add(bestStep);
            current = current.move(bestStep);
        }
        return total;
    }
}
