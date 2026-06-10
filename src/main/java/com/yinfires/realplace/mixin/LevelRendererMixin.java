package com.yinfires.realplace.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yinfires.realplace.client.RealPlaceClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2000)
public abstract class LevelRendererMixin {
    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void realplace$hideBlockOutlineBehindRealPlace(PoseStack poseStack, VertexConsumer consumer, Entity entity, double camX, double camY, double camZ, BlockPos pos, BlockState state, CallbackInfo callbackInfo) {
        if (RealPlaceClient.isLookingAtObject()) {
            callbackInfo.cancel();
        }
    }
}
