package com.streetfighter.client;

import com.streetfighter.fight.FighterType;
import com.streetfighter.fight.Moveset;
import com.streetfighter.fight.Movesets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Street Fighter style character select, drawn over the live world.
 * <ul>
 *   <li>A portrait grid (SF2 / MUGEN "select.def" style): live 3D faces of every style, a "?" random cell and
 *       black empty boxes reserved for future fighters. The cursor never lands on empty boxes.</li>
 *   <li>Big showcases (SF4 / SF6 style): the highlighted style on the left on a blue slab, the opponent on the
 *       right on a red slab, each with a huge italic name, title, home turf, archetype and stat bars.</li>
 *   <li>A blinking 1P cursor and a CPU marker, a 30 second select timer, a random roulette and a VS splash.</li>
 * </ul>
 */
final class SelectScreenUi {
    static final int COLS = 7, ROWS = 3;
    /** Grid layout; null = empty (future) slot. */
    private static final FighterType[] GRID = {
            FighterType.STEVE, FighterType.PIG, FighterType.SKELETON, FighterType.VILLAGER, FighterType.ALCHEMIST,
            FighterType.CHICKEN, FighterType.GOAT,
            FighterType.OAK, FighterType.IRON_ORE, FighterType.STONE_PICKAXE, null, FighterType.ZOMBIE, null, null,
            null, null, null, null, null, null, null};
    private static final int RANDOM_CELL = 10; // centre of the bottom row
    private static final float SELECT_SECONDS = 30f;

    private enum Phase { CHOOSE, ROULETTE, CONFIRMED }

    private final FightSession session;
    private final Font font;
    private final FightHud hud;
    private final Map<FighterType, LivingEntity> dummies = new EnumMap<>(FighterType.class);
    private final Random random = new Random();

    private int cursor = 0;
    private Phase phase = Phase.CHOOSE;
    private float phaseStart;
    private float openedAt = -1;
    private FighterType chosen;
    private int rouletteIndex;
    private float lastRouletteTick;
    private boolean showMoves;
    private int lastSecond = -1;
    private boolean vsSoundPlayed;

    SelectScreenUi(FightSession session, Font font, FightHud hud) {
        this.session = session;
        this.font = font;
        this.hud = hud;
    }

    // =====================================================================================================
    // data
    // =====================================================================================================

    record Profile(String archetype, String home, int power, int speed, int defense, int range, int accent) {
    }

    static Profile profile(FighterType t) {
        return switch (t) {
            case STEVE -> new Profile("SHOTO", "THE OVERWORLD", 3, 3, 3, 3, 0x3FA7FF);
            case SKELETON -> new Profile("ZONER", "DARK CAVES", 2, 4, 2, 5, 0xD8D8D8);
            case VILLAGER -> new Profile("GRAPPLER", "PLAINS VILLAGE", 4, 2, 4, 2, 0x4FD06A);
            case ALCHEMIST -> new Profile("STATUS ZONER", "VILLAGE CHAPEL", 2, 2, 3, 4, 0xB45CFF);
            case PIG -> new Profile("TANK", "THE FARM", 4, 3, 5, 1, 0xFF8FB0);
            case CHICKEN -> new Profile("AERIAL RUSHDOWN", "THE COOP", 2, 5, 1, 2, 0xFFD84A);
            case GOAT -> new Profile("RAMMER", "SNOWY PEAKS", 5, 2, 3, 2, 0x9CD2FF);
            case OAK -> new Profile("ROOTED BRUISER", "OAK FOREST", 4, 1, 5, 4, 0x7DBA3C);
            case IRON_ORE -> new Profile("JUGGERNAUT", "DEEP CAVES", 5, 1, 5, 2, 0xD8AF93);
            case STONE_PICKAXE -> new Profile("RUSHDOWN", "CRAFTING TABLE", 3, 4, 2, 2, 0xA0A0A0);
            case ZOMBIE -> new Profile("UNDEAD GRAPPLER", "THE NIGHT", 4, 2, 4, 1, 0x5DAA46);
            case BRAWLER -> new Profile("WILD MOB", "THE WILD", 3, 3, 3, 2, 0xFF6A4A);
        };
    }

