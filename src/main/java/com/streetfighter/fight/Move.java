package com.streetfighter.fight;

/**
 * Frame data for a single attack. All frame counts are at 60 fps. Hitbox coordinates are relative to the
 * fighter's feet: x grows in the direction the fighter faces, y grows upward.
 */
public final class Move {
    public enum Cancel { NONE, CHAIN, SPECIAL, SUPER }

    public enum Kind { NORMAL, COMMAND_NORMAL, TARGET, SPECIAL, SUPER, THROW, DRIVE_IMPACT, ITEM }

    public final String name;
    public final Kind kind;
    public final int strength;     // 0 L, 1 M, 2 H, 3 EX
    public final int superLevel;   // 1..3 for supers
    public final int meterCost;
    public final int startup, active, recovery;
    public final float damage;
    public final float hx0, hx1, hy0, hy1;
    public final HitLevel level;
    public final int hitstun, blockstun;
    public final float push, launch;
    public final boolean knockdown, special, untilLanding, stopOnHit;
    public final Cancel cancel;
    public final int hits, hitInterval;
    public final float moveVx, moveVy;
    public final int moveStart, moveEnd;
    public final int invulnStart, invulnEnd;
    public final int projInvulnStart, projInvulnEnd;
    public final int armorHits, armorEnd;
    public final int landingLag;
    public final ProjectileSpec projectile;
    public final int projectileFrame;
    public final AnimPose pose;
    public final String callout;
    public final boolean crouching;
    public final boolean air;
    public final boolean wallSplat, groundBounce, crumple;
    /** Cinematic supers: once the first hit lands, the victim is locked in place for the rest of the move. */
    public final boolean lockOnHit;
    public final boolean backThrow;
    /** Buffs the user gets when the move becomes active (Brew Up). */
    public final StatusEffect[] selfEffects;
    public final int selfEffectFrames;
    /** Status effect the move inflicts on hit. */
    public final StatusEffect hitEffect;
    public final int hitEffectFrames;
    /** Item moves: Ender Pearl warp (teleport behind the opponent), mystery bottle (random brew), throws that use the item up. */
    public final boolean teleport, randomBrew, consumesProp;
    /** Cow milk: clears your debuffs. Donkey saddlebag: pulls out a random prop. */
    public final boolean cleanse, grantProp;
    /** Creeper explosions hurt the creeper too (HP lost on the first active frame). */
    public final float selfDamage;
    /** Ki Clash Dragon Dash: flies straight at the opponent during the dash frames. */
    public final boolean homing;

    private Move(Builder b) {
        name = b.name;
        kind = b.kind;
        strength = b.strength;
        superLevel = b.superLevel;
        meterCost = b.meterCost;
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
        special = b.kind == Kind.SPECIAL || b.kind == Kind.SUPER || b.specialFlag;
        untilLanding = b.untilLanding;
        stopOnHit = b.stopOnHit;
        cancel = b.cancel;
        hits = b.hits;
        hitInterval = b.hitInterval;
        moveVx = b.moveVx;
        moveVy = b.moveVy;
        moveStart = b.moveStart;
        moveEnd = b.moveEnd;
        invulnStart = b.invulnStart;
        invulnEnd = b.invulnEnd;
        projInvulnStart = b.projInvulnStart;
        projInvulnEnd = b.projInvulnEnd;
        armorHits = b.armorHits;
        armorEnd = b.armorEnd;
        landingLag = b.landingLag;
        projectile = b.projectile;
        projectileFrame = b.projectileFrame;
        pose = b.pose;
        callout = b.callout;
        crouching = b.crouching;
        air = b.air;
        wallSplat = b.wallSplat;
        groundBounce = b.groundBounce;
        crumple = b.crumple;
        lockOnHit = b.lockOnHit;
        backThrow = b.backThrow;
        selfEffects = b.selfEffects;
        selfEffectFrames = b.selfEffectFrames;
        hitEffect = b.hitEffect;
        hitEffectFrames = b.hitEffectFrames;
        teleport = b.teleport;
        randomBrew = b.randomBrew;
        consumesProp = b.consumesProp;
        cleanse = b.cleanse;
        grantProp = b.grantProp;
        selfDamage = b.selfDamage;
        homing = b.homing;
    }

    public boolean isSuper() {
        return kind == Kind.SUPER;
    }

