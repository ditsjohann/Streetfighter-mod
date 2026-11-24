package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Blade dancer: pokes from max range, jumps in for critical hits, lunges through gaps. */
public class IronSwordAI extends FighterAI {
    public IronSwordAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.6f;
        zoning = 0.35f;
        grappling = 0.05f;
        aerial = 0.45f;
    }
}
