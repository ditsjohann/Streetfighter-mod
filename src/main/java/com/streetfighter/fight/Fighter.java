package com.streetfighter.fight;

/** Runtime state of one fighter in a match. */
public final class Fighter {
    public enum State {
        IDLE, WALK, CROUCH, JUMP, ATTACK, HITSTUN, BLOCKSTUN, KNOCKDOWN, LANDING, KO, VICTORY
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

    public final FightInput input = new FightInput();
    private final int[] dirHistory = new int[24];
    private int dirHead;

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

    public boolean canBlock() {
        return grounded() && (actionable() || state == State.BLOCKSTUN);
    }

    public boolean invulnerable() {
        if (invuln > 0) return true;
        if (state == State.KNOCKDOWN || state == State.KO || state == State.VICTORY) return true;
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

    // ------------------------------------------------------------------ motion inputs

    /** Records the held direction as a numpad digit relative to facing (6 = forward, 2 = down...). */
    void recordDirection() {
        boolean fwd = holdingForward(), back = holdingBack();
        int h = fwd ? 1 : back ? -1 : 0;
        int v = input.up ? 1 : input.down ? -1 : 0;
        int digit = 5 + h + v * 3;
        dirHistory[dirHead] = digit;
        dirHead = (dirHead + 1) % dirHistory.length;
    }

    private boolean motion(int a, int b, int c) {
        // Search newest -> oldest for c, then b, then a within the history window.
        int stage = 0;
        int[] seq = {c, b, a};
        for (int i = 0; i < dirHistory.length; i++) {
            int idx = Math.floorMod(dirHead - 1 - i, dirHistory.length);
            int d = dirHistory[idx];
            if (stage == 0 && i > 10) return false;
            if (d == seq[stage]) {
                stage++;
                if (stage == 3) return true;
            }
        }
        return false;
    }

    public boolean quarterCircleForward() {
        return motion(2, 3, 6);
    }

    public boolean quarterCircleBack() {
        return motion(2, 1, 4);
    }

    public void clearMotion() {
        java.util.Arrays.fill(dirHistory, 5);
    }
}
