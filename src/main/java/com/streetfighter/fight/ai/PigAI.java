package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Rushdown tank: charges, low/overhead mix-ups, armored hog charges. */
public class PigAI extends FighterAI {
    public PigAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.8f;
        zoning = 0.05f;
        grappling = 0.1f;
    }
}
