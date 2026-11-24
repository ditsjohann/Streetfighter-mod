package com.streetfighter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The Fight Guide: everything a new player needs, one short chapter at a time. Open it with G (outside fights),
 * from the character select screen, or from the pause menu. Text in [brackets] is drawn as a keyboard key.
 */
public class GuideScreen extends Screen {
    private record Chapter(String title, ItemStack icon, List<String> lines) {
    }

    private record Entry(ItemStack icon, String name, String where) {
    }

    private static final List<Chapter> CHAPTERS = List.of(
            new Chapter("Start here", new ItemStack(Items.IRON_SWORD), List.of(
                    "# Welcome to Street Fighter mode!",
                    "Mobs don't attack you any more. Instead, you settle things with a 1-on-1 fight, right there in the world.",
                    "",
                    "# Your first fight in 3 steps",
                    "1. Look at any mob and HOLD [E] for a moment. (A quick tap of [E] still opens your inventory.)",
                    "2. Pick a fighting style with [A] [D] and press [ENTER]. New? Pick STEVE: he's the all-rounder.",
                    "3. Win 2 rounds to knock the mob out.",
                    "",
                    "# Why fight?",
                    "- A beaten mob drops far more loot than killing it normally (a pig drops about 10 porkchops).",
                    "- You learn its fighting style and can pick it in your next fight.",
                    "> Your first fight comes with step-by-step training at the bottom of the screen.")),
            new Chapter("Moving & blocking", new ItemStack(Items.SHIELD), List.of(
                    "# Moving",
                    "- [A] [D]  walk left and right",
                    "- [W]  jump      [S]  crouch",
                    "- Tap forward twice ([D] [D] when the enemy is on your right) to DASH in.",
                    "- Tap back twice to BACKDASH away (you're briefly invincible).",
                    "",
                    "# Blocking",
                    "Hold the key that points AWAY from your opponent. If they're on your right, hold [A].",
                    "- Standing block stops most attacks and overheads.",
                    "- Crouch block ([S] + away) stops LOW attacks like sweeps.",
                    "> Forward and back flip when you switch sides. The keys shown in fights always match your current side.")),
            new Chapter("Attacking", new ItemStack(Items.IRON_AXE), List.of(
                    "# Punches and kicks",
                    "- Punch: [U] light   [I] medium   [O] heavy",
                    "- Kick:  [J] light   [K] medium   [L] heavy",
                    "Light attacks are fast but weak. Heavy attacks hit hard but leave you open if they miss.",
                    "",
                    "# Mix it up",
                    "- Hold [S] while attacking for low attacks. Press them in the air for jumping attacks.",
                    "- Chain them: light, then medium, then heavy ([U] [I] [O]) is a simple combo.",
                    "> Every style has its own moves. Pause with [ESC] to see the full move list of yours.")),
            new Chapter("Special moves", new ItemStack(Items.BLAZE_POWDER), List.of(
                    "# The easy way: [H] + a direction",
                    "Every style has 4 special moves (fireballs, uppercuts, charges...). You get them with ONE key:",
                    "- [H] on its own",
                    "- [H] + toward the enemy",
                    "- [H] + away from the enemy",
                    "- [H] + [S]",
                    "> In a fight, hold [TAB] to see what each [H] move does for your style.",
                    "",
                    "# The classic way (optional)",
                    "Fighting-game players can also use the classic inputs, like a quarter circle: [S], [S]+[D], [D] + a punch.",
                    "They do the same moves. The pause menu lists them for your current side.")),
            new Chapter("Super meter", new ItemStack(Items.NETHER_STAR), List.of(
                    "# The SUPER bar",
                    "The bars at the bottom fill up as you hit and get hit. When one is full, you can spend it:",
                    "- [H] + [O]  your level 1 SUPER (1 bar)",
                    "- [H] + [L]  your strongest SUPER (2 or 3 bars)",
                    "- Special + two buttons of the same kind (like [U]+[I]) = an EX special (half a bar).",
                    "> When your meter is full for the first time, you'll get a reminder on screen.")),
            new Chapter("Defense & tricks", new ItemStack(Items.TOTEM_OF_UNDYING), List.of(
                    "# Grabs",
                    "- [U]+[J] together: THROW. Throws beat blocking!",
                    "- Got grabbed? Press [U]+[J] right away to break free (a tech).",
                    "",
                    "# Advanced",
                    "- Hold [I]+[K]: PARRY. Catches attacks and builds meter.",
                    "- [O]+[L]: DRIVE IMPACT, an armored rush that crumples your opponent.",
                    "- Jump at a wall and press [W] again to wall-jump.",
                    "- Sometimes a funny villager throws an item in. Walk over it to pick it up: [U] uses it one way, [J] another.")),
            new Chapter("Winning & loot", new ItemStack(Items.CHEST), List.of(
                    "# When you win",
                    "- The mob is knocked out and drops a pile of loot where you fought.",
                    "- You learn its style. Pick it on the select screen next time.",
                    "- Hard AI gives x1.5 loot, and a perfect win (no damage taken) gives x1.25.",
                    "",
                    "# FINISH HIM!",
                    "When you win the match, the loser stands there dazed. You have about 6 seconds to type your style's FATALITY:",
                    "- The keys are shown on screen (like [A] [D] [S] [O]). They light up as you press them.",
                    "- Get them right for a gory cinematic finisher unique to your style.",
                    "- Do nothing (or press [SPACE]) and they just fall. [SPACE] also skips the cinematic.",
                    "> Careful: if the mob beats you, it does its fatality on YOU.",
                    "",
                    "# When you lose",
                    "- Nothing is lost, but you're bruised (Weakness and Slowness) for 30 seconds.",
                    "- Backing out of a fight costs nothing: [ESC], then [Q].")),
            new Chapter("Who can I fight?", new ItemStack(Items.SPAWNER), List.of("@ROSTER")),
            new Chapter("Ki Clash", new ItemStack(Items.GLOWSTONE_DUST), List.of(
                    "# Dragon Ball rules, villagers only",
                    "Against a villager, press [M] on the select screen to switch to KI CLASH: flying, ki blasts and beams.",
                    "- [W]  fly up / take off      [S]  fly down (hold near the ground to land)",
                    "- [H]  ki blast        hold [H] + [S]  charge your ki",
                    "- Tap forward twice: DRAGON DASH (homes in on them)",
                    "- [U] [U] [U] [U]  rush combo        [O]  SMASH (sends them flying)",
                    "- [I]+[K]  VANISH: teleport behind them (1 bar)",
                    "- Beams that collide start a BEAM STRUGGLE: mash any button!",
                    "> The Leatherworker villager ONLY fights by Ki Clash rules.")),
            new Chapter("Settings", new ItemStack(Items.COMPARATOR), List.of(
                    "# Difficulty",
                    "You chose Normal or Hard when the world was made. Ops can change it any time:",
                    "- /streetfighter difficulty normal   or   hard",
                    "",
                    "# Commands",
                    "- /streetfighter help: a quick reminder of the basics",
                    "- /streetfighter styles: which styles you know, and which you still have to beat",
                    "- /streetfighter training on: practice fights. The mob stands still, with no loot and no penalty.",
                    "- Ops: /streetfighter unlock all, and /streetfighter resetstyles",
                    "",
                    "# Config file (config/streetfighter-common.toml)",
                    "Peaceful mobs, challenge range, loot multiplier, X-ray finishers, crafting challenges and more.",
                    "> In fights, the training tips can be turned off from the pause menu with [T]."))
    );

