package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.Fighter.State;
import com.streetfighter.fight.InputBuffer.Button;

import java.util.List;

/**
 * Ki Clash AI (Dragon Ball style): charges ki at range, peppers you with ki blasts, takes off after flyers, chases
 * in the air with dragon dashes, rush-combos into smashes up close and vanishes behind your attacks.
 */
public class KiAI extends FighterAI {
    private int chargeFrames;

    public KiAI(AIDifficulty difficulty) {
        super(difficulty);
        aggression = 0.6f;
        zoning = 0.35f;
        grappling = 0.05f;
        aerial = 0.4f;
    }

    private void rush(Fighter self) {
        startCombo(self, List.of(Step.n(Button.LP), Step.t(Button.LP), Step.t(Button.LP), Step.t(Button.LP),
                chance(0.5f) ? Step.t(Button.LP) : Step.t(Button.HP)), 0);
    }

    @Override
    protected boolean preThink(FightEngine e, Fighter self, Fighter opp, float dist) {
        float dy = opp.y - self.y;
        if (chargeFrames > 0) {
            if (dist < 170 || opp.isAttackingWithin(10) || self.meter >= Fighter.MAX_METER) {
                chargeFrames = 0;
            } else {
                chargeFrames--;
                out.kiCharge = true;
                return true;
            }
        }
        boolean free = self.actionable() || (self.state == State.JUMP && self.flying);
        if (!free) return false;
        if (opp.isAttackingWithin(hard() ? 6 : 3) && opp.move != null && dist < opp.move.reach() + 60
                && chance(difficulty.blockChance * 0.7f)) {
            holdBack(self); // guard
            return true;
        }
        // super arts: transform at full ki, otherwise beams and spirit balls
        if (self.meter >= 300 && !self.has(com.streetfighter.fight.StatusEffect.SUPER_FORM) && chance(0.04f)) {
            out.superLevel = 3;
            return true;
        }
        if (self.meter >= 200 && dist > 200 && chance(0.02f)) {
            out.superLevel = 2;
            return true;
        }
        if (self.meter >= 100 && dist > 260 && chance(0.012f)) {
            out.superLevel = 1;
            return true;
        }
        if (self.meter >= 100 && opp.isAttackingWithin(hard() ? 8 : 4) && dist < 170 && chance(hard() ? 0.1f : 0.035f)) {
            out.parry = true; // vanish
            return true;
        }
        boolean wantsForm = self.meter >= 200 && !self.has(com.streetfighter.fight.StatusEffect.SUPER_FORM);
        if ((self.meter < 90 || wantsForm) && dist > 300 && chance(0.05f)) {
            chargeFrames = 40 + rng.nextInt(70);
            out.kiCharge = true;
            return true;
        }
        if (self.flying && self.state == State.JUMP) {
            if (dist > 330 && chance(0.03f)) {
                out.dash = 1;
                return true;
            }
            if (dist > 190 && self.meter >= 8 && chance(0.07f)) {
                out.kiBlast = true;
                return true;
            }
            if (self.meter >= 60 && dist > 220 && chance(0.012f)) {
                out.special = self.moves.specialIndex(com.streetfighter.fight.Moveset.Role.PROJECTILE);
                out.strength = 1;
                return true;
            }
            if (Math.abs(dy) > 25) {
                if (dy > 0) out.up = true;
                else out.down = true;
            }
            if (dist > 85) holdForward(self);
            else if (Math.abs(dy) < 60 && chance(0.3f)) {
                if (chance(0.75f)) rush(self);
                else out.hp = true;
            }
            if (opp.grounded() && !opp.flying && chance(0.015f)) out.down = true; // come down and fight
            return true;
        }
        // on the ground: take off after flyers, blast from range
        if ((opp.flying && opp.y > 50 && chance(0.25f)) || chance(aerial * 0.015f)) {
            out.up = true;
            return true;
        }
        if (dist > 240 && self.meter >= 8 && chance(0.05f)) {
            out.kiBlast = true;
            return true;
        }
        if (dist < 95 && chance(0.12f)) {
            rush(self);
            return true;
        }
        return false;
    }
}
