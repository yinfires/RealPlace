package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RealPlaceNetworking {
    private RealPlaceNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(PlaceRealObjectPayload.TYPE, PlaceRealObjectPayload.STREAM_CODEC, PlaceRealObjectPayload::handle);
        registrar.playToServer(PickupRealObjectPayload.TYPE, PickupRealObjectPayload.STREAM_CODEC, PickupRealObjectPayload::handle);
        registrar.playToServer(RealPlacePlacementModePayload.TYPE, RealPlacePlacementModePayload.STREAM_CODEC, RealPlacePlacementModePayload::handle);
        registrar.playToClient(SyncRealObjectsPayload.TYPE, SyncRealObjectsPayload.STREAM_CODEC, SyncRealObjectsPayload::handle);
    }
}
