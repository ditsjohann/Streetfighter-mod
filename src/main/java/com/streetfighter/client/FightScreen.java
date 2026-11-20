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
    private boolean pLight, pHeavy, pSp1, pSp2, pSuper, pJump;

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

    /** One frame of player input for the simulation (called by the session at 60 Hz). */
    public FightInput pollInput() {
        FightInput in = new FightInput();
        in.left = down(GLFW.GLFW_KEY_A) || down(GLFW.GLFW_KEY_LEFT);
        in.right = down(GLFW.GLFW_KEY_D) || down(GLFW.GLFW_KEY_RIGHT);
        in.up = pJump || down(GLFW.GLFW_KEY_W) || down(GLFW.GLFW_KEY_UP) || down(GLFW.GLFW_KEY_SPACE);
        in.down = down(GLFW.GLFW_KEY_S) || down(GLFW.GLFW_KEY_DOWN);
        in.light = pLight;
        in.heavy = pHeavy;
        in.special1 = pSp1;
        in.special2 = pSp2;
        in.superMove = pSuper;
        pLight = pHeavy = pSp1 = pSp2 = pSuper = pJump = false;
        return in;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (session.stage == FightSession.Stage.SELECT) return selectKey(key);
        if (session.engine == null) return true;
        if (session.engine.isOver()) {
            if (session.matchEndFrames > 45 && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER
                    || key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_J)) {
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
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_Z -> pLight = true;
            case GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_X -> pHeavy = true;
            case GLFW.GLFW_KEY_U -> pLight = pHeavy = true; // throw shortcut
            case GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_C -> pSp1 = true;
            case GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_V -> pSp2 = true;
            case GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_B -> pSuper = true;
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_SPACE -> pJump = true;
            default -> {
            }
        }
        return true;
    }

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
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_J -> confirm();
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
        if (session.paused) hud.pausePanel(g, width, height);
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
            default -> new ItemStack(Items.ROTTEN_FLESH);
        };
    }

    static String styleBlurb(FighterType t) {
        return switch (t) {
            case STEVE -> "Balanced all-rounder: tosses blocks, diamond uppercuts, crafts combos.";
            case SKELETON -> "Zoner: keeps you out with arrows. Fast but fragile.";
            case VILLAGER -> "Grappler: Hmm... a Trade Offer can't be blocked.";
            case PIG -> "Tank: tough as bacon. Low stomps & overhead belly flops.";
            case BRAWLER -> "Wild mob: scrappy and straightforward.";
        };
    }

    static String movesSummary(Moveset ms) {
        return "J " + ms.light().name + "   K " + ms.heavy().name
                + "   S+J " + ms.crouchLight().name + "   S+K " + ms.crouchHeavy().name + "\n"
                + "Air J " + ms.airLight().name + "   Air K " + ms.airHeavy().name + "   J+K Throw\n"
                + "↓↘→+J or L: " + ms.special1().name + "     ↓↙← / →↓↘+J or I: " + ms.special2().name + "\n"
                + "O (full SUPER meter) or ↓↘→↓↘→+J: " + ms.superMove().name;
    }

    private void renderSelect(GuiGraphics g, int mouseX, int mouseY) {
        hud.render(g, width, height); // letterbox
        float t = session.time;
        FighterType sel = session.styles.get(selected);
        boolean open = session.isUnlocked(sel);

        hud.outlined(g, "CHOOSE YOUR FIGHTING STYLE", width / 2f, height * 0.04f + 6, width >= 420 ? 2f : 1.4f, 0xFFFFD21A, 0xFF4A2000);
        g.drawCenteredString(font, "vs " + session.info.enemyName() + "   |   AI: " + session.difficulty.displayName.toUpperCase()
                + "   |   A/D choose   ENTER fight   ESC back out", width / 2, (int) (height * 0.04f) + 18, 0xFFCCCCCC);

        // info panel on the left
        int px = 10, py = (int) (height * 0.16f);
        int pw = Math.min(width / 2 - 20, 240);
        String[] blurb = hud.wrap(styleBlurb(sel), pw - 12);
        int lines = 2 + blurb.length + (open ? 4 : 1);
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
            g.drawString(font, "L  " + ms.special1().name, px + 8, y, 0xFFB8C8FF, false);
            g.drawString(font, "I  " + ms.special2().name, px + 8, y + 11, 0xFFB8C8FF, false);
            g.drawString(font, "O  " + ms.superMove().name, px + 8, y + 22, 0xFFFFD21A, false);
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
