package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.server.RealPlaceManager;
import com.yinfires.realplace.server.RealPlaceShape;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

public record PlaceRealObjectPayload(Vec3 position, float yaw, float pitch, float scale, int modelMode, InteractionHand hand, RealPlaceShape shape) {
    public static final ResourceLocation ID = new ResourceLocation(RealPlace.MOD_ID, "place_real_object");

    public static void encode(PlaceRealObjectPayload payload, FriendlyByteBuf buf) {
        buf.writeDouble(payload.position.x);
        buf.writeDouble(payload.position.y);
        buf.writeDouble(payload.position.z);
        buf.writeFloat(payload.yaw);
        buf.writeFloat(payload.pitch);
        buf.writeFloat(payload.scale);
        buf.writeVarInt(payload.modelMode);
        buf.writeEnum(payload.hand);
        payload.shape.write(buf);
    }

    public static PlaceRealObjectPayload decode(FriendlyByteBuf buf) {
        return new PlaceRealObjectPayload(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readEnum(InteractionHand.class),
                RealPlaceShape.read(buf));
    }

    public static void handle(PlaceRealObjectPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            RealPlaceManager.place(player, payload.hand, payload.position, payload.yaw, payload.pitch, payload.scale, payload.modelMode, player.getItemInHand(payload.hand), payload.shape);
        }
        context.setPacketHandled(true);
    }
}
