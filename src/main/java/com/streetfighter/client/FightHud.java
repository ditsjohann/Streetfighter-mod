package com.streetfighter.client;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.FighterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Street Fighter style overlay drawn on top of the live world view. */
public final class FightHud {
    private final FightSession s;
    private final Font font;

    public FightHud(FightSession session, Font font) {
        this.s = session;
        this.font = font;
    }

    // ------------------------------------------------------------------ primitives

    static int argb(int a, int rgb) {
        return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Parallelogram filled row by row; {@code slant} px of horizontal shift from bottom to top. */
    private static void slant(GuiGraphics g, int x0, int x1, int y, int h, int slant, int color) {
        if (x1 <= x0) return;
        for (int r = 0; r < h; r++) {
            int off = (int) ((h - 1 - r) / (float) Math.max(1, h - 1) * slant);
            g.fill(x0 + off, y + r, x1 + off, y + r + 1, color);
        }
    }

    public void outlined(GuiGraphics g, String text, float cx, float cy, float scale, int color, int outline) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        int w = font.width(text);
        int x = -w / 2, y = -4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.drawString(font, text, x + dx, y + dy, outline, false);
            }
        }
        g.drawString(font, text, x + 1, y + 2, argb(160, 0), false);
        g.drawString(font, text, x, y, color, false);
        g.pose().popPose();
    }

    private void portrait(GuiGraphics g, LivingEntity e, int x, int y, int size, boolean faceRight, int border) {
        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, border);
        g.fillGradient(x, y, x + size, y + size, 0xFF2A3350, 0xFF0B0F1C);
        if (e == null) return;
        FighterRenderer.portrait(g, e, x, y, size, size, faceRight, 1f);
    }

    // ------------------------------------------------------------------ main HUD

    public void render(GuiGraphics g, int w, int h) {
        letterbox(g, w, h);
        FightEngine e = s.engine;
        if (e == null) return;
        Minecraft mc = Minecraft.getInstance();
        int cx = w / 2;
        int barH = Mth.clamp(h / 28, 7, 13);
        int box = barH * 3 + 4;
        int top = 6;
        int barY = top + box / 2 - barH / 2 - 3;
        int barX0 = 8 + box + 6;
        int barW = Math.max(60, cx - 28 - barX0);

        // portraits
        portrait(g, mc.player, 8, top, box, true, 0xFFE8C04A);
        portrait(g, s.enemyEntity, w - 8 - box, top, box, false, 0xFFE8C04A);

        // health bars (P1 drains toward the portrait, like SF)
        healthBar(g, e.player, barX0, barY, barW, barH, false);
        healthBar(g, e.enemy, w - barX0 - barW, barY, barW, barH, true);

        // names
        String pName = e.player.name.toUpperCase();
        g.drawString(font, pName, barX0, barY + barH + 4, 0xFFFFFFFF, true);
        if (s.playerStyle != FighterType.STEVE) {
            g.drawString(font, " §b§o" + s.playerStyle.displayName + " style", barX0 + font.width(pName), barY + barH + 4, 0xFFFFFFFF, true);
        }
        String eName = e.enemy.name.toUpperCase();
        g.drawString(font, eName, w - barX0 - font.width(eName), barY + barH + 4, 0xFFFFFFFF, true);

        // round badges
        for (int i = 0; i < FightEngine.ROUNDS_TO_WIN; i++) {
            badge(g, cx - 34 - i * 12, barY + barH + 6, i < e.player.roundsWon);
            badge(g, cx + 34 + i * 12 - 8, barY + barH + 6, i < e.enemy.roundsWon);
        }

        // timer
        int secs = (int) Math.ceil(e.timer / 60f);
        g.fill(cx - 21, top - 2, cx + 21, top + box - 6, 0xFFE8C04A);
        g.fillGradient(cx - 19, top, cx + 19, top + box - 8, 0xFF1B2238, 0xFF06080F);
        boolean low = secs <= 10 && e.phase == FightEngine.Phase.FIGHTING;
        outlined(g, String.valueOf(Math.min(99, secs)), cx, top + (box - 8) / 2f, 2.2f,
                low && (e.timer / 15) % 2 == 0 ? 0xFFFF4A3A : 0xFFFFE070, 0xFF000000);
        String diff = s.difficulty == AIDifficulty.HARD ? "HARD" : "NORMAL";
        g.drawCenteredString(font, diff, cx, top + box - 4, s.difficulty == AIDifficulty.HARD ? 0xFFFF6B5B : 0xFF8BEA8B);

        // super meters
        int mw = Math.max(70, w / 4), mh = Math.max(6, barH - 3);
        int my = h - mh - 12 - (int) (s.hud.letterbox * h * 0.06f);
        superMeter(g, e.player, 12, my, mw, mh, false);
        superMeter(g, e.enemy, w - 12 - mw, my, mw, mh, true);
        heldProp(g, e.player, 12, my - 50, false);
        heldProp(g, e.enemy, w - 12, my - 50, true);
        statusTags(g, e.player, 12, my - 24, false, w);
        statusTags(g, e.enemy, w - 12, my - 24, true, w);

        if (e.struggle != null) struggleBar(g, e.struggle, w, h);
        combo(g, e.player, false, w, h);
        combo(g, e.enemy, true, w, h);
        callouts(g, w, barY + barH + 22, barX0);
        lowHealthVignette(g, e.player, w, h);
        announcer(g, w, h);
        if (s.hud.superBanner > 0) superCutIn(g, w, h);
        if (s.hud.flash > 0) g.fill(0, 0, w, h, argb(s.hud.flash * 18, 0xFFFFFF));

        boolean tab = org.lwjgl.glfw.GLFW.glfwGetKey(net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (!tab && e.player.heldProp == null && e.enemy.heldProp == null && !s.paused && !e.isOver()) {
            if (s.coach.showing()) {
                s.coach.render(g, font, e, w, my - 10);
            } else if (e.phase == FightEngine.Phase.INTRO || (e.phase == FightEngine.Phase.FIGHTING && e.round == 1
                    && e.timer > FightEngine.ROUND_FRAMES - 60 * 7)) {
                help(g, w, my - 32); // above the status tags
            }
        }
    }

    /** Beam struggle: whoever mashes faster pushes the meeting point toward the other. */
    private void struggleBar(GuiGraphics g, com.streetfighter.fight.BeamStruggle st, int w, int h) {
        int bw = Math.min(260, w - 60), bh = 10, x = w / 2 - bw / 2, y = (int) (h * 0.24f);
        g.fill(x - 2, y - 2, x + bw + 2, y + bh + 2, 0xFF000000);
        int mid = x + bw / 2 + (int) (st.progress * bw / 2f);
        g.fillGradient(x, y, mid, y + bh, 0xFF5CC8FF, 0xFF1A5CA0);
        g.fillGradient(mid, y, x + bw, y + bh, 0xFFFF7A4A, 0xFFA02A10);
        g.fill(mid - 2, y - 4, mid + 2, y + bh + 4, 0xFFFFFFFF);
        boolean blink = (st.timer / 6) % 2 == 0;
        outlined(g, "MASH!", w / 2f, y - 12, 2f, blink ? 0xFFFFE070 : 0xFFFFFFFF, 0xFF000000);
        g.drawString(font, "YOU", x, y + bh + 4, 0xFF7FE0FF, true);
        g.drawString(font, "CPU", x + bw - font.width("CPU"), y + bh + 4, 0xFFFF8A6A, true);
    }

    /** What a fighter is holding: icon, name, time/uses left, and (for the player) how to use it. */
    private void heldProp(GuiGraphics g, Fighter f, int x, int y, boolean rightSide) {
        com.streetfighter.fight.Prop pr = f.heldProp;
        if (pr == null) return;
        String name = pr.displayName.toUpperCase() + "  " + (f.heldTimer + 59) / 60 + "s  x" + f.heldUses;
        String help = "PUNCH: " + pr.wayA.name + "   KICK: " + pr.wayB.name;
        int tw = Math.max(font.width(name), f.index == 0 ? font.width(help) : 0) + 26;
        int x0 = rightSide ? x - tw : x;
        int h = f.index == 0 ? 22 : 13;
        boolean blink = f.heldTimer < 90 && (f.heldTimer / 6) % 2 == 0;
        g.fill(x0, y, x0 + tw, y + h, blink ? 0x80301000 : 0xC0101808);
        g.fill(x0, y + h - 1, x0 + (int) (tw * Math.min(1f, f.heldTimer / (float) com.streetfighter.fight.Prop.HOLD_FRAMES)), y + h,
                0xFFFFD21A);
        g.renderItem(com.streetfighter.client.world.PropRenderer.stackOf(pr), x0 + 3, y + (h - 16) / 2);
        g.drawString(font, name, x0 + 22, y + 2, 0xFFFFE27A, true);
        if (f.index == 0) g.drawString(font, help, x0 + 22, y + 12, 0xFFBBBBBB, false);
    }

    /** Small coloured pills for active status effects (POISON 4s, STRENGTH 8s...). */
    private void statusTags(GuiGraphics g, Fighter f, int x, int y, boolean rightSide, int w) {
        int cx = x;
        for (com.streetfighter.fight.StatusEffect fx : com.streetfighter.fight.StatusEffect.ALL) {
            int left = f.status[fx.ordinal()];
            if (left <= 0) continue;
            String name = switch (fx) {
                case POISON -> "POISON";
                case SLOW -> "SLOW";
                case STRENGTH -> "STR";
                case SPEED -> "SPEED";
                case REGEN -> "REGEN";
                case HUNGER -> "HUNGER";
                case SUPER_FORM -> "SUPER";
                case BURN -> "BURN";
            };
            String txt = name + " " + (left + 59) / 60 + "s";
            int tw = font.width(txt) + 6;
            int x0 = rightSide ? cx - tw : cx;
            boolean blink = left < 60 && (left / 6) % 2 == 0;
            g.fill(x0, y, x0 + tw, y + 10, blink ? 0x60000000 : 0xC0000000 | (fx.color & 0xFFFFFF) >> 1 & 0x7F7F7F);
            g.fill(x0, y, x0 + 2, y + 10, 0xFF000000 | fx.color);
            g.drawString(font, txt, x0 + 4, y + 1, fx.buff ? 0xFFFFF2B0 : 0xFFD8FFC0, true);
            cx = rightSide ? x0 - 3 : x0 + tw + 3;
        }
    }

    private void letterbox(GuiGraphics g, int w, int h) {
        int lb = (int) (s.hud.letterbox * h * 0.075f);
        if (lb <= 0) return;
        g.fill(0, 0, w, lb, 0xF0000000);
        g.fill(0, h - lb, w, h, 0xF0000000);
    }

    private void badge(GuiGraphics g, int x, int y, boolean won) {
        g.fill(x - 1, y - 1, x + 9, y + 9, 0xFF000000);
        g.fill(x, y, x + 8, y + 8, won ? 0xFFFFC21A : 0xFF30364A);
        if (won) g.drawString(font, "V", x + 2, y, 0xFF5A2A00, false);
    }

    private void healthBar(GuiGraphics g, Fighter f, int x, int y, int w, int h, boolean rightSide) {
        int sl = rightSide ? -h : h;
        slant(g, x - 3, x + w + 3, y - 2, h + 4, sl, 0xFFE8C04A);
        slant(g, x - 1, x + w + 1, y - 1, h + 2, sl, 0xFF000000);
        slant(g, x, x + w, y, h, sl, 0xFF4A0A0A);
        float lag = f.displayHp / f.maxHp, cur = f.hp / f.maxHp;
        int lagW = Math.round(w * lag), curW = Math.round(w * cur);
        boolean danger = cur < 0.25f;
        int t = (int) (s.time * 8);
        int top = danger ? (t % 2 == 0 ? 0xFFFF7A1A : 0xFFFFB21A) : 0xFFFFE65A;
        int bottom = danger ? 0xFFC02A0A : 0xFFE89A12;
        if (!rightSide) {
            slant(g, x + w - lagW, x + w, y, h, sl, 0xFFFF3A2A);
            for (int r = 0; r < h; r++) {
                int col = r < h / 2 ? top : bottom;
                int off = (int) ((h - 1 - r) / (float) Math.max(1, h - 1) * sl);
                g.fill(x + w - curW + off, y + r, x + w + off, y + r + 1, col);
            }
        } else {
            slant(g, x, x + lagW, y, h, sl, 0xFFFF3A2A);
            for (int r = 0; r < h; r++) {
                int col = r < h / 2 ? top : bottom;
                int off = (int) ((h - 1 - r) / (float) Math.max(1, h - 1) * sl);
                g.fill(x + off, y + r, x + curW + off, y + r + 1, col);
            }
        }
        slant(g, x, x + w, y, 1, sl, 0x50FFFFFF);
    }

    private void superMeter(GuiGraphics g, Fighter f, int x, int y, int w, int h, boolean rightSide) {
        int bars = f.bars();
        boolean any = bars >= 1;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, any ? 0xFF8FEBFF : 0xFF3A4A7A);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFF0D1430);
        int seg = w / 3;
        for (int i = 0; i < 3; i++) {
            float fill = Math.max(0, Math.min(1, (f.meter - i * 100) / 100f));
            int sx0 = rightSide ? x + w - (i + 1) * seg : x + i * seg;
            int fw = (int) ((seg - 2) * fill);
            boolean full = fill >= 1;
            int c1 = full ? ((int) (s.time * 10 + i) % 2 == 0 ? 0xFF8FEBFF : 0xFFFFFFFF) : 0xFF3F8BFF;
            int c2 = full ? (i == 2 ? 0xFFFFC21A : 0xFF2FB0FF) : 0xFF1A4FCF;
            if (rightSide) g.fillGradient(sx0 + seg - 1 - fw, y, sx0 + seg - 1, y + h, c1, c2);
            else g.fillGradient(sx0 + 1, y, sx0 + 1 + fw, y + h, c1, c2);
            g.fill(rightSide ? sx0 : sx0 + seg - 1, y, (rightSide ? sx0 : sx0 + seg - 1) + 1, y + h, 0xFF000000);
        }
        String lvl = bars > 0 ? String.valueOf(bars) : "";
        String base = s.kiMode ? "KI" : "SUPER";
        String label = bars >= 3 ? base + "  MAX" : base;
        int lx = rightSide ? x + w - font.width(label) : x;
        g.drawString(font, label, lx, y - 11, any ? 0xFF8FEBFF : 0xFFB0B8D0, true);
        if (!lvl.isEmpty()) {
            g.pose().pushPose();
            int bx = rightSide ? x - 16 : x + w + 6;
            g.pose().translate(bx, y - 4, 0);
            g.pose().scale(1.8f, 1.8f, 1f);
            g.drawString(font, lvl, 0, 0, bars >= 3 ? 0xFFFFC21A : 0xFF8FEBFF, true);
            g.pose().popPose();
        }
    }

    private void combo(GuiGraphics g, Fighter attacker, boolean rightSide, int w, int h) {
        if (attacker.comboShown < 2 || attacker.comboTimer <= 0) return;
        float pop = 1f + Math.max(0, attacker.comboTimer - 80) * 0.05f;
        int x = rightSide ? w - 18 : 18;
        int y = (int) (h * 0.32f);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(3f * pop, 3f * pop, 1f);
        String n = String.valueOf(attacker.comboShown);
        int nw = font.width(n);
        g.drawString(font, n, rightSide ? -nw : 0, 0, 0xFFFFB020, true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(x, y + 26 * pop, 0);
        g.pose().scale(1.4f, 1.4f, 1f);
        String hits = "HITS";
        g.drawString(font, hits, rightSide ? -font.width(hits) : 0, 0, 0xFFFFE070, true);
        g.pose().popPose();
    }

    private void callouts(GuiGraphics g, int w, int y0, int margin) {
        List<String[]> list = new ArrayList<>(s.hud.callouts);
        int[] yBySide = {y0, y0};
        for (int i = 0; i < list.size(); i++) {
            String[] c = list.get(i);
            int side = Integer.parseInt(c[1]);
            int color = (int) Long.parseLong(c[2]);
            int timer = s.hud.calloutTimers.get(i)[0];
            int alpha = timer < 20 ? timer * 12 : 255;
            int slide = timer > 80 ? (timer - 80) * 6 : 0;
            String text = c[0];
            int tw = font.width(text);
            int y = yBySide[side];
            if (side == 0) {
                int x = margin - slide;
                g.fill(x - 4, y - 2, x + tw + 4, y + 10, argb(alpha * 2 / 3, 0x000000));
                g.drawString(font, text, x, y, argb(Math.max(8, alpha), color), true);
            } else {
                int x = w - margin - tw + slide;
                g.fill(x - 4, y - 2, x + tw + 4, y + 10, argb(alpha * 2 / 3, 0x000000));
                g.drawString(font, text, x, y, argb(Math.max(8, alpha), color), true);
            }
            yBySide[side] += 13;
        }
    }

    private void lowHealthVignette(GuiGraphics g, Fighter p, int w, int h) {
        if (p.hp <= 0 || p.hp / p.maxHp >= 0.25f) return;
        int a = (int) (60 + 50 * Math.sin(s.time * 6));
        int edge = Math.max(10, h / 10);
        g.fillGradient(0, 0, w, edge, argb(a, 0xC00000), 0x00C00000);
        g.fillGradient(0, h - edge, w, h, 0x00C00000, argb(a, 0xC00000));
    }

    /** A compact key card for the first seconds of a fight (after the first-fight training is done). */
    private void help(GuiGraphics g, int w, int bottom) {
        boolean right = s.engine == null || s.engine.player.facingRight;
        String fwd = right ? "D" : "A", back = right ? "A" : "D";
        List<String> rows = s.kiMode
                ? List.of("[W] fly up   [S] fly down   [H] ki blast   hold [H]+[S] charge ki   [" + fwd + "] [" + fwd + "] dragon dash",
                "[U] [U] [U] [U] rush   [O] smash   [I]+[K] vanish   hold [" + back + "] guard   hold [TAB] specials")
                : List.of("[A] [D] move   [W] jump   [S] crouch   hold [" + back + "] block   [U] [I] [O] punch   [J] [K] [L] kick",
                "[H] + direction: special moves   hold [TAB] to see them   [ESC] pause and move list");
        int rowH = 14;
        int y = bottom - rows.size() * rowH;
        for (String row : rows) {
            int rw = Ui.richWidth(font, row);
            int x = w / 2 - rw / 2;
            g.fill(x - 6, y - 3, x + rw + 6, y + rowH - 3, 0xA0000000);
            Ui.rich(g, font, row, x, y, Ui.TEXT);
            y += rowH;
        }
    }


    /** While H is held: the player's specials as one-button and full inputs, keys flipped for the current side. */
    /** Hold TAB: your special moves as keys. */
    public void specialsHint(GuiGraphics g, int w, int h) {
        if (s.engine == null) return;
        var f = s.engine.player;
        var ms = f.moves;
        boolean right = f.facingRight;
        List<String[]> rows = new ArrayList<>();
        for (var sp : ms.specials) {
            String keys = "[" + KeyNotation.simple(sp.modernDir(), right).replace("+", "] + [") + "]";
            rows.add(new String[]{keys, sp.name(), KeyNotation.special(sp.motion(), sp.kick(), right)});
        }
        String[] superNames = new String[3];
        for (var su : ms.supers) superNames[su.level() - 1] = su.move().name;
        rows.add(new String[]{"[H] + [O]", "SUPER: " + (superNames[0] != null ? superNames[0] : "Lv1"), "1 bar"});
        rows.add(new String[]{"[H] + [L]", "SUPER: " + (superNames[2] != null ? superNames[2] : "Lv2/3"), "2-3 bars"});
        int keyW = 0, nameW = 0, extraW = 0;
        for (String[] r : rows) {
            keyW = Math.max(keyW, Ui.richWidth(font, r[0]));
            nameW = Math.max(nameW, font.width(r[1]));
            extraW = Math.max(extraW, font.width(r[2]));
        }
        int bw = keyW + nameW + extraW + 40, bh = 22 + rows.size() * 14 + 4;
        int x = w / 2 - bw / 2, y = h / 2 - bh / 2 + 20;
        Ui.panel(g, x, y, x + bw, y + bh, Ui.CYAN);
        g.drawString(font, ms.type().displayName.toUpperCase() + " SPECIALS", x + 8, y + 6, Ui.CYAN, true);
        String sub = "keys flip when you switch sides";
        g.drawString(font, sub, x + bw - font.width(sub) - 8, y + 6, Ui.DIM, false);
        int yy = y + 22;
        for (String[] r : rows) {
            Ui.rich(g, font, r[0], x + 8, yy, Ui.TEXT);
            g.drawString(font, r[1], x + 16 + keyW, yy, r[1].startsWith("SUPER") ? Ui.GOLD : 0xFFFFFFFF, true);
            g.drawString(font, r[2], x + 24 + keyW + nameW, yy, Ui.DIM, false);
            yy += 14;
        }
    }


    String[] wrap(String text, int maxWidth) {
        String sep = text.contains("   ") ? "   " : " ";
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String part : text.split(sep)) {
            String cand = cur.length() == 0 ? part : cur + sep + part;
            if (font.width(cand) > maxWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(part);
            } else cur = new StringBuilder(cand);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private void announcer(GuiGraphics g, int w, int h) {
        FightEngine e = s.engine;
        int t = e.phaseTimer;
        float cy = h * 0.36f;
        float pop = Math.max(1f, 1.8f - t * 0.08f);
        switch (e.phase) {
            case INTRO -> {
                if (t > 10) {
                    boolean fin = e.player.roundsWon == FightEngine.ROUNDS_TO_WIN - 1 && e.enemy.roundsWon == FightEngine.ROUNDS_TO_WIN - 1;
                    float sc = 4.5f * Math.max(1f, 1.8f - (t - 10) * 0.08f);
                    speedLines(g, w, (int) cy, 0x40FFE070, t);
                    outlined(g, fin ? "FINAL ROUND" : "ROUND " + e.round, w / 2f, cy, sc, 0xFFFFE070, 0xFF5A2A00);
                }
            }
            case FIGHT_CALL -> {
                speedLines(g, w, (int) cy, 0x60FF4A2A, t * 3);
                float shake = t < 10 ? (float) Math.sin(t * 3) * 3 : 0;
                outlined(g, "FIGHT!", w / 2f + shake, cy, 6f * pop, 0xFFFF4A2A, 0xFFFFFFFF);
            }
            case KO -> {
                if (e.timeOver) outlined(g, "TIME", w / 2f, cy, 6f * pop, 0xFFFFE070, 0xFF000000);
                else if (s.finisher == null) outlined(g, "K.O.", w / 2f, cy, 7f * pop, 0xFFFF2A1A, 0xFFFFFFFF);
            }
            case ROUND_END -> {
                if (s.finisher != null) break; // the finisher already announced the winner
                String text;
                int col;
                if (e.roundWinner == -1) {
                    text = "DOUBLE K.O.";
                    col = 0xFFDDDDDD;
                } else if (e.roundWinner == e.player.index) {
                    text = "YOU WIN";
                    col = 0xFF5CFF7A;
                } else {
                    text = e.enemy.name.toUpperCase() + " WINS";
                    col = 0xFFFF6A5A;
                }
                outlined(g, text, w / 2f, cy, 4f, col, 0xFF000000);
                if (e.roundWinner >= 0 && e.fighter(e.roundWinner).hp >= e.fighter(e.roundWinner).maxHp) {
                    outlined(g, "PERFECT", w / 2f, cy + 34, 3f, (int) (s.time * 8) % 2 == 0 ? 0xFFFFD21A : 0xFFFFFFFF, 0xFF5A2A00);
                }
            }
            default -> {
            }
        }
    }

    private void speedLines(GuiGraphics g, int w, int cy, int color, int t) {
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 18; i++) {
            int y = cy - 40 + r.nextInt(80);
            int len = 40 + r.nextInt(120);
            int x = (int) ((r.nextInt(w + 200) + t * 18L) % (w + 200)) - 100;
            g.fill(x, y, x + len, y + 1, color);
        }
    }

    private void superCutIn(GuiGraphics g, int w, int h) {
        int t = 80 - s.hud.superBanner;
        boolean left = s.hud.superOwner == 0;
        int bandH = Math.max(40, h / 5);
        int y = (int) (h * 0.5f) - bandH / 2;
        float in = Math.min(1f, t / 8f), out = s.hud.superBanner < 10 ? s.hud.superBanner / 10f : 1f;
        int a = (int) (220 * in * out);
        g.fill(0, 0, w, h, argb((int) (90 * in * out), 0x000010));
        g.fill(0, y, w, y + bandH, argb(a, 0x101836));
        g.fill(0, y, w, y + 2, argb(a, 0xFFD21A));
        g.fill(0, y + bandH - 2, w, y + bandH, argb(a, 0xFFD21A));
        speedLines(g, w, y + bandH / 2, argb(a / 2, 0x8FEBFF), t * 4);
        // the attacker's portrait slides across the band
        LivingEntity who = left ? Minecraft.getInstance().player : s.enemyEntity;
        int ps = bandH - 6;
        float slide = Math.min(1f, t / 10f);
        int px = left ? (int) (-ps + slide * (w * 0.12f + ps)) : (int) (w - slide * (w * 0.12f + ps));
        if (who != null) portrait(g, who, px, y + 3, ps, left, argb(a, 0xFFD21A));
        int tx = left ? px + ps + 14 : px - 14;
        g.pose().pushPose();
        g.pose().translate(tx, y + bandH / 2f, 0);
        g.pose().scale(2.6f, 2.6f, 1f);
        int tw = font.width(s.hud.superText);
        g.drawString(font, s.hud.superText, left ? 0 : -tw, -4, argb(Math.max(8, a), 0xFFE070), true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ panels

    public void pausePanel(GuiGraphics g, int w, int h, int page) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // above every HUD layer
        pausePanelInner(g, w, h, page);
        g.pose().popPose();
    }

    private void pausePanelInner(GuiGraphics g, int w, int h, int page) {
        g.fill(0, 0, w, h, 0xE6050812);
        outlined(g, "PAUSED", w / 2f, 20, 3f, 0xFFFFFFFF, 0xFF000000);
        String keys = "[ESC] resume   [Q] give up   [TAB] show " + (page == 0 ? "normals" : "specials") + "   [G] Fight Guide   [T] training tips: "
                + (Coach.enabled() ? "ON" : "OFF");
        Ui.rich(g, font, keys, w / 2 - Ui.richWidth(font, keys) / 2, 38, 0xFFDDDDDD);
        var ms = s.engine.player.moves;
        java.util.List<String> lines = FightScreen.moveList(ms, page, s.engine.player.facingRight);
        int y = 56;
        int x = Math.max(10, w / 2 - 230);
        g.drawString(font, "\u00a7b" + ms.type().displayName.toUpperCase() + " STYLE \u00a77- " + ms.moveCount() + " moves", x, y, 0xFFFFFFFF, true);
        y += 14;
        for (String line : lines) {
            if (y > h - 14) break;
            g.drawString(font, line, x, y, 0xFFE0E6FF, false);
            y += 11;
        }
    }

    /** Beginner tips shown after a loss. */
    private static final String[] TIPS = {
            "Tip: hold the key pointing AWAY from your opponent to block.",
            "Tip: [H] + a direction does a special move. Hold [TAB] in a fight to see yours.",
            "Tip: crouch-block ([S] + away) stops sweeps and other low attacks.",
            "Tip: [U]+[J] together is a throw, and throws beat blocking.",
            "Tip: tap away twice to backdash out of trouble.",
            "Tip: when your SUPER bar is full, press [H] + [O].",
            "Tip: training mode (/streetfighter training on) lets you practise against a mob that stands still."};

    public void resultPanel(GuiGraphics g, int w, int h, int frames) {
        boolean won = s.engine.playerWon();
        float in = Math.min(1f, frames / 20f);
        int bandTop = (int) (h * 0.30f), bandBot = (int) (h * 0.70f);
        g.fill(0, bandTop, w, bandBot, argb((int) (190 * in), 0x05070F));
        g.fill(0, bandTop, w, bandTop + 2, won ? 0xFFFFD21A : 0xFFB02A1A);
        g.fill(0, bandBot - 2, w, bandBot, won ? 0xFFFFD21A : 0xFFB02A1A);
        outlined(g, won ? "VICTORY!" : "DEFEAT", w / 2f, h * 0.40f, 5f * Math.max(1f, 1.6f - frames * 0.03f),
                won ? 0xFFFFD21A : 0xFFFF4A3A, 0xFF000000);
        g.drawCenteredString(font, s.engine.player.roundsWon + " - " + s.engine.enemy.roundsWon + "   vs   " + s.engine.enemy.name,
                w / 2, (int) (h * 0.49f), 0xFFFFFFFF);
        int y = (int) (h * 0.55f);
        if (won) {
            if (s.enemyType.learnable && !s.unlocked.contains(s.enemyType.id)) {
                outlined(g, "NEW STYLE: " + s.enemyType.displayName.toUpperCase() + " - " + s.enemyType.title.toUpperCase(),
                        w / 2f, y + 4, 1.3f, (int) (s.time * 6) % 2 == 0 ? 0xFF7FE0FF : 0xFFFFFFFF, 0xFF00303A);
                y += 6;
                g.drawCenteredString(font, "Pick it on the select screen in your next fight.", w / 2, y + 14, 0xFFB8C8FF);
                y += 4;
            } else if (s.enemyType.learnable) {
                g.drawCenteredString(font, "You already know the " + s.enemyType.displayName + " style. Respect earned!", w / 2, y, 0xFFB8C8FF);
            } else {
                g.drawCenteredString(font, "Wild mobs fight in a brawler style: there's no style to learn from this one.", w / 2, y, 0xFFB8C8FF);
            }
            g.drawCenteredString(font, "Your loot dropped where the fight happened.", w / 2, y + 14, 0xFF9CFFA0);
        } else {
            g.drawCenteredString(font, "You'll be bruised for 30 seconds... challenge them again when you're ready!", w / 2, y, 0xFFFFB0A0);
            Ui.rich(g, font, TIPS[(int) (s.enemyEntity.getId() % TIPS.length)], w / 2 - Ui.richWidth(font, TIPS[(int) (s.enemyEntity.getId() % TIPS.length)]) / 2, y + 14, 0xFFCFF4FF);
        }
        if (frames > 45 && (frames / 20) % 2 == 0) {
            Ui.rich(g, font, "Press [SPACE] to continue", w / 2 - Ui.richWidth(font, "Press [SPACE] to continue") / 2, (int) (h * 0.66f), 0xFFFFFFFF);
        }
    }
}
