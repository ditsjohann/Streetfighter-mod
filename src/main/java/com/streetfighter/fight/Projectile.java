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

    public Projectile(int id, int owner, ProjectileSpec spec) {
        this.id = id;
        this.owner = owner;
        this.spec = spec;
    }

    /** Golems and pigs stand on the ground (y = feet); everything else is centred on y. */
    public boolean bottomAnchored() {
        return spec.visual == ProjectileVisual.GOLEM || spec.visual == ProjectileVisual.PIG;
    }

    public float minX() {
        return x - spec.width / 2f;
    }

    public float maxX() {
        return x + spec.width / 2f;
    }

    public float minY() {
        return bottomAnchored() ? y : y - spec.height / 2f;
    }

    public float maxY() {
        return bottomAnchored() ? y + spec.height : y + spec.height / 2f;
    }

    public boolean hitboxActive() {
        if (delay > 0 || dead) return false;
        if (spec.visual == ProjectileVisual.GOLEM) return !landed || landedTimer < 5;
        return true;
    }

    public boolean facingRight() {
        return vx >= 0;
    }
}
