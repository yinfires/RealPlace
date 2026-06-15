package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.RealPlaceClientState;
import com.yinfires.realplace.server.RealPlaceObject;
import com.yinfires.realplace.server.RealPlaceShape;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

public record SyncRealObjectsPayload(List<RealPlaceObject> objects) {
    public static final ResourceLocation ID = new ResourceLocation(RealPlace.MOD_ID, "sync_real_objects");

    public static void encode(SyncRealObjectsPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.objects.size());
        for (RealPlaceObject object : payload.objects) {
            buf.writeUUID(object.id());
            buf.writeDouble(object.position().x);
            buf.writeDouble(object.position().y);
            buf.writeDouble(object.position().z);
            buf.writeItem(object.stack());
            buf.writeFloat(object.yaw());
            buf.writeFloat(object.pitch());
            buf.writeFloat(object.scale());
            buf.writeVarInt(object.modelMode());
            object.shape().write(buf);
        }
    }

    public static SyncRealObjectsPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<RealPlaceObject> objects = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            java.util.UUID id = buf.readUUID();
            Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            ItemStack stack = buf.readItem();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            float scale = buf.readFloat();
            int modelMode = buf.readVarInt();
            RealPlaceShape shape = RealPlaceShape.read(buf);
            objects.add(new RealPlaceObject(id, position, stack, yaw, pitch, scale, modelMode, shape));
        }
        return new SyncRealObjectsPayload(objects);
    }

    public static void handle(SyncRealObjectsPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> RealPlaceClientState.replaceObjects(payload.objects));
        context.setPacketHandled(true);
    }
}
