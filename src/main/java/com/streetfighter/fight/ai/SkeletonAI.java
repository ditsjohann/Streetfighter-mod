package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;

/** Keep-away zoner: hates being close, rains arrows from range. */
public class SkeletonAI extends FighterAI {
    public SkeletonAI(AIDifficulty difficulty) {
        super(difficulty);
    }

    @Override
    protected void decide(FightEngine e, Fighter self, Fighter opp, float dist) {
        boolean arrowOut = e.projectilesOf(self) > 0;
        if (self.meter >= 100 && dist > 140 && chance(hard() ? 0.7f : 0.35f)) {
            out.superMove = true;
            return;
        }
        if (dist < 115) {
            float r = rng.nextFloat();
            if (r < 0.35f) out.light = true;
            else if (r < 0.55f) {
                out.heavy = true;
                if (hard() && chance(0.4f)) out.down = true; // femur sweep
            } else if (r < 0.70f && hard()) {
                out.up = true; // jump out
                walkBack();
            } else walkBack();
            return;
        }
        if (dist < 280) {
            if (!arrowOut && chance(0.45f)) out.special1 = true;
            else if (chance(0.6f)) walkBack();
            else walkForward();
            return;
        }
        if (!arrowOut && chance(hard() ? 0.75f : 0.5f)) {
            out.special1 = true;
        } else if (chance(0.25f)) {
            walkForward();
        }
    }

    @Override
    protected void antiAir(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (dist > 90) out.special2 = true; // Triple Volley covers the sky
        else out.heavy = true;
    }

    @Override
    protected void comboFollowUp(FightEngine e, Fighter self, Fighter opp) {
        if (self.meter >= 100) out.superMove = true;
        else out.special1 = true;
    }
}
