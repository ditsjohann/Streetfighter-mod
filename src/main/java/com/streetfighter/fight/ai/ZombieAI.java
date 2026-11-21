package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Relentless undead grappler: shambles forward, bites to starve your meter, grabs. */
public class ZombieAI extends FighterAI {
    public ZombieAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.75f;
        zoning = 0.15f;
        grappling = 0.35f;
        aerial = 0.05f;
    }
}
