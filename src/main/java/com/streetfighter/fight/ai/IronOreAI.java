package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Armored juggernaut: walks through pokes, smelter grabs, stalagmites against jumps. */
public class IronOreAI extends FighterAI {
    public IronOreAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.5f;
        zoning = 0.25f;
        grappling = 0.18f;
        aerial = 0.02f;
    }
}
