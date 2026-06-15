package com.yinfires.realplace.mixin;

import com.yinfires.realplace.RealPlaceClientState;
import com.yinfires.realplace.server.RealPlaceEntityCollision;
import com.yinfires.realplace.server.RealPlaceObject;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ClientEntityMixin {
    @Shadow
    public boolean noPhysics;

    @Shadow
    public abstract AABB getBoundingBox();

    @Inject(method = "move", at = @At("HEAD"))
    private void realplace$forceClientPhysicsForRealPlace(MoverType type, Vec3 movement, CallbackInfo callbackInfo) {
        Entity self = (Entity)(Object)this;
        if (!noPhysics || !(self instanceof ItemEntity) || !(self.level() instanceof ClientLevel)) {
            return;
        }
        AABB searchBox = getBoundingBox().expandTowards(movement).inflate(1.0E-4D);
        List<RealPlaceObject> objects = RealPlaceClientState.query(searchBox);
        if (RealPlaceEntityCollision.intersects(searchBox, objects)) {
            noPhysics = false;
        }
    }

    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void realplace$clientCollide(Vec3 requestedMovement, CallbackInfoReturnable<Vec3> callbackInfo) {
        Entity self = (Entity)(Object)this;
        if (!(self.level() instanceof ClientLevel)) {
            return;
        }
        Vec3 vanillaMovement = callbackInfo.getReturnValue();
        AABB searchBox = getBoundingBox().expandTowards(vanillaMovement).inflate(1.0E-4D);
        List<RealPlaceObject> objects = RealPlaceClientState.query(searchBox);
        if (!objects.isEmpty()) {
            callbackInfo.setReturnValue(RealPlaceEntityCollision.collide(getBoundingBox(), vanillaMovement, objects));
        }
    }
}
