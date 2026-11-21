package com.streetfighter.entity;

import com.streetfighter.StreetFighterMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StreetFighterMod.MODID);

    public static final RegistryObject<EntityType<TreantEntity>> OAK_TREANT = ENTITIES.register("oak_treant",
            () -> EntityType.Builder.of(TreantEntity::new, MobCategory.MISC).sized(0.9f, 2.6f).clientTrackingRange(10)
                    .build("oak_treant"));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(ModEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(OAK_TREANT.get(), TreantEntity.createAttributes().build());
    }
}
