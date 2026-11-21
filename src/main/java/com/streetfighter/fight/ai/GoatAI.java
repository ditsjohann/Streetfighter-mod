package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Rammer: charges from mid range, leaps from far away, screams at zoners and wall-splats. */
public class GoatAI extends FighterAI {
    public GoatAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.7f;
        zoning = 0.2f;
        grappling = 0.08f;
        aerial = 0.35f;
    }
}
