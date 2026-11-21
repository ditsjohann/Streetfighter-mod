package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Keep-away zoner: arrows from range, volleys against jumps, backflips out of pressure. */
public class SkeletonAI extends FighterAI {
    public SkeletonAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.2f;
        zoning = 0.6f;
        grappling = 0.02f;
    }
}
