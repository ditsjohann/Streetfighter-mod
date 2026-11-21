package com.streetfighter.fight;

import com.streetfighter.fight.Fighter.State;
import com.streetfighter.fight.FightEnvironment.Floor;
import com.streetfighter.fight.FightEnvironment.Hazard;
import com.streetfighter.fight.FightEnvironment.Wall;
import com.streetfighter.fight.FightEvent.Type;
import com.streetfighter.fight.InputBuffer.Button;
import com.streetfighter.fight.InputBuffer.Motion;
import com.streetfighter.fight.ai.FighterAI;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The whole 2D fighting game simulation. Runs at a fixed 60 steps per second and knows nothing about
 * rendering; the client reads its state and drains {@link #events}.
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

    /** SF4-style damage scaling by hit number in a combo. */
    private static final float[] SCALING = {1f, 1f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f};

    public final float arenaMin, arenaMax;
    private final float startSeparation;
    public final FightEnvironment env;

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
    /** Training mode: the dummy refills its health when a combo ends and the timer never runs out. */
    public boolean training;
    private boolean firstAttackDone;
    private int projectileIds;
    /** Total simulated frames (used for render interpolation and effects). */
    public long frame;
    private final FightInput neutral = new FightInput();

    public FightEngine(Fighter player, Fighter enemy, FighterAI ai, AIDifficulty difficulty) {
        this(player, enemy, ai, difficulty, 0, ARENA_WIDTH, 340, FightEnvironment.FLAT);
    }

    public FightEngine(Fighter player, Fighter enemy, FighterAI ai, AIDifficulty difficulty,
                       float arenaMin, float arenaMax, float startSeparation, FightEnvironment env) {
        this.arenaMin = arenaMin;
        this.arenaMax = arenaMax;
        this.env = env;
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
            f.armorLeft = 0;
            f.groundBouncePending = false;
            f.wallSplatPending = false;
            f.clearStatus();
            f.airJumpsLeft = 0;
            f.glideLeft = 0;
            f.gliding = false;
            f.prevX = f.x;
            f.prevY = f.y;
        }
        player.facingRight = true;
        enemy.facingRight = false;
        projectiles.clear();
        timer = ROUND_FRAMES;
        hitstop = 0;
        freeze = 0;
        timeOver = false;
        firstAttackDone = false;
        roundWinner = -2;
        phase = Phase.INTRO;
        phaseTimer = 0;
    }

    public void step(FightInput playerInput) {
        frame++;
        for (Fighter f : fighters) {
            f.prevX = f.x;
            f.prevY = f.y;
        }
        for (Projectile p : projectiles) {
            p.prevX = p.x;
            p.prevY = p.y;
        }
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
                if (freeze <= 0 && hitstop <= 0 && timer > 0 && !training) timer--;
                if (training) {
                    if (enemy.actionable() && enemy.comboTaken == 0) enemy.hp = enemy.maxHp;
                    player.meter = Math.max(player.meter, 300);
                    if (enemy.hp <= 0) enemy.hp = 1;
                }
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
        else if (round >= MAX_ROUNDS) matchWinner = player.roundsWon > enemy.roundsWon ? player.index : enemy.index;
        setPhase(Phase.ROUND_END);
    }

    private void updateHealthBars() {
        for (Fighter f : fighters) {
            if (f.displayHp > f.hp) {
                boolean stunned = f.state == State.HITSTUN || f.state == State.BLOCKSTUN || f.state == State.WALL_SPLAT
                        || f.state == State.CRUMPLE;
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
        // inputs are buffered every frame, even during hitstop / super freeze (like SF)
        boolean frozen = freeze > 0 || hitstop > 0;
        feed(player, in1, frozen);
        feed(enemy, in2, frozen);
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
            if (f.actionable()) {
                if (f.comboTaken > 0) f.comboTaken = 0;
                f.actionableAge++;
            } else if (f.state != State.ATTACK) {
                f.actionableAge = 0;
                f.stunnedBefore = f.state == State.HITSTUN || f.state == State.BLOCKSTUN || f.state == State.KNOCKDOWN
                        || f.state == State.WALL_SPLAT || f.state == State.CRUMPLE;
            }
            environment(f);
            tickStatus(f);
        }
    }

    /** Counts down status effects; poison and regeneration tick like their vanilla potions. */
    private void tickStatus(Fighter f) {
        boolean live = phase == Phase.FIGHTING && f.state != State.KO;
        for (StatusEffect e : StatusEffect.ALL) {
            int left = f.status[e.ordinal()];
            if (left <= 0) continue;
            f.status[e.ordinal()] = left - 1;
            if (!live) continue;
            if (e == StatusEffect.POISON && left % 20 == 0) {
                f.hp = Math.max(1, f.hp - 7);
                events.add(FightEvent.text(Type.STATUS_TICK, f.index, f.x, f.y + f.hurtHeight() * 0.6f, e.name()));
            } else if (e == StatusEffect.REGEN && left % 15 == 0 && f.hp < f.maxHp) {
                f.hp = Math.min(f.maxHp, f.hp + 8);
                events.add(FightEvent.text(Type.STATUS_TICK, f.index, f.x, f.y + f.hurtHeight() * 0.6f, e.name()));
            }
        }
    }

    private void giveStatus(Fighter f, StatusEffect e, int frames) {
        if (e == null || frames <= 0) return;
        boolean fresh = !f.has(e);
        f.give(e, frames);
        if (fresh) events.add(FightEvent.text(Type.STATUS, f.index, f.x, f.y + f.hurtHeight() + 20, e.name()));
    }

    // ------------------------------------------------------------------ input -> moves

    private void feed(Fighter f, FightInput in, boolean frozen) {
        f.input.left = in.left;
        f.input.right = in.right;
        f.input.up = in.up;
        f.input.down = in.down;
        f.input.lp = in.lp;
        f.input.mp = in.mp;
        f.input.hp = in.hp;
        f.input.lk = in.lk;
        f.input.mk = in.mk;
        f.input.hk = in.hk;
        f.input.sp = in.sp;
        f.buffer.update(in, f.facingRight, frozen);
    }

    private void control(Fighter f, FightInput in) {
        Fighter opp = other(f);
        InputBuffer b = f.buffer;
        float speedMul = floorSpeed(f) * f.statusSpeed();

        switch (f.state) {
            case IDLE, WALK, CROUCH -> {
                if (Math.abs(opp.x - f.x) > 1) f.facingRight = opp.x > f.x;
                Move chosen = chooseGroundMove(f, in, opp);
                boolean parryHeld = in.parry || (b.held(Button.MP) && b.held(Button.MK) && chosen == null);
                if (chosen != null) {
                    startMove(f, chosen);
                } else if (parryHeld) {
                    f.state = State.PARRY;
                    f.stateAge = 0;
                    f.vx = 0;
                } else if (in.up) {
                    f.state = State.JUMP;
                    f.stateAge = 0;
                    f.vy = f.stats.jumpVy();
                    f.vx = (f.holdingForward() ? f.dir() * f.stats.jumpVx() : f.holdingBack() ? -f.dir() * f.stats.jumpVx() : 0) * speedMul;
                    f.airAttackUsed = false;
                    f.wallJumpUsed = false;
                    f.airJumpsLeft = f.moves.airJumps;
                    f.glideLeft = f.moves.glideFrames;
                    events.add(FightEvent.of(Type.JUMP, f.index, f.x, f.y));
                } else if (in.down) {
                    setState(f, State.CROUCH);
                    f.vx = icy(f) ? f.vx * 0.96f : 0;
                } else if (f.holdingForward()) {
                    setState(f, State.WALK);
                    walk(f, f.dir() * f.stats.walkSpeed() * speedMul);
                } else if (f.holdingBack()) {
                    setState(f, State.WALK);
                    walk(f, -f.dir() * f.stats.backSpeed() * speedMul);
                } else {
                    setState(f, State.IDLE);
                    f.vx = icy(f) ? f.vx * 0.96f : 0;
                }
            }
            case JUMP -> {
                // wall jump: kick off the arena wall by pressing up again while touching it
                boolean atLeft = f.x <= arenaMin + WALL + 12, atRight = f.x >= arenaMax - WALL - 12;
                if (!f.wallJumpUsed && (atLeft || atRight) && b.upPressedThisFrame() && f.vy < 6
                        && env.wall(atRight) != Wall.NONE) {
                    f.wallJumpUsed = true;
                    f.vy = f.stats.jumpVy() * 0.95f;
                    f.vx = (atRight ? -1 : 1) * f.stats.jumpVx() * 1.5f;
                    f.airAttackUsed = false;
                    events.add(FightEvent.of(Type.WALL_JUMP, f.index, f.x, f.y));
                    break;
                }
                // air jump (Chicken flap): a fresh up press while airborne
                if (f.airJumpsLeft > 0 && f.stateAge > 5 && (b.upPressedThisFrame() || in.airJump)) {
                    f.airJumpsLeft--;
                    f.vy = f.stats.jumpVy() * 0.82f;
                    float sp = f.stats.jumpVx() * f.statusSpeed();
                    f.vx = f.holdingForward() ? f.dir() * sp : f.holdingBack() ? -f.dir() * sp : f.vx * 0.5f;
                    f.airAttackUsed = false;
                    f.stateAge = 0;
                    events.add(FightEvent.of(Type.AIR_JUMP, f.index, f.x, f.y));
                    break;
                }
                if (!f.airAttackUsed) {
                    Button btn = strongestPressed(b, false);
                    if (btn != null && f.moves.air.containsKey(btn)) {
                        f.airAttackUsed = true;
                        consumeAll(b);
                        startMove(f, f.moves.air.get(btn));
                    }
                }
            }
            case ATTACK -> {
                Move m = f.move;
                if (m == null || !f.moveConnected || f.curFrame < m.startup) break;
                Move next = null;
                if (m.cancel == Move.Cancel.SUPER || m.cancel == Move.Cancel.SPECIAL || m.cancel == Move.Cancel.CHAIN) {
                    next = pickSuper(f, in);
                }
                if (next == null && (m.cancel == Move.Cancel.SPECIAL || m.cancel == Move.Cancel.CHAIN)) {
                    next = pickSpecial(f, in);
                }
                if (next == null) {
                    // target combos, then chains
                    Button btn = strongestPressed(b, null);
                    if (btn != null) {
                        Move tc = f.moves.targetFrom(m, btn);
                        if (tc != null) next = tc;
                        else if (m.cancel == Move.Cancel.CHAIN && f.chainCount < 3) {
                            boolean down = in.down;
                            Move cand = (down ? f.moves.crouch : f.moves.stand).get(btn);
                            if (cand != null && (btn.strength() == 0 || m.cancel == Move.Cancel.CHAIN)) {
                                next = cand;
                                f.chainCount++;
                            }
                        }
                    }
                }
                if (next != null) {
                    consumeAll(b);
                    startMove(f, next);
                }
            }
            case PARRY -> {
                boolean still = in.parry || (b.held(Button.MP) && b.held(Button.MK));
                if (!still || f.stateAge > 90) {
                    f.state = State.LANDING;
                    f.stateTimer = f.parryRecovery > 0 ? 4 : 12;
                    f.parryRecovery = 0;
                }
            }
            case HITSTUN -> {
                if (f.throwTechWindow > 0) {
                    f.throwTechWindow--;
                    if (b.together(Button.LP, Button.LK, 3) || in.throwReq) techThrow(f);
                }
            }
            default -> {
            }
        }
    }

    private void walk(Fighter f, float target) {
        if (icy(f)) f.vx += (target - f.vx) * 0.08f;
        else f.vx = target;
    }

    private boolean icy(Fighter f) {
        return f.grounded() && env.floor(f.x) == Floor.ICE;
    }

    private float floorSpeed(Fighter f) {
        if (!f.grounded()) return 1f;
        return switch (env.floor(f.x)) {
            case SLOW -> 0.6f;
            case WATER -> 0.7f;
            case SNOW -> 0.9f;
            default -> 1f;
        };
    }

    private static final Button[] PUNCHES = {Button.HP, Button.MP, Button.LP};
    private static final Button[] KICKS = {Button.HK, Button.MK, Button.LK};

    /** Strongest freshly-pressed attack button; {@code kicks} null = either group. */
    private Button strongestPressed(InputBuffer b, Boolean kicks) {
        Button best = null;
        for (Button btn : new Button[]{Button.HP, Button.HK, Button.MP, Button.MK, Button.LP, Button.LK}) {
            if (kicks != null && btn.isKick() != kicks) continue;
            if (b.buffered(btn)) {
                if (best == null || btn.strength() > best.strength()) best = btn;
            }
        }
        return best;
    }

    private void consumeAll(InputBuffer b) {
        for (Button btn : Button.values()) b.consume(btn);
    }

    private Move chooseGroundMove(Fighter f, FightInput in, Fighter opp) {
        InputBuffer b = f.buffer;
        Move m = pickSuper(f, in);
        if (m == null && (in.driveImpact || b.together(Button.HP, Button.HK, 2))) m = f.moves.driveImpact;
        if (m == null) {
            boolean close = Math.abs(opp.x - f.x) < (f.hurtWidth() + opp.hurtWidth()) / 2f + 50;
            boolean throwIn = in.throwReq || in.backThrowReq || b.together(Button.LP, Button.LK, 2);
            if (throwIn && close && !in.down) {
                m = (in.backThrowReq || f.holdingBack()) ? f.moves.backThrow : f.moves.forwardThrow;
            }
        }
        if (m == null) m = pickSpecial(f, in);
        if (m == null && in.commandNormal >= 0 && in.commandNormal < f.moves.commandNormals.size()) {
            m = f.moves.commandNormals.get(in.commandNormal).move();
        }
        if (m == null) {
            int dir = b.currentDir();
            for (Moveset.CommandNormal cn : f.moves.commandNormals) {
                if (cn.dir() == dir && b.buffered(cn.button())) {
                    m = cn.move();
                    break;
                }
            }
        }
        if (m == null) {
            Button btn = strongestPressed(b, null);
            if (btn != null) m = (in.down ? f.moves.crouch : f.moves.stand).get(btn);
        }
        if (m != null) consumeAll(b);
        return m;
    }

    private Move pickSuper(Fighter f, FightInput in) {
        if (!f.grounded()) return null;
        InputBuffer b = f.buffer;
        int level = 0;
        if (in.superLevel > 0) level = in.superLevel;
        else {
            boolean kickPressed = b.freshestAge(true) <= InputBuffer.BUTTON_BUFFER;
            boolean punchPressed = b.freshestAge(false) <= InputBuffer.BUTTON_BUFFER;
            if (kickPressed && b.motion(Motion.QCF2)) level = 3;
            else if ((punchPressed || kickPressed) && b.motion(Motion.QCB2)) level = 2;
            else if (punchPressed && b.motion(Motion.QCF2)) level = 1;
            else if (b.buffered(Button.SP) && b.buffered(Button.HP)) level = 1;
            else if (b.buffered(Button.SP) && b.buffered(Button.HK)) level = f.meter >= 300 ? 3 : 2;
        }
        // fall back to the strongest affordable level when asked for more than we have
        while (level > 0 && f.meter < level * 100) level--;
        if (level == 0) return null;
        Moveset.Super s = f.moves.superOf(level);
        return s == null ? null : s.move();
    }

    private Move pickSpecial(Fighter f, FightInput in) {
        if (!f.grounded()) return null;
        InputBuffer b = f.buffer;
        if (in.special >= 0) {
            Moveset.Special s = f.moves.special(in.special);
            if (s == null) return null;
            int st = in.strength == 3 && f.meter < 50 ? 2 : in.strength;
            return s.variant(st);
        }
        // Modern: SP + direction picks the special (medium strength, EX with SP + light).
        if (b.buffered(Button.SP)) {
            int dir = b.currentDir();
            int want = dir == 6 || dir == 9 ? 6 : dir == 2 || dir == 1 || dir == 3 ? 2 : dir == 4 || dir == 7 ? 4 : 5;
            for (Moveset.Special s : f.moves.specials) {
                if (s.modernDir() == want) {
                    boolean ex = (b.buffered(Button.LP) || b.buffered(Button.LK)) && f.meter >= 50;
                    return s.variant(ex ? 3 : 1);
                }
            }
        }
        // Classic: motion + button. Longer motions are checked first (623 and 63214 before 236/214).
        Moveset.Special best = null;
        int bestRank = -1;
        for (Moveset.Special s : f.moves.specials) {
            if (b.freshestAge(s.kick()) > InputBuffer.BUTTON_BUFFER) continue;
            if (!b.motion(s.motion())) continue;
            int rank = switch (s.motion()) {
                case HCB -> 3;
                case DP -> 2;
                default -> 1;
            };
            if (rank > bestRank) {
                best = s;
                bestRank = rank;
            }
        }
        if (best == null) return null;
        Button[] group = best.kick() ? KICKS : PUNCHES;
        int pressedCount = 0, strongest = -1;
        for (Button btn : group) {
            if (b.buffered(btn)) {
                pressedCount++;
                strongest = Math.max(strongest, btn.strength());
            }
        }
        int strength = pressedCount >= 2 && f.meter >= 50 ? 3 : Math.max(0, strongest);
        return best.variant(strength);
    }

    private void setState(Fighter f, State s) {
        if (f.state != s) {
            f.state = s;
            f.stateAge = 0;
        }
    }

    private void startMove(Fighter f, Move m) {
        boolean chain = f.state == State.ATTACK;
        f.reversal = !chain && f.stunnedBefore && f.actionableAge <= 3 && (m.special || m.kind == Move.Kind.DRIVE_IMPACT);
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
        f.armorLeft = m.armorHits;
        if (!chain) f.chainCount = 0;
        if (!m.air && f.grounded() && !icy(f)) f.vx = 0;
        if (m.meterCost > 0) f.meter = Math.max(0, f.meter - m.meterCost);
        if (m.isSuper()) {
            freeze = 40 + m.superLevel * 8;
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
        if (!icy(f)) f.vx = 0;
        f.chainCount = 0;
        f.armorLeft = 0;
    }

    // ------------------------------------------------------------------ per-frame physics

    private void advance(Fighter f) {
        f.stateAge++;
        if (f.invuln > 0) f.invuln--;
        float friction = icy(f) ? 0.97f : 0.82f;
        switch (f.state) {
            case ATTACK -> advanceMove(f);
            case HITSTUN -> {
                f.stateTimer--;
                if (f.grounded()) {
                    f.vx *= friction;
                    if (f.stateTimer <= 0 && !f.launched) toIdle(f);
                }
            }
            case BLOCKSTUN -> {
                f.stateTimer--;
                f.vx *= friction;
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
                f.vx = icy(f) ? f.vx * 0.96f : 0;
                if (f.stateTimer <= 0) toIdle(f);
            }
            case CRUMPLE -> {
                f.stateTimer--;
                f.vx *= 0.8f;
                if (f.stateTimer <= 0) {
                    f.state = State.KNOCKDOWN;
                    f.stateTimer = 40;
                    f.stateAge = 0;
                    events.add(FightEvent.of(Type.THUD, f.index, f.x, 0));
                }
            }
            case WALL_SPLAT -> {
                f.stateTimer--;
                f.vx = 0;
                f.vy = 0;
                if (f.stateTimer <= 0) {
                    f.state = State.HITSTUN;
                    f.stateTimer = 10;
                    f.launched = true;
                    f.vx = (f.x < (arenaMin + arenaMax) / 2f ? 1 : -1) * 2f;
                    f.vy = 1;
                }
                return; // stuck to the wall: no gravity
            }
            case KO, VICTORY -> {
                if (f.grounded()) f.vx *= 0.88f;
            }
            default -> {
            }
        }

        boolean wasAir = f.y > 0 || f.vy > 0;
        if (wasAir) f.vy -= f.stats.gravity();
        // glide: holding up while falling slows the descent (Chicken)
        f.gliding = false;
        if (wasAir && f.glideLeft > 0 && f.vy < -1.5f && f.input.up
                && (f.state == State.JUMP || (f.state == State.ATTACK && f.move != null && f.move.air))) {
            f.vy = -1.5f;
            f.glideLeft--;
            f.gliding = true;
        }
        f.x += f.vx;
        f.y += f.vy;
        // walls: launched fighters bounce or splat; everyone else stops
        float left = arenaMin + WALL, right = arenaMax - WALL;
        if (f.x < left || f.x > right) {
            boolean rightWall = f.x > right;
            f.x = Math.max(left, Math.min(right, f.x));
            if ((f.state == State.HITSTUN && f.launched) || f.state == State.KO) wallImpact(f, rightWall);
        }
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

    private void wallImpact(Fighter f, boolean rightWall) {
        Wall wall = env.wall(rightWall);
        if (wall == Wall.NONE) return;
        float speed = Math.abs(f.vx);
        if (f.wallSplatPending && f.state != State.KO) {
            f.wallSplatPending = false;
            f.state = State.WALL_SPLAT;
            f.stateTimer = 32;
            f.stateAge = 0;
            f.launched = false;
            f.y = Math.max(f.y, 40);
            hitstop = Math.max(hitstop, 10);
            shake = Math.max(shake, 8);
            events.add(new FightEvent(Type.WALL_SPLAT, f.index, f.x, f.y + 60, rightWall ? "R" : "L", null, true));
            wallHazard(f, wall, rightWall, 45);
            return;
        }
        if (speed > 2.5f) {
            f.vx = -f.vx * 0.35f;
            f.vy = Math.max(f.vy, 3);
            events.add(new FightEvent(Type.WALL_BOUNCE, f.index, f.x, f.y + 50, rightWall ? "R" : "L", null, speed > 6));
            wallHazard(f, wall, rightWall, 25);
        }
    }

    private void wallHazard(Fighter f, Wall wall, boolean rightWall, float bonus) {
        if (wall == Wall.SPIKES) {
            f.hp = Math.max(0, f.hp - bonus * 1.5f);
            events.add(new FightEvent(Type.HAZARD, f.index, f.x, f.y + 60, "SPIKES", null, true));
            if (f.hp <= 0) knockOut(f, rightWall ? -1 : 1);
        } else if (wall == Wall.BREAKABLE) {
            f.hp = Math.max(0, f.hp - bonus);
            events.add(new FightEvent(Type.WALL_BREAK, f.index, f.x, f.y + 60, rightWall ? "R" : "L", null, true));
            if (f.hp <= 0) knockOut(f, rightWall ? -1 : 1);
        }
    }

    private void environment(Fighter f) {
        if (!f.grounded() || f.state == State.KO) return;
        Hazard h = env.hazard(f.x);
        if (h == Hazard.NONE) {
            f.hazardTimer = 0;
            return;
        }
        if (++f.hazardTimer % 30 == 0 && phase == Phase.FIGHTING) {
            float dmg = h == Hazard.FIRE ? 14 : 10;
            f.hp = Math.max(1, f.hp - dmg);
            events.add(new FightEvent(Type.HAZARD, f.index, f.x, 20, h == Hazard.FIRE ? "FIRE" : "SPIKES", null, false));
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
                f.vx = f.dir() * m.moveVx * floorSpeed(f);
            } else if (f.grounded()) {
                f.vx *= icy(f) ? 0.95f : 0.6f;
            }
        } else if (f.grounded()) {
            f.vx *= icy(f) ? 0.95f : 0.6f;
        }
        if (m.armorHits > 0 && fr >= m.armorEnd) f.armorLeft = 0;
        if (m.projectile != null && fr == m.projectileFrame) spawnProjectiles(f, m.projectile);
        if (fr == m.startup) for (StatusEffect e : m.selfEffects) giveStatus(f, e, m.selfEffectFrames);
        // cinematic supers keep the victim pinned in front of the attacker
        if (m.lockOnHit && f.moveConnected) {
            Fighter d = other(f);
            if (d.state == State.HITSTUN && d.hp > 0) {
                d.x = f.x + f.dir() * ((f.hurtWidth() + d.hurtWidth()) / 2f + 8);
                d.vx = 0;
                d.stateTimer = Math.max(d.stateTimer, 12);
                if (f.hitsDone < m.hits) {
                    d.launched = false;
                    d.vy = 0;
                    d.y = Math.max(0, d.y * 0.8f);
                }
            }
        }
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
                if (!icy(f)) f.vx = 0;
                events.add(FightEvent.of(Type.LAND, f.index, f.x, 0));
            }
            case ATTACK -> {
                Move m = f.move;
                if (m.untilLanding && f.curFrame >= m.startup && f.activeEnd == Integer.MAX_VALUE) {
                    f.activeEnd = f.moveFrame;
                    f.vx = 0;
                    shake = Math.max(shake, 5);
                    events.add(new FightEvent(Type.SLAM, f.index, f.x, 0, null, null, m.isSuper()));
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
                    if (f.groundBouncePending) {
                        f.groundBouncePending = false;
                        f.vy = 8;
                        f.vx *= 0.6f;
                        shake = Math.max(shake, 6);
                        events.add(FightEvent.of(Type.GROUND_BOUNCE, f.index, f.x, 0));
                        return;
                    }
                    f.state = State.KNOCKDOWN;
                    f.stateTimer = 48;
                    f.stateAge = 0;
                    f.launched = false;
                    f.wallSplatPending = false;
                    f.vx *= 0.5f;
                    events.add(FightEvent.of(Type.THUD, f.index, f.x, 0));
                    Hazard h = env.hazard(f.x);
                    if (h != Hazard.NONE) {
                        f.hp = Math.max(0, f.hp - 30);
                        events.add(new FightEvent(Type.HAZARD, f.index, f.x, 10, h == Hazard.FIRE ? "FIRE" : "SPIKES", null, true));
                        if (f.hp <= 0) knockOut(f, 1);
                    }
                } else {
                    f.vx = 0;
                }
            }
            case KO -> {
                f.vx *= 0.4f;
                events.add(FightEvent.of(Type.THUD, f.index, f.x, 0));
            }
            default -> f.vx = icy(f) ? f.vx : 0;
        }
    }

    // ------------------------------------------------------------------ hits

    private record HitInfo(float damage, HitLevel level, int hitstun, int blockstun, float push, float launch,
                           boolean knockdown, boolean special, boolean wallSplat, boolean groundBounce,
                           boolean crumple, int strength, boolean superHit, boolean projectile,
                           StatusEffect effect, int effectFrames) {
        static HitInfo of(Move m, boolean last) {
            return new HitInfo(m.damage, m.level, m.hitstun, m.blockstun, m.push, last ? m.launch : 0,
                    last && m.knockdown, m.special, last && m.wallSplat, last && m.groundBounce, m.crumple,
                    m.strength, m.isSuper(), false, m.hitEffect, m.hitEffectFrames);
        }

        static HitInfo of(ProjectileSpec s, boolean last) {
            return new HitInfo(s.damage, s.level, s.hitstun, s.blockstun, s.push, last ? s.launch : 0,
                    last && s.knockdown, true, last && s.wallSplat, false, false, 1, false, true,
                    s.effect, s.effectFrames);
        }
    }

    public enum HitResult { MISS, HIT, BLOCK, PARRY, ARMOR }

    private void resolveMeleeHits() {
        boolean hit1 = meleeOverlaps(player, enemy);
        boolean hit2 = meleeOverlaps(enemy, player);
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
        boolean last = a.hitsDone + 1 >= m.hits;
        HitInfo info = HitInfo.of(m, last);
        // after a cinematic grab connects, follow-up hits are plain strikes
        if (m.lockOnHit && a.hitsDone > 0 && info.level == HitLevel.GRAB) {
            info = new HitInfo(info.damage, HitLevel.MID, info.hitstun, info.blockstun, info.push, info.launch,
                    info.knockdown, info.special, info.wallSplat, info.groundBounce, false, info.strength, true, false,
                    info.effect, info.effectFrames);
        }
        if (m.backThrow && m.level == HitLevel.GRAB) dirAway = -dirAway;
        HitResult r = applyHit(a, d, info, px, py, dirAway, true);
        if (r == HitResult.MISS) return;
        a.hitsDone++;
        a.lastHitFrame = a.curFrame;
        a.moveConnected = true;
        if (r == HitResult.PARRY || r == HitResult.ARMOR) return;
        if (m.stopOnHit) {
            a.activeEnd = Math.min(a.activeEnd, a.curFrame + 1);
            a.vx = 0;
        }
        if (m.level == HitLevel.GRAB && r == HitResult.HIT && !m.lockOnHit) {
            if (m.backThrow) {
                // swap sides
                float ax = a.x;
                a.x = d.x;
                d.x = ax;
                a.facingRight = !a.facingRight;
            }
            d.vx = dirAway * 6f;
        }
    }

    private HitResult applyHit(Fighter a, Fighter d, HitInfo h, float px, float py, int dirAway, boolean melee) {
        if (d.invulnerable()) return HitResult.MISS;
        if (!melee && d.state == State.ATTACK && d.move != null && d.curFrame >= d.move.projInvulnStart
                && d.curFrame < d.move.projInvulnEnd) return HitResult.MISS;
        if (d.launched && d.comboTaken >= 8) return HitResult.MISS; // juggle limit
        if (h.level == HitLevel.GRAB) {
            boolean stunned = d.state == State.HITSTUN || d.state == State.BLOCKSTUN || d.state == State.WALL_SPLAT
                    || d.state == State.CRUMPLE;
            if (!d.grounded() || stunned || d.state == State.JUMP) return HitResult.MISS;
        }

        // Drive Parry: absorbs strikes and projectiles (not throws)
        if (d.state == State.PARRY && h.level != HitLevel.GRAB) {
            boolean perfect = d.stateAge <= 3;
            d.meter = Math.min(Fighter.MAX_METER, d.meter + (perfect ? 20 : 6));
            d.parryRecovery = 1;
            d.stateAge = Math.min(d.stateAge, 60);
            if (melee) a.vx -= dirAway * 2f;
            if (perfect && melee) {
                hitstop = 22;
                a.activeEnd = Math.min(a.activeEnd, a.curFrame + 1);
                a.moveFrame = Math.max(a.moveFrame, a.activeEnd);
                a.state = State.LANDING;
                a.stateTimer = 22;
                a.move = null;
                events.add(FightEvent.of(Type.PERFECT_PARRY, d.index, px, py));
            } else {
                hitstop = 6;
                events.add(FightEvent.of(Type.PARRY, d.index, px, py));
            }
            return HitResult.PARRY;
        }
        // Armor (Drive Impact, EX moves): absorb the hit, take a little damage
        if (d.state == State.ATTACK && d.armorLeft > 0 && h.level != HitLevel.GRAB && !h.superHit) {
            d.armorLeft--;
            d.hp = Math.max(1, d.hp - h.damage * 0.3f);
            hitstop = 8;
            events.add(FightEvent.of(Type.ARMOR, d.index, px, py));
            return HitResult.ARMOR;
        }

        boolean crouch = d.input.down;
        boolean levelOk = switch (h.level) {
            case HIGH -> !crouch;
            case LOW -> crouch;
            case MID -> true;
            case GRAB -> false;
        };
        boolean blocked = levelOk && d.canBlock() && d.holdingBack();

        if (blocked) {
            float chip = h.special ? h.damage * 0.18f * a.damageScale * strengthMul(a) : 0;
            if (h.effect != null && h.projectile) giveStatus(d, h.effect, h.effectFrames / 2);
            d.hp = Math.max(0, d.hp - chip);
            d.state = State.BLOCKSTUN;
            d.stateAge = 0;
            d.stateTimer = h.blockstun;
            d.move = null;
            d.vx = dirAway * h.push * 0.9f;
            if (melee && nearWall(d)) a.vx -= dirAway * h.push * 0.6f;
            a.meter = Math.min(Fighter.MAX_METER, a.meter + 2);
            d.meter = Math.min(Fighter.MAX_METER, d.meter + 3);
            hitstop = 6 + h.strength * 2;
            // Drive Impact blocked in the corner still splats (like SF6)
            if (h.crumple && nearWall(d)) {
                d.state = State.WALL_SPLAT;
                d.stateTimer = 26;
                events.add(new FightEvent(Type.WALL_SPLAT, d.index, d.x, d.y + 60, "", null, true));
            }
            events.add(new FightEvent(Type.BLOCK, d.index, px, py, null, null, h.damage >= 70));
            if (d.hp <= 0) knockOut(d, dirAway);
            return HitResult.BLOCK;
        }

        String counter = null;
        if (d.state == State.ATTACK && d.move != null && d.comboTaken == 0) {
            counter = d.curFrame >= d.activeEnd ? "PUNISH COUNTER" : "COUNTER";
        }
        float scale = SCALING[Math.min(d.comboTaken, SCALING.length - 1)];
        if (h.superHit) scale = Math.max(scale, 0.5f);
        float dmg = h.damage * scale * a.damageScale * strengthMul(a) * (counter != null ? 1.2f : 1f);
        giveStatus(d, h.effect, h.effectFrames);
        d.hp = Math.max(0, d.hp - dmg);
        d.comboTaken++;
        a.comboShown = d.comboTaken;
        a.comboTimer = 90;
        d.wasCrouchingWhenHit = d.crouching();
        d.lastCounter = counter;
        d.lastHitLevel = h.level;
        d.lastHitHeavy = dmg >= 70 || h.knockdown;
        d.move = null;
        boolean airborneOrJuggled = !d.grounded() || d.state == State.WALL_SPLAT || d.state == State.CRUMPLE;
        d.state = State.HITSTUN;
        d.stateAge = 0;
        d.stateTimer = h.hitstun + (counter == null ? 0 : counter.startsWith("P") ? 8 : 4);
        d.throwTechWindow = h.level == HitLevel.GRAB ? 7 : 0;

        if (h.crumple && d.grounded() && !nearWall(d)) {
            d.state = State.CRUMPLE;
            d.stateTimer = 62;
            d.vx = dirAway * 2;
            events.add(FightEvent.of(Type.CRUMPLE, d.index, d.x, 60));
        } else if (h.knockdown || airborneOrJuggled || h.launch > 0) {
            d.launched = true;
            d.vy = Math.max(h.launch, airborneOrJuggled ? 6.5f : 4.5f);
            d.vx = dirAway * Math.max(h.push * 0.6f, 2.5f);
            if (h.wallSplat || h.crumple) {
                d.wallSplatPending = true;
                d.vx = dirAway * Math.max(9f, h.push);
            }
            if (h.groundBounce) d.groundBouncePending = true;
        } else {
            d.vx = dirAway * h.push;
        }
        if (melee && nearWall(d)) a.vx -= dirAway * h.push * 0.5f;
        a.meter = Math.min(Fighter.MAX_METER, a.meter + 3 + h.damage / 25f);
        d.meter = Math.min(Fighter.MAX_METER, d.meter + 3);
        boolean heavy = dmg >= 70 || h.knockdown;
        hitstop = (h.superHit ? 12 : 7 + h.strength * 2) + (counter != null ? 5 : 0) + (h.crumple ? 8 : 0);
        if (dmg >= 100 || counter != null) shake = Math.max(shake, 7);
        if (counter != null && counter.startsWith("P")) a.meter = Math.min(Fighter.MAX_METER, a.meter + 15);
        events.add(new FightEvent(Type.HIT, d.index, px, py, counter, null, heavy));
        if (!firstAttackDone) {
            firstAttackDone = true;
            events.add(FightEvent.text(Type.CALLOUT, a.index, px, py, "FIRST ATTACK"));
        }
        if (a.reversal && a.move != null && a.hitsDone == 0) {
            events.add(FightEvent.text(Type.CALLOUT, a.index, px, py, "REVERSAL"));
        }
        if (d.hp <= 0) knockOut(d, dirAway);
        return HitResult.HIT;
    }

    private static float strengthMul(Fighter a) {
        return a.has(StatusEffect.STRENGTH) ? 1.25f : 1f;
    }

    private void knockOut(Fighter d, int dirAway) {
        if (training) {
            d.hp = Math.max(1, d.hp);
            return;
        }
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

    private void techThrow(Fighter victim) {
        Fighter thrower = other(victim);
        victim.throwTechWindow = 0;
        victim.hp = Math.min(victim.maxHp, victim.hp + 100);
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
        events.add(new FightEvent(Type.TECH, victim.index, (victim.x + thrower.x) / 2f, 70, "TECH!", null, true));
    }

    public boolean nearWall(Fighter f) {
        return f.x <= arenaMin + WALL + 8 || f.x >= arenaMax - WALL - 8;
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
            p.prevX = p.x;
            p.prevY = p.y;
            projectiles.add(p);
        }
        events.add(new FightEvent(Type.PROJECTILE, f.index, f.x, f.y, null, spec.visual, false));
    }

    private float clampArena(float x) {
        return Math.max(arenaMin + WALL + 20, Math.min(arenaMax - WALL - 20, x));
    }

    private void explode(Projectile p) {
        if (p.exploded) return;
        p.exploded = true;
        p.landed = true;
        p.landedTimer = 0;
        p.vx = p.vy = 0;
        p.hasHit = false;
        p.hitsDone = 0;
        if (p.y < 75) p.y = 75;
        shake = Math.max(shake, 9);
        events.add(new FightEvent(Type.EXPLOSION, p.owner, p.x, p.y, null, p.spec.visual, true));
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
                if (p.landedTimer > (p.exploded ? 14 : 40)) p.dead = true;
            }
            float bottom = p.minY();
            if (!p.landed && bottom <= 0 && p.vy < 0) {
                float floorY = p.bottomAnchored() ? 0 : p.spec.height / 2f;
                if (p.spec.explodes) {
                    p.y = floorY;
                    explode(p);
                } else if (p.spec.bounce > 0 && p.bounces < 3) {
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
                } else if (p.bottomAnchored()) {
                    p.y = 0;
                    p.vy = 0;
                } else {
                    p.dead = true;
                    events.add(new FightEvent(Type.POOF, p.owner, p.x, 0, null, p.spec.visual, false));
                }
            }
            if (p.age > p.spec.lifetime && !p.landed) {
                if (p.spec.explodes) explode(p);
                else p.dead = true;
            }
            if (p.x < arenaMin - 400 || p.x > arenaMax + 400) p.dead = true;
            if (p.dead) continue;

            Fighter target = fighters[1 - p.owner];
            if (p.spec.aura) {
                auraTick(p, target);
                continue;
            }
            boolean canHit = p.hitsDone < Math.max(1, p.spec.hits) && (p.hitsDone == 0 || p.age - p.lastHitAge >= p.spec.hitInterval);
            if (p.exploded) canHit = !p.hasHit;
            if (canHit && p.hitboxActive() && overlaps(p, target)) {
                if (p.spec.explodes && !p.exploded) {
                    explode(p);
                    continue;
                }
                int dirAway = p.vx != 0 ? (p.vx > 0 ? 1 : -1) : (target.x >= p.x ? 1 : -1);
                float py = Math.max(target.y + 10, Math.min(p.maxY(), target.y + target.hurtHeight()) - 10);
                boolean last = p.hitsDone + 1 >= Math.max(1, p.spec.hits) || p.exploded;
                HitResult r = applyHit(fighters[p.owner], target, HitInfo.of(p.spec, last), p.x, py, dirAway, false);
                if (r != HitResult.MISS) {
                    p.hitsDone++;
                    p.lastHitAge = p.age;
                    if (p.exploded || p.hitsDone >= Math.max(1, p.spec.hits)) {
                        p.hasHit = true;
                        if (!p.spec.durable && !p.exploded) p.dead = true;
                    }
                }
            }
        }
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile a = projectiles.get(i);
            if (a.dead || a.delay > 0 || a.spec.aura) continue;
            for (int j = i + 1; j < projectiles.size(); j++) {
                Projectile b = projectiles.get(j);
                if (b.dead || b.delay > 0 || a.owner == b.owner || b.spec.aura) continue;
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

    /** Lingering clouds: unblockable tick damage plus their effect, without hitstun. */
    private void auraTick(Projectile p, Fighter target) {
        if (phase != Phase.FIGHTING || target.state == State.KO || target.invulnerable()) return;
        if (p.age - p.lastHitAge < p.spec.hitInterval || !overlaps(p, target)) return;
        p.lastHitAge = p.age;
        p.hitsDone++;
        Fighter owner = fighters[p.owner];
        target.hp = Math.max(1, target.hp - p.spec.damage * owner.damageScale * strengthMul(owner));
        giveStatus(target, p.spec.effect, p.spec.effectFrames);
        owner.meter = Math.min(Fighter.MAX_METER, owner.meter + 1);
        events.add(FightEvent.text(Type.STATUS_TICK, target.index, target.x, target.y + target.hurtHeight() * 0.6f,
                p.spec.effect == null ? "POISON" : p.spec.effect.name()));
    }

    private boolean overlaps(Projectile p, Fighter f) {
        float fx0 = f.x - f.hurtWidth() / 2f, fx1 = f.x + f.hurtWidth() / 2f;
        return p.minX() < fx1 && p.maxX() > fx0 && p.minY() < f.y + f.hurtHeight() && p.maxY() > f.y;
    }

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
        if (verticalOverlap && Math.abs(dx) < minDist && player.state != State.WALL_SPLAT && enemy.state != State.WALL_SPLAT) {
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

    public float cameraX() {
        float mid = (player.x + enemy.x) / 2f;
        return Math.max(arenaMin, Math.min(arenaMax - VIEW_WIDTH, mid - VIEW_WIDTH / 2f));
    }
}
