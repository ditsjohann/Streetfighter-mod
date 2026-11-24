package com.streetfighter.client;

import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FightEvent;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.InputBuffer.Button;
import com.streetfighter.fight.Move;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * First-fight training: one small step at a time (move, punch, kick, block, special), shown as a card at the bottom
 * of the screen. Each step ticks off as soon as you do it, and progress is remembered across fights. Afterwards,
 * one-time tips pop up the first time something matters (your super meter fills up).
 * Can be switched off from the pause menu (T).
 */
public final class Coach {
    private static final String STEP = "coachStep", OFF = "coachOff", SUPER_TIP = "tipSuper";

    private record Step(String title, String text) {
    }

    private int step = ClientPrefs.integer(STEP, 0);
    private float progress;
    private float doneFlash;      // seconds left of the "NICE!" check after a step
    private float readyBanner;    // "You're ready!" after the last step
    private float superTip = -1;  // seconds the super tip has been showing
    private boolean kiMode;

    void setKiMode(boolean ki) {
        this.kiMode = ki;
    }

    public static boolean enabled() {
        return !ClientPrefs.flag(OFF);
    }

    public static void toggle() {
        ClientPrefs.setFlag(OFF, enabled());
    }

    private List<Step> steps(boolean faceRight) {
        String fwd = faceRight ? "D" : "A", back = faceRight ? "A" : "D";
        return List.of(
                new Step("MOVE", "Walk toward your opponent with [" + fwd + "]. Tap [" + fwd + "] [" + fwd + "] to dash."),
                new Step("PUNCH", "Punch: [U] light   [I] medium   [O] heavy"),
                new Step("KICK", "Kick: [J] light   [K] medium   [L] heavy"),
                new Step("BLOCK", "Now they attack! Block: hold [" + back + "] (away from them). Add [S] to block low attacks."),
                new Step("SPECIAL", "Special move: hold [H] and press [" + fwd + "]. Hold [TAB] to see all of yours."));
    }

    /** During the practice steps the opponent stands and waits (except for the block lesson). */
    boolean enemyWaits() {
        return training() && step != 3;
    }

    private boolean training() {
        return enabled() && !kiMode && step < 5;
    }

    /** Called with each simulation step's events. */
    void observe(FightEngine e, List<FightEvent> events, float dt) {
        if (e.phase != FightEngine.Phase.FIGHTING) return;
        Fighter p = e.player;
        if (training()) {
            switch (step) {
                case 0 -> {
                    if (p.state == Fighter.State.WALK || p.state == Fighter.State.ATTACK && p.move == p.moves.dash) progress += dt;
                    if (progress >= 0.8f) advance();
                }
                case 3 -> {
                    if (p.holdingBack()) progress += dt;
                    if (progress >= 1.5f) advance();
                }
                default -> {
                }
            }
            for (FightEvent ev : events) {
                if (ev.fighter() != 0) {
                    continue;
                }
                if (ev.type() == FightEvent.Type.BLOCK && step == 3) advance();
                if (ev.type() != FightEvent.Type.MOVE_START && ev.type() != FightEvent.Type.SUPER) continue;
                Move m = p.move;
                if (m == null) continue;
                boolean punch = isNormal(p, m, Button.LP, Button.MP, Button.HP);
                boolean kick = isNormal(p, m, Button.LK, Button.MK, Button.HK);
                boolean special = m.kind == Move.Kind.SPECIAL || m.superLevel > 0;
                if (step == 1 && punch && (progress += 1) >= 3) advance();
                else if (step == 2 && kick && (progress += 1) >= 2) advance();
                else if (step == 4 && special) advance();
            }
        }
        // one-time tip: the first time the super meter fills up
        if (enabled() && !kiMode && superTip < 0 && p.meter >= 100 && !ClientPrefs.flag(SUPER_TIP) && !training()) {
            superTip = 0;
        }
        if (superTip >= 0) {
            superTip += dt;
            for (FightEvent ev : events) {
                if (ev.type() == FightEvent.Type.SUPER && ev.fighter() == 0) superTip = 99;
            }
            if (superTip > 8) {
                ClientPrefs.setFlag(SUPER_TIP, true);
                superTip = -2; // done for good
            }
        }
    }

