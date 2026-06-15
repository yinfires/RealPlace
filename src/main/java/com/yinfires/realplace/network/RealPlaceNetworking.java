package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RealPlaceNetworking {
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel channel;
    private static int nextPacketId;

    private RealPlaceNetworking() {
    }

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(RealPlace.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals);

        channel.messageBuilder(PlaceRealObjectPayload.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PlaceRealObjectPayload::encode)
                .decoder(PlaceRealObjectPayload::decode)
                .consumerMainThread(PlaceRealObjectPayload::handle)
                .add();
        channel.messageBuilder(PickupRealObjectPayload.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PickupRealObjectPayload::encode)
                .decoder(PickupRealObjectPayload::decode)
                .consumerMainThread(PickupRealObjectPayload::handle)
                .add();
        channel.messageBuilder(RealPlacePlacementModePayload.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RealPlacePlacementModePayload::encode)
                .decoder(RealPlacePlacementModePayload::decode)
                .consumerMainThread(RealPlacePlacementModePayload::handle)
                .add();
        channel.messageBuilder(SyncRealObjectsPayload.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRealObjectsPayload::encode)
                .decoder(SyncRealObjectsPayload::decode)
                .consumerMainThread(SyncRealObjectsPayload::handle)
                .add();
    }

    public static void sendToServer(Object message) {
        channel.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
