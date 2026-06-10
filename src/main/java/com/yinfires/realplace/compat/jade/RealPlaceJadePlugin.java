package com.yinfires.realplace.compat.jade;

import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.RealPlaceClientState;
import com.yinfires.realplace.client.RealPlaceClient;
import com.yinfires.realplace.server.RealPlaceObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.IElementHelper;

@WailaPlugin(RealPlace.MOD_ID)
public final class RealPlaceJadePlugin implements IWailaPlugin {
    private static final String SERVER_DATA_MARKER = "RealPlaceTarget";

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hitResult, accessor, originalAccessor) -> createAccessor(registration, hitResult, accessor));
        registration.addTooltipCollectedCallback(Integer.MAX_VALUE, (tooltip, accessor) -> {
            if (accessor instanceof BlockAccessor blockAccessor) {
                rewriteTooltip(tooltip, blockAccessor);
            }
        });
    }

    private static Accessor<?> createAccessor(IWailaClientRegistration registration, net.minecraft.world.phys.HitResult hitResult, Accessor<?> currentAccessor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return currentAccessor;
        }
        RealPlaceClient.ObjectHit hit = RealPlaceClient.lookedAtObjectHit();
        if (hit == null) {
            return currentAccessor;
        }
        CompoundTag serverData = new CompoundTag();
        serverData.putBoolean(SERVER_DATA_MARKER, true);
        serverData.putUUID("Id", hit.object().id());
        serverData.put("Item", hit.object().stack().saveOptional(minecraft.level.registryAccess()));
        BlockPos pos = BlockPos.containing(hit.location());
        BlockHitResult blockHit = new BlockHitResult(hit.location(), hit.direction(), pos, false);
        return registration.blockAccessor()
                .level(minecraft.level)
                .player(minecraft.player)
                .serverData(serverData)
                .serverConnected(false)
                .showDetails(currentAccessor != null && currentAccessor.showDetails())
                .hit(blockHit)
                .blockState(Blocks.AIR.defaultBlockState())
                .fakeBlock(hit.object().stack().copy())
                .build();
    }

    private static boolean isRealPlace(BlockAccessor accessor) {
        return accessor.getServerData().getBoolean(SERVER_DATA_MARKER);
    }

    private static RealPlaceObject currentObject(BlockAccessor accessor) {
        if (!isRealPlace(accessor)) {
            return null;
        }
        if (!accessor.getServerData().hasUUID("Id")) {
            return null;
        }
        return RealPlaceClientState.find(accessor.getServerData().getUUID("Id"));
    }

    private static void rewriteTooltip(IBoxElement box, BlockAccessor accessor) {
        RealPlaceObject object = currentObject(accessor);
        if (object == null) {
            return;
        }
        ITooltip tooltip = box.getTooltip();
        ItemStack stack = object.stack().copy();
        tooltip.clear();
        box.setIcon(IElementHelper.get().item(stack));
        tooltip.add(Component.literal(stack.getHoverName().getString()).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("realplace.jade.mod_name").withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
    }
}
