package com.yinfires.realplace.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RealPlaceObjectIndex {
    private static final int SECTION_BITS = 4;
    private static final int SECTION_SIZE = 1 << SECTION_BITS;

    private final Map<Long, List<RealPlaceObject>> sections = new HashMap<>();

    public void clear() {
        sections.clear();
    }

    public void add(RealPlaceObject object) {
        forEachSection(object.bounds(), key -> sections.computeIfAbsent(key, ignored -> new ArrayList<>()).add(object));
    }

    public void remove(RealPlaceObject object) {
        forEachSection(object.bounds(), key -> {
            List<RealPlaceObject> objects = sections.get(key);
            if (objects == null) {
                return;
            }
            objects.remove(object);
            if (objects.isEmpty()) {
                sections.remove(key);
            }
        });
    }

    public List<RealPlaceObject> query(AABB box) {
        List<RealPlaceObject> result = new ArrayList<>();
        Set<RealPlaceObject> seen = new HashSet<>();
        forEachSection(box, key -> {
            List<RealPlaceObject> objects = sections.get(key);
            if (objects == null) {
                return;
            }
            for (RealPlaceObject object : objects) {
                if (seen.add(object) && object.bounds().intersects(box)) {
                    result.add(object);
                }
            }
        });
        return result;
    }

    public List<RealPlaceObject> nearby(Vec3 center, double radius) {
        AABB box = new AABB(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius);
        double radiusSqr = radius * radius;
        List<RealPlaceObject> result = new ArrayList<>();
        Set<RealPlaceObject> seen = new HashSet<>();
        forEachSection(box, key -> {
            List<RealPlaceObject> objects = sections.get(key);
            if (objects == null) {
                return;
            }
            for (RealPlaceObject object : objects) {
                if (seen.add(object) && object.position().distanceToSqr(center) <= radiusSqr) {
                    result.add(object);
                }
            }
        });
        return result;
    }

    private static void forEachSection(AABB box, SectionConsumer consumer) {
        int minX = sectionCoordinate(box.minX);
        int minY = sectionCoordinate(box.minY);
        int minZ = sectionCoordinate(box.minZ);
        int maxX = sectionCoordinate(box.maxX);
        int maxY = sectionCoordinate(box.maxY);
        int maxZ = sectionCoordinate(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    consumer.accept(sectionKey(x, y, z));
                }
            }
        }
    }

    private static int sectionCoordinate(double coordinate) {
        return Math.floorDiv((int)Math.floor(coordinate), SECTION_SIZE);
    }

    private static long sectionKey(int x, int y, int z) {
        return (((long)x & 0x3FFFFFL) << 42) | (((long)y & 0xFFFFFL) << 22) | ((long)z & 0x3FFFFFL);
    }

    private interface SectionConsumer {
        void accept(long key);
    }
}
