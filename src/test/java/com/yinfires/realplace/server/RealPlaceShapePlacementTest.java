package com.yinfires.realplace.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class RealPlaceShapePlacementTest {
    private static final double EPSILON = 1.0E-4D;
    private static final double TOLERANCE = 1.0E-8D;
    private static final float SCALE = 1.0F;
    private static final RealPlaceShape SHAPE = new RealPlaceShape(List.of(
            new RealPlaceShape.Box(-0.42D, -0.17D, -0.25D, 0.18D, 0.11D, 0.35D)));

    @Test
    void topFacePlacementStaysOutsideBlockAcrossYawSteps() {
        Vec3 hit = new Vec3(0.5D, 1.0D, 0.5D);
        for (float yaw = 0.0F; yaw < 360.0F; yaw += 22.5F) {
            Vec3 position = SHAPE.placementPosition(hit, Direction.UP, yaw, 0.0F, SCALE);
            AABB bounds = SHAPE.bounds(position, yaw, 0.0F, SCALE);
            assertFalse(SHAPE.intersectsVoxelShape(position, yaw, 0.0F, SCALE, Shapes.block()),
                    "Expected no overlap for yaw=" + yaw);
            assertTrue(bounds.minY >= hit.y + EPSILON - TOLERANCE,
                    "Expected minY outside top face for yaw=" + yaw + ", actual minY=" + bounds.minY);
        }
    }

    @Test
    void topFacePlacementStaysOutsideBlockAcrossPitchVariants() {
        Vec3 hit = new Vec3(0.5D, 1.0D, 0.5D);
        float[] yaws = new float[]{0.0F, 22.5F, 45.0F, 67.5F};
        float[] pitches = new float[]{-67.5F, -45.0F, -22.5F, 0.0F, 22.5F, 45.0F, 67.5F};
        for (float yaw : yaws) {
            for (float pitch : pitches) {
                Vec3 position = SHAPE.placementPosition(hit, Direction.UP, yaw, pitch, SCALE);
                AABB bounds = SHAPE.bounds(position, yaw, pitch, SCALE);
                assertFalse(SHAPE.intersectsVoxelShape(position, yaw, pitch, SCALE, Shapes.block()),
                        "Expected no overlap for yaw=" + yaw + ", pitch=" + pitch);
                assertTrue(bounds.minY >= hit.y + EPSILON - TOLERANCE,
                        "Expected minY outside top face for yaw=" + yaw + ", pitch=" + pitch + ", actual minY=" + bounds.minY);
            }
        }
    }

    @Test
    void sideFacePlacementStaysOutsideBlockAcrossRepresentativeAngles() {
        verifySideFace(Direction.EAST, new Vec3(1.0D, 0.5D, 0.5D), true);
        verifySideFace(Direction.WEST, new Vec3(0.0D, 0.5D, 0.5D), false);
    }

    @Test
    void clipExactReturnsNearestBoxHit() {
        RealPlaceShape shape = new RealPlaceShape(List.of(
                new RealPlaceShape.Box(2.0D, -0.25D, -0.25D, 2.5D, 0.25D, 0.25D),
                new RealPlaceShape.Box(0.5D, -0.25D, -0.25D, 1.0D, 0.25D, 0.25D)));

        RealPlaceShape.ShapeHit hit = shape.clipExact(Vec3.ZERO, 0.0F, 0.0F, 1.0F, Vec3.ZERO, new Vec3(4.0D, 0.0D, 0.0D)).orElseThrow();

        assertEquals(0.5D, hit.location().x, TOLERANCE);
        assertEquals(Direction.WEST, hit.direction());
    }

    private static void verifySideFace(Direction direction, Vec3 hit, boolean positiveAxis) {
        float[] yaws = new float[]{0.0F, 22.5F, 67.5F, 90.0F};
        float[] pitches = new float[]{0.0F, 22.5F};
        for (float yaw : yaws) {
            for (float pitch : pitches) {
                Vec3 position = SHAPE.placementPosition(hit, direction, yaw, pitch, SCALE);
                AABB bounds = SHAPE.bounds(position, yaw, pitch, SCALE);
                assertFalse(SHAPE.intersectsVoxelShape(position, yaw, pitch, SCALE, Shapes.block()),
                        "Expected no overlap for direction=" + direction + ", yaw=" + yaw + ", pitch=" + pitch);
                double face = positiveAxis ? bounds.minX : bounds.maxX;
                double expected = hit.x + (positiveAxis ? EPSILON : -EPSILON);
                if (positiveAxis) {
                    assertTrue(face >= expected - TOLERANCE,
                            "Expected minX outside east face for yaw=" + yaw + ", pitch=" + pitch + ", actual minX=" + face);
                } else {
                    assertTrue(face <= expected + TOLERANCE,
                            "Expected maxX outside west face for yaw=" + yaw + ", pitch=" + pitch + ", actual maxX=" + face);
                }
            }
        }
    }
}
