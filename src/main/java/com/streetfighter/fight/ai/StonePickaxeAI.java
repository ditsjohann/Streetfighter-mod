package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Hyper rushdown: drills in, spin-mines and strings mining combos. */
public class StonePickaxeAI extends FighterAI {
    public StonePickaxeAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.85f;
        zoning = 0.2f;
        grappling = 0.08f;
        aerial = 0.3f;
    }
}
