package com.streetfighter.fight;

/** Something that happened during a frame; the screen turns these into sounds and visual effects. */
public record FightEvent(Type type, int fighter, float x, float y, String text, ProjectileVisual visual,
                         boolean heavy) {

    public enum Type {
        ROUND, FIGHT, MOVE_START, HIT, BLOCK, PROJECTILE, CLASH, BOUNCE, POOF, SLAM, LAND, THUD, JUMP,
        SUPER, KO, TIME, ROUND_WIN, DRAW, PERFECT, MATCH_WIN
    }

    public static FightEvent of(Type type, int fighter, float x, float y) {
        return new FightEvent(type, fighter, x, y, null, null, false);
    }
}
