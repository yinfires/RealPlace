package com.yinfires.realplace.mixin;

import com.yinfires.realplace.server.RealPlaceEntityCollision;
import com.yinfires.realplace.server.RealPlaceSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/item/ItemEntity;noPhysics:Z", ordinal = 1, shift = At.Shift.AFTER))
    private void realplace$keepPhysicsForRealPlaceCollision(CallbackInfo callbackInfo) {
        ItemEntity self = (ItemEntity)(Object)this;
        if (!self.noPhysics || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        if (RealPlaceEntityCollision.intersects(self.getBoundingBox().inflate(1.0E-5D), RealPlaceSavedData.get(level).query(self.getBoundingBox().inflate(1.0E-5D)))) {
            self.noPhysics = false;
        }
    }
}
