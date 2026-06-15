package com.yinfires.realplace;

import com.mojang.logging.LogUtils;
import com.yinfires.realplace.client.RealPlaceClient;
import com.yinfires.realplace.network.RealPlaceNetworking;
import com.yinfires.realplace.server.RealPlaceServerEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(RealPlace.MOD_ID)
public class RealPlace {
    public static final String MOD_ID = "realplace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealPlace() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RealPlaceClientConfig.SPEC, RealPlaceClientConfig.FILE_NAME);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RealPlaceClient.registerModBus(modEventBus);
        }
        RealPlaceServerEvents.register();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(RealPlaceNetworking::register);
        LOGGER.info("RealPlace initialized.");
    }
}
