package com.streetfighter.client;

import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FighterType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/** Draws a side-on Minecraft-themed stage behind the fighters, picked by who you're fighting. */
final class StageRenderer {
    static final float BLOCK = FightScreen.UNITS_PER_BLOCK;

    enum Theme {
        CRYPT("Moonlit Crypt", 0xFF060A1C, 0xFF2A3360, "stone_bricks", "deepslate", true),
        VILLAGE("Village Square", 0xFF5C98FF, 0xFFC4E2FF, "grass_block_side", "dirt", false),
        FARM("Muddy Farm", 0xFFFF7448, 0xFFFFD98A, "grass_block_side", "mud", false),
        PLAINS("Open Plains", 0xFF6FA8FF, 0xFFD6ECFF, "grass_block_side", "dirt", false);

        final String title;
        final int skyTop, skyBottom;
        final String topBlock, fillBlock;
        final boolean night;

        Theme(String title, int skyTop, int skyBottom, String topBlock, String fillBlock, boolean night) {
            this.title = title;
            this.skyTop = skyTop;
            this.skyBottom = skyBottom;
            this.topBlock = topBlock;
            this.fillBlock = fillBlock;
            this.night = night;
        }

        static Theme forEnemy(FighterType type) {
            return switch (type) {
                case SKELETON -> CRYPT;
                case VILLAGER -> VILLAGE;
                case PIG -> FARM;
                default -> PLAINS;
            };
        }
    }

    private final Theme theme;

    StageRenderer(Theme theme) {
        this.theme = theme;
    }

    Theme theme() {
        return theme;
    }

    static ResourceLocation block(String name) {
        return ResourceLocation.withDefaultNamespace("textures/block/" + name + ".png");
    }

    static void tile(GuiGraphics g, ResourceLocation tex, int x0, int y0, int x1, int y1) {
        if (x1 <= x0 || y1 <= y0) return;
        g.blit(tex, x0, y0, x1 - x0, y1 - y0, 0, 0, 16, 16, 16, 16);
    }