    private LivingEntity entityFor(FighterType t) {
        Minecraft mc = Minecraft.getInstance();
        if (t == FighterType.STEVE) return mc.player;
        if (t == FighterType.OAK || t == FighterType.IRON_ORE || t == FighterType.STONE_PICKAXE) {
            return dummies.computeIfAbsent(t, k -> mc.level == null ? null : (switch (k) {
                case OAK -> com.streetfighter.entity.ModEntities.OAK_TREANT.get();
                case IRON_ORE -> com.streetfighter.entity.ModEntities.IRON_ORE_GOLEM.get();
                default -> com.streetfighter.entity.ModEntities.STONE_PICKAXE.get();
            }).create(mc.level));
        }
        return dummies.computeIfAbsent(t, k -> {
            if (mc.level == null) return null;
            EntityType<?> type = switch (k) {
                case SKELETON -> EntityType.SKELETON;
                case VILLAGER, ALCHEMIST -> EntityType.VILLAGER;
                case PIG -> EntityType.PIG;
                case CHICKEN -> EntityType.CHICKEN;
                case GOAT -> EntityType.GOAT;
                case ZOMBIE -> EntityType.ZOMBIE;
                default -> EntityType.ZOMBIE;
            };
            var e = type.create(mc.level);
            if (e instanceof Villager v) {
                v.setVillagerData(v.getVillagerData().setProfession(
                        k == FighterType.ALCHEMIST ? VillagerProfession.CLERIC : VillagerProfession.NONE));
            }
            if (e instanceof net.minecraft.world.entity.monster.Skeleton sk) {
                sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
            }
            return e instanceof LivingEntity le ? le : null;
        });
    }

    private FighterType shown() {
        if (phase == Phase.ROULETTE) return GRID[rouletteIndex];
        if (chosen != null) return chosen;
        FighterType t = cursor == RANDOM_CELL ? null : GRID[cursor];
        return t;
    }

    private boolean selectable(int cell) {
        return cell == RANDOM_CELL || (cell >= 0 && cell < GRID.length && GRID[cell] != null);
    }

    private int enemyCell() {
        for (int i = 0; i < GRID.length; i++) if (GRID[i] == session.enemyType) return i;
        return -1;
    }

    private float now() {
        return session.time;
    }

    // =====================================================================================================
    // input
    // =====================================================================================================

