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
    private SelectScreenUi select;

    // latched presses so very quick taps are never lost between simulation steps
    private boolean lLP, lMP, lHP, lLK, lMK, lHK, lSP, lJump;

    public FightScreen(FightSession session) {
        super(Component.literal("Street Fight"));
        this.session = session;
    }

    @Override
    protected void init() {
        hud = new FightHud(session, font);
        if (select == null) select = new SelectScreenUi(session, font, hud);
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

    /** The Fight Guide, drawn on top of the fight (opening it as its own screen would end the fight). */
    private GuideScreen guide;

    private void openGuide(int chapter) {
        guide = GuideScreen.overlay(chapter, () -> guide = null);
        guide.init(Minecraft.getInstance(), width, height);
        play(SoundEvents.BOOK_PAGE_TURN, 1f);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (guide != null) return guide.mouseScrolled(mx, my, dx, dy);
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        if (guide != null) guide.init(mc, w, h);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (guide != null) return guide.keyPressed(key, scanCode, modifiers);
        if (session.stage == FightSession.Stage.SELECT) {
            if (key == GLFW.GLFW_KEY_G) {
                openGuide(0);
                return true;
            }
            return select == null || select.key(key);
        }
        if (session.engine == null) return true;
        if (session.finisher != null && session.finisher.active(session.time)) {
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) session.finisher.skip(session.time);
            else session.finisher.key(key); // FINISH HIM: the fatality input
            return true;
        }
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
            } else if (key == GLFW.GLFW_KEY_G) {
                openGuide(session.kiMode ? 8 : 1);
            } else if (key == GLFW.GLFW_KEY_T) {
                Coach.toggle();
                play(SoundEvents.UI_BUTTON_CLICK.value(), Coach.enabled() ? 1.4f : 0.8f);
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

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (guide != null) return guide.mouseClicked(mx, my, button);
        if (session.stage == FightSession.Stage.SELECT) {
            return select == null || select.click(mx, my, width, height);
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
            if (select == null) select = new SelectScreenUi(session, font, hud);
            if (guide != null) select.freezeTimer(session.lastDt);
            select.render(g, width, height, mouseX, mouseY);
            if (guide != null) renderGuide(g, mouseX, mouseY, partialTick);
            return;
        }
        if (session.finisher != null && session.finisher.active(session.time)) {
            session.finisher.renderHud(g, font, hud, width, height, session.time);
            return;
        }
        hud.render(g, width, height);
        if (session.ki != null && !session.paused) session.ki.renderOverlay(g, font, hud, width, height);
        if (session.paused) hud.pausePanel(g, width, height, movePage);
        else if (session.engine != null && !session.engine.isOver() && down(GLFW.GLFW_KEY_TAB)) hud.specialsHint(g, width, height);
        if (session.engine != null && session.engine.isOver() && session.matchEndFrames > 30) {
            hud.resultPanel(g, width, height, session.matchEndFrames - 30);
        }
        if (guide != null) {
            if (!session.paused) guide = null; // only from the pause menu
            else renderGuide(g, mouseX, mouseY, partialTick);
        }
    }

    /** The guide sits well above the HUD layers (text is batched, so it needs real depth to cover them). */
    private void renderGuide(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        guide.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ character select (over the world)

    static ItemStack iconFor(FighterType t) {
        return switch (t) {
            case STEVE -> new ItemStack(Items.DIAMOND_PICKAXE);
            case SKELETON -> new ItemStack(Items.BOW);
            case VILLAGER -> new ItemStack(Items.EMERALD);
            case PIG -> new ItemStack(Items.CARROT);
            case CHICKEN -> new ItemStack(Items.EGG);
            case ALCHEMIST -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.SPLASH_POTION,
                    net.minecraft.world.item.alchemy.Potions.POISON);
            case GOAT -> new ItemStack(Items.GOAT_HORN);
            case OAK -> new ItemStack(Items.OAK_SAPLING);
            case IRON_ORE -> new ItemStack(Items.RAW_IRON);
            case STONE_PICKAXE -> new ItemStack(Items.STONE_PICKAXE);
            case ZOMBIE -> new ItemStack(Items.ROTTEN_FLESH);
            case COW -> new ItemStack(Items.MILK_BUCKET);
            case DONKEY -> new ItemStack(Items.SADDLE);
            case CREEPER -> new ItemStack(Items.CREEPER_HEAD);
            case SLIME -> new ItemStack(Items.SLIME_BALL);
            case SPIDER -> new ItemStack(Items.SPIDER_EYE);
            case LEATHERWORKER -> new ItemStack(Items.LEATHER);
            case IRON_SWORD -> new ItemStack(Items.IRON_SWORD);
            case FURNACE -> new ItemStack(Items.FURNACE);
            case COAL_ORE -> new ItemStack(Items.COAL);
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
            case OAK -> "Rooted bruiser: enormous reach, armored charges, apple lobs and roots that erupt under you.";
            case IRON_ORE -> "Juggernaut: armored heavies, stalagmites, a command grab and boulder rolls. Slowest of all.";
            case STONE_PICKAXE -> "Rushdown: 4-hit mining target combo, spin mines, drill dives and rubble tosses.";
            case ZOMBIE -> "Undead grappler: bites inflict HUNGER (your super meter drains), command grab, the horde.";
            case COW -> "Bruiser: charges and back-bucks; Milk Break cleanses your debuffs and regenerates.";
            case DONKEY -> "Item trickster: Rummage pulls a random fight item from its saddlebag. Legendary Donkey Kick.";
            case CREEPER -> "Glass cannon: huge explosions that also hurt the creeper. Creep Up silently, then KABOOM.";
            case SLIME -> "Bouncer: hold W when you land to BOING higher (twice). Sticky slimeballs slow you; Mitosis splits it up.";
            case SPIDER -> "Trapper: webs slow you, bites poison you. Press W again in the air to Web Zip. Low and fast.";
            case IRON_SWORD -> "Longest reach in the game, but fragile. Falling jump attacks are CRITICAL HITS (+40%).";
            case FURNACE -> "Slow fire grappler: sets you BURNING, lays hot coals, and Smelt Down grabs you.";
            case COAL_ORE -> "Hot-headed rock: burning coal, soot clouds, Stoke the Fire (+damage). Diamond Pressure!";
            case LEATHERWORKER -> "KI CLASH ONLY: tough hide (armored Smash), a Leather Lasso that yanks you in, and hide barrages.";
            case BRAWLER -> "Wild mob: scrappy and straightforward.";
        };
    }

    static String motionText(com.streetfighter.fight.InputBuffer.Motion m) {
        return KeyNotation.motion(m, true) + " +";
    }

    static String arrowText(com.streetfighter.fight.InputBuffer.Motion m) {
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
        return moveList(ms, page, true);
    }

    static java.util.List<String> moveList(Moveset ms, int page, boolean facingRight) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String fwd = facingRight ? "D" : "A", back = facingRight ? "A" : "D";
        if (page == 0) {
            out.add("\u00a7eSPECIALS\u00a77   easy: H + direction    classic: the keys in order, then a punch (U/I/O) or kick (J/K/L)");
            out.add("\u00a77Shown for facing " + (facingRight ? "RIGHT" : "LEFT") + " (flips when you switch sides). Two buttons = EX version (half a bar).");
            for (Moveset.Special sp : ms.specials) {
                out.add(KeyNotation.special(sp.motion(), sp.kick(), facingRight) + "   \u00a77or " + KeyNotation.simple(sp.modernDir(), facingRight)
                        + "\u00a7r   " + sp.name());
            }
            out.add("\u00a76SUPER ARTS");
            for (Moveset.Super su : ms.supers) {
                String modern = su.level() == 1 ? "H+O" : "H+L";
                out.add("Lv" + su.level() + "  " + KeyNotation.special(su.motion(), su.kick(), facingRight) + "  \u00a77or " + modern
                        + "\u00a7r   " + su.move().name + "  \u00a77(" + su.level() + " bar" + (su.level() > 1 ? "s" : "") + ")");
            }
            out.add("\u00a7bSYSTEM");
            out.add(fwd + ", " + fwd + " (tap twice) Dash     " + back + ", " + back + " Backdash (briefly invincible)");
            out.add("U+J  " + ms.forwardThrow.name + "  (" + back + "+U+J  " + ms.backThrow.name + ")    U+J when grabbed: tech");
            out.add("I+K (hold)  Drive Parry      O+L  Drive Impact (armored, crumples)");
            for (Moveset.CommandNormal cn : ms.commandNormals) {
                String d = KeyNotation.dir(cn.dir(), facingRight);
                out.add(d + "+" + keyOf(cn.button()) + "  " + cn.move().name + (cn.move().level == com.streetfighter.fight.HitLevel.HIGH ? " (overhead)" : cn.move().level == com.streetfighter.fight.HitLevel.LOW ? " (low)" : ""));
            }
            for (Moveset.TargetCombo tc : ms.targetCombos) {
                out.add("Target: " + tc.from().name + " then " + keyOf(tc.button()) + "  " + tc.move().name);
            }
        } else {
            out.add("\u00a7eNORMALS\u00a7r   U I O = light/medium/heavy punch    J K L = light/medium/heavy kick");
            for (var b : new com.streetfighter.fight.InputBuffer.Button[]{com.streetfighter.fight.InputBuffer.Button.LP,
                    com.streetfighter.fight.InputBuffer.Button.MP, com.streetfighter.fight.InputBuffer.Button.HP,
                    com.streetfighter.fight.InputBuffer.Button.LK, com.streetfighter.fight.InputBuffer.Button.MK,
                    com.streetfighter.fight.InputBuffer.Button.HK}) {
                out.add(keyOf(b) + "  " + ms.stand.get(b).name + "   \u00a77S+" + keyOf(b) + "\u00a7r " + ms.crouch.get(b).name
                        + "   \u00a77in the air " + keyOf(b) + "\u00a7r " + ms.air.get(b).name);
            }
            out.add("\u00a77Lights chain into each other and into heavier normals; most normals cancel into specials,");
            out.add("\u00a77specials cancel into Super Arts. Jump into a wall and press W again to wall-jump.");
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
}
