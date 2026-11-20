package com.streetfighter.fight;

import com.streetfighter.fight.ai.FighterAI;
import com.streetfighter.fight.Fighter.State;
import com.streetfighter.fight.FightEvent.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The whole 2D fighting game simulation. Runs at a fixed 60 steps per second and knows nothing about
 * rendering; the screen reads its state and drains {@link #events}.
 */
public final class FightEngine {
    public static final float ARENA_WIDTH = 1300;
    public static final float VIEW_WIDTH = 720;
    public static final float WALL = 30;
    public static final float MAX_SEPARATION = VIEW_WIDTH - 100;
    public static final int ROUND_FRAMES = 99 * 60;
    public static final int ROUNDS_TO_WIN = 2;
    public static final int MAX_ROUNDS = 7;

    public enum Phase { INTRO, FIGHT_CALL, FIGHTING, KO, ROUND_END, MATCH_END }

    /** SF4-style damage scaling by hit number in a combo (1st, 2nd, 3rd...). */
    private static final float[] SCALING = {1f, 1f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f};

    /** Arena walls in arena units (may be narrower than ARENA_WIDTH when terrain gets in the way). */
    public final float arenaMin, arenaMax;
    private final float startSeparation;

    public final Fighter player;
    public final Fighter enemy;
    private final Fighter[] fighters;
    private final FighterAI ai;
    public final AIDifficulty difficulty;
    public final Random random = new Random();

    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<FightEvent> events = new ArrayList<>();

    public Phase phase = Phase.INTRO;
    public int phaseTimer;
    public int round = 1;
    public int timer = ROUND_FRAMES;
    public int hitstop;
    public int freeze;
    public int freezeOwner = -1;
    public float shake;
    public int roundWinner = -2;
    public int matchWinner = -1;
    public boolean timeOver;
    private int projectileIds;
    private final FightInput neutral = new FightInput();

    public FightEngine(Fighter player, Fighter enemy, FighterAI ai, AIDifficulty difficulty) {
        this(player, enemy, ai, difficulty, 0, ARENA_WIDTH, 340);
    }

    /**
     * @param arenaMin        left wall (arena units)
     * @param arenaMax        right wall
     * @param startSeparation distance between the fighters when each round starts
     */
    public FightEngine(Fighter player, Fighter enemy, FighterAI ai, AIDifficulty difficulty,
                       float arenaMin, float arenaMax, float startSeparation) {
        this.arenaMin = arenaMin;
        this.arenaMax = arenaMax;
        this.startSeparation = Math.min(startSeparation, Math.max(120, arenaMax - arenaMin - 2 * WALL - 20));
        this.player = player;
        this.enemy = enemy;
        this.fighters = new Fighter[]{player, enemy};
        this.ai = ai;
        this.difficulty = difficulty;
        resetRound();
    }

    public Fighter other(Fighter f) {
        return f == player ? enemy : player;
    }

    public Fighter fighter(int index) {
        return fighters[index];
    }

    public boolean isOver() {
        return phase == Phase.MATCH_END;
    }

    public boolean playerWon() {
        return matchWinner == player.index;
    }

    // ------------------------------------------------------------------ round flow

    private void resetRound() {
        float mid = (arenaMin + arenaMax) / 2f;
        player.x = mid - startSeparation / 2f;
        enemy.x = mid + startSeparation / 2f;
        for (Fighter f : fighters) {
            f.y = 0;
            f.vx = f.vy = 0;
            f.hp = f.maxHp;
            f.displayHp = f.maxHp;
            f.state = State.IDLE;
            f.move = null;
            f.launched = false;
            f.invuln = 0;
            f.comboTaken = 0;
            f.comboShown = 0;
            f.chainCount = 0;
            f.clearMotion();
            f.throwTechWindow = 0;
            f.lastCounter = null;
        }
        player.facingRight = true;
        enemy.facingRight = false;
        projectiles.clear();
        timer = ROUND_FRAMES;
        hitstop = 0;
        freeze = 0;
        timeOver = false;
        roundWinner = -2;
        phase = Phase.INTRO;
        phaseTimer = 0;
    }

    public void step(FightInput playerInput) {
        phaseTimer++;
        switch (phase) {
            case INTRO -> {
                if (phaseTimer == 1) events.add(new FightEvent(Type.ROUND, -1, 0, 0, "ROUND " + round, null, false));
                simulate(neutral, neutral);
                if (phaseTimer >= 80) setPhase(Phase.FIGHT_CALL);
            }
            case FIGHT_CALL -> {
                if (phaseTimer == 1) events.add(FightEvent.of(Type.FIGHT, -1, 0, 0));
                simulate(neutral, neutral);
                if (phaseTimer >= 40) setPhase(Phase.FIGHTING);
            }
            case FIGHTING -> {
                FightInput aiInput = ai.think(this, enemy, player);
                simulate(playerInput, aiInput);
                if (freeze <= 0 && hitstop <= 0 && timer > 0) timer--;
                if (player.hp <= 0 || enemy.hp <= 0) {
                    setPhase(Phase.KO);
                    events.add(FightEvent.of(Type.KO, -1, 0, 0));
                } else if (timer <= 0) {
                    timeOver = true;
                    setPhase(Phase.KO);
                    events.add(FightEvent.of(Type.TIME, -1, 0, 0));
                }
            }
            case KO -> {
                // dramatic slow motion for the first second
                if (phaseTimer > 60 || phaseTimer % 3 == 0) simulate(neutral, neutral);
                if (phaseTimer >= 130 && player.grounded() && enemy.grounded()) finishRound();
                else if (phaseTimer >= 240) finishRound();
            }
            case ROUND_END -> {
                simulate(neutral, neutral);
                if (phaseTimer >= 110) {
                    if (matchWinner >= 0) {
                        setPhase(Phase.MATCH_END);
                        events.add(new FightEvent(Type.MATCH_WIN, matchWinner, 0, 0, null, null, false));
                    } else {
                        round++;
                        resetRound();
                    }
                }
            }
            case MATCH_END -> simulate(neutral, neutral);
        }
        updateHealthBars();
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTimer = 0;
    }

    private void finishRound() {
        float pRatio = player.hp / player.maxHp;
        float eRatio = enemy.hp / enemy.maxHp;
        if (player.hp <= 0 && enemy.hp <= 0) roundWinner = -1;
        else if (enemy.hp <= 0) roundWinner = player.index;
        else if (player.hp <= 0) roundWinner = enemy.index;
        else if (Math.abs(pRatio - eRatio) < 0.0001f) roundWinner = -1;
        else roundWinner = pRatio > eRatio ? player.index : enemy.index;

        if (roundWinner == -1) {
            player.roundsWon++;
            enemy.roundsWon++;
            events.add(FightEvent.of(Type.DRAW, -1, 0, 0));
        } else {
            Fighter w = fighters[roundWinner];
            w.roundsWon++;
            events.add(FightEvent.of(Type.ROUND_WIN, roundWinner, w.x, w.y));
            if (w.hp >= w.maxHp) events.add(FightEvent.of(Type.PERFECT, roundWinner, w.x, w.y));
            if (w.state != State.KO) {
                w.state = State.VICTORY;
                w.move = null;
                w.vx = 0;
            }
        }

        if (player.roundsWon >= ROUNDS_TO_WIN && player.roundsWon > enemy.roundsWon) matchWinner = player.index;
        else if (enemy.roundsWon >= ROUNDS_TO_WIN && enemy.roundsWon > player.roundsWon) matchWinner = enemy.index;
        else if (round >= MAX_ROUNDS) {
            matchWinner = player.roundsWon > enemy.roundsWon ? player.index : enemy.index;
        }
        setPhase(Phase.ROUND_END);
    }

    private void updateHealthBars() {
        for (Fighter f : fighters) {
            if (f.displayHp > f.hp) {
                boolean stunned = f.state == State.HITSTUN || f.state == State.BLOCKSTUN;
                if (!stunned || f.displayHpDelay++ > 40) f.displayHp = Math.max(f.hp, f.displayHp - f.maxHp * 0.006f);
            } else {
                f.displayHp = f.hp;
                f.displayHpDelay = 0;
            }
            if (f.comboTimer > 0) f.comboTimer--;
        }
    }

    // ------------------------------------------------------------------ simulation

    private void simulate(FightInput in1, FightInput in2) {
        if (shake > 0) shake = Math.max(0, shake - 0.5f);
        if (freeze > 0) {
            freeze--;
            return;
        }
        if (hitstop > 0) {
            hitstop--;
            return;
        }
        control(player, in1);
        control(enemy, in2);
        for (Fighter f : fighters) advance(f);
        resolveMeleeHits();
        updateProjectiles();
        resolveBodies();
        for (Fighter f : fighters) {
            if (f.actionable() && f.comboTaken > 0) f.comboTaken = 0;
        }
    }

    private void control(Fighter f, FightInput in) {
        f.input.left = in.left;
        f.input.right = in.right;
        f.input.up = in.up;
        f.input.down = in.down;
        f.input.light = in.light;
        f.input.heavy = in.heavy;
        f.input.special1 = in.special1;
        f.input.special2 = in.special2;
        f.input.superMove = in.superMove;
        f.buffer.update(in, f.facingRight);
        Fighter opp = other(f);
        InputBuffer b = f.buffer;

        switch (f.state) {
            case IDLE, WALK, CROUCH -> {
                if (Math.abs(opp.x - f.x) > 1) f.facingRight = opp.x > f.x;
                Move chosen = pickSpecial(f);
                if (chosen == null && b.together(InputBuffer.Button.LIGHT, InputBuffer.Button.HEAVY, 2)
                        && Math.abs(opp.x - f.x) < 95 && !in.down) {
                    chosen = Movesets.THROW;
                    b.consume(InputBuffer.Button.LIGHT);
                    b.consume(InputBuffer.Button.HEAVY);
                }
                if (chosen == null) {
                    if (b.buffered(InputBuffer.Button.HEAVY)) {
                        chosen = in.down ? f.moves.crouchHeavy() : f.moves.heavy();
                        b.consume(InputBuffer.Button.HEAVY);
                    } else if (b.buffered(InputBuffer.Button.LIGHT)) {
                        chosen = in.down ? f.moves.crouchLight() : f.moves.light();
                        b.consume(InputBuffer.Button.LIGHT);
                    }
                }
                if (chosen != null) {
                    startMove(f, chosen);
                } else if (in.up) {
                    f.state = State.JUMP;
                    f.stateAge = 0;
                    f.vy = f.stats.jumpVy();
                    f.vx = f.holdingForward() ? f.dir() * f.stats.jumpVx()
                            : f.holdingBack() ? -f.dir() * f.stats.jumpVx() : 0;
                    f.airAttackUsed = false;
                    events.add(FightEvent.of(Type.JUMP, f.index, f.x, f.y));
                } else if (in.down) {
                    setState(f, State.CROUCH);
                    f.vx = 0;
                } else if (f.holdingForward()) {
                    setState(f, State.WALK);
                    f.vx = f.dir() * f.stats.walkSpeed();
                } else if (f.holdingBack()) {
                    setState(f, State.WALK);
                    f.vx = -f.dir() * f.stats.backSpeed();
                } else {
                    setState(f, State.IDLE);
                    f.vx = 0;
                }
            }
            case JUMP -> {
                if (!f.airAttackUsed && (b.buffered(InputBuffer.Button.LIGHT) || b.buffered(InputBuffer.Button.HEAVY))) {
                    f.airAttackUsed = true;
                    boolean heavy = b.buffered(InputBuffer.Button.HEAVY);
                    b.consume(InputBuffer.Button.LIGHT);
                    b.consume(InputBuffer.Button.HEAVY);
                    startMove(f, heavy ? f.moves.airHeavy() : f.moves.airLight());
                }
            }
            case ATTACK -> {
                Move m = f.move;
                if (m != null && f.moveConnected && m.cancelable && !m.special && f.curFrame >= m.startup) {
                    Move sp = pickSpecial(f);
                    if (sp != null) {
                        startMove(f, sp);
                    } else if (m.chainable && b.buffered(InputBuffer.Button.HEAVY)) {
                        b.consume(InputBuffer.Button.HEAVY);
                        startMove(f, in.down ? f.moves.crouchHeavy() : f.moves.heavy());
                    } else if (m.chainable && f.chainCount < 3 && b.buffered(InputBuffer.Button.LIGHT)) {
                        b.consume(InputBuffer.Button.LIGHT);
                        f.chainCount++;
                        startMove(f, in.down ? f.moves.crouchLight() : f.moves.light());
                    }
                }
            }
            case HITSTUN -> {
                // throw tech: press light + heavy right as you get grabbed
                if (f.throwTechWindow > 0) {
                    f.throwTechWindow--;
                    if (b.together(InputBuffer.Button.LIGHT, InputBuffer.Button.HEAVY, 3)) techThrow(f);
                }
            }
            default -> {
            }
        }
    }

    /** Super > special 2 (incl. 623) > special 1, either from dedicated buttons or motions + a button. */
    private Move pickSpecial(Fighter f) {
        if (!f.grounded()) return null;
        InputBuffer b = f.buffer;
        boolean attackButton = b.buffered(InputBuffer.Button.LIGHT) || b.buffered(InputBuffer.Button.HEAVY);
        Move result = null;
        if (f.meter >= 100 && f.moves.superMove() != null
                && (b.buffered(InputBuffer.Button.SUPER) || (attackButton && b.motion(InputBuffer.Motion.QCF2)))) {
            result = f.moves.superMove();
        } else if (b.buffered(InputBuffer.Button.SPECIAL2)
                || (attackButton && (b.motion(InputBuffer.Motion.DP) || b.motion(InputBuffer.Motion.QCB)))) {
            result = f.moves.special2();
        } else if (b.buffered(InputBuffer.Button.SPECIAL1) || (attackButton && b.motion(InputBuffer.Motion.QCF))) {
            result = f.moves.special1();
        }
        if (result != null) {
            for (InputBuffer.Button btn : InputBuffer.Button.values()) b.consume(btn);
        }
        return result;
    }

    private void techThrow(Fighter victim) {
        Fighter thrower = other(victim);
        victim.throwTechWindow = 0;
        victim.hp = Math.min(victim.maxHp, victim.hp + Movesets.THROW.damage * 0.8f); // refund most of it
        victim.launched = false;
        victim.vy = 0;
        int away = victim.x >= thrower.x ? 1 : -1;
        victim.vx = away * 7;
        victim.state = State.BLOCKSTUN;
        victim.stateTimer = 14;
        thrower.state = State.BLOCKSTUN;
        thrower.stateTimer = 14;
        thrower.move = null;
        thrower.vx = -away * 7;
        victim.comboTaken = 0;
        hitstop = 8;
        events.add(new FightEvent(Type.CLASH, victim.index, (victim.x + thrower.x) / 2f, 70, "TECH!", null, true));
    }

    private void setState(Fighter f, State s) {
        if (f.state != s) {
            f.state = s;
            f.stateAge = 0;
        }
    }

    private void startMove(Fighter f, Move m) {
        boolean chain = f.state == State.ATTACK;
        f.move = m;
        f.moveFrame = 0;
        f.curFrame = -1;
        f.activeEnd = m.untilLanding ? Integer.MAX_VALUE : m.startup + m.active;
        f.hitsDone = 0;
        f.lastHitFrame = 0;
        f.moveConnected = false;
        f.moveSerial++;
        f.state = State.ATTACK;
        f.stateAge = 0;
        if (!chain) f.chainCount = 0;
        if (!m.air && f.grounded()) f.vx = 0;
        if (m.isSuper) {
            f.meter -= 100;
            freeze = 45;
            freezeOwner = f.index;
            events.add(new FightEvent(Type.SUPER, f.index, f.x, f.y, m.callout, null, true));
        } else {
            events.add(new FightEvent(Type.MOVE_START, f.index, f.x, f.y, m.callout, null, m.special));
        }
        f.clearMotion();
    }

    private void toIdle(Fighter f) {
        f.state = State.IDLE;
        f.stateAge = 0;
        f.move = null;
        f.launched = false;
        f.vx = 0;
        f.chainCount = 0;
    }

    private void advance(Fighter f) {
        f.stateAge++;
        if (f.invuln > 0) f.invuln--;
        switch (f.state) {
            case ATTACK -> advanceMove(f);
            case HITSTUN -> {
                f.stateTimer--;
                if (f.grounded()) {
                    f.vx *= 0.82f;
                    if (f.stateTimer <= 0 && !f.launched) toIdle(f);
                }
            }
            case BLOCKSTUN -> {
                f.stateTimer--;
                f.vx *= 0.82f;
                if (f.stateTimer <= 0) toIdle(f);
            }
            case KNOCKDOWN -> {
                f.stateTimer--;
                f.vx *= 0.85f;
                if (f.stateTimer <= 0) {
                    toIdle(f);
                    f.invuln = 10;
                }
            }
            case LANDING -> {
                f.stateTimer--;
                f.vx = 0;
                if (f.stateTimer <= 0) toIdle(f);
            }
            case KO, VICTORY -> {
                if (f.grounded()) f.vx *= 0.88f;
            }
            default -> {
            }
        }

        boolean wasAir = f.y > 0 || f.vy > 0;
        if (wasAir) f.vy -= f.stats.gravity();
        f.x += f.vx;
        f.y += f.vy;
        if (f.y <= 0) {
            f.y = 0;
            if (wasAir && f.vy <= 0) {
                f.vy = 0;
                onLand(f);
            } else {
                f.vy = Math.max(f.vy, 0);
            }
        }
    }

    private void advanceMove(Fighter f) {
        Move m = f.move;
        int fr = f.moveFrame;
        f.curFrame = fr;
        if (m.moveStart >= 0) {
            if (m.moveVy != 0) {
                if (fr == m.moveStart) {
                    f.vy = m.moveVy;
                    f.vx = f.dir() * m.moveVx;
                }
            } else if (fr >= m.moveStart && fr < m.moveEnd && f.curFrame < f.activeEnd) {
                f.vx = f.dir() * m.moveVx;
            } else if (f.grounded()) {
                f.vx *= 0.6f;
            }
        } else if (f.grounded()) {
            f.vx *= 0.6f;
        }
        if (m.projectile != null && fr == m.projectileFrame) spawnProjectiles(f, m.projectile);
        f.moveFrame++;
        long end = (long) f.activeEnd + m.recovery;
        if (f.moveFrame >= end) {
            if (f.grounded()) {
                toIdle(f);
                if (f.input.down) setState(f, State.CROUCH);
            } else {
                f.moveFrame = (int) end;
            }
        }
    }

    private void onLand(Fighter f) {
        switch (f.state) {
            case JUMP -> {
                f.state = State.LANDING;
                f.stateTimer = 4;
                f.vx = 0;
                events.add(FightEvent.of(Type.LAND, f.index, f.x, 0));
            }
            case ATTACK -> {
                Move m = f.move;
                if (m.untilLanding && f.curFrame >= m.startup && f.activeEnd == Integer.MAX_VALUE) {
                    f.activeEnd = f.moveFrame;
                    f.vx = 0;
                    shake = Math.max(shake, 5);
                    events.add(new FightEvent(Type.SLAM, f.index, f.x, 0, null, null, false));
                } else if (m.air) {
                    f.state = State.LANDING;
                    f.stateTimer = 4;
                    f.move = null;
                    f.vx = 0;
                    events.add(FightEvent.of(Type.LAND, f.index, f.x, 0));
                } else if (f.moveFrame >= f.activeEnd + m.recovery) {
                    f.state = State.LANDING;
                    f.stateTimer = m.landingLag;
                    f.move = null;
                    f.vx = 0;
                    events.add(FightEvent.of(Type.LAND, f.index, f.x, 0));
                } else {
                    f.vx = 0;
                }
            }
            case HITSTUN -> {
                if (f.launched) {
                    f.state = State.KNOCKDOWN;
                    f.stateTimer = 48;
                    f.stateAge = 0;
                    f.launched = false;
                    f.vx *= 0.5f;
                    events.add(FightEvent.of(Type.THUD, f.index, f.x, 0));
                } else {
                    f.vx = 0;
                }
            }
            case KO -> {
                f.vx *= 0.4f;
                events.add(FightEvent.of(Type.THUD, f.index, f.x, 0));
            }
            default -> f.vx = 0;
        }
    }

    // ------------------------------------------------------------------ hits

    private record HitInfo(float damage, HitLevel level, int hitstun, int blockstun, float push, float launch,
                           boolean knockdown, boolean special) {
        static HitInfo of(Move m) {
            return new HitInfo(m.damage, m.level, m.hitstun, m.blockstun, m.push, m.launch, m.knockdown, m.special);
        }

        static HitInfo of(ProjectileSpec s) {
            return new HitInfo(s.damage, s.level, s.hitstun, s.blockstun, s.push, s.launch, s.knockdown, true);
        }
    }

    public enum HitResult { MISS, HIT, BLOCK }

    private void resolveMeleeHits() {
        boolean hit1 = meleeOverlaps(player, enemy);
        boolean hit2 = meleeOverlaps(enemy, player);
        // Snapshot both before applying so simultaneous hits trade.
        Move m1 = player.move, m2 = enemy.move;
        if (hit1) applyMelee(player, enemy, m1);
        if (hit2) applyMelee(enemy, player, m2);
    }

    private boolean meleeOverlaps(Fighter a, Fighter d) {
        if (!a.hitboxActive()) return false;
        Move m = a.move;
        float ax0 = a.hitMinX(m), ax1 = a.hitMaxX(m);
        float ay0 = a.y + m.hy0, ay1 = a.y + m.hy1;
        float dx0 = d.x - d.hurtWidth() / 2f, dx1 = d.x + d.hurtWidth() / 2f;
        float dy0 = d.y, dy1 = d.y + d.hurtHeight();
        return ax0 < dx1 && ax1 > dx0 && ay0 < dy1 && ay1 > dy0;
    }

    private void applyMelee(Fighter a, Fighter d, Move m) {
        if (m == null) return;
        float px = (Math.max(a.hitMinX(m), d.x - d.hurtWidth() / 2f) + Math.min(a.hitMaxX(m), d.x + d.hurtWidth() / 2f)) / 2f;
        float py = Math.min(a.y + m.hy1, d.y + d.hurtHeight()) - 15;
        int dirAway = d.x >= a.x ? 1 : -1;
        HitInfo info = HitInfo.of(m);
        boolean last = a.hitsDone + 1 >= m.hits;
        if (m.hits > 1 && !last) {
            // multi-hit moves only knock down / launch on the final hit
            info = new HitInfo(m.damage, m.level, m.hitstun, m.blockstun, m.push, 0, false, m.special);
        }
        HitResult r = applyHit(a, d, info, px, py, dirAway, true);
        if (r == HitResult.MISS) return;
        a.hitsDone++;
        a.lastHitFrame = a.curFrame;
        a.moveConnected = true;
        if (m.stopOnHit) {
            a.activeEnd = Math.min(a.activeEnd, a.curFrame + 1);
            a.vx = 0;
        }
        if (m.level == HitLevel.GRAB && r == HitResult.HIT) {
            // throw the victim over to the far side for flavour
            d.vx = dirAway * 6f;
        }
    }

    private HitResult applyHit(Fighter a, Fighter d, HitInfo h, float px, float py, int dirAway, boolean melee) {
        if (d.invulnerable()) return HitResult.MISS;
        if (d.launched && d.comboTaken >= 7) return HitResult.MISS; // juggle limit
        if (h.level() == HitLevel.GRAB) {
            if (!d.grounded() || d.state == State.HITSTUN || d.state == State.BLOCKSTUN || d.state == State.JUMP) {
                return HitResult.MISS;
            }
        }
        boolean crouch = d.input.down;
        boolean levelOk = switch (h.level()) {
            case HIGH -> !crouch;
            case LOW -> crouch;
            case MID -> true;
            case GRAB -> false;
        };
        boolean blocked = levelOk && d.canBlock() && d.holdingBack();

        if (blocked) {
            float chip = h.special() ? h.damage() * 0.18f * a.damageScale : 0;
            d.hp = Math.max(0, d.hp - chip);
            d.state = State.BLOCKSTUN;
            d.stateAge = 0;
            d.stateTimer = h.blockstun();
            d.move = null;
            d.vx = dirAway * h.push() * 0.9f;
            if (melee && nearWall(d)) a.vx -= dirAway * h.push() * 0.6f;
            a.meter = Math.min(100, a.meter + 2);
            d.meter = Math.min(100, d.meter + 3);
            hitstop = 5;
            events.add(new FightEvent(Type.BLOCK, d.index, px, py, null, null, h.damage() >= 70));
            if (d.hp <= 0) knockOut(d, dirAway);
            return HitResult.BLOCK;
        }

        // Counter hit = interrupting the opponent's startup/active frames, punish counter = hitting their recovery.
        String counter = null;
        if (d.state == State.ATTACK && d.move != null && d.comboTaken == 0) {
            counter = d.curFrame >= d.activeEnd ? "PUNISH COUNTER" : "COUNTER";
        }
        float scale = SCALING[Math.min(d.comboTaken, SCALING.length - 1)];
        boolean superHit = a.move != null && a.move.isSuper;
        if (superHit) scale = Math.max(scale, 0.5f); // supers keep at least 50% damage, like SF
        float dmg = h.damage() * scale * a.damageScale * (counter != null ? 1.2f : 1f);
        d.hp = Math.max(0, d.hp - dmg);
        d.comboTaken++;
        a.comboShown = d.comboTaken;
        a.comboTimer = 90;
        d.wasCrouchingWhenHit = d.crouching();
        d.lastCounter = counter;
        d.move = null;
        d.state = State.HITSTUN;
        d.stateAge = 0;
        d.stateTimer = h.hitstun() + (counter == null ? 0 : counter.startsWith("P") ? 8 : 4);
        d.throwTechWindow = h.level() == HitLevel.GRAB ? 7 : 0;
        boolean air = !d.grounded();
        if (h.knockdown() || air || h.launch() > 0) {
            d.launched = true;
            d.vy = Math.max(h.launch(), air ? 6.5f : 4.5f);
            d.vx = dirAway * Math.max(h.push() * 0.6f, 2.5f);
        } else {
            d.vx = dirAway * h.push();
        }
        if (melee && nearWall(d)) a.vx -= dirAway * h.push() * 0.5f;
        a.meter = Math.min(100, a.meter + 2.5f + h.damage() / 30f);
        d.meter = Math.min(100, d.meter + 3);
        boolean heavy = dmg >= 70 || h.knockdown();
        hitstop = (heavy ? 10 : 7) + (counter != null ? 5 : 0);
        if (dmg >= 100 || counter != null) shake = Math.max(shake, 7);
        if (counter != null && counter.startsWith("P")) a.meter = Math.min(100, a.meter + 8);
        events.add(new FightEvent(Type.HIT, d.index, px, py, counter, null, heavy));
        if (d.hp <= 0) knockOut(d, dirAway);
        return HitResult.HIT;
    }

    private void knockOut(Fighter d, int dirAway) {
        d.hp = 0;
        d.state = State.KO;
        d.stateAge = 0;
        d.move = null;
        d.launched = false;
        d.vy = 9;
        d.vx = dirAway * 5;
        hitstop = 20;
        shake = 10;
    }

    private boolean nearWall(Fighter f) {
        return f.x <= arenaMin + WALL + 6 || f.x >= arenaMax - WALL - 6;
    }

    // ------------------------------------------------------------------ projectiles

    private void spawnProjectiles(Fighter f, ProjectileSpec spec) {
        Fighter opp = other(f);
        for (int i = 0; i < spec.count; i++) {
            Projectile p = new Projectile(projectileIds++, f.index, spec);
            if (spec.fromSky) {
                float offset = 0;
                if (spec.count > 1) {
                    int k = (i + 1) / 2;
                    float side = (i % 2 == 0) ? 1 : -1;
                    offset = side * spec.skySpread * k / ((spec.count + 1) / 2f);
                }
                p.x = clampArena(opp.x + offset);
                p.y = spec.skyHeight;
                p.vx = 0;
                p.vy = spec.vy(0);
                p.delay = i * spec.delayStep + (spec.visual == ProjectileVisual.GOLEM ? 30 : 4);
            } else {
                p.x = f.x + f.dir() * spec.offX;
                p.y = f.y + spec.offY;
                p.vx = f.dir() * spec.vx;
                p.vy = spec.vy(i);
                p.delay = i * spec.delayStep;
            }
            projectiles.add(p);
        }
        events.add(new FightEvent(Type.PROJECTILE, f.index, f.x, f.y, null, spec.visual, false));
    }

    private float clampArena(float x) {
        return Math.max(arenaMin + WALL + 20, Math.min(arenaMax - WALL - 20, x));
    }

    private void updateProjectiles() {
        for (Projectile p : projectiles) {
            if (p.dead) continue;
            if (p.delay > 0) {
                p.delay--;
                continue;
            }
            p.age++;
            if (!p.landed) {
                p.vy -= p.spec.gravity;
                p.x += p.vx;
                p.y += p.vy;
            } else {
                p.landedTimer++;
                if (p.landedTimer > 40) p.dead = true;
            }
            float bottom = p.minY();
            if (!p.landed && bottom <= 0 && p.vy < 0) {
                float floorY = p.bottomAnchored() ? 0 : p.spec.height / 2f;
                if (p.spec.bounce > 0 && p.bounces < 3) {
                    p.y = floorY;
                    p.vy = -p.vy * p.spec.bounce;
                    p.bounces++;
                    events.add(new FightEvent(Type.BOUNCE, p.owner, p.x, 0, null, p.spec.visual, false));
                } else if (p.spec.visual == ProjectileVisual.GOLEM) {
                    p.y = 0;
                    p.vy = 0;
                    p.landed = true;
                    shake = Math.max(shake, 10);
                    events.add(new FightEvent(Type.SLAM, p.owner, p.x, 0, null, p.spec.visual, true));
                } else if (p.spec.visual == ProjectileVisual.PIG) {
                    p.y = 0;
                    p.vy = 0;
                } else {
                    p.dead = true;
                    events.add(new FightEvent(Type.POOF, p.owner, p.x, 0, null, p.spec.visual, false));
                }
            }
            if (p.age > p.spec.lifetime && !p.landed) p.dead = true;
            if (p.x < arenaMin - 400 || p.x > arenaMax + 400) p.dead = true;
            if (p.dead) continue;

            Fighter target = fighters[1 - p.owner];
            if (!p.hasHit && p.hitboxActive() && overlaps(p, target)) {
                int dirAway = p.vx != 0 ? (p.vx > 0 ? 1 : -1) : (target.x >= p.x ? 1 : -1);
                float py = Math.max(target.y + 10, Math.min(p.maxY(), target.y + target.hurtHeight()) - 10);
                HitResult r = applyHit(fighters[p.owner], target, HitInfo.of(p.spec), p.x, py, dirAway, false);
                if (r != HitResult.MISS) {
                    p.hasHit = true;
                    if (!p.spec.durable) p.dead = true;
                }
            }
        }
        // projectile clashes
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile a = projectiles.get(i);
            if (a.dead || a.delay > 0) continue;
            for (int j = i + 1; j < projectiles.size(); j++) {
                Projectile b = projectiles.get(j);
                if (b.dead || b.delay > 0 || a.owner == b.owner) continue;
                if (a.minX() < b.maxX() && a.maxX() > b.minX() && a.minY() < b.maxY() && a.maxY() > b.minY()) {
                    if (a.spec.durable && b.spec.durable) continue;
                    if (!a.spec.durable) a.dead = true;
                    if (!b.spec.durable) b.dead = true;
                    events.add(new FightEvent(Type.CLASH, -1, (a.x + b.x) / 2f, (a.y + b.y) / 2f, null, null, false));
                }
            }
        }
        projectiles.removeIf(p -> p.dead);
    }

    private boolean overlaps(Projectile p, Fighter f) {
        float fx0 = f.x - f.hurtWidth() / 2f, fx1 = f.x + f.hurtWidth() / 2f;
        return p.minX() < fx1 && p.maxX() > fx0 && p.minY() < f.y + f.hurtHeight() && p.maxY() > f.y;
    }

    /** Count of this fighter's projectiles still on screen (used by the AI to avoid spam). */
    public int projectilesOf(Fighter f) {
        int n = 0;
        for (Projectile p : projectiles) if (p.owner == f.index) n++;
        return n;
    }

    // ------------------------------------------------------------------ bodies & bounds

    private void resolveBodies() {
        for (Fighter f : fighters) f.x = Math.max(arenaMin + WALL, Math.min(arenaMax - WALL, f.x));

        float dist = Math.abs(player.x - enemy.x);
        if (dist > MAX_SEPARATION) {
            float excess = (dist - MAX_SEPARATION) / 2f;
            int s = player.x < enemy.x ? 1 : -1;
            player.x += s * excess;
            enemy.x -= s * excess;
        }

        float minDist = (player.hurtWidth() + enemy.hurtWidth()) / 2f;
        float dx = enemy.x - player.x;
        boolean verticalOverlap = player.y < enemy.y + enemy.hurtHeight() * 0.8f && enemy.y < player.y + player.hurtHeight() * 0.8f;
        if (verticalOverlap && Math.abs(dx) < minDist) {
            float overlap = minDist - Math.abs(dx);
            int s = dx > 0 ? 1 : dx < 0 ? -1 : (player.facingRight ? 1 : -1);
            boolean pWall = (s > 0 && player.x <= arenaMin + WALL + 1) || (s < 0 && player.x >= arenaMax - WALL - 1);
            boolean eWall = (s > 0 && enemy.x >= arenaMax - WALL - 1) || (s < 0 && enemy.x <= arenaMin + WALL + 1);
            if (pWall) enemy.x += s * overlap;
            else if (eWall) player.x -= s * overlap;
            else {
                player.x -= s * overlap / 2f;
                enemy.x += s * overlap / 2f;
            }
        }
        for (Fighter f : fighters) f.x = Math.max(arenaMin + WALL, Math.min(arenaMax - WALL, f.x));
    }

    /** Camera left edge in arena units. */
    public float cameraX() {
        float mid = (player.x + enemy.x) / 2f;
        return Math.max(arenaMin, Math.min(arenaMax - VIEW_WIDTH, mid - VIEW_WIDTH / 2f));
    }
}
