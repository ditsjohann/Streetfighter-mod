package com.streetfighter.fight;

/** Something that happened during a frame; the client turns these into sounds and visual effects. */
public record FightEvent(Type type, int fighter, float x, float y, String text, ProjectileVisual visual,
                         boolean heavy) {

    public enum Type {
        ROUND, FIGHT, MOVE_START, HIT, BLOCK, PROJECTILE, CLASH, BOUNCE, POOF, SLAM, LAND, THUD, JUMP,
        SUPER, KO, TIME, ROUND_WIN, DRAW, PERFECT, MATCH_WIN,
        // new
        EXPLOSION, PARRY, PERFECT_PARRY, ARMOR, CRUMPLE, WALL_SPLAT, WALL_BOUNCE, WALL_BREAK, WALL_JUMP,
        HAZARD, CALLOUT, TECH, GROUND_BOUNCE, WHIFF,
        // round 4
        AIR_JUMP, STATUS, STATUS_TICK,
        // props
        PROP_VILLAGER, PROP_THROW, PROP_LAND, PROP_PICKUP, PROP_DROP, PROP_EXPIRE, TELEPORT,
        SELF_DAMAGE, CLEANSE
    }

    public static FightEvent of(Type type, int fighter, float x, float y) {
        return new FightEvent(type, fighter, x, y, null, null, false);
    }

    public static FightEvent text(Type type, int fighter, float x, float y, String text) {
        return new FightEvent(type, fighter, x, y, text, null, false);
    }
}
