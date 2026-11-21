package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Rooted bruiser: walls you out with long branches, apples and root eruptions, armors through pokes. */
public class OakAI extends FighterAI {
    public OakAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.45f;
        zoning = 0.4f;
        grappling = 0.15f;
        aerial = 0.02f;
    }
}
