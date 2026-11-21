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
    /** Multi-hit projectiles (EX fireballs): number of hits and frames between them. */
    public final int hits, hitInterval;
    /** Explodes on contact or landing into a big short-lived hitbox. */
    public final boolean explodes;
    public final boolean wallSplat;
    /** Hits on both sides (bell shockwaves). */
    public final boolean bothSides;
    /** Status effect applied on hit (potions, snow). */
    public final StatusEffect effect;
    public final int effectFrames;
    /** Lingering cloud: no hitstun, ticks damage + its effect every hitInterval frames, can't be blocked or clashed. */
    public final boolean aura;
    /** Size of the explosion hitbox for {@link #explodes} projectiles. */
    public final float blastSize;

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
        hits = b.hits;
        hitInterval = b.hitInterval;
        explodes = b.explodes;
        wallSplat = b.wallSplat;
        bothSides = b.bothSides;
        effect = b.effect;
        effectFrames = b.effectFrames;
        aura = b.aura;
        blastSize = b.blastSize;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public float vy(int index) {
        if (vyList.length == 0) return 0;
        return vyList[Math.min(index, vyList.length - 1)];
    }

    public static Builder builder(ProjectileVisual visual) {
        return new Builder(visual);
    }

    public static final class Builder {
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
        private int hits = 1, hitInterval = 8;
        private boolean explodes, wallSplat, bothSides;
        private ProjectileVisual visual;
        private StatusEffect effect;
        private int effectFrames;
        private boolean aura;
        private float blastSize = 150;

        private Builder(ProjectileVisual visual) {
            this.visual = visual;
        }

        private Builder(ProjectileSpec s) {
            visual = s.visual; vx = s.vx; vyList = s.vyList; gravity = s.gravity; width = s.width; height = s.height;
            offX = s.offX; offY = s.offY; damage = s.damage; hitstun = s.hitstun; blockstun = s.blockstun;
            push = s.push; launch = s.launch; knockdown = s.knockdown; level = s.level; lifetime = s.lifetime;
            bounce = s.bounce; count = s.count; delayStep = s.delayStep; fromSky = s.fromSky; skySpread = s.skySpread;
            skyHeight = s.skyHeight; durable = s.durable; spin = s.spin; hits = s.hits; hitInterval = s.hitInterval;
            explodes = s.explodes; wallSplat = s.wallSplat; bothSides = s.bothSides;
            effect = s.effect; effectFrames = s.effectFrames; aura = s.aura; blastSize = s.blastSize;
        }

        public Builder effect(StatusEffect e, int frames) {
            this.effect = e;
            this.effectFrames = frames;
            return this;
        }

        public Builder aura() {
            this.aura = true;
            this.durable = true;
            return this;
        }

        public Builder blast(float size) {
            this.blastSize = size;
            return this;
        }

        public Builder visual(ProjectileVisual v) {
            this.visual = v;
            return this;
        }

        public Builder multiHit(int hits, int interval) {
            this.hits = hits;
            this.hitInterval = interval;
            return this;
        }

        public Builder explodes() {
            this.explodes = true;
            return this;
        }

        public Builder wallSplat() {
            this.wallSplat = true;
            return this;
        }

        public Builder bothSides() {
            this.bothSides = true;
            return this;
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
