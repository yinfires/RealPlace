package com.yinfires.realplace.mixin;

import com.yinfires.realplace.server.RealPlaceEntityCollision;
import com.yinfires.realplace.server.RealPlaceObject;
import com.yinfires.realplace.server.RealPlaceSavedData;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public boolean noPhysics;

    @Inject(method = "move", at = @At("HEAD"))
    private void realplace$forcePhysicsForRealPlace(MoverType type, Vec3 movement, CallbackInfo callbackInfo) {
        Entity self = (Entity)(Object)this;
        if (!noPhysics || !(self instanceof ItemEntity) || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        var searchBox = self.getBoundingBox().expandTowards(movement).inflate(1.0E-4D);
        List<RealPlaceObject> objects = RealPlaceSavedData.get(level).query(searchBox);
        if (RealPlaceEntityCollision.intersects(searchBox, objects)) {
            noPhysics = false;
        }
    }

    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void realplace$collide(Vec3 requestedMovement, CallbackInfoReturnable<Vec3> callbackInfo) {
        Entity self = (Entity)(Object)this;
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 vanillaMovement = callbackInfo.getReturnValue();
        var searchBox = self.getBoundingBox().expandTowards(vanillaMovement).inflate(1.0E-4D);
        List<RealPlaceObject> objects = RealPlaceSavedData.get(level).query(searchBox);
        if (objects.isEmpty()) {
            return;
        }
        callbackInfo.setReturnValue(RealPlaceEntityCollision.collide(self.getBoundingBox(), vanillaMovement, objects));
    }
}
