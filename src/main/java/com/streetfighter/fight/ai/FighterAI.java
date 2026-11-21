package com.streetfighter.fight.ai;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FightInput;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.Fighter.State;
import com.streetfighter.fight.FighterType;
import com.streetfighter.fight.HitLevel;
import com.streetfighter.fight.InputBuffer.Button;
import com.streetfighter.fight.Move;
import com.streetfighter.fight.Moveset;
import com.streetfighter.fight.Moveset.Role;
import com.streetfighter.fight.Projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mob fighting brain. Produces the same {@link FightInput} a player would (plus direct special/super requests,
 * since mobs don't need to roll their thumbs). Shared reactions — blocking, anti-airs, punishes, parries and
 * hit-confirmed combo routes — scale with the world's {@link AIDifficulty}; subclasses set the personality.
 */
public class FighterAI {
    protected final AIDifficulty difficulty;
    protected final Random rng = new Random();
    protected final FightInput out = new FightInput();

    /** Personality: how much the mob likes to rush in, keep away, or grab. */
    protected float aggression = 0.5f, zoning = 0.2f, grappling = 0.1f;
    /** How much this fighter likes to be airborne (jump-ins, flaps, glides). */
    protected float aerial = 0.1f;
    /** Training dummy: does nothing at all. */
    public boolean passive;

    protected int planHorizontal;
    protected boolean planCrouch;
    protected int decisionTimer;

    private int lastThreatSerial = -1;
    private int lastThreatProjectile = -1;
    private boolean blockingThreat, blockLow, parrying;
    private int blockHold;
    private boolean sawOppJump, antiAirPending;
    private int lastPunishSerial = -1;

    // ---- combo executor
    protected enum StepKind { NORMAL, CROUCH_NORMAL, TARGET, SPECIAL, SUPER, COMMAND }

    protected record Step(StepKind kind, Button button, int special, int strength, int superLevel) {
        static Step n(Button b) { return new Step(StepKind.NORMAL, b, -1, 0, 0); }
        static Step c(Button b) { return new Step(StepKind.CROUCH_NORMAL, b, -1, 0, 0); }
        static Step t(Button b) { return new Step(StepKind.TARGET, b, -1, 0, 0); }
        static Step sp(int idx, int strength) { return new Step(StepKind.SPECIAL, null, idx, strength, 0); }
        static Step sup(int level) { return new Step(StepKind.SUPER, null, -1, 0, level); }
        static Step cmd(int idx) { return new Step(StepKind.COMMAND, null, idx, 0, 0); }
    }

    private List<Step> combo;
    private int comboIndex;
    private int comboSerial;
    private int releaseFrame;

    protected FighterAI(AIDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    public static FighterAI create(FighterType type, AIDifficulty difficulty) {
        return switch (type) {
            case SKELETON -> new SkeletonAI(difficulty);
            case VILLAGER -> new VillagerAI(difficulty);
            case PIG -> new PigAI(difficulty);
            case CHICKEN -> new ChickenAI(difficulty);
            case ALCHEMIST -> new AlchemistAI(difficulty);
            case GOAT -> new GoatAI(difficulty);
            case OAK -> new OakAI(difficulty);
            default -> new BrawlerAI(difficulty);
        };
    }

    protected boolean hard() {
        return difficulty == AIDifficulty.HARD;
    }

    protected boolean chance(float p) {
        return rng.nextFloat() < p;
    }

    public final FightInput think(FightEngine e, Fighter self, Fighter opp) {
        out.clear();
        if (passive || self.state == State.KO || self.state == State.VICTORY) return out;
        float dist = Math.abs(opp.x - self.x);

        if (self.state == State.HITSTUN || self.state == State.KNOCKDOWN || self.state == State.WALL_SPLAT
                || self.state == State.CRUMPLE) {
            blockingThreat = false;
            combo = null;
            if (self.throwTechWindow == 7 && chance(hard() ? 0.45f : 0.12f)) out.throwReq = true;
            return out;
        }
        if (self.state == State.BLOCKSTUN) {
            holdBack(self);
            out.down = blockLow;
            return out;
        }
        if (self.state == State.PARRY) {
            if (parrying && parryStillUseful(e, self, opp, dist)) out.parry = true;
            else parrying = false;
            return out;
        }

        // ---------------- combo in progress
        if (combo != null) {
            if (continueCombo(e, self, opp)) return out;
        }

        if (self.state == State.ATTACK) {
            // spontaneous cancel when a normal connects and no combo is running
            Move m = self.move;
            if (m != null && self.moveConnected && m.cancel != Move.Cancel.NONE && lastPunishSerial != self.moveSerial
                    && chance(difficulty.comboChance * 0.5f)) {
                lastPunishSerial = self.moveSerial;
                pressSpecial(self, comboEnder(self.moves), 1);
            }
            return out;
        }
        if (self.state == State.JUMP) {
            // flap again near the apex, glide over the opponent's head
            if (self.airJumpsLeft > 0 && self.vy < 1.5f && self.stateAge > 8 && chance(aerial * 0.12f)) {
                out.airJump = true;
                if (dist > 90) holdForward(self);
                return out;
            }
            if (self.moves.glideFrames > 0 && self.vy < 0 && (dist > 110 || chance(aerial))) out.up = true;
            if (!self.airAttackUsed && dist < 120 && self.vy < 4 && chance(0.3f)) {
                out.hk = chance(0.6f);
                out.hp = !out.hk;
                startCombo(self, jumpInRoute(self.moves, self), 1);
            }
            return out;
        }
        if (!self.actionable()) return out;

        if (handleThreats(e, self, opp, dist)) return out;

        // anti-air
        boolean oppJumping = !opp.grounded() && (opp.state == State.JUMP || (opp.state == State.ATTACK && opp.move != null && opp.move.air));
        if (oppJumping && !sawOppJump) {
            sawOppJump = true;
            antiAirPending = chance(difficulty.antiAirChance);
        } else if (!oppJumping) {
            sawOppJump = false;
            antiAirPending = false;
        }
        if (antiAirPending && dist < 200 && opp.vy < 3) {
            antiAirPending = false;
            antiAir(e, self, opp, dist);
            return out;
        }

        // punish a whiffed move
        if (opp.state == State.ATTACK && opp.move != null && opp.curFrame >= opp.activeEnd && !opp.moveConnected
                && lastPunishSerial != opp.moveSerial) {
            lastPunishSerial = opp.moveSerial;
            if (chance(difficulty.punishChance) && dist < 130) {
                startCombo(self, punishRoute(self.moves, self), 0);
                return out;
            }
        }

        // throw a turtle
        if (dist < 85 && opp.grounded() && opp.state == State.BLOCKSTUN && chance(hard() ? 0.25f : 0.08f)) {
            if (chance(grappling * 2 + 0.2f) && self.moves.specialIndex(Role.GRAB) >= 0) {
                pressSpecial(self, self.moves.specialIndex(Role.GRAB), hard() ? 2 : 1);
            } else {
                out.throwReq = true;
            }
            return out;
        }

        if (--decisionTimer <= 0) {
            decisionTimer = difficulty.reactionFrames + rng.nextInt(difficulty.reactionFrames + 1);
            planHorizontal = 0;
            planCrouch = false;
            if (chance(difficulty.idleChance)) decisionTimer += 20;
            else decide(e, self, opp, dist);
        }
        applyPlan(self);
        return out;
    }

    // ------------------------------------------------------------------ neutral

    /** Default neutral game, tuned by personality. Subclasses may override. */
    protected void decide(FightEngine e, Fighter self, Fighter opp, float dist) {
        Moveset ms = self.moves;
        int proj = ms.specialIndex(Role.PROJECTILE);
        int rush = ms.specialIndex(Role.RUSH);
        int low = ms.specialIndex(Role.LOW);
        int over = ms.specialIndex(Role.OVERHEAD);
        int grab = ms.specialIndex(Role.GRAB);
        float closeRange = (self.hurtWidth() + opp.hurtWidth()) / 2f + 45;

        if (self.meter >= 100 && dist < 240 && chance(hard() ? 0.06f : 0.03f)) {
            out.superLevel = Math.min(3, self.bars());
            return;
        }
        int buff = ms.specialIndex(Role.BUFF);
        if (buff >= 0 && dist > 220 && !buffed(self) && chance(0.3f)) {
            pressSpecial(self, buff, self.meter >= 150 && chance(0.3f) ? 3 : rng.nextInt(3));
            return;
        }
        int trap = ms.specialIndex(Role.TRAP);
        if (trap >= 0 && dist > 150 && clouds(e, self) == 0 && chance(zoning * 0.35f)) {
            pressSpecial(self, trap, dist > 300 ? 2 : dist > 220 ? 1 : 0);
            return;
        }
        if (aerial > 0.3f && dist > 120 && dist < 320 && chance(aerial * 0.3f)) {
            out.up = true;
            planHorizontal = 1;
            return;
        }
        if (dist < closeRange) {
            float r = rng.nextFloat();
            if (r < 0.25f) startCombo(self, lightRoute(ms, self), 0);
            else if (r < 0.40f) startCombo(self, targetRoute(ms, self), 0);
            else if (r < 0.40f + grappling) {
                if (grab >= 0) pressSpecial(self, grab, rng.nextInt(3));
                else out.throwReq = true;
            } else if (r < 0.6f + grappling) out.throwReq = chance(0.4f);
            else if (r < 0.75f && over >= 0) pressSpecial(self, over, 1);
            else if (r < 0.85f) {
                planHorizontal = -1;
            } else {
                out.down = true;
                out.mk = true;
            }
            return;
        }
        if (dist < 200) {
            float r = rng.nextFloat();
            if (r < 0.2f) { out.down = true; out.mk = true; startCombo(self, List.of(Step.c(Button.MK), Step.sp(comboEnder(ms), 1)), 1); }
            else if (r < 0.3f && low >= 0) pressSpecial(self, low, rng.nextInt(3));
            else if (r < 0.38f && proj >= 0 && shots(e, self) == 0) pressSpecial(self, proj, rng.nextInt(3));
            else if (r < 0.45f && rush >= 0) pressSpecial(self, rush, chance(0.15f) && self.meter >= 50 ? 3 : 1);
            else if (r < 0.5f + aggression * 0.1f) { out.up = true; planHorizontal = 1; }
            else if (r < 0.55f && chance(0.3f)) out.driveImpact = true;
            else if (chance(aggression)) planHorizontal = 1;
            else planHorizontal = -1;
            return;
        }
        // far
        if (proj >= 0 && shots(e, self) == 0 && chance(0.25f + zoning)) {
            pressSpecial(self, proj, self.meter >= 150 && chance(0.2f) ? 3 : rng.nextInt(3));
        } else if (rush >= 0 && chance(aggression * 0.25f)) {
            pressSpecial(self, rush, 2);
        } else if (chance(aggression + 0.2f)) {
            planHorizontal = 1;
        } else if (chance(zoning)) {
            planHorizontal = -1;
        }
    }

    /** Live non-cloud projectiles. */
    protected static int shots(FightEngine e, Fighter self) {
        int n = 0;
        for (Projectile p : e.projectiles) if (p.owner == self.index && !p.spec.aura) n++;
        return n;
    }

    protected static int clouds(FightEngine e, Fighter self) {
        int n = 0;
        for (Projectile p : e.projectiles) if (p.owner == self.index && p.spec.aura) n++;
        return n;
    }

    protected static boolean buffed(Fighter self) {
        for (com.streetfighter.fight.StatusEffect fx : com.streetfighter.fight.StatusEffect.ALL) {
            if (fx.buff && self.has(fx)) return true;
        }
        return false;
    }

    protected void antiAir(FightEngine e, Fighter self, Fighter opp, float dist) {
        int aa = self.moves.specialIndex(Role.ANTI_AIR);
        if (aa >= 0 && (hard() || chance(0.6f))) {
            pressSpecial(self, aa, self.meter >= 50 && hard() && chance(0.3f) ? 3 : 2);
        } else {
            out.down = true;
            out.hp = true;
        }
    }

    // ------------------------------------------------------------------ combo routes

    protected int comboEnder(Moveset ms) {
        int i = ms.specialIndex(Role.RUSH);
        if (i < 0) i = ms.specialIndex(Role.ANTI_AIR);
        if (i < 0) i = ms.specialIndex(Role.LOW);
        if (i < 0) i = 0;
        return i;
    }

    protected List<Step> lightRoute(Moveset ms, Fighter self) {
        List<Step> r = new ArrayList<>(List.of(Step.c(Button.LK), Step.c(Button.LP), Step.sp(comboEnder(ms), 1)));
        if (hard() && self.meter >= 100) r.add(Step.sup(1));
        return r;
    }

    protected List<Step> targetRoute(Moveset ms, Fighter self) {
        List<Step> r = new ArrayList<>();
        if (!ms.targetCombos.isEmpty()) {
            Moveset.TargetCombo tc = ms.targetCombos.get(0);
            r.add(Step.n(buttonOf(ms, tc.from())));
            r.add(Step.t(tc.button()));
        } else {
            r.add(Step.n(Button.MP));
        }
        r.add(Step.sp(comboEnder(ms), 2));
        if (self.meter >= 200 && hard()) r.add(Step.sup(2));
        else if (self.meter >= 100 && chance(0.6f)) r.add(Step.sup(1));
        return r;
    }

    protected List<Step> punishRoute(Moveset ms, Fighter self) {
        List<Step> r = new ArrayList<>(List.of(Step.n(Button.HP)));
        int aa = ms.specialIndex(Role.ANTI_AIR);
        r.add(Step.sp(aa >= 0 ? aa : comboEnder(ms), self.meter >= 50 && hard() ? 3 : 2));
        if (self.meter >= 150 && hard()) r.add(Step.sup(Math.min(3, self.bars())));
        return r;
    }

    protected List<Step> jumpInRoute(Moveset ms, Fighter self) {
        return new ArrayList<>(List.of(Step.n(Button.HP), Step.sp(comboEnder(ms), 2)));
    }

    private Button buttonOf(Moveset ms, Move m) {
        for (var en : ms.stand.entrySet()) if (en.getValue() == m) return en.getKey();
        return Button.MP;
    }

    /** Starts a combo. {@code startIndex} > 0 means step 0 was already pressed by the caller. */
    protected void startCombo(Fighter self, List<Step> route, int startIndex) {
        combo = route;
        comboIndex = startIndex;
        comboSerial = self.moveSerial;
        if (startIndex == 0 && !route.isEmpty()) {
            press(self, route.get(0));
            comboIndex = 1;
        }
        releaseFrame = 0;
    }

    private boolean continueCombo(FightEngine e, Fighter self, Fighter opp) {
        if (comboIndex >= combo.size()) {
            combo = null;
            return false;
        }
        // release buttons for one frame so the next press registers as fresh
        if (releaseFrame > 0) {
            releaseFrame--;
            return true;
        }
        Move m = self.move;
        boolean inMove = self.state == State.ATTACK && m != null;
        if (inMove && !self.moveConnected && self.curFrame >= self.activeEnd) {
            combo = null; // whiffed: drop it
            return false;
        }
        boolean confirmBlocked = opp.state == State.BLOCKSTUN;
        if (inMove && self.moveConnected && self.curFrame >= m.startup) {
            // hit-confirm: on Hard, stop pressing on block (except light chains)
            if (confirmBlocked && hard() && comboIndex >= 2) {
                combo = null;
                return false;
            }
            if (!hard() && chance(0.12f)) { // normal AI drops combos sometimes
                combo = null;
                return false;
            }
            press(self, combo.get(comboIndex++));
            releaseFrame = 1;
            return true;
        }
        if (self.actionable() && self.moveSerial != comboSerial) {
            // a link: press as soon as we can act again
            press(self, combo.get(comboIndex++));
            releaseFrame = 1;
            return true;
        }
        return inMove;
    }

    private void press(Fighter self, Step s) {
        switch (s.kind()) {
            case NORMAL, TARGET -> setButton(s.button());
            case CROUCH_NORMAL -> {
                out.down = true;
                setButton(s.button());
            }
            case SPECIAL -> {
                out.special = s.special();
                out.strength = s.strength() == 3 && self.meter < 50 ? 2 : s.strength();
            }
            case SUPER -> out.superLevel = s.superLevel();
            case COMMAND -> out.commandNormal = s.special();
        }
        comboSerial = self.moveSerial;
    }

    private void setButton(Button b) {
        switch (b) {
            case LP -> out.lp = true;
            case MP -> out.mp = true;
            case HP -> out.hp = true;
            case LK -> out.lk = true;
            case MK -> out.mk = true;
            case HK -> out.hk = true;
            case SP -> out.sp = true;
        }
    }

    protected void pressSpecial(Fighter self, int index, int strength) {
        if (index < 0) return;
        out.special = index;
        out.strength = strength == 3 && self.meter < 50 ? 2 : strength;
    }

    // ------------------------------------------------------------------ defense

    private boolean handleThreats(FightEngine e, Fighter self, Fighter opp, float dist) {
        boolean meleeThreat = opp.isAttackingWithin(hard() ? 6 : 3) && opp.move.projectile == null
                && dist < opp.move.reach() + 70;
        if (meleeThreat && lastThreatSerial != opp.moveSerial) {
            lastThreatSerial = opp.moveSerial;
            boolean grab = opp.move.level == HitLevel.GRAB;
            blockingThreat = !grab && chance(difficulty.blockChance);
            blockLow = guessLow(opp.move.level);
            blockHold = 0;
            if (grab && hard() && chance(0.4f)) { // jump out of throws
                out.up = true;
                planHorizontal = -1;
                return true;
            }
            if (blockingThreat && hard() && chance(0.12f) && opp.move.level != HitLevel.GRAB) {
                parrying = true;
                out.parry = true;
                return true;
            }
        }
        Projectile incoming = null;
        for (Projectile p : e.projectiles) {
            if (p.owner == self.index || p.delay > 40 || p.spec.aura) continue;
            float dx = self.x - p.x;
            boolean approaching = p.spec.fromSky ? Math.abs(dx) < 160 : (Math.signum(dx) == Math.signum(p.vx) && Math.abs(dx) < 220);
            if (approaching) {
                incoming = p;
                break;
            }
        }
        if (incoming != null && lastThreatProjectile != incoming.id) {
            lastThreatProjectile = incoming.id;
            float r = rng.nextFloat();
            if (hard() && r < 0.25f) {
                parrying = true;
                out.parry = true;
                return true;
            } else if (r < difficulty.blockChance + 0.1f) {
                blockingThreat = true;
                blockLow = false;
                blockHold = 0;
            } else if (!incoming.spec.fromSky && chance(0.4f)) {
                out.up = true;
                planHorizontal = 1;
                blockingThreat = false;
                return true;
            }
        }
        boolean live = meleeThreat || incoming != null;
        if (blockingThreat && (live || blockHold < 8)) {
            blockHold++;
            holdBack(self);
            out.down = blockLow;
            return true;
        }
        blockingThreat = false;
        return false;
    }

    private boolean parryStillUseful(FightEngine e, Fighter self, Fighter opp, float dist) {
        if (opp.state == State.ATTACK && dist < 160) return true;
        for (Projectile p : e.projectiles) if (p.owner != self.index && Math.abs(p.x - self.x) < 200) return true;
        return self.stateAge < 10;
    }

    private boolean guessLow(HitLevel level) {
        if (hard()) return level == HitLevel.LOW || (level == HitLevel.MID && rng.nextFloat() < 0.5f);
        boolean correct = rng.nextFloat() < 0.6f;
        return correct == (level == HitLevel.LOW);
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
}
