package com.yinfires.realplace.mixin;

import com.yinfires.realplace.client.RealPlaceClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, priority = 2000)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void realplace$keyPress(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (windowPointer != minecraft.getWindow().getWindow() || minecraft.screen != null || action == GLFW.GLFW_RELEASE) {
            return;
        }
        if (RealPlaceClient.PLACEMENT_MODE.matches(key, scanCode)) {
            if (action == GLFW.GLFW_PRESS) {
                RealPlaceClient.togglePlacementMode();
            }
            callbackInfo.cancel();
            return;
        }
        if (com.yinfires.realplace.RealPlaceClientState.placementMode() && RealPlaceClient.SWITCH_MODEL.matches(key, scanCode)) {
            if (action == GLFW.GLFW_PRESS) {
                RealPlaceClient.switchModelMode();
            }
            callbackInfo.cancel();
        }
    }
}
