package com.tyrthurey.tyrsfluidvoidstuff;

import com.mojang.logging.LogUtils;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.util.List;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(value = TyrsFluidVoidStuff.MODID, dist = Dist.CLIENT)
public class TyrsFluidVoidStuff {
    public static final String MODID = "tyrsfluidvoidstuff";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> TRANSPARENT_FLUIDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        TRANSPARENT_FLUIDS = builder.comment("If you notice a fluid not rendering transparent, try adding its identifier here")
                .defineListAllowEmpty("additionalTransparentFluids", List.of("minecraft:flowing_lava"), () -> "", o -> {
                    if (o instanceof String) {
                        try {
                            ResourceLocation.parse((String) o);
                            return true;
                        } catch (ResourceLocationException ignored) {
                        }
                    }
                    return false;
                });
        SPEC = builder.build();
    }

    public TyrsFluidVoidStuff(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        bus.addListener(TyrsFluidVoidStuff::clientStartup);
    }

    public static void clientStartup(FMLClientSetupEvent event) {
        for (String s : TRANSPARENT_FLUIDS.get()) {
            ResourceLocation rl = ResourceLocation.parse(s);
            // 1.21.1: BuiltInRegistries.FLUID.get(ResourceLocation) returns the value (no .getValue)
            Fluid fluid = BuiltInRegistries.FLUID.get(rl);
            if (fluid == Fluids.EMPTY) {
                LOGGER.error("Fluid '{}' not found!", s);
            } else {
                // 1.21.1: ChunkSectionLayer does not exist; use RenderType.translucent()
                ItemBlockRenderTypes.setRenderLayer(fluid, RenderType.translucent());
            }
        }
    }
}