    private static final List<Entry> ROSTER = List.of(
            new Entry(new ItemStack(Items.PLAYER_HEAD), "Steve", "Your own starting style."),
            new Entry(new ItemStack(Items.BONE), "Skeleton", "Any skeleton. Hold [E] on it."),
            new Entry(new ItemStack(Items.EMERALD), "Villager", "A plain villager (no job)."),
            new Entry(new ItemStack(Items.SPLASH_POTION), "Alchemist", "A cleric villager (purple robe)."),
            new Entry(new ItemStack(Items.LEATHER), "Leatherworker", "A leatherworker villager. Ki Clash only!"),
            new Entry(new ItemStack(Items.PORKCHOP), "Pig", "Any pig."),
            new Entry(new ItemStack(Items.FEATHER), "Chicken", "Any chicken."),
            new Entry(new ItemStack(Items.GOAT_HORN), "Goat", "Goats on mountains."),
            new Entry(new ItemStack(Items.MILK_BUCKET), "Cow", "Cows and mooshrooms."),
            new Entry(new ItemStack(Items.SADDLE), "Donkey", "Donkeys and mules."),
            new Entry(new ItemStack(Items.ROTTEN_FLESH), "Zombie", "Zombies, husks, drowned."),
            new Entry(new ItemStack(Items.CREEPER_HEAD), "Creeper", "Any creeper (it can't blow you up now)."),
            new Entry(new ItemStack(Items.SLIME_BALL), "Slime", "Slimes in swamps and slime chunks."),
            new Entry(new ItemStack(Items.SPIDER_EYE), "Spider", "Spiders and cave spiders."),
            new Entry(new ItemStack(Items.OAK_SAPLING), "Oak Tree", "Hold [E] on an oak tree's trunk."),
            new Entry(new ItemStack(Items.RAW_IRON), "Iron Ore", "Hold [E] on iron ore."),
            new Entry(new ItemStack(Items.COAL), "Coal Ore", "Hold [E] on coal ore."),
            new Entry(new ItemStack(Items.FURNACE), "Furnace", "Hold [E] on a furnace, smoker or blast furnace."),
            new Entry(new ItemStack(Items.STONE_PICKAXE), "Stone Pickaxe", "Craft one: it fights back!"),
            new Entry(new ItemStack(Items.IRON_SWORD), "Iron Sword", "Craft one: it fights back!"),
            new Entry(new ItemStack(Items.ROTTEN_FLESH), "Any other mob", "Every mob can be fought (a wild brawler style).")
    );

