package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Hot-headed rock: stokes its fire, throws burning coal and smothers you in soot. */
public class CoalOreAI extends FighterAI {
    public CoalOreAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.6f;
        zoning = 0.35f;
        grappling = 0.1f;
        aerial = 0.2f;
    }
}
