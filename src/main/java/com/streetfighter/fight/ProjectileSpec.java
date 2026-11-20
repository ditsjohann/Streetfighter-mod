package com.streetfighter.fight;

/** Describes projectiles (and summoned assists) a move creates. */
public final class ProjectileSpec {
    public final ProjectileVisual visual;
    public final float vx, gravity, width, height, offX, offY;
    public final float[] vyList;
    public final float damage;
    public final int hitstun, blockstun;
    public final float push, launch;
    public final boolean knockdown;
    public final HitLevel level;
    public final int lifetime;
    public final float bounce;
    public final int count;
    public final int delayStep;
    /** Spawn above the opponent and fall down (arrow rain, iron golem). */
    public final boolean fromSky;
    public final float skySpread, skyHeight;
    /** Durable projectiles destroy normal ones and keep going after a hit. */
    public final boolean durable;
    public final boolean spin;

    private ProjectileSpec(Builder b) {
        visual = b.visual;
        vx = b.vx;
        vyList = b.vyList;
        gravity = b.gravity;
        width = b.width;
        height = b.height;
        offX = b.offX;
        offY = b.offY;
        damage = b.damage;
        hitstun = b.hitstun;
        blockstun = b.blockstun;
        push = b.push;
        launch = b.launch;
        knockdown = b.knockdown;
        level = b.level;
        lifetime = b.lifetime;
        bounce = b.bounce;
        count = b.count;
        delayStep = b.delayStep;
        fromSky = b.fromSky;
        skySpread = b.skySpread;
        skyHeight = b.skyHeight;
        durable = b.durable;
        spin = b.spin;
    }

    public float vy(int index) {
        if (vyList.length == 0) return 0;
        return vyList[Math.min(index, vyList.length - 1)];
    }

    public static Builder builder(ProjectileVisual visual) {
        return new Builder(visual);
    }

    public static final class Builder {
        private final ProjectileVisual visual;
        private float vx = 8;
        private float[] vyList = {0};
        private float gravity = 0;
        private float width = 30, height = 20;
        private float offX = 40, offY = 85;
        private float damage = 60;
        private int hitstun = 18, blockstun = 12;
        private float push = 6, launch = 0;
        private boolean knockdown;
        private HitLevel level = HitLevel.MID;
        private int lifetime = 180;
        private float bounce = 0;
        private int count = 1;
        private int delayStep = 0;
        private boolean fromSky;
        private float skySpread, skyHeight = 420;
        private boolean durable;
        private boolean spin;

        private Builder(ProjectileVisual visual) {
            this.visual = visual;
        }

        public Builder velocity(float vx, float... vy) {
            this.vx = vx;
            this.vyList = vy.length == 0 ? new float[]{0} : vy;
            return this;
        }

        public Builder gravity(float gravity) {
            this.gravity = gravity;
            return this;
        }

        public Builder size(float w, float h) {
            this.width = w;
            this.height = h;
            return this;
        }

        public Builder offset(float x, float y) {
            this.offX = x;
            this.offY = y;
            return this;
        }

        public Builder damage(float damage) {
            this.damage = damage;
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

        public Builder level(HitLevel level) {
            this.level = level;
            return this;
        }

        public Builder lifetime(int lifetime) {
            this.lifetime = lifetime;
            return this;
        }

        public Builder bounce(float bounce) {
            this.bounce = bounce;
            return this;
        }

        public Builder count(int count, int delayStep) {
            this.count = count;
            this.delayStep = delayStep;
            return this;
        }

        public Builder fromSky(float spread, float height) {
            this.fromSky = true;
            this.skySpread = spread;
            this.skyHeight = height;
            return this;
        }

        public Builder durable() {
            this.durable = true;
            return this;
        }

        public Builder spin() {
            this.spin = true;
            return this;
        }

        public ProjectileSpec build() {
            return new ProjectileSpec(this);
        }
    }
}
