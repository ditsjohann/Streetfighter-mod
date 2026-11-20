package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;

/** Generic mob: walks in and swings. */
public class BrawlerAI extends FighterAI {
    public BrawlerAI(AIDifficulty difficulty) {
        super(difficulty);
    }

    @Override
    protected void decide(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (self.meter >= 100 && dist < 200 && chance(0.5f)) {
            out.superMove = true;
            return;
        }
        if (dist < 95) {
            float r = rng.nextFloat();
            if (r < 0.4f) out.light = true;
            else if (r < 0.65f) out.heavy = true;
            else if (r < 0.8f) {
                out.heavy = true;
                out.down = true;
            } else walkBack();
            return;
        }
        if (dist < 230 && chance(0.2f)) {
            out.special1 = true; // Lunge
            return;
        }
        if (dist < 260 && chance(0.12f)) {
            out.special2 = true; // Leap Smash
            return;
        }
        walkForward();
    }
}
