package com.yinfires.realplace.network;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.RealPlaceClientState;
import com.yinfires.realplace.server.RealPlaceObject;
import com.yinfires.realplace.server.RealPlaceShape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncRealObjectsPayload(List<RealPlaceObject> objects) implements CustomPacketPayload {
    public static final Type<SyncRealObjectsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RealPlace.MOD_ID, "sync_real_objects"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRealObjectsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.objects.size());
                for (RealPlaceObject object : payload.objects) {
                    buf.writeUUID(object.id());
                    buf.writeVec3(object.position());
                    ItemStack.STREAM_CODEC.encode(buf, object.stack());
                    buf.writeFloat(object.yaw());
                    buf.writeFloat(object.pitch());
                    buf.writeFloat(object.scale());
                    buf.writeVarInt(object.modelMode());
                    object.shape().write(buf);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<RealPlaceObject> objects = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    java.util.UUID id = buf.readUUID();
                    Vec3 position = buf.readVec3();
                    ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                    float yaw = buf.readFloat();
                    float pitch = buf.readFloat();
                    float scale = buf.readFloat();
                    int modelMode = buf.readVarInt();
                    RealPlaceShape shape = RealPlaceShape.read(buf);
                    objects.add(new RealPlaceObject(id, position, stack, yaw, pitch, scale, modelMode, shape));
                }
                return new SyncRealObjectsPayload(objects);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncRealObjectsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RealPlaceClientState.replaceObjects(payload.objects));
    }
}
