package com.yinfires.realplace.mixin;

import com.yinfires.realplace.server.RealPlaceManager;
import com.yinfires.realplace.server.RealPlaceObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void realplace$blockBreak(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face, int maxBuildHeight, int sequence, CallbackInfo callbackInfo) {
        if (RealPlaceManager.raycast(player) != null) {
            level.destroyBlockProgress(player.getId(), pos, -1);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void realplace$useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> callbackInfo) {
        if (level instanceof ServerLevel) {
            RealPlaceObject object = RealPlaceManager.raycast(player);
            if (object != null && RealPlaceManager.pickup(player, object.id(), hand) != null) {
                callbackInfo.setReturnValue(InteractionResult.SUCCESS);
            } else if (RealPlaceManager.isPlacementMode(player)) {
                callbackInfo.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void realplace$useItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callbackInfo) {
        if (RealPlaceManager.isPlacementMode(player)) {
            callbackInfo.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
