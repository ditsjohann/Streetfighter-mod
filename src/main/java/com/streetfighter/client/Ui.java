package com.streetfighter.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared drawing helpers for every piece of help text: keyboard key caps and "rich" lines where anything in
 * square brackets is drawn as a key, e.g. {@code "Hold [E] to challenge"}.
 */
public final class Ui {
    public static final int TEXT = 0xFFE8ECF8, DIM = 0xFF9AA3B8, GOLD = 0xFFFFD21A, CYAN = 0xFF7FE0FF;

    private Ui() {
    }

    /** A keyboard key, 11 px tall. Returns its width. */
    public static int keycap(GuiGraphics g, Font font, String label, int x, int y) {
        int w = Math.max(11, font.width(label) + 6);
        g.fill(x, y, x + w, y + 11, 0xFF1C2130);
        g.fill(x, y, x + w, y + 1, 0xFF6A7290);
        g.fill(x, y + 1, x + 1, y + 10, 0xFF4A5068);
        g.fill(x + w - 1, y + 1, x + w, y + 10, 0xFF4A5068);
        g.fill(x, y + 10, x + w, y + 11, 0xFF0A0C12);
        // the label sits a layer above its box: text and boxes are batched separately, so draw order alone
        // doesn't decide which ends up on top
        g.pose().pushPose();
        g.pose().translate(0, 0, 1);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 2, 0xFFFFFFFF, false);
        g.pose().popPose();
        return w;
    }

    public static int keycapWidth(Font font, String label) {
        return Math.max(11, font.width(label) + 6);
    }

    /** Splits a rich string into words and [key] tokens. */
    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') {
                int end = s.indexOf(']', i);
                if (end > i) {
                    if (cur.length() > 0) out.add(cur.toString());
                    cur.setLength(0);
                    out.add(s.substring(i, end + 1));
                    i = end;
                    continue;
                }
            }
            if (c == ' ') {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
                out.add(" ");
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static boolean isKey(String t) {
        return t.length() > 2 && t.startsWith("[") && t.endsWith("]");
    }

    private static int tokenWidth(Font font, String t) {
        return isKey(t) ? keycapWidth(font, t.substring(1, t.length() - 1)) : font.width(t);
    }

    /** Width of a rich line (no wrapping). */
    public static int richWidth(Font font, String s) {
        int w = 0;
        for (String t : tokens(s)) w += tokenWidth(font, t);
        return w;
    }

    /** Draws a rich line; returns its width. */
    public static int rich(GuiGraphics g, Font font, String s, int x, int y, int color) {
        int cx = x;
        for (String t : tokens(s)) {
            if (isKey(t)) cx += keycap(g, font, t.substring(1, t.length() - 1), cx, y - 2);
            else {
                g.drawString(font, t, cx, y, color, true);
                cx += font.width(t);
            }
        }
        return cx - x;
    }

    /** Draws a rich paragraph wrapped to {@code width}; returns the height used. */
    public static int richWrapped(GuiGraphics g, Font font, String s, int x, int y, int width, int color, int lineH) {
        List<String> lines = wrap(font, s, width);
        int yy = y;
        for (String l : lines) {
            if (g != null) rich(g, font, l, x, yy, color);
            yy += lineH;
        }
        return yy - y;
    }

    /** Word-wraps a rich string (keys never split). */
    public static List<String> wrap(Font font, String s, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int w = 0;
        for (String t : tokens(s)) {
            int tw = tokenWidth(font, t);
            if (t.equals(" ") && w == 0) continue;
            if (w + tw > width && w > 0 && !t.equals(" ")) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
                w = 0;
            }
            line.append(t);
            w += tw;
        }
        if (line.length() > 0) lines.add(line.toString().stripTrailing());
        return lines;
    }

    /** A dark panel with a coloured accent edge on the left. */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int accent) {
        g.fill(x0, y0, x1, y1, 0xD80A0E1A);
        g.fill(x0, y0, x0 + 2, y1, accent);
        g.fill(x0, y0, x1, y0 + 1, 0x40FFFFFF);
    }
}
