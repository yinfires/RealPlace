package com.yinfires.realplace.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

public class RealPlaceSavedData extends SavedData {
    private static final String DATA_NAME = "realplace_objects";
    private final Map<UUID, RealPlaceObject> objects = new HashMap<>();
    private final RealPlaceObjectIndex index = new RealPlaceObjectIndex();
    private long version;

    public static RealPlaceSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(RealPlaceSavedData::new, RealPlaceSavedData::load), DATA_NAME);
    }

    public static RealPlaceSavedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        RealPlaceSavedData data = new RealPlaceSavedData();
        ListTag list = tag.getList("Objects", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag child = list.getCompound(i);
            RealPlaceObject object = RealPlaceObject.fromTag(child, provider);
            data.objects.put(object.id(), object);
            data.index.add(object);
        }
        return data;
    }

    public List<RealPlaceObject> all() {
        return new ArrayList<>(objects.values());
    }

    public RealPlaceObject get(UUID id) {
        return objects.get(id);
    }

    public RealPlaceObject findAt(BlockPos pos) {
        for (RealPlaceObject object : query(new AABB(pos).inflate(1.0E-4D))) {
            if (object.anchorPos().equals(pos)) {
                return object;
            }
        }
        return null;
    }

    public void put(RealPlaceObject object) {
        RealPlaceObject previous = objects.put(object.id(), object);
        if (previous != null) {
            index.remove(previous);
        }
        index.add(object);
        version++;
        setDirty();
    }

    public void remove(UUID id) {
        RealPlaceObject removed = objects.remove(id);
        if (removed != null) {
            index.remove(removed);
            version++;
            setDirty();
        }
    }

    public List<RealPlaceObject> query(AABB box) {
        return index.query(box);
    }

    public List<RealPlaceObject> nearby(net.minecraft.world.phys.Vec3 center, double radius) {
        return index.nearby(center, radius);
    }

    public long version() {
        return version;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (RealPlaceObject object : objects.values()) {
            list.add(object.save(provider));
        }
        tag.put("Objects", list);
        return tag;
    }
}
