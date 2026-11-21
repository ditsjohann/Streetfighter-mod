package com.streetfighter.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.streetfighter.fight.FightInput;
import com.streetfighter.fight.FighterType;
import com.streetfighter.fight.Moveset;
import com.streetfighter.fight.Movesets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Transparent screen used during an in-world fight: it captures the keyboard (so the player doesn't walk
 * around), and draws the HUD / character select on top of the live world view.
 */
public class FightScreen extends Screen {
    private final FightSession session;
    private FightHud hud;
    private int selected;

    // latched presses so very quick taps are never lost between simulation steps
    private boolean lLP, lMP, lHP, lLK, lMK, lHK, lSP, lJump;

    public FightScreen(FightSession session) {
        super(Component.literal("Street Fight"));
        this.session = session;
    }

    @Override
    protected void init() {
        hud = new FightHud(session, font);
    }

    // ------------------------------------------------------------------ input

    private boolean down(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }

    /** One frame of player input for the simulation (called by the session at 60 Hz). Buttons are held states. */
    public FightInput pollInput() {
        FightInput in = new FightInput();
        in.left = down(GLFW.GLFW_KEY_A) || down(GLFW.GLFW_KEY_LEFT);
        in.right = down(GLFW.GLFW_KEY_D) || down(GLFW.GLFW_KEY_RIGHT);
        in.up = lJump || down(GLFW.GLFW_KEY_W) || down(GLFW.GLFW_KEY_UP) || down(GLFW.GLFW_KEY_SPACE);
        in.down = down(GLFW.GLFW_KEY_S) || down(GLFW.GLFW_KEY_DOWN);
        in.lp = lLP || down(GLFW.GLFW_KEY_U);
        in.mp = lMP || down(GLFW.GLFW_KEY_I);
        in.hp = lHP || down(GLFW.GLFW_KEY_O);
        in.lk = lLK || down(GLFW.GLFW_KEY_J);
        in.mk = lMK || down(GLFW.GLFW_KEY_K);
        in.hk = lHK || down(GLFW.GLFW_KEY_L);
        in.sp = lSP || down(GLFW.GLFW_KEY_H) || down(GLFW.GLFW_KEY_SEMICOLON);
        lLP = lMP = lHP = lLK = lMK = lHK = lSP = lJump = false;
        return in;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (session.stage == FightSession.Stage.SELECT) return selectKey(key);
        if (session.engine == null) return true;
        if (session.engine.isOver()) {
            if (session.matchEndFrames > 45 && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER
                    || key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_U || key == GLFW.GLFW_KEY_J)) {
                onClose();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_P) {
            session.paused = !session.paused;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1f);
            return true;
        }
        if (session.paused) {
            if (key == GLFW.GLFW_KEY_Q) {
                session.sendResult(false);
                onClose();
            } else if (key == GLFW.GLFW_KEY_TAB) {
                movePage = (movePage + 1) % 2;
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_U -> lLP = true;
            case GLFW.GLFW_KEY_I -> lMP = true;
            case GLFW.GLFW_KEY_O -> lHP = true;
            case GLFW.GLFW_KEY_J -> lLK = true;
            case GLFW.GLFW_KEY_K -> lMK = true;
            case GLFW.GLFW_KEY_L -> lHK = true;
            case GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_SEMICOLON -> lSP = true;
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_SPACE -> lJump = true;
            default -> {
            }
        }
        return true;
    }

    int movePage;

