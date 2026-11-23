package com.streetfighter.client;

import com.streetfighter.fight.InputBuffer;

/**
 * Writes motions as the actual keys to press, flipped for the side you're on, instead of fighting-game arrows:
 * a quarter circle forward is "S, S+D, D" when facing right and "S, S+A, A" when facing left.
 */
public final class KeyNotation {
    private KeyNotation() {
    }

    /** Numpad direction (6 = forward) to keys. */
    public static String dir(int numpad, boolean facingRight) {
        String fwd = facingRight ? "D" : "A", back = facingRight ? "A" : "D";
        return switch (numpad) {
            case 1 -> "S+" + back;
            case 2 -> "S";
            case 3 -> "S+" + fwd;
            case 4 -> back;
            case 6 -> fwd;
            case 7 -> "W+" + back;
            case 8 -> "W";
            case 9 -> "W+" + fwd;
            default -> "";
        };
    }

    public static int[] steps(InputBuffer.Motion m) {
        return switch (m) {
            case QCF -> new int[]{2, 3, 6};
            case QCB -> new int[]{2, 1, 4};
            case DP -> new int[]{6, 2, 3};
            case HCB -> new int[]{6, 3, 2, 1, 4};
            case QCF2 -> new int[]{2, 3, 6, 2, 3, 6};
            case QCB2 -> new int[]{2, 1, 4, 2, 1, 4};
        };
    }

    /** e.g. "S, S+D, D" */
    public static String motion(InputBuffer.Motion m, boolean facingRight) {
        StringBuilder sb = new StringBuilder();
        for (int d : steps(m)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(dir(d, facingRight));
        }
        return sb.toString();
    }

    /** Which keys a punch/kick input means. */
    public static String button(boolean kick) {
        return kick ? "J/K/L" : "U/I/O";
    }

    /** Full classic input, e.g. "S, S+D, D + U/I/O". */
    public static String special(InputBuffer.Motion m, boolean kick, boolean facingRight) {
        return motion(m, facingRight) + " + " + button(kick);
    }

    /** One-button ("simple") input: H plus a direction. */
    public static String simple(int modernDir, boolean facingRight) {
        return switch (modernDir) {
            case 6 -> "H+" + (facingRight ? "D" : "A");
            case 4 -> "H+" + (facingRight ? "A" : "D");
            case 2 -> "H+S";
            default -> "H";
        };
    }
}
