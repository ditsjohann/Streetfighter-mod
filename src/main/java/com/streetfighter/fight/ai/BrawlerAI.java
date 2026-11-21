package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Generic mob: straightforward and scrappy. */
public class BrawlerAI extends FighterAI {
    public BrawlerAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.55f;
        zoning = 0.15f;
        grappling = 0.1f;
    }
}
