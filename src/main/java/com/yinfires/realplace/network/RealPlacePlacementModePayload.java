package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.server.RealPlaceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RealPlacePlacementModePayload(boolean active) implements CustomPacketPayload {
    public static final Type<RealPlacePlacementModePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RealPlace.MOD_ID, "placement_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RealPlacePlacementModePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.active),
            buf -> new RealPlacePlacementModePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RealPlacePlacementModePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RealPlaceManager.setPlacementMode(player, payload.active);
        }
    }
}