    boolean key(int key) {
        if (phase != Phase.CHOOSE) return true;
        switch (key) {
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> move(-1, 0);
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> move(1, 0);
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> move(0, -1);
            case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> move(0, 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_J -> confirm();
            case GLFW.GLFW_KEY_TAB -> {
                showMoves = !showMoves;
                play(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f);
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                session.cancel(); // backing out before the first punch costs nothing
                Minecraft.getInstance().setScreen(null);
            }
            default -> {
            }
        }
        return true;
    }

    /** Spatial cursor movement that skips empty boxes (MUGEN's moveoveremptyboxes = 0). */
    private void move(int dx, int dy) {
        int cx = cursor % COLS, cy = cursor / COLS;
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < GRID.length; i++) {
            if (i == cursor || !selectable(i)) continue;
            int ix = i % COLS, iy = i / COLS;
            int ddx = ix - cx, ddy = iy - cy;
            if (dx != 0 && Integer.signum(ddx) != dx) continue;
            if (dy != 0 && Integer.signum(ddy) != dy) continue;
            float score = dx != 0 ? Math.abs(ddx) + Math.abs(ddy) * 3f : Math.abs(ddy) + Math.abs(ddx) * 0.6f;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best < 0 && dx != 0) { // wrap around the row like SF2
            for (int i = 0; i < GRID.length; i++) {
                int ix = dx > 0 ? i : GRID.length - 1 - i;
                if (ix / COLS == cy && selectable(ix) && ix != cursor) {
                    best = ix;
                    break;
                }
            }
        }
        if (best >= 0) {
            cursor = best;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f);
        }
    }

    boolean click(double mx, double my, int w, int h) {
        if (phase != Phase.CHOOSE) return true;
        Layout L = layout(w, h);
        for (int i = 0; i < GRID.length; i++) {
            int x = L.cellX(i), y = L.cellY(i);
            if (mx >= x && mx < x + L.cs && my >= y && my < y + L.cs) {
                if (!selectable(i)) return true;
                if (i == cursor) confirm();
                else {
                    cursor = i;
                    play(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f);
                }
                return true;
            }
        }
        return true;
    }

    private void confirm() {
        if (cursor == RANDOM_CELL) {
            if (unlockedCells().isEmpty()) return;
            phase = Phase.ROULETTE;
            phaseStart = now();
            rouletteIndex = unlockedCells().get(0);
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1f);
            return;
        }
        FighterType t = GRID[cursor];
        if (t == null) return;
        if (!session.isUnlocked(t)) {
            play(SoundEvents.VILLAGER_NO, 1f);
            return;
        }
        lockIn(t);
    }

    private List<Integer> unlockedCells() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < GRID.length; i++) if (GRID[i] != null && session.isUnlocked(GRID[i])) out.add(i);
        return out;
    }

    private void lockIn(FighterType t) {
        chosen = t;
        phase = Phase.CONFIRMED;
        phaseStart = now();
        play(SoundEvents.PLAYER_LEVELUP, 1.6f);
        play(SoundEvents.ANVIL_LAND, 1.4f);
    }

    private void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.5f));
    }

    /** Timers, roulette and the hand-off to the fight. Called every render frame. */
    private void tick() {
        float t = now();
        if (openedAt < 0) openedAt = t;
        switch (phase) {
            case CHOOSE -> {
                int left = (int) Math.ceil(SELECT_SECONDS - (t - openedAt));
                if (left != lastSecond && left <= 5 && left > 0) play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f);
                lastSecond = left;
                if (left <= 0) { // time's up: lock in what's highlighted (SF2 behaviour)
                    FighterType cur = cursor == RANDOM_CELL ? null : GRID[cursor];
                    if (cur == null || !session.isUnlocked(cur)) {
                        cursor = 0;
                        cur = FighterType.STEVE;
                    }
                    lockIn(cur);
                }
            }
            case ROULETTE -> {
                if (t - lastRouletteTick > 0.06f) {
                    lastRouletteTick = t;
                    List<Integer> cells = unlockedCells();
                    rouletteIndex = cells.get(random.nextInt(cells.size()));
                    play(SoundEvents.UI_BUTTON_CLICK.value(), 2f);
                }
                if (t - phaseStart > 0.9f) {
                    cursor = rouletteIndex;
                    lockIn(GRID[rouletteIndex]);
                }
            }
            case CONFIRMED -> {
                float a = t - phaseStart;
                if (a > 0.45f && !vsSoundPlayed) {
                    vsSoundPlayed = true;
                    play(SoundEvents.PLAYER_ATTACK_STRONG, 0.6f);
                    play(SoundEvents.BELL_BLOCK, 1.2f);
                }
                if (a > 2.1f) session.beginFight(chosen);
            }
        }
    }

    // =====================================================================================================
    // layout
    // =====================================================================================================

    private record Layout(int w, int h, int cs, int gap, int gx, int gy) {
        int cellX(int i) {
            return gx + (i % COLS) * (cs + gap);
        }

        int cellY(int i) {
            return gy + (i / COLS) * (cs + gap);
        }

        int gridW() {
            return COLS * cs + (COLS - 1) * gap;
        }
    }

    private static Layout layout(int w, int h) {
        int gap = 3;
        int cs = (int) Mth.clamp(Math.min((w * 0.5f - (COLS - 1) * gap) / COLS, h * 0.1f), 18, 60);
        int gridW = COLS * cs + (COLS - 1) * gap;
        int gx = (w - gridW) / 2;
        int gy = h - ROWS * cs - gap - 16;
        return new Layout(w, h, cs, gap, gx, gy);
    }

    // =====================================================================================================
    // rendering
    // =====================================================================================================

    void render(GuiGraphics g, int w, int h, int mouseX, int mouseY) {
        tick();
        float t = now();
        Layout L = layout(w, h);
        FighterType mine = shown();
        FighterType theirs = session.enemyType;
        Profile mp = mine == null ? null : profile(mine);
        Profile ep = profile(theirs);
        float intro = Mth.clamp((t - openedAt) / 0.35f, 0, 1);
        float slide = 1 - (1 - intro) * (1 - intro);

        // ---- backdrop: darken the world, coloured slabs, speed lines
        g.fillGradient(0, 0, w, h, 0xC0060A18, 0xE0000000);
        int split = w / 2;
        slab(g, 0, h, (int) (-w * 0.5f * (1 - slide)), (int) (split - w * 0.06f), (int) (split - w * 0.16f), 0x70154A9A, 0x30071A3A);
        slab(g, 0, h, (int) (split + w * 0.16f), (int) (split + w * 0.06f), (int) (w + w * 0.5f * (1 - slide)), 0x709A1A15, 0x303A0A07);
        speedLines(g, w, h, t, mp != null ? mp.accent : 0x3FA7FF, ep.accent);

        // ---- big showcases
        float bob = Mth.sin(t * 2.2f) * 2f;
        int feetY = (int) (h * 0.93f);
        LivingEntity mineE = mine == null ? null : entityFor(mine);
        boolean lockedMine = mine != null && !session.isUnlocked(mine);
        showcase(g, mineE, (int) (w * 0.17f - w * 0.4f * (1 - slide)), feetY, h, w, true, bob, lockedMine);
        showcase(g, session.enemyEntity, (int) (w * 0.83f + w * 0.4f * (1 - slide)), feetY, h, w, false, -bob, false);

        // ---- name plates (huge italic names over the fighters' legs)
        int plateW = L.gx - 10;
        if (mine != null) {
            namePlate(g, 6, feetY - 54, plateW, mine.displayName, mine.title, mp, true, lockedMine);
        } else {
            namePlate(g, 6, feetY - 54, plateW, "RANDOM", "Fate decides", new Profile("???", "ANYWHERE", 0, 0, 0, 0, 0xC07CFF), true, false);
        }
        String eName = session.info.enemyName();
        namePlate(g, w - 6 - plateW, feetY - 54, plateW, eName, theirs == FighterType.BRAWLER ? "Wild Mob" : theirs.title,
                ep, false, false);

        // ---- stat panels / move list between the showcases
        int panelTop = 38;
        int vsGap = Math.max(44, (int) (L.gridW() * 0.2f));
        int panelW = Math.max(80, (L.gridW() - vsGap) / 2);
        int panelBottom = L.gy - 10;
        if (mine != null) {
            if (showMoves && !lockedMine) moveList(g, L.gx, panelTop, Math.max(panelW, L.gridW() / 2 + 20), mine);
            else statPanel(g, L.gx, panelTop, panelW, panelBottom, mp, true, lockedMine ? "LOCKED: defeat a " + mine.displayName + " to learn it" : FightScreen.styleBlurb(mine));
        }
        if (!(showMoves && mine != null)) {
            statPanel(g, L.gx + L.gridW() - panelW, panelTop, panelW, panelBottom, ep, false,
                    FightScreen.styleBlurb(theirs));
        }

        // ---- VS emblem
        float vsPulse = 1f + 0.06f * Mth.sin(t * 5f);
        if (!(showMoves && mine != null)) {
            hud.outlined(g, "VS", w / 2f, panelTop + 30, vsGap / 16f * vsPulse, 0xFFFFD21A, 0xFF8A1A00);
        }

        // ---- grid
        grid(g, L, t, mouseX, mouseY);

        // ---- title + timer
        italic(g, "PLAYER SELECT", 10, 8, w >= 480 ? 2.2f : 1.6f, 0xFFFFD21A, 0xFF4A2000, false);
        int left = Math.max(0, (int) Math.ceil(SELECT_SECONDS - (t - openedAt)));
        if (phase != Phase.CHOOSE) left = Math.max(0, left);
        int bx = w / 2;
        g.fill(bx - 19, 3, bx + 19, 33, 0xFFE8C04A);
        g.fillGradient(bx - 17, 5, bx + 17, 31, 0xFF1B2238, 0xFF06080F);
        hud.outlined(g, String.format("%02d", left), bx, 18, 2.1f,
                left <= 5 && (int) (t * 4) % 2 == 0 ? 0xFFFF4A3A : 0xFFFFE070, 0xFF000000);
        String help = "\u2190\u2191\u2192\u2193 / WASD move    ENTER select    TAB move list    ESC back out";
        g.drawString(font, help, w / 2 - font.width(help) / 2, h - 10, 0xFFAAAAAA, true);
        String diff = session.difficulty.displayName.toUpperCase() + " AI";
        italic(g, diff, w - 10, 8, w >= 480 ? 1.6f : 1.2f, session.difficulty == com.streetfighter.fight.AIDifficulty.HARD
                ? 0xFFFF6B5B : 0xFF8BEA8B, 0xFF000000, true);

        // ---- lock-in flash and VS splash
        if (phase == Phase.CONFIRMED) {
            float a = t - phaseStart;
            if (a < 0.3f) g.fill(0, 0, w, h, FightHud.argb((int) ((0.3f - a) / 0.3f * 120), 0xFFFFFF));
            if (a > 0.45f) vsSplash(g, w, h, a - 0.45f);
        }
    }

    /** A slanted, vertically graded slab: x at the top goes from xTop0..xTop1, bottom from xBot0..xTop1 shifted. */
    private static void slab(GuiGraphics g, int y0, int y1, int xLeft, int xRightTop, int xRightBottom, int cTop, int cBot) {
        int bands = 48;
        for (int b = 0; b < bands; b++) {
            float f = b / (float) bands;
            int ya = y0 + (int) ((y1 - y0) * f), yb = y0 + (int) ((y1 - y0) * (b + 1f) / bands);
            int xr = (int) (xRightTop + (xRightBottom - xRightTop) * f);
            int col = lerpColor(cTop, cBot, f);
            if (xLeft < xr) g.fill(xLeft, ya, xr, yb, col);
            else g.fill(xr, ya, xLeft, yb, col);
        }
    }

    private static int lerpColor(int a, int b, float f) {
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int ca = a >>> sh & 255, cb = b >>> sh & 255;
            r |= ((int) (ca + (cb - ca) * f) & 255) << sh;
        }
        return r;
    }

    private void speedLines(GuiGraphics g, int w, int h, float t, int left, int right) {
        Random r = new Random(1234);
        for (int i = 0; i < 26; i++) {
            boolean l = i % 2 == 0;
            int y = (int) (r.nextFloat() * h * 0.85f) + 10;
            float speed = 120 + r.nextFloat() * 220;
            int len = 30 + r.nextInt(80);
            float span = w / 2f + len;
            float p = ((t * speed + r.nextFloat() * 1000) % span);
            int x = l ? (int) (w / 2f - p) : (int) (w / 2f + p - len);
            int c = FightHud.argb(28 + r.nextInt(30), l ? left : right);
            g.fill(x, y, x + len, y + 1, c);
        }
    }

    private void showcase(GuiGraphics g, LivingEntity e, int cx, int feetY, int h, int w, boolean faceRight, float bob,
                          boolean silhouette) {
        if (e == null) return;
        float bbH = Math.max(0.4f, e.getBbHeight()), bbW = Math.max(0.4f, e.getBbWidth());
        boolean upright = bbH >= 1.0f && FighterRenderer.isUpright(e);
        float length = upright ? Math.max(bbW, bbH * 0.35f) : Math.max(bbW * 1.7f, bbH * 0.9f); // on-screen width in blocks
        float px = Math.min((bbH < 1f ? h * 0.32f : h * 0.5f) / bbH, w * 0.22f / length);
        FighterRenderer.Pose pose = new FighterRenderer.Pose();
        pose.offsetY = bob;
        if (silhouette) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.04f, 0.04f, 0.07f, 1f);
        FighterRenderer.render(g, e, cx, feetY, px, faceRight, pose);
        if (silhouette) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        FighterRenderer.clearDepth();
        if (silhouette) hud.outlined(g, "?", cx, feetY - bbH * px / 2f, 4f, 0xFF3A3A4A, 0xFF000000);
    }

    private void namePlate(GuiGraphics g, int x, int y, int pw, String name, String title, Profile p, boolean left,
                           boolean locked) {
        int accent = 0xFF000000 | p.accent;
        // slanted backing bar
        int bh = 44;
        for (int r = 0; r < bh; r++) {
            int off = (int) ((bh - r) * 0.35f);
            int a = left ? x + off - 4 : x - off + 4;
            g.fill(a, y + r, a + pw, y + r + 1, FightHud.argb(150 - r, 0x000000));
        }
        g.fill(x, y + bh, x + pw, y + bh + 2, accent);
        float scale = Mth.clamp((pw - 10) / (float) Math.max(1, font.width(name.toUpperCase())), 1.2f, 3.2f);
        String up = locked ? "???" : name.toUpperCase();
        float tx = left ? x + 4 : x + pw - 4;
        italic(g, up, tx, y + 2, scale, locked ? 0xFF777777 : 0xFFFFFFFF, accent, !left);
        int ty = y + 4 + (int) (9 * scale);
        String sub = "§o" + title;
        g.drawString(font, sub, left ? x + 6 : x + pw - 6 - font.width(sub), ty, 0xFFDDDDDD, true);
        // home turf with icon, like the flag under SF2 names
        String home = p.home;
        int hw = font.width(home) + 12;
        int hx = left ? x + 6 : x + pw - 6 - hw;
        g.pose().pushPose();
        g.pose().translate(hx, ty + 10, 0);
        g.pose().scale(0.6f, 0.6f, 1f);
        FighterType t = typeOf(p);
        if (t != null) g.renderItem(FightScreen.iconFor(t), 0, 0);
        g.pose().popPose();
        g.drawString(font, home, hx + 12, ty + 11, accent, true);
    }

    private static FighterType typeOf(Profile p) {
        for (FighterType t : FighterType.values()) if (profile(t).equals(p)) return t;
        return null;
    }

    private void statPanel(GuiGraphics g, int x, int y, int pw, int maxBottom, Profile p, boolean left, String blurb) {
        int accent = 0xFF000000 | p.accent;
        String[] all = hud.wrap(blurb, pw - 10);
        int room = Math.max(0, (maxBottom - (y + 16 + 4 * 11 + 6 + 4)) / 10);
        String[] lines = java.util.Arrays.copyOf(all, Math.min(all.length, room));
        if (lines.length < all.length && lines.length > 0) lines[lines.length - 1] = lines[lines.length - 1] + "...";
        int ph = 16 + 4 * 11 + 6 + lines.length * 10 + 4;
        g.fill(x, y, x + pw, y + ph, 0xA0000008);
        g.fill(left ? x : x + pw - 2, y, left ? x + 2 : x + pw, y + ph, accent);
        // archetype tag
        String tag = p.archetype;
        int tw = font.width(tag) + 8;
        int tx = left ? x + 6 : x + pw - 6 - tw;
        g.fill(tx, y + 4, tx + tw, y + 14, accent);
        g.drawString(font, tag, tx + 4, y + 5, 0xFF000000, false);
        String[] names = pw >= 120 ? new String[]{"POWER", "SPEED", "DEFENSE", "RANGE"} : new String[]{"POW", "SPD", "DEF", "RNG"};
        int[] vals = {p.power, p.speed, p.defense, p.range};
        int yy = y + 18;
        for (int i = 0; i < 4; i++) {
            g.drawString(font, names[i], x + 7, yy + 1, 0xFFBBBBBB, false);
            int pip = Math.max(6, Math.min(11, (pw - 14 - font.width(names[i]) - 4) / 5));
            int bx = x + pw - 7 - 5 * pip;
            for (int k = 0; k < 5; k++) {
                int c = k < vals[i] ? accent : 0xFF2A2A33;
                slantBox(g, bx + k * pip, yy + 1, pip - 2, 7, c);
            }
            yy += 11;
        }
        yy += 4;
        for (String l : lines) {
            g.drawString(font, l, x + 6, yy, 0xFFCCCCCC, false);
            yy += 10;
        }
    }

    private static void slantBox(GuiGraphics g, int x, int y, int w, int h, int c) {
        for (int r = 0; r < h; r++) {
            int off = (h - 1 - r) / 3;
            g.fill(x + off, y + r, x + off + Math.max(2, w - 2), y + r + 1, c);
        }
    }

    private void moveList(GuiGraphics g, int x, int y, int pw, FighterType t) {
        Moveset ms = Movesets.get(t);
        List<String> lines = new ArrayList<>();
        lines.add("§eSPECIALS");
        for (Moveset.Special sp : ms.specials) {
            lines.add(FightScreen.motionText(sp.motion()) + (sp.kick() ? "K  " : "P  ") + sp.name());
        }
        lines.add("§6SUPER ARTS");
        for (Moveset.Super su : ms.supers) {
            lines.add("Lv" + su.level() + " " + FightScreen.motionText(su.motion()) + (su.kick() ? "K  " : "P  ") + su.move().name);
        }
        lines.add("§bCOMMAND NORMALS");
        for (Moveset.CommandNormal cn : ms.commandNormals) {
            String d = switch (cn.dir()) { case 6 -> "→"; case 4 -> "←"; case 3 -> "↘"; case 2 -> "↓"; default -> ""; };
            lines.add(d + FightScreen.keyOf(cn.button()) + "  " + cn.move().name);
        }
        int ph = lines.size() * 10 + 8;
        int w2 = 0;
        for (String l : lines) w2 = Math.max(w2, font.width(l));
        pw = Math.max(pw, w2 + 14);
        g.fill(x, y, x + pw, y + ph, 0xC0000008);
        g.fill(x, y, x + 2, y + ph, 0xFF000000 | profile(t).accent);
        int yy = y + 4;
        for (String l : lines) {
            g.drawString(font, l, x + 7, yy, 0xFFDDDDDD, false);
            yy += 10;
        }
    }

    private void grid(GuiGraphics g, Layout L, float t, int mouseX, int mouseY) {
        // frame behind the grid
        g.fill(L.gx - 5, L.gy - 5, L.gx + L.gridW() + 5, L.gy + ROWS * L.cs + L.gap + 5, 0xE0000000);
        g.fill(L.gx - 5, L.gy - 6, L.gx + L.gridW() + 5, L.gy - 5, 0xFFE8C04A);
        g.fill(L.gx - 5, L.gy + ROWS * L.cs + L.gap + 5, L.gx + L.gridW() + 5, L.gy + ROWS * L.cs + L.gap + 6, 0xFFE8C04A);
        int enemyCell = enemyCell();
        int highlight = phase == Phase.ROULETTE ? rouletteIndex : cursor;
        for (int i = 0; i < GRID.length; i++) {
            int x = L.cellX(i), y = L.cellY(i), cs = L.cs;
            FighterType ft = GRID[i];
            if (i == RANDOM_CELL) {
                g.fillGradient(x, y, x + cs, y + cs, 0xFF3A1458, 0xFF12061E);
                hud.outlined(g, "?", x + cs / 2f, y + cs / 2f, cs / 14f, 0xFFFFD21A, 0xFF5A2A00);
                g.fill(x, y, x + cs, y + 1, 0x60FFFFFF);
            } else if (ft == null) {
                // empty box for a future fighter
                g.fill(x, y, x + cs, y + cs, 0xFF030305);
                g.fill(x, y, x + cs, y + 1, 0xFF15151A);
                g.fill(x, y + cs - 1, x + cs, y + cs, 0xFF0A0A0E);
            } else {
                boolean un = session.isUnlocked(ft);
                int accent = profile(ft).accent;
                g.fillGradient(x, y, x + cs, y + cs, FightHud.argb(255, lerpColor(0xFF1E2A48, 0xFF000000 | accent, 0.25f)),
                        0xFF070A14);
                if (!un) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.05f, 0.05f, 0.09f, 1f);
                FighterRenderer.portrait(g, entityFor(ft), x, y, cs, cs, true, 1f);
                if (!un) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                if (!un) hud.outlined(g, "?", x + cs / 2f, y + cs / 2f - 2, cs / 22f, 0xFF50506A, 0xFF000000);
                // icon badge
                float k = cs / 16f * 0.36f;
                g.pose().pushPose();
                g.pose().translate(x + cs - 8 * k * 2 - 1, y + cs - 8 * k * 2 - 1, 0);
                g.pose().scale(k, k, 1f);
                g.renderItem(FightScreen.iconFor(ft), 0, 0);
                g.pose().popPose();
                g.fill(x, y + cs - 2, x + cs, y + cs, FightHud.argb(un ? 255 : 90, accent));
                g.pose().popPose();
            }
        }
        // CPU marker on the opponent's cell (after all cells so nothing covers its tag)
        int ec = enemyCell;
        if (ec >= 0) {
            int ex = L.cellX(ec), ey = L.cellY(ec), cs = L.cs;
            g.pose().pushPose();
            g.pose().translate(0, 0, 350);
            frame(g, ex, ey, cs, 2, (int) (t * 3) % 2 == 0 ? 0xFFFF3A2A : 0xFFFFB0A0);
            boolean shared = ec == (phase == Phase.ROULETTE ? rouletteIndex : cursor);
            tag(g, "CPU", ex + cs - font.width("CPU") - 5, shared || ec / COLS == ROWS - 1 ? ey + cs + 1 : ey - 9, 0xFFE0301E);
            g.pose().popPose();
        }
        // 1P cursor
        int x = L.cellX(highlight), y = L.cellY(highlight);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        boolean locked = phase == Phase.CONFIRMED;
        int col = locked ? ((int) (t * 12) % 2 == 0 ? 0xFFFFFFFF : 0xFF2E9BFF) : (int) (t * 4) % 2 == 0 ? 0xFF2E9BFF : 0xFFFFFFFF;
        frame(g, x - 2, y - 2, L.cs + 4, 3, col);
        tag(g, "1P", x - 2, y - 11, 0xFF1A6FE0);
        g.pose().popPose();
    }

    private static void frame(GuiGraphics g, int x, int y, int s, int t, int c) {
        g.fill(x, y, x + s, y + t, c);
        g.fill(x, y + s - t, x + s, y + s, c);
        g.fill(x, y, x + t, y + s, c);
        g.fill(x + s - t, y, x + s, y + s, c);
    }

    private void tag(GuiGraphics g, String text, int x, int y, int bg) {
        int tw = font.width(text) + 6;
        g.fill(x, y, x + tw, y + 10, bg);
        g.drawString(font, text, x + 3, y + 1, 0xFFFFFFFF, false);
    }

    /** Big outlined italic text (sheared), anchored left or right. */
    private void italic(GuiGraphics g, String text, float x, float y, float scale, int color, int outline, boolean alignRight) {
        g.pose().pushPose();
        g.pose().translate(x, y, 450);
        g.pose().mulPose(new Matrix4f(1, 0, 0, 0, -0.22f, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1));
        g.pose().scale(scale, scale, 1f);
        int tw = font.width(text);
        int ox = alignRight ? -tw : 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.drawString(font, text, ox + dx, dy, outline, false);
            }
        }
        g.drawString(font, text, ox + 1, 2, FightHud.argb(150, 0), false);
        g.drawString(font, text, ox, 0, color, false);
        g.pose().popPose();
    }

    /** SF2-style VS screen: both faces slam in from the sides, VS punches down, names underneath. */
    private void vsSplash(GuiGraphics g, int w, int h, float a) {
        float in = Mth.clamp(a / 0.25f, 0, 1);
        float e = 1 - (1 - in) * (1 - in) * (1 - in);
        int band = (int) (h * 0.46f);
        int by = (h - band) / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(0, 0, w, h, FightHud.argb((int) (160 * e), 0x000000));
        g.fill(0, by - 3, w, by + band + 3, 0xFFE8C04A);
        g.fillGradient(0, by, w, by + band, 0xFF101830, 0xFF04060C);
        int ps = (int) (band * 0.86f);
        int lx = (int) (w * 0.28f - ps / 2f - w * 0.6f * (1 - e));
        int rx = (int) (w * 0.72f - ps / 2f + w * 0.6f * (1 - e));
        int py = by + (band - ps) / 2;
        g.fill(lx - 2, py - 2, lx + ps + 2, py + ps + 2, 0xFF2E9BFF);
        g.fillGradient(lx, py, lx + ps, py + ps, 0xFF1E3A70, 0xFF050A18);
        g.fill(rx - 2, py - 2, rx + ps + 2, py + ps + 2, 0xFFE0301E);
        g.fillGradient(rx, py, rx + ps, py + ps, 0xFF701E1E, 0xFF180505);
        g.pose().popPose();
        FighterRenderer.clearDepth();
        FighterRenderer.portrait(g, entityFor(chosen), lx, py, ps, ps, true, 0.9f);
        FighterRenderer.portrait(g, session.enemyEntity, rx, py, ps, ps, false, 0.9f);
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        float vs = a < 0.2f ? 1f : Mth.clamp(1f + (0.45f - Math.min(a, 0.45f)) * 4f, 1f, 2f);
        if (a > 0.2f) hud.outlined(g, "VS", w / 2f, h / 2f, 4.2f * vs, 0xFFFFD21A, 0xFF8A1A00);
        italic(g, chosen.displayName.toUpperCase(), lx + 4, py + ps - 20, 1.6f, 0xFFFFFFFF, 0xFF1A6FE0, false);
        italic(g, session.info.enemyName().toUpperCase(), rx + ps - 4, py + ps - 20, 1.6f, 0xFFFFFFFF, 0xFFE0301E, true);
        g.pose().popPose();
    }
}
