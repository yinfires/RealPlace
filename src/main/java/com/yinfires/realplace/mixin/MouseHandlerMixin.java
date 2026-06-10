package com.yinfires.realplace.mixin;

import com.yinfires.realplace.client.RealPlaceClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 2000)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void realplace$onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo callbackInfo) {
        if (RealPlaceClient.handleScroll(yOffset)) {
            callbackInfo.cancel();
        }
    }
}