    private boolean selectKey(int key) {
        int n = session.styles.size();
        switch (key) {
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> {
                selected = (selected + n - 1) % n;
                play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f);
            }
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> {
                selected = (selected + 1) % n;
                play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f);
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_J -> confirm();
            case GLFW.GLFW_KEY_ESCAPE -> {
                session.cancel(); // backing out before the first punch costs nothing
                onClose();
            }
            default -> {
            }
        }
        return true;
    }

    private void confirm() {
        FighterType t = session.styles.get(selected);
        if (session.isUnlocked(t)) session.beginFight(t);
        else play(SoundEvents.VILLAGER_NO, 1f);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (session.stage == FightSession.Stage.SELECT) {
            int idx = cardAt(mx, my);
            if (idx >= 0) {
                if (idx == selected) confirm();
                else {
                    selected = idx;
                    play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f);
                }
                return true;
            }
        } else if (session.engine != null && session.engine.isOver() && session.matchEndFrames > 45) {
            onClose();
            return true;
        }
        return true;
    }

    private void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.5f));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        session.leave();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the live world is the background
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (hud == null) hud = new FightHud(session, font);
        if (session.stage == FightSession.Stage.SELECT) {
            renderSelect(g, mouseX, mouseY);
            return;
        }
        hud.render(g, width, height);
        if (session.paused) hud.pausePanel(g, width, height, movePage);
        if (session.engine != null && session.engine.isOver() && session.matchEndFrames > 30) {
            hud.resultPanel(g, width, height, session.matchEndFrames - 30);
        }
    }

    // ------------------------------------------------------------------ character select (over the world)

    private int cardSize() {
        return (int) Math.max(24, Math.min(56, Math.min(width / 10f, height / 7f)));
    }

    private int cardsY() {
        return height - cardSize() - (int) (height * 0.075f) - 8;
    }

    private int cardsX() {
        int cs = cardSize();
        int n = session.styles.size();
        return width / 2 - (n * cs + (n - 1) * 6) / 2;
    }

    private int cardAt(double mx, double my) {
        int cs = cardSize(), x0 = cardsX(), y0 = cardsY();
        for (int i = 0; i < session.styles.size(); i++) {
            int x = x0 + i * (cs + 6);
            if (mx >= x && mx < x + cs && my >= y0 && my < y0 + cs) return i;
        }
        return -1;
    }

    private static ItemStack iconFor(FighterType t) {
        return switch (t) {
            case STEVE -> new ItemStack(Items.DIAMOND_PICKAXE);
            case SKELETON -> new ItemStack(Items.BOW);
            case VILLAGER -> new ItemStack(Items.EMERALD);
            case PIG -> new ItemStack(Items.CARROT);
            case CHICKEN -> new ItemStack(Items.EGG);
            case ALCHEMIST -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.SPLASH_POTION,
                    net.minecraft.world.item.alchemy.Potions.POISON);
            case GOAT -> new ItemStack(Items.GOAT_HORN);
            default -> new ItemStack(Items.ROTTEN_FLESH);
        };
    }

    static String styleBlurb(FighterType t) {
        return switch (t) {
            case STEVE -> "Balanced all-rounder: tosses blocks, diamond uppercuts, crafts combos.";
            case SKELETON -> "Zoner: keeps you out with arrows. Fast but fragile.";
            case VILLAGER -> "Grappler: Hmm... a Trade Offer can't be blocked.";
            case PIG -> "Tank: tough as bacon. Low stomps & overhead belly flops.";
            case CHICKEN -> "Aerial pest: double jump (press up again in the air), hold up to glide. Tiny and fast.";
            case ALCHEMIST -> "Status zoner: poison, slowness & harming potions, lingering clouds, Brew Up buffs.";
            case GOAT -> "Rammer: huge jumps, wall-splatting charges and a scream that slows you down.";
            case BRAWLER -> "Wild mob: scrappy and straightforward.";
        };
    }

    static String motionText(com.streetfighter.fight.InputBuffer.Motion m) {
        return switch (m) {
            case QCF -> "\u2193\u2198\u2192";
            case QCB -> "\u2193\u2199\u2190";
            case DP -> "\u2192\u2193\u2198";
            case HCB -> "\u2192\u2198\u2193\u2199\u2190";
            case QCF2 -> "\u2193\u2198\u2192\u2193\u2198\u2192";
            case QCB2 -> "\u2193\u2199\u2190\u2193\u2199\u2190";
        };
    }

    static String modernText(int dir) {
        return switch (dir) {
            case 6 -> "\u2192+H";
            case 2 -> "\u2193+H";
            case 4 -> "\u2190+H";
            default -> "H";
        };
    }

    /** Full move list lines for a style: page 0 = specials/supers/system, page 1 = normals. */
    static java.util.List<String> moveList(Moveset ms, int page) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (page == 0) {
            out.add("\u00a7eSPECIALS\u00a7r   (L/M/H by button strength, two buttons = EX for half a bar)");
            for (Moveset.Special sp : ms.specials) {
                out.add(motionText(sp.motion()) + " + " + (sp.kick() ? "K" : "P") + "   \u00a77or " + modernText(sp.modernDir())
                        + "\u00a7r   " + sp.name());
            }
            out.add("\u00a76SUPER ARTS");
            for (Moveset.Super su : ms.supers) {
                String input = motionText(su.motion()) + " + " + (su.kick() ? "K" : "P");
                String modern = su.level() == 1 ? "H+O" : "H+L";
                out.add("Lv" + su.level() + "  " + input + "  \u00a77or " + modern + "\u00a7r   " + su.move().name + "  \u00a77(" + su.level() + " bar" + (su.level() > 1 ? "s" : "") + ")");
            }
            out.add("\u00a7bSYSTEM");
            out.add("U+J  " + ms.forwardThrow.name + "  (\u2190+U+J  " + ms.backThrow.name + ")    U+J when grabbed: tech");
            out.add("I+K (hold)  Drive Parry      O+L  Drive Impact (armored, crumples)");
            for (Moveset.CommandNormal cn : ms.commandNormals) {
                String d = switch (cn.dir()) { case 6 -> "\u2192"; case 4 -> "\u2190"; case 3 -> "\u2198"; case 2 -> "\u2193"; default -> ""; };
                out.add(d + "+" + keyOf(cn.button()) + "  " + cn.move().name + (cn.move().level == com.streetfighter.fight.HitLevel.HIGH ? " (overhead)" : cn.move().level == com.streetfighter.fight.HitLevel.LOW ? " (low)" : ""));
            }
            for (Moveset.TargetCombo tc : ms.targetCombos) {
                out.add("Target: " + tc.from().name + " > " + keyOf(tc.button()) + "  " + tc.move().name);
            }
        } else {
            out.add("\u00a7eNORMALS\u00a7r   U I O = light/medium/heavy punch    J K L = light/medium/heavy kick");
            for (var b : new com.streetfighter.fight.InputBuffer.Button[]{com.streetfighter.fight.InputBuffer.Button.LP,
                    com.streetfighter.fight.InputBuffer.Button.MP, com.streetfighter.fight.InputBuffer.Button.HP,
                    com.streetfighter.fight.InputBuffer.Button.LK, com.streetfighter.fight.InputBuffer.Button.MK,
                    com.streetfighter.fight.InputBuffer.Button.HK}) {
                out.add(keyOf(b) + "  " + ms.stand.get(b).name + "   \u00a77\u2193" + keyOf(b) + "\u00a7r " + ms.crouch.get(b).name
                        + "   \u00a77air " + keyOf(b) + "\u00a7r " + ms.air.get(b).name);
            }
            out.add("\u00a77Lights chain into each other and into heavier normals; most normals cancel into specials,");
            out.add("\u00a77specials cancel into Super Arts. Jump into a wall and press up to wall-jump.");
        }
        return out;
    }

    static String keyOf(com.streetfighter.fight.InputBuffer.Button b) {
        return switch (b) {
            case LP -> "U";
            case MP -> "I";
            case HP -> "O";
            case LK -> "J";
            case MK -> "K";
            case HK -> "L";
            case SP -> "H";
        };
    }

    private void renderSelect(GuiGraphics g, int mouseX, int mouseY) {
        hud.render(g, width, height); // letterbox
        float t = session.time;
        FighterType sel = session.styles.get(selected);
        boolean open = session.isUnlocked(sel);

        hud.outlined(g, "CHOOSE YOUR FIGHTING STYLE", width / 2f, height * 0.04f + 6, width >= 420 ? 2f : 1.4f, 0xFFFFD21A, 0xFF4A2000);
        g.drawCenteredString(font, "vs " + session.info.enemyName() + "   |   AI: " + session.difficulty.displayName.toUpperCase()
                + "   |   A/D choose   ENTER fight   ESC back out", width / 2, (int) (height * 0.04f) + 18, 0xFFCCCCCC);
        g.drawCenteredString(font, "\u00a77Controls: WASD move   U I O punches   J K L kicks   H special   ESC = full move list",
                width / 2, (int) (height * 0.04f) + 29, 0xFFAAAAAA);

        // info panel on the left
        int px = 10, py = Math.max((int) (height * 0.16f), (int) (height * 0.04f) + 42);
        int pw = Math.min(width / 2 - 20, 240);
        String[] blurb = hud.wrap(styleBlurb(sel), pw - 12);
        int lines = 2 + blurb.length + (open ? 6 : 1);
        g.fill(px, py, px + pw, py + 14 + lines * 11, 0xA0000010);
        g.fill(px, py, px + 2, py + 14 + lines * 11, open ? 0xFF7FE0FF : 0xFF888888);
        g.drawString(font, sel.displayName.toUpperCase() + " STYLE", px + 8, py + 6, open ? 0xFF7FE0FF : 0xFF999999, true);
        g.drawString(font, "§o" + sel.title, px + 8, py + 17, 0xFFDDDDDD, true);
        int y = py + 30;
        if (open) {
            for (String b : blurb) {
                g.drawString(font, b, px + 8, y, 0xFFCCCCCC, false);
                y += 11;
            }
            Moveset ms = Movesets.get(sel);
            for (Moveset.Special sp : ms.specials) {
                g.drawString(font, motionText(sp.motion()) + (sp.kick() ? "K " : "P ") + sp.name(), px + 8, y, 0xFFB8C8FF, false);
                y += 11;
            }
            Moveset.Super top = ms.superOf(3);
            if (top != null) g.drawString(font, "Lv3 " + top.move().name, px + 8, y, 0xFFFFD21A, false);
        } else {
            g.drawString(font, "LOCKED - defeat a " + sel.displayName + " to learn it.", px + 8, y, 0xFFFF8A6A, false);
        }

        // VS tag on the right
        hud.outlined(g, "VS", width - 40f, height * 0.2f, 3f, 0xFFFF4A2A, 0xFFFFFFFF);
        g.drawCenteredString(font, session.info.enemyName(), width - 40, (int) (height * 0.2f) + 18, 0xFFFF8A6A);
        g.drawCenteredString(font, session.enemyType == FighterType.BRAWLER ? "Wild Mob" : session.enemyType.title,
                width - 40, (int) (height * 0.2f) + 29, 0xFFBBBBBB);

        // cards
        int cs = cardSize(), x0 = cardsX(), y0 = cardsY();
        for (int i = 0; i < session.styles.size(); i++) {
            FighterType ft = session.styles.get(i);
            int x = x0 + i * (cs + 6);
            boolean isSel = i == selected;
            boolean hover = mouseX >= x && mouseX < x + cs && mouseY >= y0 && mouseY < y0 + cs;
            boolean un = session.isUnlocked(ft);
            int border = isSel ? ((int) (t * 4) % 2 == 0 ? 0xFFFFD21A : 0xFFFFFFFF) : hover ? 0xFFAAAAAA : 0xFF444444;
            int lift = isSel ? 4 : 0;
            g.fill(x - 2, y0 - 2 - lift, x + cs + 2, y0 + cs + 2 - lift, border);
            g.fillGradient(x, y0 - lift, x + cs, y0 + cs - lift, un ? 0xE0304A7A : 0xE0202020, un ? 0xE0101830 : 0xE0101010);
            float k = cs / 16f * 0.6f;
            g.pose().pushPose();
            g.pose().translate(x + cs / 2f, y0 + cs / 2f - 3 - lift, 0);
            g.pose().scale(k, k, 1f);
            g.renderItem(iconFor(ft), -8, -8);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (!un) {
                g.fill(x, y0 - lift, x + cs, y0 + cs - lift, 0xB0000000);
                g.drawCenteredString(font, "LOCKED", x + cs / 2, y0 + cs / 2 - 8 - lift, 0xFFFF6A5A);
            }
            float ts = Math.min(1f, (cs - 4) / (float) font.width(ft.displayName));
            g.pose().translate(x + cs / 2f, y0 + cs - 9 * ts - 1 - lift, 0);
            g.pose().scale(ts, ts, 1f);
            g.drawCenteredString(font, ft.displayName, 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
        }
    }
}