    void render(GuiGraphics g, FightScreen.Mapper m, int w, int h, float time) {
        g.fillGradient(0, 0, w, h, theme.skyTop, theme.skyBottom);
        float s = m.scale();
        int groundY = (int) m.sy(0);

        // sun / moon & stars
        if (theme.night) {
            Random r = new Random(1234);
            for (int i = 0; i < 70; i++) {
                int sx = (int) (r.nextFloat() * w), sy = (int) (r.nextFloat() * groundY * 0.8f);
                boolean twinkle = ((int) (time * 2 + i) % 7) == 0;
                g.fill(sx, sy, sx + 1, sy + 1, twinkle ? 0xFF8890B0 : 0xFFE8ECFF);
            }
            int mx = (int) (w * 0.78f - m.camX() * 0.05f * s), my = (int) (h * 0.12f), ms = (int) (34 * s);
            g.fill(mx - 3, my - 3, mx + ms + 3, my + ms + 3, 0x40C8D4FF);
            g.fill(mx, my, mx + ms, my + ms, 0xFFE9EEF8);
            g.fill(mx + ms / 5, my + ms / 4, mx + ms / 5 + ms / 6, my + ms / 4 + ms / 6, 0xFFC9D0E0);
            g.fill(mx + ms / 2, my + ms / 2, mx + ms / 2 + ms / 4, my + ms / 2 + ms / 5, 0xFFC9D0E0);
        } else {
            int sunCol = theme == Theme.FARM ? 0xFFFFB347 : 0xFFFFF59A;
            int sx = (int) (w * 0.2f - m.camX() * 0.05f * s), sy = (int) (h * (theme == Theme.FARM ? 0.30f : 0.10f)), ss = (int) (40 * s);
            g.fill(sx - 4, sy - 4, sx + ss + 4, sy + ss + 4, (sunCol & 0xFFFFFF) | 0x50000000);
            g.fill(sx, sy, sx + ss, sy + ss, sunCol);
            // clouds
            for (int i = 0; i < 6; i++) {
                float cx = ((i * 260 + time * 9) % (FightEngine.ARENA_WIDTH + 400)) - 200;
                int x = (int) ((cx - m.camX() * 0.25f) * s);
                int y = (int) (h * 0.08f + (i * 37 % 60) * s);
                int cw = (int) ((80 + i * 13 % 50) * s), ch = (int) (16 * s);
                g.fill(x, y, x + cw, y + ch, 0xD0FFFFFF);
                g.fill(x + cw / 4, y - ch / 2, x + cw * 3 / 4, y, 0xD0FFFFFF);
            }
        }

        // far hills (parallax)
        int hillCol = switch (theme) {
            case CRYPT -> 0xFF161B33;
            case VILLAGE -> 0xFF5E9C58;
            case FARM -> 0xFFB86A48;
            case PLAINS -> 0xFF68A862;
        };
        float par = 0.35f;
        for (int i = -2; i < 30; i++) {
            float ax = i * BLOCK * 1.6f;
            int x0 = (int) ((ax - m.camX() * par) * s), x1 = (int) ((ax + BLOCK * 1.6f - m.camX() * par) * s);
            int hh = (int) ((60 + ((i * 7919) % 5) * 22) * s);
            g.fill(x0, groundY - hh - (int) (BLOCK * 0.6f * s), x1, groundY, hillCol);
        }

        // themed mid-ground props
        switch (theme) {
            case CRYPT -> crypt(g, m, groundY, s);
            case VILLAGE -> village(g, m, groundY, s);
            case FARM -> farm(g, m, groundY, s);
            case PLAINS -> plains(g, m, groundY, s);
        }

        // the floor
        ResourceLocation top = block(theme.topBlock), fill = block(theme.fillBlock);
        int bs = Math.max(1, Math.round(BLOCK * s));
        int first = (int) Math.floor(m.camX() / BLOCK) - 1;
        int last = (int) Math.ceil((m.camX() + w / s) / BLOCK) + 1;
        for (int i = first; i <= last; i++) {
            int x0 = Math.round(m.sx(i * BLOCK)), x1 = Math.round(m.sx((i + 1) * BLOCK));
            tile(g, top, x0, groundY, x1, groundY + bs);
            for (int row = 1; groundY + row * bs < h; row++) {
                ResourceLocation tex = fill;
                if (theme == Theme.FARM && Math.floorMod(i * 31 + row * 7, 5) == 0) tex = block("dirt");
                if (theme == Theme.CRYPT && Math.floorMod(i * 13 + row * 5, 6) == 0) tex = block("cobbled_deepslate");
                tile(g, tex, x0, groundY + row * bs, x1, groundY + (row + 1) * bs);
            }
        }
        // arena edge markers (walls)
        int wl = Math.round(m.sx(0)), wr = Math.round(m.sx(FightEngine.ARENA_WIDTH));
        if (wl > 0) g.fill(0, 0, wl, h, 0xFF000000);
        if (wr < w) g.fill(wr, 0, w, h, 0xFF000000);
    }

    // ------------------------------------------------------------------ props

    private void prop(GuiGraphics g, FightScreen.Mapper m, float par, float ax, int groundY, int bx, int by, String tex) {
        float s = m.scale();
        float base = ax + bx * BLOCK * 0.75f;
        int x0 = Math.round((base - m.camX() * par) * s);
        int x1 = Math.round((base + BLOCK * 0.75f - m.camX() * par) * s);
        int y1 = groundY - Math.round(by * BLOCK * 0.75f * s);
        int y0 = groundY - Math.round((by + 1) * BLOCK * 0.75f * s);
        tile(g, block(tex), x0, y0, x1, y1);
    }

    private void shade(GuiGraphics g, FightScreen.Mapper m, float par, float ax, int groundY, int wBlocks, int hBlocks, int color) {
        float s = m.scale();
        int x0 = Math.round((ax - m.camX() * par) * s);
        int x1 = Math.round((ax + wBlocks * BLOCK * 0.75f - m.camX() * par) * s);
        int y0 = groundY - Math.round(hBlocks * BLOCK * 0.75f * s);
        g.fill(x0, y0, x1, groundY, color);
    }

