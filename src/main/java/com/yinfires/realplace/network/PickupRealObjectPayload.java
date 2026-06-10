package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.server.RealPlaceManager;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PickupRealObjectPayload(UUID id, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<PickupRealObjectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RealPlace.MOD_ID, "pickup_real_object"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PickupRealObjectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.id);
                buf.writeEnum(payload.hand);
            },
            buf -> new PickupRealObjectPayload(buf.readUUID(), buf.readEnum(InteractionHand.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PickupRealObjectPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RealPlaceManager.pickup(player, payload.id, payload.hand);
        }
    }
}
