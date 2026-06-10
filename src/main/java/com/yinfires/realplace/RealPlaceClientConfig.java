package com.yinfires.realplace;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class RealPlaceClientConfig {
    public static final String FILE_NAME = "realplace-client.toml";
    public static final RealPlaceClientConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<RealPlaceClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(RealPlaceClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private final ModConfigSpec.BooleanValue showPlacementKeyHints;

    private RealPlaceClientConfig(ModConfigSpec.Builder builder) {
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
