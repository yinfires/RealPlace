package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.server.RealPlaceManager;
import com.yinfires.realplace.server.RealPlaceShape;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlaceRealObjectPayload(Vec3 position, float yaw, float pitch, float scale, int modelMode, InteractionHand hand, RealPlaceShape shape) implements CustomPacketPayload {
    public static final Type<PlaceRealObjectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RealPlace.MOD_ID, "place_real_object"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceRealObjectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVec3(payload.position);
                buf.writeFloat(payload.yaw);
                buf.writeFloat(payload.pitch);
                buf.writeFloat(payload.scale);
                buf.writeVarInt(payload.modelMode);
                buf.writeEnum(payload.hand);
                payload.shape.write(buf);
            },
            buf -> new PlaceRealObjectPayload(buf.readVec3(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readEnum(InteractionHand.class), RealPlaceShape.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlaceRealObjectPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RealPlaceManager.place(player, payload.hand, payload.position, payload.yaw, payload.pitch, payload.scale, payload.modelMode, player.getItemInHand(payload.hand), payload.shape);
        }
    }
}
