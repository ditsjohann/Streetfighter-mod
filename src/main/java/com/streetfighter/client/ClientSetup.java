package com.streetfighter.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.streetfighter.StreetFighterMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    /** Hold to challenge the mob you're looking at. Shares E with the inventory on purpose. */
    public static final KeyMapping CHALLENGE_KEY = new KeyMapping("key.streetfighter.challenge",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.categories.streetfighter");

    /** Opens the Fight Guide (outside fights; in fights it's in the pause menu). */
    public static final KeyMapping GUIDE_KEY = new KeyMapping("key.streetfighter.guide",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.streetfighter");

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerKeys);
        modBus.addListener(ClientSetup::registerOverlays);
        modBus.addListener(ClientSetup::registerRenderers);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        MinecraftForge.EVENT_BUS.register(com.streetfighter.client.world.WorldRenderEvents.class);
    }

    private static void registerOverlays(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(ResourceLocation.fromNamespaceAndPath(StreetFighterMod.MODID, "challenge_prompt"),
                (g, delta) -> ClientEvents.renderHud(g));
    }

    private static void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.streetfighter.entity.ModEntities.OAK_TREANT.get(),
                com.streetfighter.client.world.TreantRenderer::new);
        event.registerEntityRenderer(com.streetfighter.entity.ModEntities.IRON_ORE_GOLEM.get(),
                com.streetfighter.client.world.OreGolemRenderer::new);
        event.registerEntityRenderer(com.streetfighter.entity.ModEntities.STONE_PICKAXE.get(),
                com.streetfighter.client.world.PickaxeRenderer::new);
        event.registerEntityRenderer(com.streetfighter.entity.ModEntities.COAL_ORE_GOLEM.get(),
                com.streetfighter.client.world.OreGolemRenderer::new);
        event.registerEntityRenderer(com.streetfighter.entity.ModEntities.IRON_SWORD.get(),
                com.streetfighter.client.world.PickaxeRenderer::new);
        event.registerEntityRenderer(com.streetfighter.entity.ModEntities.FURNACE_GOLEM.get(),
                com.streetfighter.client.world.FurnaceRenderer::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(CHALLENGE_KEY);
        event.register(GUIDE_KEY);
    }
}
