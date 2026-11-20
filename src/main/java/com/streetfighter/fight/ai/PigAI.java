package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;

/** Rushdown tank: charges in, mixes low stomps with overhead belly flops. */
public class PigAI extends FighterAI {
    public PigAI(AIDifficulty difficulty) {
        super(difficulty);
    }

    @Override
    protected void decide(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (self.meter >= 100 && dist > 120 && chance(hard() ? 0.7f : 0.35f)) {
            out.superMove = true;
            return;
        }
        if (dist > 260) {
            if (chance(hard() ? 0.35f : 0.25f)) out.special1 = true; // Hog Charge
            else walkForward();
            return;
        }
        if (dist > 110) {
            float r = rng.nextFloat();
            if (r < 0.2f) out.special2 = true; // Belly Flop overhead
            else if (r < 0.32f) {
                out.heavy = true;
                out.down = true; // Mud Slide low
            } else walkForward();
            return;
        }
        // close range mix-up: low stomp vs overhead flop
        float r = rng.nextFloat();
        boolean oppCrouching = opp.crouching();
        if (oppCrouching && r < 0.4f) out.special2 = true;
        else if (r < 0.45f) out.heavy = true; // Hoof Stomp (low)
        else if (r < 0.8f) out.light = true;
        else {
            out.light = true;
            out.down = true;
        }
    }

    @Override
    protected void antiAir(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (dist < 100) out.light = true;
        else walkBack();
    }

    @Override
    protected void comboFollowUp(FightEngine e, Fighter self, Fighter opp) {
        if (self.meter >= 100) out.superMove = true;
        else out.special1 = true;
    }
}
