package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Stubborn mule: rummages for items, chest tosses, donkey kicks up close. */
public class DonkeyAI extends FighterAI {
    public DonkeyAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.55f;
        zoning = 0.35f;
        grappling = 0.12f;
        aerial = 0.15f;
    }
}
