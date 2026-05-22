package com.tyrthurey.tyrsfluidvoidstuff;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(TyrsFluidVoidStuff.MODID)
public class TyrsFluidVoidStuff {
    public static final String MODID = "tyrsfluidvoidstuff";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TyrsFluidVoidStuff(IEventBus bus, ModContainer container) {
        // Server (world-side) config: max fluid column length.
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Force every registered fluid onto the translucent render layer so
            // the fade-into-void gradient actually blends (vanilla lava and many
            // modded fluids default to the solid layer, where alpha < 1 either
            // is ignored or becomes an ugly alpha-test cutout).
            bus.addListener(TyrsFluidVoidStuff::clientStartup);

            // Register NeoForge's built-in config screen so the "Config" button on
            // the Mods screen opens an auto-generated UI for the SERVER config.
            // Mirrors the NeoForge MDK template.
            container.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
        }
    }

    public static void clientStartup(FMLClientSetupEvent event) {
        int forced = 0;
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY) continue;
            ItemBlockRenderTypes.setRenderLayer(fluid, RenderType.translucent());
            forced++;
        }
        LOGGER.info("[TyrsFluidVoidStuff] Forced translucent render layer on {} fluids.", forced);
    }
}
