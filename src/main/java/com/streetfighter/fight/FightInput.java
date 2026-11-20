package com.streetfighter.fight;

/** One frame of controller input. Directions are held, buttons are "pressed this frame". */
public final class FightInput {
    public boolean left, right, up, down;
    public boolean light, heavy, special1, special2, superMove;

    public void clear() {
        left = right = up = down = false;
        light = heavy = special1 = special2 = superMove = false;
    }

    public boolean anyButton() {
        return light || heavy || special1 || special2 || superMove;
    }
}