    private final Screen back;
    /** When shown as an overlay inside a fight (so the fight isn't closed), this closes it instead. */
    private Runnable closer;
    private int chapter;
    private int scroll;
    private int contentHeight;

    public GuideScreen(Screen back) {
        this(back, 0);
    }

    public GuideScreen(Screen back, int chapter) {
        super(Component.literal("Fight Guide"));
        this.back = back;
        this.chapter = Math.max(0, Math.min(CHAPTERS.size() - 1, chapter));
        ClientPrefs.setFlag("guideOpened", true);
    }

    @Override
    public boolean isPauseScreen() {
        return back == null;
    }

    /** An overlay drawn by another screen (the fight screen), closed through {@code closer}. */
    public static GuideScreen overlay(int chapter, Runnable closer) {
        GuideScreen s = new GuideScreen(null, chapter);
        s.closer = closer;
        return s;
    }

    @Override
    public void onClose() {
        if (closer != null) closer.run();
        else Minecraft.getInstance().setScreen(back);
    }

    private void go(int to) {
        int n = Math.max(0, Math.min(CHAPTERS.size() - 1, to));
        if (n != chapter) {
            chapter = n;
            scroll = 0;
            if (minecraft != null) minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_G -> {
                onClose();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> go(chapter + 1);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_BACKSPACE -> go(chapter - 1);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> scroll += 12;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> scroll -= 12;
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) (dy * 14);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int sideW = sideWidth();
        if (mx < sideW + 8) {
            int i = (int) ((my - 34) / 18);
            if (i >= 0 && i < CHAPTERS.size()) go(i);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private int sideWidth() {
        return Math.max(150, Math.min(175, width / 4));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xFF060912, 0xFF101A30);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int sideW = sideWidth();
        // title bar
        g.fill(0, 0, width, 26, 0xFF0A0F1E);
        g.fill(0, 25, width, 26, 0xFFE8C04A);
        g.pose().pushPose();
        g.pose().translate(10, 7, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawString(font, "FIGHT GUIDE", 0, 0, Ui.GOLD, true);
        g.pose().popPose();
        String nav = "[A] [D] chapters   [W] [S] scroll   [ESC] close";
        Ui.rich(g, font, nav, width - Ui.richWidth(font, nav) - 10, 9, Ui.DIM);

        // chapter list
        g.fill(0, 26, sideW, height, 0xC0080C18);
        for (int i = 0; i < CHAPTERS.size(); i++) {
            Chapter c = CHAPTERS.get(i);
            int y = 34 + i * 18;
            boolean sel = i == chapter;
            boolean hover = mx < sideW && my >= y && my < y + 18;
            if (sel) g.fill(0, y - 2, sideW, y + 16, 0xFF1E2A48);
            else if (hover) g.fill(0, y - 2, sideW, y + 16, 0x601E2A48);
            if (sel) g.fill(0, y - 2, 3, y + 16, Ui.GOLD);
            g.renderItem(c.icon(), 8, y - 1);
            g.drawString(font, (i + 1) + ". " + c.title(), 28, y + 3, sel ? 0xFFFFFFFF : Ui.DIM, sel);
        }

        // content
        int x0 = sideW + 16, x1 = width - 16, top = 36, bottom = height - 22;
        int maxScroll = Math.max(0, contentHeight - (bottom - top));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        g.enableScissor(x0 - 4, top - 4, x1 + 4, bottom);
        int y = top - scroll;
        Chapter c = CHAPTERS.get(chapter);
        int width = x1 - x0;
        for (String line : c.lines()) {
            if (line.equals("@ROSTER")) {
                y = roster(g, x0, y, width);
                continue;
            }
            if (line.isEmpty()) {
                y += 6;
            } else if (line.startsWith("# ")) {
                y += 2;
                g.pose().pushPose();
                g.pose().translate(x0, y, 0);
                g.pose().scale(1.3f, 1.3f, 1f);
                g.drawString(font, line.substring(2), 0, 0, Ui.GOLD, true);
                g.pose().popPose();
                g.fill(x0, y + 13, x0 + Math.min(width, (int) (font.width(line.substring(2)) * 1.3f) + 10), y + 14, 0x80E8C04A);
                y += 19;
            } else if (line.startsWith("> ")) {
                List<String> wrapped = Ui.wrap(font, line.substring(2), width - 18);
                int h = wrapped.size() * 13 + 8;
                Ui.panel(g, x0, y, x1, y + h, Ui.CYAN);
                int yy = y + 6;
                for (String w : wrapped) {
                    Ui.rich(g, font, w, x0 + 10, yy, 0xFFCFF4FF);
                    yy += 13;
                }
                y += h + 6;
            } else if (line.startsWith("- ")) {
                g.drawString(font, "•", x0 + 2, y, Ui.GOLD, false);
                y += Ui.richWrapped(g, font, line.substring(2), x0 + 12, y, width - 12, Ui.TEXT, 13) + 1;
            } else {
                y += Ui.richWrapped(g, font, line, x0, y, width, Ui.TEXT, 13) + 2;
            }
        }
        contentHeight = y + scroll - top;
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(20, (bottom - top) * (bottom - top) / Math.max(1, contentHeight));
            int barY = top + (int) ((bottom - top - barH) * (scroll / (float) maxScroll));
            g.fill(x1 + 6, top, x1 + 8, bottom, 0x40FFFFFF);
            g.fill(x1 + 6, barY, x1 + 8, barY + barH, 0xC0E8C04A);
        }

        // page footer
        String page = "Chapter " + (chapter + 1) + " of " + CHAPTERS.size();
        g.drawString(font, page, x0, height - 14, Ui.DIM, false);
        if (chapter < CHAPTERS.size() - 1) {
            String next = "Next: " + CHAPTERS.get(chapter + 1).title() + "  [D]";
            Ui.rich(g, font, next, x1 - Ui.richWidth(font, next), height - 14, Ui.TEXT);
        }
        // no super.render: it would paint the background again over the page (there are no widgets)
    }

    private int roster(GuiGraphics g, int x0, int y, int width) {
        g.drawString(font, "Beat any of these to learn their style. Here's where to find them:", x0, y, Ui.TEXT, true);
        y += 16;
        int cols = width >= 420 ? 2 : 1;
        int colW = width / cols;
        for (int i = 0; i < ROSTER.size(); i++) {
            Entry e = ROSTER.get(i);
            int cx = x0 + (i % cols) * colW, cy = y + (i / cols) * 22;
            g.fill(cx, cy - 2, cx + colW - 6, cy + 19, 0x60101830);
            g.renderItem(e.icon(), cx + 2, cy);
            g.drawString(font, e.name(), cx + 22, cy, 0xFFFFFFFF, true);
            Ui.rich(g, font, e.where(), cx + 22, cy + 10, Ui.DIM);
        }
        return y + ((ROSTER.size() + cols - 1) / cols) * 22 + 6;
    }
}
