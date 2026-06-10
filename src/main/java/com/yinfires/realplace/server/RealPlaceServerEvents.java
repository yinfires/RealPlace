package com.yinfires.realplace.server;

import com.yinfires.realplace.network.SyncRealObjectsPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class RealPlaceServerEvents {
    private RealPlaceServerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RealPlaceServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(RealPlaceServerEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(RealPlaceServerEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(RealPlaceServerEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RealPlaceServerEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(RealPlaceServerEvents::onFluidPlaced);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RealPlaceManager.sync(player);
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RealPlaceManager.clearPlacementMode(player);
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RealPlaceManager.sync(player);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 40 == 0) {
            RealPlaceManager.sync(player);
        }
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && intersectsRealPlace(level, new AABB(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    private static void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && intersectsRealPlace(level, new AABB(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    private static boolean intersectsRealPlace(ServerLevel level, AABB box) {
        box = box.inflate(1.0E-4D);
        for (RealPlaceObject object : RealPlaceSavedData.get(level).query(box)) {
            if (object.shape().intersectsAabb(object.position(), object.yaw(), object.pitch(), object.scale(), box)) {
                return true;
            }
        }
        return false;
    }

}
