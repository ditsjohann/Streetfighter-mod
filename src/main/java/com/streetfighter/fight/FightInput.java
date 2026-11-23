package com.streetfighter.fight;

/**
 * One frame of controller input. Directions and buttons are HELD states; the {@link InputBuffer} turns them
 * into presses. The AI can additionally request a specific special/super directly.
 */
public final class FightInput {
    public boolean left, right, up, down;
    /** Light/Medium/Heavy Punch and Kick, plus the "Modern" special button. */
    public boolean lp, mp, hp, lk, mk, hk, sp;

    /** AI shortcut: index into the moveset's specials (-1 = none). */
    public int special = -1;
    /** 0 = light, 1 = medium, 2 = heavy, 3 = EX. */
    public int strength = 1;
    /** AI shortcut: super level 1-3 (0 = none). */
    public int superLevel;
    /** AI shortcut: command normal index (-1 = none). */
    public int commandNormal = -1;
    /** AI shortcut: universal moves. */
    public boolean throwReq, backThrowReq, driveImpact, parry;
    /** AI shortcut: flap for an extra jump while airborne. */
    public boolean airJump;
    /** AI shortcut: use the held prop (1 = punch way, 2 = kick way, 0 = none). */
    public int itemUse;
    /** AI shortcut: 1 = dash forward, -1 = backdash. */
    public int dash;

    public void clear() {
        left = right = up = down = false;
        lp = mp = hp = lk = mk = hk = sp = false;
        special = -1;
        strength = 1;
        superLevel = 0;
        commandNormal = -1;
        throwReq = backThrowReq = driveImpact = parry = airJump = false;
        itemUse = 0;
        dash = 0;
    }

    public boolean anyButton() {
        return lp || mp || hp || lk || mk || hk || sp || special >= 0 || superLevel > 0 || commandNormal >= 0
                || throwReq || backThrowReq || driveImpact;
    }

    public FightInput copy() {
        FightInput c = new FightInput();
        c.left = left; c.right = right; c.up = up; c.down = down;
        c.lp = lp; c.mp = mp; c.hp = hp; c.lk = lk; c.mk = mk; c.hk = hk; c.sp = sp;
        c.special = special; c.strength = strength; c.superLevel = superLevel; c.commandNormal = commandNormal;
        c.throwReq = throwReq; c.backThrowReq = backThrowReq; c.driveImpact = driveImpact; c.parry = parry;
        c.airJump = airJump;
        c.itemUse = itemUse;
        c.dash = dash;
        return c;
    }
}
