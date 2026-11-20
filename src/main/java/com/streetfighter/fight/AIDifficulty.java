package com.streetfighter.fight;

/** World-wide difficulty of the fighting AI, chosen once when the world starts. */
public enum AIDifficulty {
    NORMAL("normal", "Normal",
            18, 0.30f, 0.25f, 0.20f, 0.12f, 0.25f),
    HARD("hard", "Hard",
            7, 0.72f, 0.70f, 0.85f, 0.02f, 0.75f);

    public final String id;
    public final String displayName;
    /** Frames between AI decisions (lower = faster reactions). */
    public final int reactionFrames;
    /** Chance to block an incoming attack it has noticed. */
    public final float blockChance;
    /** Chance to anti-air a jumping opponent. */
    public final float antiAirChance;
    /** Chance to cancel a connecting normal into a special (combos). */
    public final float comboChance;
    /** Chance to just stand there for a moment. */
    public final float idleChance;
    /** Chance to punish a whiffed attack. */
    public final float punishChance;

    AIDifficulty(String id, String displayName, int reactionFrames, float blockChance, float antiAirChance,
                 float comboChance, float idleChance, float punishChance) {
        this.id = id;
        this.displayName = displayName;
        this.reactionFrames = reactionFrames;
        this.blockChance = blockChance;
        this.antiAirChance = antiAirChance;
        this.comboChance = comboChance;
        this.idleChance = idleChance;
        this.punishChance = punishChance;
    }

    public static AIDifficulty byId(String id) {
        for (AIDifficulty d : values()) {
            if (d.id.equalsIgnoreCase(id)) return d;
        }
        return null;
    }
}
