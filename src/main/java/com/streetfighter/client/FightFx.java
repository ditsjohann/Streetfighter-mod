package com.streetfighter.client;

import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Little 2D effects: hit sparks, dust, shockwaves, explosions and floating callouts. */
final class FightFx {
    enum Kind { SPARK, BLOCK_SPARK, DUST, SHOCKWAVE, EXPLOSION, CLASH, TEXT, FLASH }

    static final class Fx {
        final Kind kind;
        final float x, y;
        final int life;
        final boolean heavy;
        final String text;
        final int color;
        final long seed;
        int age;

        Fx(Kind kind, float x, float y, int life, boolean heavy, String text, int color, long seed) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.life = life;
            this.heavy = heavy;
            this.text = text;
            this.color = color;
            this.seed = seed;
        }
    }

    private final List<Fx> list = new ArrayList<>();
    private final Random random = new Random();

    void add(Kind kind, float x, float y, boolean heavy) {
        int life = switch (kind) {
            case SPARK -> heavy ? 14 : 10;
            case BLOCK_SPARK -> 10;
            case DUST -> 22;
            case SHOCKWAVE -> 26;
            case EXPLOSION -> 40;
            case CLASH -> 14;
            case TEXT -> 70;
            case FLASH -> 10;
        };
        list.add(new Fx(kind, x, y, life, heavy, null, 0xFFFFFFFF, random.nextLong()));
    }

    void text(float x, float y, String text, int color) {
        list.add(new Fx(Kind.TEXT, x, y, 70, false, text, color, 0));
    }

    void step() {
        Iterator<Fx> it = list.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            if (++f.age >= f.life) it.remove();
        }
    }

    void clear() {
        list.clear();
    }

    /** Draws all effects; {@code sx/sy} map arena coordinates to screen. */
    void render(GuiGraphics g, Font font, FightScreen.Mapper m, int screenW, int screenH) {
        for (Fx f : list) {
            float p = f.age / (float) f.life;
            float x = m.sx(f.x), y = m.sy(f.y);
            float s = m.scale();
            int alpha = (int) (255 * (1f - p));
            switch (f.kind) {
                case SPARK -> rays(g, x, y, (f.heavy ? 34 : 22) * s * (0.5f + p), 3, f.heavy ? 10 : 8,
                        withAlpha(f.heavy ? 0xFFB020 : 0xFFF6B0, alpha), f.seed);
                case BLOCK_SPARK -> {
                    rays(g, x, y, 20 * s * (0.5f + p), 2, 6, withAlpha(0x6FD8FF, alpha), f.seed);
                    int r = (int) (6 * s);
                    g.fill((int) x - r, (int) y - r, (int) x + r, (int) y + r, withAlpha(0xBFF0FF, alpha / 2));
                }
                case CLASH -> {
                    rays(g, x, y, 40 * s * (0.4f + p), 4, 12, withAlpha(0xFFFFFF, alpha), f.seed);
                }
                case DUST -> {
                    Random r = new Random(f.seed);
                    for (int i = 0; i < 6; i++) {
                        float dir = i % 2 == 0 ? 1 : -1;
                        float dx = dir * (10 + r.nextFloat() * 30) * p * s;
                        float dy = -(4 + r.nextFloat() * 16) * p * s;
                        int size = (int) ((3 + r.nextFloat() * 5) * s * (1 + p));
                        g.fill((int) (x + dx), (int) (y + dy - size), (int) (x + dx + size), (int) (y + dy),
                                withAlpha(0xB8A890, (int) (alpha * 0.8f)));
                    }
                }
                case SHOCKWAVE -> {
                    float w = (30 + 140 * p) * s;
                    int h = Math.max(2, (int) (6 * s * (1 - p)));
                    int col = withAlpha(0xFFFFFF, (int) (alpha * 0.8f));
                    g.fill((int) (x - w), (int) y - h, (int) (x - w + 18 * s), (int) y, col);
                    g.fill((int) (x + w - 18 * s), (int) y - h, (int) (x + w), (int) y, col);
                    Random r = new Random(f.seed);
                    for (int i = 0; i < 10; i++) {
                        float dx = (r.nextFloat() * 2 - 1) * w;
                        int size = (int) ((4 + r.nextFloat() * 6) * s);
                        float dy = -r.nextFloat() * 30 * s * p;
                        g.fill((int) (x + dx), (int) (y + dy - size), (int) (x + dx + size), (int) (y + dy),
                                withAlpha(0x9C8A70, alpha));
                    }
                }
                case EXPLOSION -> {
                    Random r = new Random(f.seed);
                    for (int i = 0; i < 16; i++) {
                        float ang = r.nextFloat() * (float) Math.PI * 2;
                        float dist = r.nextFloat() * 50 * s * (0.3f + p);
                        float cx = x + (float) Math.cos(ang) * dist;
                        float cy = y + (float) Math.sin(ang) * dist * 0.8f;
                        int size = (int) ((8 + r.nextFloat() * 14) * s * (1 - p * 0.5f));
                        int col = p < 0.35f ? (i % 3 == 0 ? 0xFFFFFF : i % 3 == 1 ? 0xFFD040 : 0xFF7A20) : 0x6A6A6A;
                        g.fill((int) cx - size / 2, (int) cy - size / 2, (int) cx + size / 2, (int) cy + size / 2,
                                withAlpha(col, alpha));
                    }
                }
                case FLASH -> g.fill(0, 0, screenW, screenH, withAlpha(0xFFFFFF, (int) (alpha * 0.7f)));
                case TEXT -> {
                    float rise = 26 * s * Math.min(1f, p * 3f);
                    float sc = 1.2f + (f.age < 6 ? (6 - f.age) * 0.12f : 0);
                    int a = p > 0.75f ? (int) (255 * (1 - (p - 0.75f) / 0.25f)) : 255;
                    g.pose().pushPose();
                    g.pose().translate(x, y - rise, 0);
                    g.pose().scale(sc, sc, 1);
                    int w = font.width(f.text);
                    g.drawString(font, f.text, -w / 2, -4, withAlpha(f.color & 0xFFFFFF, Math.max(8, a)), true);
                    g.pose().popPose();
                }
            }
        }
    }

    private static void rays(GuiGraphics g, float x, float y, float radius, int thickness, int count, int color, long seed) {
        Random r = new Random(seed);
        float offset = r.nextFloat() * 360;
        for (int i = 0; i < count; i++) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(offset + i * 360f / count));
            float len = radius * (0.7f + r.nextFloat() * 0.5f);
            g.fill((int) (len * 0.35f), -thickness / 2, (int) len, thickness - thickness / 2, color);
            g.pose().popPose();
        }
    }

    static int withAlpha(int rgb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
