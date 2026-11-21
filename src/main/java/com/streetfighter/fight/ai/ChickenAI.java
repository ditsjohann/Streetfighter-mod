package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Hyper-aggressive aerial pest: flaps, glides over projectiles, dive kicks and peck flurries. */
public class ChickenAI extends FighterAI {
    public ChickenAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.85f;
        zoning = 0.15f;
        grappling = 0.05f;
        aerial = 0.7f;
    }
}
