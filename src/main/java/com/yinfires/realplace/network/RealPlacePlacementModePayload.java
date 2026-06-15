package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.server.RealPlaceManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

public record RealPlacePlacementModePayload(boolean active) {
    public static final ResourceLocation ID = new ResourceLocation(RealPlace.MOD_ID, "placement_mode");

    public static void encode(RealPlacePlacementModePayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.active);
    }

    public static RealPlacePlacementModePayload decode(FriendlyByteBuf buf) {
        return new RealPlacePlacementModePayload(buf.readBoolean());
    }

    public static void handle(RealPlacePlacementModePayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            RealPlaceManager.setPlacementMode(player, payload.active);
        }
        context.setPacketHandled(true);
    }
}
