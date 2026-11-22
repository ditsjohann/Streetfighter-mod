package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Mad cow: charges in, milks off debuffs, moo-percuts jumpers. */
public class CowAI extends FighterAI {
    public CowAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.6f;
        zoning = 0.2f;
        grappling = 0.1f;
        aerial = 0.05f;
    }
}
