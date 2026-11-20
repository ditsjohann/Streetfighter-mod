package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FightInput;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.Fighter.State;
import com.streetfighter.fight.FighterType;
import com.streetfighter.fight.HitLevel;
import com.streetfighter.fight.Move;
import com.streetfighter.fight.Projectile;

import java.util.Random;

/**
 * Base class for mob fighting brains. The AI produces the same {@link FightInput} a human would, so it
 * plays by exactly the same rules. Subclasses only decide *what* to do; reactions such as blocking,
 * anti-airs, combos and punishes are shared and scaled by the world's {@link AIDifficulty}.
 */
public abstract class FighterAI {
    protected final AIDifficulty difficulty;
    protected final Random rng = new Random();
    protected final FightInput out = new FightInput();

    /** Directional "plan" that is held between decisions. */
    protected int planHorizontal; // +1 forward, -1 back, 0 none
    protected boolean planCrouch;
    protected int decisionTimer;

    private int lastThreatSerial = -1;
    private int lastThreatProjectile = -1;
    private boolean blockingThreat;
    private boolean blockLow;
    private int blockHold;
    private int lastComboRollSerial = -1;
    private boolean sawOppJump;
    private boolean antiAirPending;
    private int lastPunishSerial = -1;

    protected FighterAI(AIDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    public static FighterAI create(FighterType type, AIDifficulty difficulty) {
        return switch (type) {
            case SKELETON -> new SkeletonAI(difficulty);
            case VILLAGER -> new VillagerAI(difficulty);
            case PIG -> new PigAI(difficulty);
            default -> new BrawlerAI(difficulty);
        };
    }

    public final FightInput think(FightEngine e, Fighter self, Fighter opp) {
        out.clear();
        if (self.state == State.KO || self.state == State.VICTORY) return out;

        // 1. Stunned: keep holding block if we were blocking.
        if (self.state == State.HITSTUN || self.state == State.KNOCKDOWN) {
            blockingThreat = false;
            return out;
        }
        if (self.state == State.BLOCKSTUN) {
            holdBack(self);
            out.down = blockLow;
            return out;
        }

        // 2. Combos: cancel a connecting normal into a special.
        if (self.state == State.ATTACK) {
            Move m = self.move;
            if (m != null && self.moveConnected && m.cancelable && !m.special && lastComboRollSerial != self.moveSerial) {
                lastComboRollSerial = self.moveSerial;
                if (chance(difficulty.comboChance)) comboFollowUp(e, self, opp);
            }
            return out;
        }
        if (self.state == State.JUMP) {
            if (!self.airAttackUsed && Math.abs(opp.x - self.x) < 110 && self.vy < 4 && chance(0.25f)) {
                if (rng.nextBoolean()) out.heavy = true;
                else out.light = true;
            }
            return out;
        }
        if (!self.actionable()) return out;

        float dist = Math.abs(opp.x - self.x);

        // 3. Defense against incoming attacks and projectiles.
        if (handleThreats(e, self, opp, dist)) return out;

        // 4. Anti-air a jumping opponent (rolled once per jump, executed as they come down).
        boolean oppJumping = !opp.grounded() && (opp.state == State.JUMP || (opp.state == State.ATTACK && opp.move != null && opp.move.air));
        if (oppJumping && !sawOppJump) {
            sawOppJump = true;
            antiAirPending = chance(difficulty.antiAirChance);
        } else if (!oppJumping) {
            sawOppJump = false;
            antiAirPending = false;
        }
        if (antiAirPending && dist < 210 && opp.vy < 3) {
            antiAirPending = false;
            antiAir(e, self, opp, dist);
            return out;
        }

        // 5. Punish a whiffed move.
        if (opp.state == State.ATTACK && opp.move != null && opp.curFrame >= opp.activeEnd && !opp.moveConnected
                && lastPunishSerial != opp.moveSerial) {
            lastPunishSerial = opp.moveSerial;
            if (chance(difficulty.punishChance) && punish(e, self, opp, dist)) return out;
        }

        // 6. Regular neutral game.
        if (--decisionTimer <= 0) {
            decisionTimer = difficulty.reactionFrames + rng.nextInt(difficulty.reactionFrames + 1);
            planHorizontal = 0;
            planCrouch = false;
            if (chance(difficulty.idleChance)) {
                decisionTimer += 20;
            } else {
                decide(e, self, opp, dist);
            }
        }
        applyPlan(self);
        return out;
    }

    private boolean handleThreats(FightEngine e, Fighter self, Fighter opp, float dist) {
        // melee threat
        boolean meleeThreat = opp.isAttackingWithin(difficulty == AIDifficulty.HARD ? 6 : 3)
                && opp.move.projectile == null && dist < opp.move.reach() + 70;
        if (meleeThreat) {
            if (lastThreatSerial != opp.moveSerial) {
                lastThreatSerial = opp.moveSerial;
                blockingThreat = opp.move.level != HitLevel.GRAB && chance(difficulty.blockChance);
                blockLow = guessLow(opp.move.level);
                blockHold = 0;
            }
        }
        // projectile threat
        Projectile incoming = null;
        for (Projectile p : e.projectiles) {
            if (p.owner == self.index || p.delay > 40) continue;
            float dx = self.x - p.x;
            boolean approaching = p.spec.fromSky ? Math.abs(dx) < 160 : (Math.signum(dx) == Math.signum(p.vx) && Math.abs(dx) < 220);
            if (approaching) {
                incoming = p;
                break;
            }
        }
        if (incoming != null && lastThreatProjectile != incoming.id) {
            lastThreatProjectile = incoming.id;
            if (chance(difficulty.blockChance + 0.1f)) {
                blockingThreat = true;
                blockLow = false;
                blockHold = 0;
            } else if (!incoming.spec.fromSky && chance(0.35f)) {
                // try to jump over it instead
                out.up = true;
                if (rng.nextBoolean()) holdForward(self);
                blockingThreat = false;
                return true;
            }
        }
        boolean threatLive = meleeThreat || incoming != null;
        if (blockingThreat && (threatLive || blockHold < 8)) {
            blockHold++;
            holdBack(self);
            out.down = blockLow;
            return true;
        }
        blockingThreat = false;
        return false;
    }

    private boolean guessLow(HitLevel level) {
        if (difficulty == AIDifficulty.HARD) {
            return level == HitLevel.LOW || (level == HitLevel.MID && rng.nextFloat() < 0.5f);
        }
        // Normal AI guesses the height correctly ~60% of the time.
        boolean correct = rng.nextFloat() < 0.6f;
        boolean low = level == HitLevel.LOW;
        return correct == low;
    }

    // ------------------------------------------------------------------ hooks

    /** Neutral game decision; set plan and/or press a button. */
    protected abstract void decide(FightEngine e, Fighter self, Fighter opp, float dist);

    /** Called when the opponent jumps in. */
    protected void antiAir(FightEngine e, Fighter self, Fighter opp, float dist) {
        out.heavy = true;
    }

    /** Called after a normal connects and the combo roll succeeded. */
    protected void comboFollowUp(FightEngine e, Fighter self, Fighter opp) {
        if (self.meter >= 100 && chance(0.6f)) out.superMove = true;
        else out.special1 = true;
    }

    /** Called when the opponent whiffs; return true if we did something. */
    protected boolean punish(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (dist < 105) {
            out.heavy = true;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    protected boolean chance(float p) {
        return rng.nextFloat() < p;
    }

    protected void holdForward(Fighter self) {
        if (self.facingRight) out.right = true;
        else out.left = true;
    }

    protected void holdBack(Fighter self) {
        if (self.facingRight) out.left = true;
        else out.right = true;
    }

    private void applyPlan(Fighter self) {
        if (out.anyButton() || out.up) return;
        if (planHorizontal > 0) holdForward(self);
        else if (planHorizontal < 0) holdBack(self);
        if (planCrouch) out.down = true;
    }

    protected void walkForward() {
        planHorizontal = 1;
    }

    protected void walkBack() {
        planHorizontal = -1;
    }

    protected boolean hard() {
        return difficulty == AIDifficulty.HARD;
    }

    protected boolean opponentBlockingALot(Fighter opp) {
        return opp.state == State.BLOCKSTUN || (opp.holdingBack() && opp.grounded());
    }
}
