package com.streetfighter.fight;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Every fighter archetype in the mod. STEVE is the player's own style; SKELETON, VILLAGER, PIG, CHICKEN,
 * ALCHEMIST (cleric villager) and GOAT are the roster with unique movesets (and can be learned by beating them). BRAWLER is the generic style
 * used by every other mob so that *any* mob can be challenged.
 */
public enum FighterType {
    STEVE("steve", "Steve", "Block Brawler", false),
    SKELETON("skeleton", "Skeleton", "Bone Archer", true),
    VILLAGER("villager", "Villager", "Hmm-Fu Master", true),
    PIG("pig", "Pig", "Pork Tank", true),
    CHICKEN("chicken", "Chicken", "Feather Fury", true),
    ALCHEMIST("alchemist", "Alchemist", "Potion Master", true),
    GOAT("goat", "Goat", "Mountain Rammer", true),
    BRAWLER("brawler", "Brawler", "Wild Mob", false);

    public final String id;
    public final String displayName;
    public final String title;
    /** Whether the player can learn this style by defeating it. */
    public final boolean learnable;

    FighterType(String id, String displayName, String title, boolean learnable) {
        this.id = id;
        this.displayName = displayName;
        this.title = title;
        this.learnable = learnable;
    }

    public static FighterType byId(String id) {
        for (FighterType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return BRAWLER;
    }

    /** Works out which fighter a world entity becomes when challenged. */
    public static FighterType fromEntity(Entity entity) {
        if (entity.getType() == EntityType.SKELETON) return SKELETON;
        if (entity.getType() == EntityType.PIG) return PIG;
        if (entity.getType() == EntityType.CHICKEN) return CHICKEN;
        if (entity.getType() == EntityType.GOAT) return GOAT;
        if (entity instanceof Villager villager) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) return VILLAGER;
            if (profession == VillagerProfession.CLERIC) return ALCHEMIST;
        }
        return BRAWLER;
    }
}
