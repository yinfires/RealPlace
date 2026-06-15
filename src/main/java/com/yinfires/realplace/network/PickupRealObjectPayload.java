package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.server.RealPlaceManager;
import java.util.function.Supplier;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

public record PickupRealObjectPayload(UUID id, InteractionHand hand) {
    public static final ResourceLocation ID = new ResourceLocation(RealPlace.MOD_ID, "pickup_real_object");

    public static void encode(PickupRealObjectPayload payload, FriendlyByteBuf buf) {
        buf.writeUUID(payload.id);
        buf.writeEnum(payload.hand);
    }

    public static PickupRealObjectPayload decode(FriendlyByteBuf buf) {
        return new PickupRealObjectPayload(buf.readUUID(), buf.readEnum(InteractionHand.class));
    }

    public static void handle(PickupRealObjectPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            RealPlaceManager.pickup(player, payload.id, payload.hand);
        }
        context.setPacketHandled(true);
    }
}
