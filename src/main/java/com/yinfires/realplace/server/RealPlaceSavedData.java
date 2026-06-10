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
        for (RealPlaceObject object : objects.values()) {
            if (object.anchorPos().equals(pos)) {
                return object;
            }
        }
        return null;
    }

    public void put(RealPlaceObject object) {
        objects.put(object.id(), object);
        setDirty();
    }

    public void remove(UUID id) {
        if (objects.remove(id) != null) {
            setDirty();
        }
    }

    public List<RealPlaceObject> query(AABB box) {
        List<RealPlaceObject> result = new ArrayList<>();
        for (RealPlaceObject object : objects.values()) {
            if (object.bounds().intersects(box)) {
                result.add(object);
            }
        }
        return result;
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
