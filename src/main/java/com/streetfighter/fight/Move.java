package com.streetfighter.fight;

/**
 * Frame data for a single attack. All frame counts are at 60 fps. Hitbox coordinates are relative to the
 * fighter's feet: x grows in the direction the fighter faces, y grows upward.
 */
public final class Move {
    public final String name;
    public final int startup, active, recovery;
    public final float damage;
    public final float hx0, hx1, hy0, hy1;
    public final HitLevel level;
    public final int hitstun, blockstun;
    public final float push, launch;
    public final boolean knockdown, cancelable, special, untilLanding, stopOnHit, chainable;
    public final int hits, hitInterval;
    public final float moveVx, moveVy;
    public final int moveStart, moveEnd;
    public final int invulnStart, invulnEnd;
    public final int landingLag;
    public final ProjectileSpec projectile;
    public final int projectileFrame;
    public final AnimPose pose;
    public final String callout;
    public final boolean isSuper;
    public final boolean crouching;
    public final boolean air;

    private Move(Builder b) {
        name = b.name;
        startup = b.startup;
        active = b.active;
        recovery = b.recovery;
        damage = b.damage;
        hx0 = b.hx0;
        hx1 = b.hx1;
        hy0 = b.hy0;
        hy1 = b.hy1;
        level = b.level;
        hitstun = b.hitstun;
        blockstun = b.blockstun;
        push = b.push;
        launch = b.launch;
        knockdown = b.knockdown;
        cancelable = b.cancelable;
        special = b.special;
        untilLanding = b.untilLanding;
        stopOnHit = b.stopOnHit;
        chainable = b.chainable;
        hits = b.hits;
        hitInterval = b.hitInterval;
        moveVx = b.moveVx;
        moveVy = b.moveVy;
        moveStart = b.moveStart;
        moveEnd = b.moveEnd;
        invulnStart = b.invulnStart;
        invulnEnd = b.invulnEnd;
        landingLag = b.landingLag;
        projectile = b.projectile;
        projectileFrame = b.projectileFrame;
        pose = b.pose;
        callout = b.callout;
        isSuper = b.isSuper;
        crouching = b.crouching;
        air = b.air;
    }

    public int totalFrames() {
        return startup + active + recovery;
    }

    /** Rough horizontal reach, used by the AI. */
    public float reach() {
        if (projectile != null) return 2000f;
        float r = Math.max(hx0, hx1);
        if (moveVx > 0) r += moveVx * Math.max(0, Math.min(moveEnd, startup + active) - moveStart);
        return r;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private int startup = 5, active = 3, recovery = 10;
        private float damage = 30;
        private float hx0 = 10, hx1 = 60, hy0 = 60, hy1 = 110;
        private HitLevel level = HitLevel.MID;
        private int hitstun = 14, blockstun = 9;
        private float push = 4, launch = 0;
        private boolean knockdown, cancelable, special, untilLanding, stopOnHit, chainable;
        private int hits = 1, hitInterval = 1;
        private float moveVx, moveVy;
        private int moveStart = -1, moveEnd = -1;
        private int invulnStart = -1, invulnEnd = -1;
        private int landingLag = 4;
        private ProjectileSpec projectile;
        private int projectileFrame = -1;
        private AnimPose pose = AnimPose.PUNCH;
        private String callout;
        private boolean isSuper;
        private boolean crouching;
        private boolean air;

        private Builder(String name) {
            this.name = name;
        }

        public Builder frames(int startup, int active, int recovery) {
            this.startup = startup;
            this.active = active;
            this.recovery = recovery;
            return this;
        }

        public Builder damage(float damage) {
            this.damage = damage;
            return this;
        }

        public Builder box(float x0, float x1, float y0, float y1) {
            this.hx0 = x0;
            this.hx1 = x1;
            this.hy0 = y0;
            this.hy1 = y1;
            return this;
        }

        public Builder level(HitLevel level) {
            this.level = level;
            return this;
        }

        public Builder stun(int hitstun, int blockstun) {
            this.hitstun = hitstun;
            this.blockstun = blockstun;
            return this;
        }

        public Builder push(float push) {
            this.push = push;
            return this;
        }

        public Builder launch(float launch) {
            this.launch = launch;
            return this;
        }

        public Builder knockdown() {
            this.knockdown = true;
            return this;
        }

        public Builder cancelable() {
            this.cancelable = true;
            return this;
        }

        public Builder chainable() {
            this.chainable = true;
            return this;
        }

        public Builder special() {
            this.special = true;
            return this;
        }

        public Builder untilLanding() {
            this.untilLanding = true;
            return this;
        }

        public Builder stopOnHit() {
            this.stopOnHit = true;
            return this;
        }

        public Builder multiHit(int hits, int interval) {
            this.hits = hits;
            this.hitInterval = interval;
            return this;
        }

        public Builder dash(float vx, int from, int to) {
            this.moveVx = vx;
            this.moveStart = from;
            this.moveEnd = to;
            return this;
        }

        public Builder leap(float vy, float vx, int frame) {
            this.moveVy = vy;
            this.moveVx = vx;
            this.moveStart = frame;
            this.moveEnd = frame + 1;
            return this;
        }

        public Builder invuln(int from, int to) {
            this.invulnStart = from;
            this.invulnEnd = to;
            return this;
        }

        public Builder landingLag(int frames) {
            this.landingLag = frames;
            return this;
        }

        public Builder projectile(ProjectileSpec spec, int frame) {
            this.projectile = spec;
            this.projectileFrame = frame;
            return this;
        }

        public Builder pose(AnimPose pose) {
            this.pose = pose;
            return this;
        }

        public Builder callout(String callout) {
            this.callout = callout;
            return this;
        }

        public Builder superMove() {
            this.isSuper = true;
            this.special = true;
            return this;
        }

        public Builder crouching() {
            this.crouching = true;
            return this;
        }

        public Builder air() {
            this.air = true;
            return this;
        }

        public Move build() {
            return new Move(this);
        }
    }
}
