package com.yinfires.realplace.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;

public final class RealPlaceServerEvents {
    private RealPlaceServerEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(RealPlaceServerEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(RealPlaceServerEvents::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(RealPlaceServerEvents::onPlayerChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(RealPlaceServerEvents::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(RealPlaceServerEvents::onBlockPlaced);
        MinecraftForge.EVENT_BUS.addListener(RealPlaceServerEvents::onFluidPlaced);
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

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof ServerPlayer player && player.tickCount % 40 == 0) {
            RealPlaceManager.syncIfChanged(player);
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