    private void crypt(GuiGraphics g, FightScreen.Mapper m, int groundY, float s) {
        float par = 0.65f;
        for (int k = 0; k < 9; k++) {
            float ax = 60 + k * 190;
            int height = 1 + (k * 3 % 3);
            String tex = k % 3 == 0 ? "mossy_cobblestone" : "cobblestone";
            for (int y = 0; y < height; y++) prop(g, m, par, ax, groundY, 0, y, tex);
            if (k % 2 == 0) prop(g, m, par, ax, groundY, 1, 0, "cobblestone");
            if (k % 4 == 1) prop(g, m, par, ax + 90, groundY, 0, 0, "cobweb");
            if (k % 3 == 2) prop(g, m, par, ax + 120, groundY, 0, 0, "dead_bush");
            shade(g, m, par, ax, groundY, 2, height, 0x50000018);
        }
        // iron fence silhouette
        for (int k = 0; k < 40; k++) {
            float ax = k * 40;
            int x = Math.round((ax - m.camX() * 0.5f) * s);
            g.fill(x, groundY - (int) (70 * s), x + Math.max(1, (int) (3 * s)), groundY, 0xFF0D1024);
        }
        g.fill(0, groundY - (int) (62 * s), 4000, groundY - (int) (58 * s), 0xFF0D1024);
    }

    private void village(GuiGraphics g, FightScreen.Mapper m, int groundY, float s) {
        float par = 0.65f;
        for (int k = 0; k < 5; k++) {
            float ax = 40 + k * 330;
            for (int x = 0; x < 5; x++) {
                prop(g, m, par, ax, groundY, x, 0, "cobblestone");
                for (int y = 1; y < 4; y++) {
                    String tex = (x == 0 || x == 4) ? "oak_log" : "oak_planks";
                    if (y == 2 && (x == 1 || x == 3)) tex = "glass";
                    if (x == 2 && y < 3) tex = y == 1 ? "oak_door_bottom" : "oak_door_top";
                    prop(g, m, par, ax, groundY, x, y, tex);
                }
            }
            for (int x = -1; x < 6; x++) prop(g, m, par, ax, groundY, x, 4, "spruce_planks");
            for (int x = 1; x < 4; x++) prop(g, m, par, ax, groundY, x, 5, "spruce_planks");
            shade(g, m, par, ax - BLOCK * 0.75f, groundY, 7, 6, 0x20000000);
            prop(g, m, par, ax + 280, groundY, 0, 0, "hay_block_side");
            prop(g, m, par, ax + 250, groundY, 0, 0, "poppy");
        }
    }

    private void farm(GuiGraphics g, FightScreen.Mapper m, int groundY, float s) {
        float par = 0.65f;
        for (int k = 0; k < 14; k++) {
            float ax = 30 + k * 120;
            prop(g, m, par, ax, groundY, 0, 0, k % 4 == 0 ? "hay_block_side" : "carrots_stage3");
            if (k % 4 == 0) prop(g, m, par, ax, groundY, 0, 1, "hay_block_side");
            if (k % 3 == 1) prop(g, m, par, ax + 50, groundY, 0, 0, "potatoes_stage3");
        }
        // fence
        float fpar = 0.8f;
        int rail1 = groundY - (int) (34 * s), rail2 = groundY - (int) (18 * s);
        for (int k = 0; k < 60; k++) {
            int x = Math.round((k * 50 - m.camX() * fpar) * s);
            StageRenderer.tile(g, block("oak_planks"), x, groundY - (int) (44 * s), x + Math.max(2, (int) (8 * s)), groundY);
        }
        g.fill(0, rail1, 4000, rail1 + Math.max(2, (int) (4 * s)), 0xFF9C7A48);
        g.fill(0, rail2, 4000, rail2 + Math.max(2, (int) (4 * s)), 0xFF9C7A48);
    }

    private void plains(GuiGraphics g, FightScreen.Mapper m, int groundY, float s) {
        float par = 0.65f;
        for (int k = 0; k < 8; k++) {
            float ax = 50 + k * 220;
            for (int y = 0; y < 3; y++) prop(g, m, par, ax, groundY, 1, y, "oak_log");
            for (int x = 0; x < 3; x++) for (int y = 3; y < 5; y++) prop(g, m, par, ax, groundY, x, y, "azalea_leaves");
            prop(g, m, par, ax, groundY, 1, 5, "azalea_leaves");
            prop(g, m, par, ax + 130, groundY, 0, 0, k % 2 == 0 ? "dandelion" : "poppy");
            prop(g, m, par, ax + 170, groundY, 0, 0, "azure_bluet");
        }
    }
}
