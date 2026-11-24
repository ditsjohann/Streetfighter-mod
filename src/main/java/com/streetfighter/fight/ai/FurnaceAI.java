package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Smelter: lays hot coals, lobs fire charges, then walks you down for a Smelt Down grab. */
public class FurnaceAI extends FighterAI {
    public FurnaceAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.55f;
        zoning = 0.35f;
        grappling = 0.3f;
        aerial = 0.1f;
    }
}
