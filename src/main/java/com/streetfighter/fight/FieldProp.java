package com.streetfighter.fight;

/** A prop lying in (or flying into) the arena, waiting to be picked up. */
public final class FieldProp {
    public final int id;
    public final Prop kind;
    public float x, y, vx, vy, prevX, prevY;
    public boolean landed;
    public int age;
    /** Frames it stays on the ground before vanishing. */
    public int groundLeft = Prop.GROUND_FRAMES;
    /** A dropped prop keeps its remaining hold time and uses. */
    public int holdLeft = Prop.HOLD_FRAMES, usesLeft;
    public boolean dead;

    public FieldProp(int id, Prop kind) {
        this.id = id;
        this.kind = kind;
        this.usesLeft = kind.uses;
    }
}
