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

    public static final ForgeConfigSpec.BooleanValue DEFEATED_HOSTILES_DIE = BUILDER
            .comment("Hostile mobs that lose a fight are knocked out for good (and drop their loot).")
            .define("defeatedHostilesDie", true);

    public static final ForgeConfigSpec.BooleanValue DEFEATED_PASSIVES_DIE = BUILDER
            .comment("Passive mobs (pigs, villagers...) that lose a fight are knocked out for good.")
            .define("defeatedPassivesDie", false);

    public static final ForgeConfigSpec.BooleanValue LOSER_PENALTY = BUILDER
            .comment("Players who lose a fight are left bruised (Weakness + Slowness for 30 seconds).")
            .define("loserPenalty", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private SFConfig() {
    }
}