    private static boolean isNormal(Fighter p, Move m, Button... buttons) {
        for (Button b : buttons) {
            if (p.moves.stand.get(b) == m || p.moves.crouch.get(b) == m || p.moves.air.get(b) == m) return true;
        }
        for (var tc : p.moves.targetCombos) {
            if (tc.move() == m) for (Button b : buttons) if (tc.button() == b) return true;
        }
        return false;
    }

    private void advance() {
        step++;
        progress = 0;
        doneFlash = 1.1f;
        ClientPrefs.setInteger(STEP, step);
        if (step >= 5) readyBanner = 5f;
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, step >= 5 ? 1.2f : 1.6f));
    }

    void tick(float dt) {
        doneFlash = Math.max(0, doneFlash - dt);
        readyBanner = Math.max(0, readyBanner - dt);
    }

    /** True when the coach is showing something (the plain help line is hidden then). */
    boolean showing() {
        return training() || readyBanner > 0 || doneFlash > 0 || (superTip >= 0 && superTip < 8);
    }

    /** Draws the training card centred above {@code bottom}. */
    void render(GuiGraphics g, Font font, FightEngine e, int w, int bottom) {
        if (e == null) return;
        boolean faceRight = e.player.facingRight;
        String title, text;
        int accent = Ui.GOLD;
        if (training()) {
            Step s = steps(faceRight).get(step);
            title = "TRAINING " + (step + 1) + "/5  " + s.title() + (step != 3 ? "   (your opponent waits)" : "");
            text = s.text();
        } else if (readyBanner > 0) {
            title = "TRAINING COMPLETE";
            text = "You're ready! Hold [TAB] in any fight to see your specials. [G] opens the Fight Guide outside fights.";
            accent = 0xFF6BE36B;
        } else if (superTip >= 0 && superTip < 8) {
            title = "SUPER READY";
            text = "Your SUPER bar is full! Press [H] + [O] for a super move.";
            accent = 0xFF7FE0FF;
        } else {
            return;
        }
        int maxW = Math.min(w - 40, 380);
        List<String> lines = Ui.wrap(font, text, maxW - 16);
        int tw = font.width(title) + (training() ? 44 : 0); // room for the progress dots
        for (String l : lines) tw = Math.max(tw, Ui.richWidth(font, l));
        int bw = tw + 18, bh = 16 + lines.size() * 13 + 2;
        int x0 = w / 2 - bw / 2, y0 = bottom - bh;
        float pulse = doneFlash > 0 ? doneFlash / 1.1f : 0;
        Ui.panel(g, x0, y0, x0 + bw, y0 + bh, accent);
        if (pulse > 0) g.fill(x0, y0, x0 + bw, y0 + bh, ((int) (pulse * 90) << 24) | 0x40FF40);
        g.drawString(font, title, x0 + 9, y0 + 4, accent, true);
        if (training()) {
            // progress dots
            for (int i = 0; i < 5; i++) {
                int dx = x0 + bw - 9 - (5 - i) * 7;
                g.fill(dx, y0 + 6, dx + 5, y0 + 11, i < step ? 0xFF6BE36B : i == step ? accent : 0xFF3A4058);
            }
        }
        int yy = y0 + 17;
        for (String l : lines) {
            Ui.rich(g, font, l, x0 + 9, yy, Ui.TEXT);
            yy += 13;
        }
        if (doneFlash > 0) {
            float a = Mth.clamp(doneFlash / 1.1f, 0, 1);
            String nice = "NICE!";
            g.pose().pushPose();
            g.pose().translate(w / 2f, y0 - 14, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            g.drawString(font, nice, -font.width(nice) / 2, 0, ((int) (a * 255) << 24) | 0x6BE36B, true);
            g.pose().popPose();
        }
    }
}
