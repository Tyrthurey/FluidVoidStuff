package com.tyrthurey.tyrsfluidvoidstuff;

import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Server-side config for TyrsFluidVoidStuff.
 *
 * Options:
 *  - {@link #MAX_FLUID_COLUMN_LENGTH}: vertical-drop cap from the source
 *    (0 = vanilla, unlimited).
 *  - {@link #FLUID_COLUMN_LENGTH_BLACKLIST}: list of fluid IDs the custom
 *    column-length logic must NOT affect (those fluids behave 100% vanilla).
 */
public final class Config {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_FLUID_COLUMN_LENGTH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FLUID_COLUMN_LENGTH_BLACKLIST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        MAX_FLUID_COLUMN_LENGTH = b
                .comment(
                        "Maximum vertical drop (in blocks) that a fluid column may span below its source.",
                        "Applies to every FlowingFluid (water, lava, modded fluids) unless blacklisted below.",
                        "0 (default) = vanilla behaviour (unlimited).",
                        "Any other value N means a fluid can drop at most N blocks below its source Y;",
                        "the column then ends with air underneath, and the renderer draws the same",
                        "fade-into-void gradient at the bottom of that truncated column.")
                .translation("tyrsfluidvoidstuff.configuration.maxFluidColumnLength")
                .defineInRange("maxFluidColumnLength", 0, 0, Integer.MAX_VALUE);

        FLUID_COLUMN_LENGTH_BLACKLIST = b
                .comment(
                        "Fluids in this list are NOT affected by the custom column-length / orphaned-fall",
                        "logic and behave 100% vanilla. Both the source and flowing form should be listed",
                        "if you want full vanilla behaviour (e.g. \"minecraft:lava\", \"minecraft:flowing_lava\").")
                .translation("tyrsfluidvoidstuff.configuration.fluidColumnLengthBlacklist")
                .defineListAllowEmpty("fluidColumnLengthBlacklist", List.of(), () -> "", o -> {
                    if (o instanceof String s) {
                        try {
                            ResourceLocation.parse(s);
                            return true;
                        } catch (ResourceLocationException ignored) {
                        }
                    }
                    return false;
                });

        SPEC = b.build();
    }

    private Config() {}
}
