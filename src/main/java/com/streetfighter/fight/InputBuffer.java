package com.streetfighter.fight;

/**
 * Fighting-game input buffer and motion parser.
 * <p>
 * Design notes (from studying Ikemen GO's {@code input.go} and SF6 behaviour):
 * <ul>
 *   <li>Every key keeps a signed counter: {@code +n} = held for n frames, {@code -n} = released for n frames
 *       (Ikemen GO's per-key buffer). A "press" is a counter of exactly 1.</li>
 *   <li>Directions are stored per frame as numpad digits relative to facing (6 = forward), after SOCD
 *       resolution (left+right = neutral, up+down = up).</li>
 *   <li>Motions are matched newest-to-oldest; every step must be found strictly before the step after it,
 *       so one input frame can never complete two steps, and each step has its own time window.</li>
 *   <li>Button presses stay usable for {@link #BUTTON_BUFFER} frames (SF6 uses a ~4 frame buffer for links,
 *       7 for some assisted inputs) so presses during hitstop/recovery come out on the first possible frame.</li>
 *   <li>Lenient "$"-style direction sets: e.g. "down" accepts 1, 2 or 3.</li>
 * </ul>
 */
public final class InputBuffer {
    public static final int BUTTON_BUFFER = 6;
    private static final int HISTORY = 40;

    public enum Button {
        LP, MP, HP, LK, MK, HK, SP;

        public boolean isPunch() {
            return this == LP || this == MP || this == HP;
        }

        public boolean isKick() {
            return this == LK || this == MK || this == HK;
        }

        /** 0 light, 1 medium, 2 heavy. */
        public int strength() {
            return switch (this) {
                case LP, LK -> 0;
                case MP, MK -> 1;
                default -> 2;
            };
        }
    }

    /** A motion command: a sequence of direction sets, newest step last. */
    public enum Motion {
        /** 236 quarter circle forward (diagonal optional, so 2,6 also works on keyboards). */
        QCF(18, new int[][]{{1, 2, 3}, {3}, {6, 9}}, new boolean[]{false, true, false}),
        /** 214 quarter circle back. */
        QCB(18, new int[][]{{1, 2, 3}, {1}, {4, 7}}, new boolean[]{false, true, false}),
        /** 623 dragon punch (3,2,3 shortcut accepted). */
        DP(20, new int[][]{{6, 3, 9}, {2, 1}, {3}}, new boolean[]{false, false, false}),
        /** 63214 half circle back (command grabs). */
        HCB(24, new int[][]{{6, 3, 9}, {2, 3, 1}, {4, 1, 7}}, new boolean[]{false, false, false}),
        /** 236236 double quarter circle forward. */
        QCF2(30, new int[][]{{1, 2}, {6, 9}, {1, 2, 3}, {6, 9}}, new boolean[]{false, false, false, false}),
        /** 214214 double quarter circle back. */
        QCB2(30, new int[][]{{3, 2}, {4, 7}, {1, 2, 3}, {4, 7}}, new boolean[]{false, false, false, false});

        final int totalTime;
        final int[][] steps;
        final boolean[] optional;

        Motion(int totalTime, int[][] steps, boolean[] optional) {
            this.totalTime = totalTime;
            this.steps = steps;
            this.optional = optional;
        }
    }

    private final int[] dirHistory = new int[HISTORY];
    private int head;
    private final int[] buttons = new int[Button.values().length];
    private final boolean[] consumed = new boolean[Button.values().length];
    private final int[] pressLength = new int[Button.values().length];
    private int frame;

    public InputBuffer() {
        clear();
    }

    public void clear() {
        java.util.Arrays.fill(dirHistory, 5);
        java.util.Arrays.fill(buttons, -100);
        java.util.Arrays.fill(pressLength, 1);
        java.util.Arrays.fill(consumed, true);
    }

    /** Feeds one frame of input. */
    public void update(FightInput in, boolean facingRight) {
        update(in, facingRight, false);
    }

