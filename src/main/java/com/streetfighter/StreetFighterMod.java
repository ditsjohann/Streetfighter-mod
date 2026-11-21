package com.streetfighter;

import com.mojang.logging.LogUtils;
import com.streetfighter.network.ModNetwork;
import com.streetfighter.server.ServerEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(StreetFighterMod.MODID)
public class StreetFighterMod {
    public static final String MODID = "streetfighter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StreetFighterMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        com.streetfighter.entity.ModEntities.register(modBus);
        ModNetwork.register();
        context.registerConfig(ModConfig.Type.COMMON, SFConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(ServerEvents.class);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.streetfighter.client.ClientSetup.init(modBus);
        }
    }
}
