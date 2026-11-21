package com.streetfighter.fight;

/** Runtime state of one fighter in a match. */
public final class Fighter {
    public enum State {
        IDLE, WALK, CROUCH, JUMP, ATTACK, HITSTUN, BLOCKSTUN, KNOCKDOWN, LANDING, KO, VICTORY,
        PARRY, CRUMPLE, WALL_SPLAT
    }

    public final int index;
    public final String name;
    /** Which body is drawn (player = STEVE, otherwise the mob's type). */
    public final FighterType body;
    public final Moveset moves;
    public final FighterStats stats;
    public final float damageScale;
    public final float maxHp;

    public float x, y, vx, vy;
    public boolean facingRight;
    public float hp, displayHp, meter;
    public State state = State.IDLE;
    public int stateTimer;
    public int stateAge;

    public Move move;
    public int moveFrame;
    public int curFrame = -1;
    public int activeEnd;
    public int hitsDone;
    public int lastHitFrame;
    public boolean moveConnected;
    public int moveSerial;
    public int chainCount;

    public boolean airAttackUsed;
    public boolean launched;
    public int invuln;
    public int comboTaken;
    public int comboShown;
    public int comboTimer;
    public int roundsWon;
    public int displayHpDelay;
    public boolean wasCrouchingWhenHit;

    // --- new mechanics
    public static final float MAX_METER = 300;
    public int armorLeft;
    public boolean reversal;
    public int actionableAge;
    public boolean stunnedBefore;
    public HitLevel lastHitLevel = HitLevel.MID;
    public boolean lastHitHeavy;
    public boolean groundBouncePending;
    public boolean wallSplatPending;
    public boolean wallJumpUsed;
    public int hazardTimer;
    public int parryRecovery;
    /** Frame counter for sub-step render interpolation. */
    public float prevX, prevY;

    public final FightInput input = new FightInput();

    // --- round 4: status effects, air mobility
    /** Frames left on each {@link StatusEffect}. */
    public final int[] status = new int[StatusEffect.ALL.length];
    public int airJumpsLeft;
    public int glideLeft;
    public boolean gliding;

    // --- props
    /** The item this fighter is holding (thrown in by the villager), or null. */
    public Prop heldProp;
    public int heldTimer, heldUses;

    public boolean has(StatusEffect e) {
        return status[e.ordinal()] > 0;
    }

    public void give(StatusEffect e, int frames) {
        status[e.ordinal()] = Math.max(status[e.ordinal()], frames);
    }

    public void clearStatus() {
        java.util.Arrays.fill(status, 0);
    }

    /** Walk/jump speed multiplier from Slowness/Swiftness. */
    public float statusSpeed() {
        float m = 1f;
        if (has(StatusEffect.SLOW)) m *= 0.6f;
        if (has(StatusEffect.SPEED)) m *= 1.3f;
        return m;
    }

    public Fighter(int index, String name, FighterType body, Moveset moves, FighterStats stats, float hpScale,
                   float damageScale) {
        this.index = index;
        this.name = name;
        this.body = body;
        this.moves = moves;
        this.stats = stats;
        this.maxHp = stats.maxHp() * hpScale;
        this.damageScale = damageScale;
        this.hp = maxHp;
        this.displayHp = maxHp;
    }

    public int dir() {
        return facingRight ? 1 : -1;
    }

    public boolean grounded() {
        return y <= 0.0001f && vy <= 0;
    }

    public boolean crouching() {
        return switch (state) {
            case CROUCH -> true;
            case ATTACK -> move != null && move.crouching;
            case BLOCKSTUN -> input.down;
            case HITSTUN -> wasCrouchingWhenHit && grounded();
            default -> false;
        };
    }

    public float hurtWidth() {
        return stats.hurtWidth();
    }

    public float hurtHeight() {
        if (state == State.KNOCKDOWN || state == State.KO) return 40;
        return crouching() ? stats.crouchHeight() : stats.hurtHeight();
    }

    public boolean holdingForward() {
        return facingRight ? input.right : input.left;
    }

    public boolean holdingBack() {
        return facingRight ? input.left : input.right;
    }

    public boolean actionable() {
        return state == State.IDLE || state == State.WALK || state == State.CROUCH;
    }

    public int bars() {
        return (int) (meter / 100f);
    }

    public boolean canBlock() {
        return grounded() && (actionable() || state == State.BLOCKSTUN);
    }

    public boolean invulnerable() {
        if (invuln > 0) return true;
        if (state == State.KNOCKDOWN || state == State.KO || state == State.VICTORY) return true;
        if (state == State.PARRY) return false;
        if (state == State.ATTACK && move != null && curFrame >= move.invulnStart && curFrame < move.invulnEnd) {
            return true;
        }
        return false;
    }

    /** True while the current move's hitbox is out and may still hit. */
    public boolean hitboxActive() {
        if (state != State.ATTACK || move == null || move.damage <= 0) return false;
        if (curFrame < move.startup || curFrame >= activeEnd) return false;
        if (hitsDone >= move.hits) return false;
        return hitsDone == 0 || curFrame - lastHitFrame >= move.hitInterval;
    }

    public float hitMinX(Move m) {
        float a = x + dir() * m.hx0, b = x + dir() * m.hx1;
        return Math.min(a, b);
    }

    public float hitMaxX(Move m) {
        float a = x + dir() * m.hx0, b = x + dir() * m.hx1;
        return Math.max(a, b);
    }

    public boolean isAttackingWithin(int framesAhead) {
        return state == State.ATTACK && move != null && curFrame < activeEnd && curFrame + framesAhead >= move.startup;
    }

    // ------------------------------------------------------------------ input buffer

    public final InputBuffer buffer = new InputBuffer();
    /** Set when this fighter is being thrown, so a light+heavy press can tech it. */
    public int throwTechWindow;
    /** Text shown when a counter hit lands on this fighter ("COUNTER", "PUNISH COUNTER"). */
    public String lastCounter;

    public void clearMotion() {
        buffer.clear();
    }
}
