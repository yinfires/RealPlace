package com.yinfires.realplace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RealPlaceObjectIndexTest {
    @Test
    void queryReturnsEachCrossSectionObjectOnce() {
        RealPlaceObjectIndex index = new RealPlaceObjectIndex();
        RealPlaceObject object = objectAt(new Vec3(15.9D, 0.0D, 15.9D), new AABB(-1.0D, -0.5D, -1.0D, 1.0D, 0.5D, 1.0D));

        index.add(object);

        List<RealPlaceObject> result = index.query(new AABB(0.0D, -1.0D, 0.0D, 32.0D, 1.0D, 32.0D));
        assertEquals(1, result.size());
        assertEquals(object.id(), result.get(0).id());
    }

    @Test
    void queryExcludesFarObjectsAndRemovedObjects() {
        RealPlaceObjectIndex index = new RealPlaceObjectIndex();
        RealPlaceObject near = objectAt(new Vec3(1.0D, 0.0D, 1.0D), new AABB(-0.25D, -0.25D, -0.25D, 0.25D, 0.25D, 0.25D));
        RealPlaceObject far = objectAt(new Vec3(80.0D, 0.0D, 80.0D), new AABB(-0.25D, -0.25D, -0.25D, 0.25D, 0.25D, 0.25D));

        index.add(near);
        index.add(far);

        AABB query = new AABB(0.0D, -1.0D, 0.0D, 4.0D, 1.0D, 4.0D);
        assertTrue(index.query(query).contains(near));
        assertFalse(index.query(query).contains(far));

        index.remove(near);
        assertTrue(index.query(query).isEmpty());
    }

    @Test
    void nearbyFiltersByDistanceAfterSectionLookup() {
        RealPlaceObjectIndex index = new RealPlaceObjectIndex();
        RealPlaceObject inside = objectAt(new Vec3(8.0D, 0.0D, 0.0D), new AABB(-0.25D, -0.25D, -0.25D, 0.25D, 0.25D, 0.25D));
        RealPlaceObject outside = objectAt(new Vec3(13.0D, 0.0D, 0.0D), new AABB(-0.25D, -0.25D, -0.25D, 0.25D, 0.25D, 0.25D));

        index.add(inside);
        index.add(outside);

        List<RealPlaceObject> result = index.nearby(Vec3.ZERO, 10.0D);
        assertTrue(result.contains(inside));
        assertFalse(result.contains(outside));
    }

    private static RealPlaceObject objectAt(Vec3 position, AABB localBox) {
        return new RealPlaceObject(
                UUID.randomUUID(),
                position,
                ItemStack.EMPTY,
                0.0F,
                0.0F,
                1.0F,
                0,
                RealPlaceShape.single(localBox));
    }
}
