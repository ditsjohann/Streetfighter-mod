package com.streetfighter;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SFConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue PEACEFUL_MOBS = BUILDER
            .comment("Hostile mobs never target or damage players. Settle things with a street fight instead!")
            .define("peacefulMobs", true);

    public static final ForgeConfigSpec.DoubleValue CHALLENGE_RANGE = BUILDER
            .comment("How far away (in blocks) a mob can be challenged from.")
            .defineInRange("challengeRange", 6.0, 2.0, 16.0);

    public static final ForgeConfigSpec.IntValue HOLD_TICKS = BUILDER
            .comment("How long (in ticks, 20 = 1 second) the challenge key must be held.")
            .defineInRange("holdTicks", 20, 1, 100);

    public static final ForgeConfigSpec.BooleanValue DEFEATED_MOBS_DIE = BUILDER
            .comment("Mobs (and oak trees) that lose a fight are knocked out for good and drop their fight rewards.",
                    "Turn off to let beaten mobs walk away (no rewards).")
            .define("defeatedMobsDie", true);

    public static final ForgeConfigSpec.DoubleValue REWARD_MULTIPLIER = BUILDER
            .comment("Multiplies the amount of loot a beaten mob drops (a pig drops ~10 porkchops at 1.0).")
            .defineInRange("rewardMultiplier", 1.0, 0.0, 10.0);

    public static final ForgeConfigSpec.BooleanValue PICKAXE_CHALLENGE = BUILDER
            .comment("Taking a stone pickaxe out of a crafting table makes it fight you first (win to get it).")
            .define("stonePickaxeChallenge", true);

    public static final ForgeConfigSpec.BooleanValue XRAY_FINISHERS = BUILDER
            .comment("Play a Mortal Kombat style X-ray bone-breaking finisher on the match-winning K.O.")
            .define("xrayFinishers", true);

    public static final ForgeConfigSpec.BooleanValue LOSER_PENALTY = BUILDER
            .comment("Players who lose a fight are left bruised (Weakness + Slowness for 30 seconds).")
            .define("loserPenalty", true);

    public static final ForgeConfigSpec.BooleanValue BREAKABLE_WALLS = BUILDER
            .comment("Fighters smashed into leaves, glass, flowers or hay bales break them (respects claim/protection mods).")
            .define("breakableWalls", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private SFConfig() {
    }
}
