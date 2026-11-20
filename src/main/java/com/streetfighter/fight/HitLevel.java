package com.streetfighter.fight;

/**
 * HIGH = overhead (must block standing), MID = block either way, LOW = must block crouching,
 * GRAB = cannot be blocked, but misses airborne or stunned opponents.
 */
public enum HitLevel {
    HIGH, MID, LOW, GRAB
}
