package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Walking bomb: creeps up silently and blows up in your face. */
public class CreeperAI extends FighterAI {
    public CreeperAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.7f;
        zoning = 0.25f;
        grappling = 0.05f;
        aerial = 0.1f;
    }
}
