package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Trapper: lays webs, shoots silk, then skitters in for poisonous bites and pounces. */
public class SpiderAI extends FighterAI {
    public SpiderAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.65f;
        zoning = 0.45f;
        grappling = 0.1f;
        aerial = 0.35f;
    }
}
