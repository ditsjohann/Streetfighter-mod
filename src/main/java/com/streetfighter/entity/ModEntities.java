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

    public static final RegistryObject<EntityType<OreGolemEntity>> IRON_ORE_GOLEM = ENTITIES.register("iron_ore_golem",
            () -> EntityType.Builder.of(OreGolemEntity::new, MobCategory.MISC).sized(1.0f, 2.1f).clientTrackingRange(10)
                    .build("iron_ore_golem"));

    public static final RegistryObject<EntityType<PickaxeFighterEntity>> STONE_PICKAXE = ENTITIES.register("stone_pickaxe",
            () -> EntityType.Builder.of(PickaxeFighterEntity::new, MobCategory.MISC).sized(0.7f, 1.9f).clientTrackingRange(10)
                    .build("stone_pickaxe"));

    public static final RegistryObject<EntityType<OreGolemEntity>> COAL_ORE_GOLEM = ENTITIES.register("coal_ore_golem",
            () -> EntityType.Builder.of(OreGolemEntity::new, MobCategory.MISC).sized(1.0f, 2.1f).clientTrackingRange(10)
                    .build("coal_ore_golem"));

    public static final RegistryObject<EntityType<PickaxeFighterEntity>> IRON_SWORD = ENTITIES.register("iron_sword",
            () -> EntityType.Builder.of(PickaxeFighterEntity::new, MobCategory.MISC).sized(0.6f, 2.0f).clientTrackingRange(10)
                    .build("iron_sword"));

    public static final RegistryObject<EntityType<FurnaceGolemEntity>> FURNACE_GOLEM = ENTITIES.register("furnace_golem",
            () -> EntityType.Builder.of(FurnaceGolemEntity::new, MobCategory.MISC).sized(1.0f, 1.9f).clientTrackingRange(10)
                    .build("furnace_golem"));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(ModEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(OAK_TREANT.get(), BlockBodyEntity.createAttributes().build());
        event.put(IRON_ORE_GOLEM.get(), BlockBodyEntity.createAttributes().build());
        event.put(STONE_PICKAXE.get(), BlockBodyEntity.createAttributes().build());
        event.put(COAL_ORE_GOLEM.get(), BlockBodyEntity.createAttributes().build());
        event.put(IRON_SWORD.get(), BlockBodyEntity.createAttributes().build());
        event.put(FURNACE_GOLEM.get(), BlockBodyEntity.createAttributes().build());
    }
}
