package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;

/** Status zoner: buffs up from range, lays poison clouds, lobs potions and pokes with vials. */
public class AlchemistAI extends FighterAI {
    public AlchemistAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.3f;
        zoning = 0.65f;
        grappling = 0.1f;
        aerial = 0.05f;
    }
}
