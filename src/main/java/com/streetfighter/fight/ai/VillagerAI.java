package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Grappler: walks you down and makes you an offer you can't block. */
public class VillagerAI extends FighterAI {
    public VillagerAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.6f;
        zoning = 0.15f;
        grappling = 0.35f;
    }
}
