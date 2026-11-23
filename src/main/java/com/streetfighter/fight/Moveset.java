package com.streetfighter.fight;

import com.streetfighter.fight.InputBuffer.Button;
import com.streetfighter.fight.InputBuffer.Motion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A complete Street Fighter style kit: 18 normals (stand/crouch/jump x 6 buttons), command normals,
 * target combos, specials with light/medium/heavy/EX versions, three super levels and throws.
 */
public final class Moveset {
    /** What a special is for (used by the AI and the move list). */
    public enum Role { PROJECTILE, ANTI_AIR, RUSH, GRAB, LOW, OVERHEAD, EVADE, TRAP, BUFF }

    public record CommandNormal(int dir, Button button, Move move) {
    }

    public record TargetCombo(Move from, Button button, Move move) {
    }

    /** A special move; {@code variants} holds L, M, H and EX versions. {@code modernDir} is 5, 6, 2 or 4. */
    public record Special(String name, Motion motion, boolean kick, Role role, int modernDir, Move[] variants) {
        public Move variant(int strength) {
            return variants[Math.max(0, Math.min(3, strength))];
        }
    }

    public record Super(int level, Motion motion, boolean kick, Move move) {
    }

    public final FighterType type;
    public final FighterStats stats;
    public final Map<Button, Move> stand = new EnumMap<>(Button.class);
    public final Map<Button, Move> crouch = new EnumMap<>(Button.class);
    public final Map<Button, Move> air = new EnumMap<>(Button.class);
    public final List<CommandNormal> commandNormals = new ArrayList<>();
    public final List<TargetCombo> targetCombos = new ArrayList<>();
    public final List<Special> specials = new ArrayList<>();
    public final List<Super> supers = new ArrayList<>();
    public Move forwardThrow, backThrow, driveImpact;
    /** Double-tap forward / back. */
    public Move dash, backdash;
    /** Extra jumps in the air (Chicken flap). */
    public int airJumps;
    /** Frames of slow-fall available per jump while holding up (Chicken glide); 0 = none. */
    public int glideFrames;

    public Moveset(FighterType type, FighterStats stats) {
        this.type = type;
        this.stats = stats;
    }

    public FighterType type() {
        return type;
    }

    public FighterStats stats() {
        return stats;
    }

    public Special special(int index) {
        return index >= 0 && index < specials.size() ? specials.get(index) : null;
    }

    public Special specialByRole(Role role) {
        for (Special s : specials) if (s.role == role) return s;
        return null;
    }

    public int specialIndex(Role role) {
        for (int i = 0; i < specials.size(); i++) if (specials.get(i).role == role) return i;
        return -1;
    }

    public Super superOf(int level) {
        for (Super s : supers) if (s.level == level) return s;
        return null;
    }

    /** Target combo continuing from {@code from} with {@code button}, or null. */
    public Move targetFrom(Move from, Button button) {
        for (TargetCombo t : targetCombos) if (t.from == from && t.button == button) return t.move;
        return null;
    }

    public int moveCount() {
        int n = stand.size() + crouch.size() + air.size() + commandNormals.size() + targetCombos.size()
                + supers.size() + 3;
        for (Special s : specials) n += s.variants.length;
        return n;
    }
}
