package com.streetfighter.fight;

public final class Projectile {
    public final int id;
    public final int owner;
    public final ProjectileSpec spec;
    public float x, y, vx, vy;
    public int delay;
    public int age;
    public boolean dead;
    public boolean hasHit;
    public int bounces;
    public boolean landed;
    public int landedTimer;
    public int hitsDone;
    public int lastHitAge = -100;
    public boolean exploded;
    public float prevX, prevY;

    public Projectile(int id, int owner, ProjectileSpec spec) {
        this.id = id;
        this.owner = owner;
        this.spec = spec;
    }

    /** Golems and pigs stand on the ground (y = feet); everything else is centred on y. */
    public boolean bottomAnchored() {
        return spec.visual == ProjectileVisual.GOLEM || spec.visual == ProjectileVisual.PIG
                || spec.visual == ProjectileVisual.BELL || spec.visual == ProjectileVisual.SHOCKWAVE
                || spec.visual == ProjectileVisual.CLOUD || spec.visual == ProjectileVisual.GOAT
                || spec.visual == ProjectileVisual.ROOTS || spec.visual == ProjectileVisual.SPIKE
                || spec.visual == ProjectileVisual.MINECART || spec.visual == ProjectileVisual.ZOMBIE
                || spec.visual == ProjectileVisual.BABY_ZOMBIE || spec.visual == ProjectileVisual.WEB;
    }

    public float width() {
        return exploded ? spec.blastSize : spec.width;
    }

    public float height() {
        return exploded ? spec.blastSize : spec.height;
    }

    public float minX() {
        return x - width() / 2f;
    }

    public float maxX() {
        return x + width() / 2f;
    }

    public float minY() {
        return bottomAnchored() ? y : y - height() / 2f;
    }

    public float maxY() {
        return bottomAnchored() ? y + height() : y + height() / 2f;
    }

    public boolean hitboxActive() {
        if (delay > 0 || dead) return false;
        if (spec.visual == ProjectileVisual.GOLEM) return !landed || landedTimer < 5;
        if (exploded) return landedTimer < 5;
        return true;
    }

    public boolean facingRight() {
        return vx >= 0;
    }
}
