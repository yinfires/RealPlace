package com.yinfires.realplace.mixin;

import com.yinfires.realplace.client.RealPlaceClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiPlayerGameMode.class, priority = 2000)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void realplace$startDestroyBlock(BlockPos loc, Direction face, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (RealPlaceClient.isLookingAtObject()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void realplace$continueDestroyBlock(BlockPos posBlock, Direction directionFacing, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (RealPlaceClient.isLookingAtObject()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void realplace$useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> callbackInfo) {
        if (RealPlaceClient.handleUseOn(result, hand)) {
            callbackInfo.setReturnValue(InteractionResult.SUCCESS);
        } else if (RealPlaceClient.blocksPlacement(result, hand)) {
            callbackInfo.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void realplace$useItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callbackInfo) {
        if (RealPlaceClient.handleUse(player, hand)) {
            callbackInfo.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
