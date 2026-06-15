package com.yinfires.realplace;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class RealPlaceClientConfig {
    public static final String FILE_NAME = "realplace-client.toml";
    public static final RealPlaceClientConfig INSTANCE;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<RealPlaceClientConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(RealPlaceClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private final ForgeConfigSpec.BooleanValue showPlacementKeyHints;

    private RealPlaceClientConfig(ForgeConfigSpec.Builder builder) {
        builder.push("ui");
        showPlacementKeyHints = builder
                .comment("Show the placement-mode key hints above the hotbar.")
                .define("showPlacementKeyHints", true);
        builder.pop();
    }

    public boolean showPlacementKeyHints() {
        return showPlacementKeyHints.get();
    }
}