    public boolean leapLike() {
        return moveVy > 0;
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

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private String name;
        private Kind kind = Kind.NORMAL;
        private int strength = 1, superLevel, meterCost;
        private int startup = 5, active = 3, recovery = 10;
        private float damage = 30;
        private float hx0 = 10, hx1 = 60, hy0 = 60, hy1 = 110;
        private HitLevel level = HitLevel.MID;
        private int hitstun = 14, blockstun = 9;
        private float push = 4, launch = 0;
        private boolean knockdown, specialFlag, untilLanding, stopOnHit;
        private Cancel cancel = Cancel.NONE;
        private int hits = 1, hitInterval = 1;
        private float moveVx, moveVy;
        private int moveStart = -1, moveEnd = -1;
        private int invulnStart = -1, invulnEnd = -1;
        private int projInvulnStart = -1, projInvulnEnd = -1;
        private int armorHits, armorEnd;
        private int landingLag = 4;
        private ProjectileSpec projectile;
        private int projectileFrame = -1;
        private AnimPose pose = AnimPose.JAB;
        private String callout;
        private boolean crouching, air, wallSplat, groundBounce, crumple, lockOnHit, backThrow;
        private StatusEffect[] selfEffects = new StatusEffect[0];
        private int selfEffectFrames;
        private StatusEffect hitEffect;
        private int hitEffectFrames;
        private boolean teleport, randomBrew, consumesProp, cleanse, grantProp;
        private float selfDamage;
        private boolean homing;

        private Builder(String name) {
            this.name = name;
        }

        private Builder(Move m) {
            name = m.name; kind = m.kind; strength = m.strength; superLevel = m.superLevel; meterCost = m.meterCost;
            startup = m.startup; active = m.active; recovery = m.recovery; damage = m.damage;
            hx0 = m.hx0; hx1 = m.hx1; hy0 = m.hy0; hy1 = m.hy1; level = m.level; hitstun = m.hitstun;
            blockstun = m.blockstun; push = m.push; launch = m.launch; knockdown = m.knockdown;
            specialFlag = m.special; untilLanding = m.untilLanding; stopOnHit = m.stopOnHit; cancel = m.cancel;
            hits = m.hits; hitInterval = m.hitInterval; moveVx = m.moveVx; moveVy = m.moveVy;
            moveStart = m.moveStart; moveEnd = m.moveEnd; invulnStart = m.invulnStart; invulnEnd = m.invulnEnd;
            projInvulnStart = m.projInvulnStart; projInvulnEnd = m.projInvulnEnd; armorHits = m.armorHits;
            armorEnd = m.armorEnd; landingLag = m.landingLag; projectile = m.projectile;
            projectileFrame = m.projectileFrame; pose = m.pose; callout = m.callout; crouching = m.crouching;
            air = m.air; wallSplat = m.wallSplat; groundBounce = m.groundBounce; crumple = m.crumple;
            lockOnHit = m.lockOnHit; backThrow = m.backThrow;
            selfEffects = m.selfEffects; selfEffectFrames = m.selfEffectFrames;
            hitEffect = m.hitEffect; hitEffectFrames = m.hitEffectFrames;
            teleport = m.teleport; randomBrew = m.randomBrew; consumesProp = m.consumesProp;
            cleanse = m.cleanse; grantProp = m.grantProp; selfDamage = m.selfDamage; homing = m.homing;
        }

        public Builder name(String n) { this.name = n; return this; }
        public Builder kind(Kind k) { this.kind = k; return this; }
        public Builder strength(int s) { this.strength = s; return this; }
        public Builder superLevel(int lvl) { this.kind = Kind.SUPER; this.superLevel = lvl; this.meterCost = lvl * 100; return this; }
        public Builder meter(int cost) { this.meterCost = cost; return this; }
        public Builder frames(int startup, int active, int recovery) { this.startup = startup; this.active = active; this.recovery = recovery; return this; }
        public Builder damage(float d) { this.damage = d; return this; }
        public Builder box(float x0, float x1, float y0, float y1) { hx0 = x0; hx1 = x1; hy0 = y0; hy1 = y1; return this; }
        public Builder level(HitLevel l) { this.level = l; return this; }
        public Builder stun(int hit, int block) { this.hitstun = hit; this.blockstun = block; return this; }
        public Builder push(float p) { this.push = p; return this; }
        public Builder launch(float l) { this.launch = l; return this; }
        public Builder knockdown() { this.knockdown = true; return this; }
        public Builder special() { this.specialFlag = true; return this; }
        public Builder untilLanding() { this.untilLanding = true; return this; }
        public Builder stopOnHit() { this.stopOnHit = true; return this; }
        public Builder cancel(Cancel c) { this.cancel = c; return this; }
        public Builder chain() { this.cancel = Cancel.CHAIN; return this; }
        public Builder cancelable() { this.cancel = Cancel.SPECIAL; return this; }
        public Builder multiHit(int hits, int interval) { this.hits = hits; this.hitInterval = interval; return this; }
        public Builder dash(float vx, int from, int to) { this.moveVx = vx; this.moveStart = from; this.moveEnd = to; return this; }
        public Builder leap(float vy, float vx, int frame) { this.moveVy = vy; this.moveVx = vx; this.moveStart = frame; this.moveEnd = frame + 1; return this; }
        public Builder invuln(int from, int to) { this.invulnStart = from; this.invulnEnd = to; return this; }
        public Builder projInvuln(int from, int to) { this.projInvulnStart = from; this.projInvulnEnd = to; return this; }
        public Builder armor(int hits, int until) { this.armorHits = hits; this.armorEnd = until; return this; }
        public Builder landingLag(int f) { this.landingLag = f; return this; }
        public Builder projectile(ProjectileSpec spec, int frame) { this.projectile = spec; this.projectileFrame = frame; return this; }
        public Builder pose(AnimPose p) { this.pose = p; return this; }
        public Builder callout(String c) { this.callout = c; return this; }
        public Builder crouching() { this.crouching = true; return this; }
        public Builder air() { this.air = true; return this; }
        public Builder wallSplat() { this.wallSplat = true; return this; }
        public Builder groundBounce() { this.groundBounce = true; return this; }
        public Builder crumple() { this.crumple = true; return this; }
        public Builder lockOnHit() { this.lockOnHit = true; return this; }
        public Builder backThrow() { this.backThrow = true; return this; }
        public Builder selfEffect(int frames, StatusEffect... effects) { this.selfEffects = effects; this.selfEffectFrames = frames; return this; }
        public Builder hitEffect(StatusEffect e, int frames) { this.hitEffect = e; this.hitEffectFrames = frames; return this; }
        public Builder teleport() { this.teleport = true; return this; }
        public Builder randomBrew() { this.randomBrew = true; return this; }
        public Builder consumesProp() { this.consumesProp = true; return this; }
        public Builder cleanse() { this.cleanse = true; return this; }
        public Builder grantProp() { this.grantProp = true; return this; }
        public Builder selfDamage(float hp) { this.selfDamage = hp; return this; }
        public Builder homing() { this.homing = true; return this; }

        public Move build() {
            return new Move(this);
        }
    }
}
