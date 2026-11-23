package com.streetfighter.fight;

/**
 * Timed status effects (Alchemist potions, Goat snow, buffs). Durations are in frames at 60 fps.
 * Debuffs are applied by hits; buffs by the user's own moves (Brew Up).
 */
public enum StatusEffect {
    POISON("POISONED", false, 0x4E9331),
    SLOW("SLOWED", false, 0x5A6C81),
    STRENGTH("STRENGTH UP", true, 0x932423),
    SPEED("SPEED UP", true, 0x7CAFC6),
    REGEN("REGENERATION", true, 0xCD5CAB),
    /** Zombie bites: the super meter drains away. */
    HUNGER("HUNGER", false, 0x587653),
    /** Ki Clash transformation (Super Villager / Super Steve): stronger, faster, regenerates ki. */
    SUPER_FORM("SUPER FORM", true, 0xFFD21A);

    public final String label;
    public final boolean buff;
    /** Vanilla potion colour (RGB) for particles and HUD tags. */
    public final int color;

    StatusEffect(String label, boolean buff, int color) {
        this.label = label;
        this.buff = buff;
        this.color = color;
    }

    public static final StatusEffect[] ALL = values();
}
