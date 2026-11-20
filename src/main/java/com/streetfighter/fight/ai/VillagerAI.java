package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;

/** Grappler: walks in, hmms, and makes you an offer you can't block. */
public class VillagerAI extends FighterAI {
    private int blockCounter;

    public VillagerAI(AIDifficulty difficulty) {
        super(difficulty);
    }

    @Override
    protected void decide(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (opponentBlockingALot(opp)) blockCounter++;
        else blockCounter = Math.max(0, blockCounter - 1);

        if (self.meter >= 100 && chance(hard() ? 0.65f : 0.3f)) {
            out.superMove = true;
            return;
        }
        if (dist < 75) {
            float grabChance = (hard() ? 0.45f : 0.2f) + (blockCounter > 1 ? 0.3f : 0f);
            if (chance(grabChance)) {
                out.special1 = true; // Trade Offer
                blockCounter = 0;
            } else if (chance(0.5f)) out.light = true;
            else out.heavy = true;
            return;
        }
        if (dist < 140) {
            if (chance(0.35f)) out.heavy = true;
            else walkForward();
            return;
        }
        if (dist > 300 && e.projectilesOf(self) == 0 && chance(0.4f)) {
            out.special2 = true; // Emerald Toss
            return;
        }
        if (chance(0.12f) && hard()) {
            out.up = true;
            walkForward();
            return;
        }
        walkForward();
    }

    @Override
    protected void comboFollowUp(FightEngine e, Fighter self, Fighter opp) {
        if (self.meter >= 100) out.superMove = true;
        else out.special2 = true;
    }

    @Override
    protected boolean punish(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (dist < 75 && opp.grounded()) {
            out.special1 = true;
            return true;
        }
        return super.punish(e, self, opp, dist);
    }
}
