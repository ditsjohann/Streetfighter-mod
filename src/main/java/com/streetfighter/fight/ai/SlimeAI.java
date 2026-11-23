package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;

/** Bouncing blob: lobs sticky slimeballs, bounces around (BOING!) and comes down with belly flops. */
public class SlimeAI extends FighterAI {
    private boolean decided, wantBounce;

    public SlimeAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.7f;
        zoning = 0.2f;
        grappling = 0.1f;
        aerial = 0.45f;
    }

    @Override
    protected boolean preThink(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (self.state != Fighter.State.JUMP) {
            decided = false;
            return false;
        }
        // on the way down: sometimes hold up to BOING off the floor
        if (self.vy < 0 && self.y < 70 && self.bounces < 2) {
            if (!decided) {
                decided = true;
                wantBounce = dist > 90 && chance(hard() ? 0.5f : 0.3f);
            }
            if (wantBounce) {
                out.up = true;
                return true;
            }
        }
        return false;
    }
}
