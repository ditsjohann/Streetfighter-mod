package com.streetfighter.fight;

/** Two beams met: both fighters mash buttons; the meeting point slides toward whoever mashes slower. */
public final class BeamStruggle {
    public final Projectile a, b;      // a belongs to fighter a.owner
    public final float meetX, meetY;
    /** -1 .. 1: positive pushes toward b's owner (a is winning). */
    public float progress;
    public int timer;
    public int mashA, mashB;

    BeamStruggle(Projectile a, Projectile b) {
        this.a = a;
        this.b = b;
        this.meetX = (a.x + b.x) / 2f;
        this.meetY = (a.y + b.y) / 2f;
    }

    public boolean involves(Fighter f) {
        return a.owner == f.index || b.owner == f.index;
    }
}
