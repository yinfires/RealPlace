package com.yinfires.realplace;

import com.mojang.logging.LogUtils;
import com.yinfires.realplace.client.RealPlaceClient;
import com.yinfires.realplace.network.RealPlaceNetworking;
import com.yinfires.realplace.server.RealPlaceServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(RealPlace.MOD_ID)
public class RealPlace {
    public static final String MOD_ID = "realplace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealPlace(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, RealPlaceClientConfig.SPEC, RealPlaceClientConfig.FILE_NAME);
        modEventBus.addListener(RealPlaceNetworking::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RealPlaceClient.registerModBus(modEventBus);
        }
        RealPlaceServerEvents.register();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("RealPlace initialized.");
    }
}