    /**
     * @param frozen true during hitstop / super freeze: new presses still register, but nothing ages, so a button
     *               pressed during the freeze is still fresh when play resumes.
     */
    public void update(FightInput in, boolean facingRight, boolean frozen) {
        frame++;
        boolean left = in.left, right = in.right, up = in.up, down = in.down;
        if (left && right) left = right = false; // SOCD: neutral
        if (up && down) down = false;            // SOCD: up wins
        boolean fwd = facingRight ? right : left;
        boolean back = facingRight ? left : right;
        int h = fwd ? 1 : back ? -1 : 0;
        int v = up ? 1 : down ? -1 : 0;
        dirHistory[head] = 5 + h + v * 3;
        head = (head + 1) % HISTORY;

        boolean[] held = {in.lp, in.mp, in.hp, in.lk, in.mk, in.hk, in.sp};
        for (int i = 0; i < buttons.length; i++) {
            if (held[i]) {
                if (buttons[i] <= 0) {
                    buttons[i] = 1;       // fresh press
                    consumed[i] = false;
                } else if (!frozen) {
                    buttons[i]++;
                }
            } else {
                if (buttons[i] > 0) {
                    pressLength[i] = buttons[i];
                    buttons[i] = -1;
                } else if (!frozen) {
                    buttons[i]--;
                }
            }
        }
    }

    /** True while the button is physically held. */
    public boolean held(Button b) {
        return buttons[b.ordinal()] > 0;
    }

    /** Frames since the button was pressed (0 = this frame), or a big number. */
    private int age(Button b) {
        int c = buttons[b.ordinal()];
        if (c > 0) return c - 1;          // held: frames since the press
        return c < 0 ? -c + pressLength[b.ordinal()] - 1 : 1000;
    }

    /** True if the button was pressed within the buffer window and not used yet. */
    public boolean buffered(Button b) {
        return !consumed[b.ordinal()] && age(b) <= BUTTON_BUFFER;
    }

    public boolean pressedThisFrame(Button b) {
        return buttons[b.ordinal()] == 1;
    }

    /** Frames since the newest press of any button in the group (punches or kicks). */
    public int freshestAge(boolean kicks) {
        int best = 1000;
        for (Button b : Button.values()) {
            if (b == Button.SP || b.isKick() != kicks || consumed[b.ordinal()]) continue;
            best = Math.min(best, age(b));
        }
        return best;
    }

    /** True if an up-direction appeared this frame (for wall jumps). */
    public boolean upPressedThisFrame() {
        int now = dirHistory[Math.floorMod(head - 1, HISTORY)];
        int prev = dirHistory[Math.floorMod(head - 2, HISTORY)];
        return now >= 7 && prev < 7;
    }

    public void consume(Button b) {
        consumed[b.ordinal()] = true;
    }

    /** Both buttons pressed within {@code window} frames of each other (e.g. throw = light + heavy). */
    public boolean together(Button a, Button b, int window) {
        return buffered(a) && buffered(b) && Math.abs(age(a) - age(b)) <= window;
    }

    /** Current (newest) direction digit. */
    public int currentDir() {
        return dirHistory[Math.floorMod(head - 1, HISTORY)];
    }

    /**
     * Matches a motion that finished within {@link #BUTTON_BUFFER} frames of a button press. Steps are searched
     * newest to oldest; each must occur strictly before the next one and inside the motion's total window.
     */
    public boolean motion(Motion m) {
        int buttonAge = Integer.MAX_VALUE;
        for (Button b : Button.values()) {
            if (!consumed[b.ordinal()]) buttonAge = Math.min(buttonAge, age(b));
        }
        if (buttonAge > BUTTON_BUFFER) return false;

        int step = m.steps.length - 1;
        int lastFound = -1;
        for (int i = 0; i < Math.min(HISTORY, m.totalTime + buttonAge + 4); i++) {
            int d = dirHistory[Math.floorMod(head - 1 - i, HISTORY)];
            if (step == m.steps.length - 1 && i > buttonAge + BUTTON_BUFFER) return false; // last step must be fresh
            while (step >= 0) {
                if (contains(m.steps[step], d) && i != lastFound) {
                    lastFound = i;
                    step--;
                    break;
                } else if (m.optional[step]) {
                    step--; // optional diagonal: try the earlier step against this same frame
                } else {
                    break;
                }
            }
            if (step < 0) return lastFound - 0 <= m.totalTime + buttonAge;
        }
        return false;
    }

    private static boolean contains(int[] set, int d) {
        for (int v : set) if (v == d) return true;
        return false;
    }

    public int frame() {
        return frame;
    }
}
