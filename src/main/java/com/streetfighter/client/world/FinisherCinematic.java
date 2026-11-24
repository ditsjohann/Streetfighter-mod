package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.math.Axis;
import com.streetfighter.SFConfig;
import com.streetfighter.client.FightSession;
import com.streetfighter.client.Ui;
import com.streetfighter.fight.AnimPose;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.FighterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.streetfighter.client.world.AnimClips.*;

/**
 * Mortal Kombat style finishers, staged in the world.
 * <ol>
 *   <li><b>FINISH HIM!</b> The match is won, time stops and the arena goes dark. The loser stands dazed while the
 *       winner gets a few seconds to enter its style's <b>Fatality</b> (the keys are shown on screen and light up as
 *       you press them). Doing nothing (or SPACE) just lets them fall: the victim's own unique death plays.
 *       When the computer wins, it always performs its fatality on you.</li>
 *   <li><b>FATALITY</b>: a multi-act cinematic unique to the winner's style, chained from acts like an x-ray bone
 *       break, an uppercut into the sky and a crater-making slam, a rain of arrows, a crushing block, impalement,
 *       explosion, burning to ash, dissolving, a swarming horde, decapitation, a web cocoon... with hard camera cuts,
 *       slow orbits, gore (blood, or bone, wood, stone and metal chips depending on the victim) and body chunks in the
 *       victim's colours.</li>
 *   <li><b>The outro</b>: the camera turns to the winner; FATALITY (dripping blood), "X WINS" and FLAWLESS VICTORY.</li>
 * </ol>
 */
public final class FinisherCinematic implements FightAnimator.FinisherHook {
    public static FinisherCinematic current;

    enum Bone { SKULL, JAW, SPINE, RIB, ARM, LEG, PELVIS, ALL }

    enum Shape { BIPED, QUAD, BIRD, CREEPER, TOOL, BLOB, SPIDER, FURNACE }

    public enum Death {
        COLLAPSE("KNOCKED OUT COLD"), EMERALD_SPILL("BANKRUPT"), POTION_POP("SHATTERED"), BONE_PILE("BONE PILE"),
        SINK("BACK TO THE GRAVE"), BELLY_UP("BELLY UP"), MILK_SPILL("SPILT MILK"), FEATHER_BURST("FEATHER EXPLOSION"),
        HORN_SNAP("DEHORNED"), BUCK_COLLAPSE("LAST KICK"), FIZZLE("FIZZLED OUT"), TIMBER("TIMBER!"),
        CRUMBLE("CRUMBLED"), SNAP("SNAPPED"), SPLIT("SPLIT UP"), CURL("CURLED UP"), TANNED("TANNED HIDE"),
        BLADE_BREAK("BROKEN BLADE"), BURN_OUT("BURNT OUT");
        final String title;

        Death(String title) {
            this.title = title;
        }
    }

    /** The building blocks of a fatality. The last act of each fatality is the kill. */
    enum Act {
        APPROACH, XRAY, LAUNCH_SLAM, MOONSHOT, KICK_AWAY, RAIN, POTION_RAIN, CRUSH, IMPALE_SKY, IMPALE_GROUND,
        EXPLODE, BURN, DISSOLVE, HORDE, DECAP, COCOON, ENGULF, LASSO
    }

    /** A style's fatality: its name, the keys to enter during FINISH HIM, and its acts. */
    record Fatal(String name, String[] input, Act[] acts) {
    }

    record Finisher(String name, Bone target, int blows, ItemStack weapon) {
    }

    private record Piece(Bone tag, float x, float y, float z, float sx, float sy, float sz) {
    }

    private record Run(Act act, float start, float dur) {
        float p(float a) {
            return Mth.clamp((a - start) / dur, 0, 1);
        }

        boolean started(float a) {
            return a >= start;
        }

        float end() {
            return start + dur;
        }
    }

    private record Stuck(ItemStack item, float lx, float ly, float lz, float rot) {
    }

    private static final class Debris {
        ItemStack item;
        BlockState block;
        Vec3 pos, vel;
        float rot, spin, scale, drag;
        final Vector3f axis = new Vector3f();
        boolean feather;
    }

    public record Shot(Vec3 focus, double dist, double orbit, double height, float roll, boolean cut, float shake) {
    }

    private enum Gore { FLESH, ROTTEN, GOO, CREEPER, BONE, WOOD, STONE, METAL }

    // timeline (seconds)
    private static final float T_INTRO = 0.5f, T_BLOW = 1.15f, T_IMPACT = 0.42f, T_SNAP = 0.85f, T_DEATH = 3.1f,
            T_FH = 6.5f, T_FH_AI = 2.6f, T_REMAINS = 2.2f, T_STAMP = 3.8f, NEVER = 1e9f;
    private static final int RAIN_HITS = 24;

    private final FightSession session;
    private final Fighter winner, loser;
    private final LivingEntity winnerE, loserE;
    private final Finisher fin;
    private final Fatal fatal;
    private final Death death;
    private final Shape shape;
    private final BlockState boneMat, flesh;
    private final List<Piece> pieces = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();
    private final List<Run> runs = new ArrayList<>();
    private final List<Stuck> stuck = new ArrayList<>();
    private final Set<String> fired = new HashSet<>();
    private final Random random = new Random();
    private final float layoutHeight;
    private float start, lastNow, flash, redFlash, shakeAmt, jolt;
    private int blowsDone;
    private boolean deathStarted, deathMid, deathEnd, snapFx;

    // FINISH HIM / timeline
    private float decidedAt = -1;
    private boolean fatality;
    private float xrayAt = NEVER, deathAt = NEVER, outroAt = NEVER, killAt = NEVER;
    private int inputIndex;
    private float inputFlash, wrongFlash, nextBeat, nextGlow;
    private float farX, nearX, loserX0;
    private final float[][] rainTargets = new float[RAIN_HITS][4];

    // the victim's body during the fatality (recomputed every frame from the acts)
    private float vLift, vAlong, vSpin, vFall, vSquash = 1, vScale = 1, wScale = 1, pillarH, cocoon, slimeCube, puddle,
            bloodPool, webs;
    private boolean vHidden, vHeadless;
    private Debris headDebris;

    public FinisherCinematic(FightSession session, Fighter winner, Fighter loser, LivingEntity winnerE, LivingEntity loserE,
                             float now) {
        this.session = session;
        this.winner = winner;
        this.loser = loser;
        this.winnerE = winnerE;
        this.loserE = loserE;
        this.start = now;
        this.lastNow = now;
        this.fin = finisherFor(winner.moves.type());
        this.fatal = fatalFor(winner.moves.type());
        FighterType body = loser.body;
        this.death = deathFor(body, FightAnimator.bodyOf(loserE));
        this.shape = switch (body) {
            case PIG, GOAT, COW, DONKEY -> Shape.QUAD;
            case CHICKEN -> Shape.BIRD;
            case CREEPER -> Shape.CREEPER;
            case STONE_PICKAXE, IRON_SWORD -> Shape.TOOL;
            case SLIME -> Shape.BLOB;
            case FURNACE -> Shape.FURNACE;
            case SPIDER -> Shape.SPIDER;
            default -> FightAnimator.bodyOf(loserE) == FightAnimator.Body.QUAD ? Shape.QUAD : Shape.BIPED;
        };
        this.boneMat = switch (body) {
            case OAK -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            case IRON_ORE, IRON_SWORD -> Blocks.IRON_BLOCK.defaultBlockState();
            case STONE_PICKAXE -> Blocks.OAK_PLANKS.defaultBlockState();
            case SLIME -> Blocks.SLIME_BLOCK.defaultBlockState(); // no bones, just a gooey core
            case FURNACE -> Blocks.MAGMA_BLOCK.defaultBlockState(); // a glowing hot core
            case COAL_ORE -> Blocks.COAL_BLOCK.defaultBlockState();
            default -> Blocks.BONE_BLOCK.defaultBlockState();
        };
        this.flesh = switch (body) {
            case OAK -> Blocks.GREEN_STAINED_GLASS.defaultBlockState();
            case IRON_ORE, STONE_PICKAXE, IRON_SWORD, FURNACE, COAL_ORE -> Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            case ZOMBIE, CREEPER, SLIME -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case SKELETON -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
            case SPIDER -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case LEATHERWORKER -> Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
            default -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        };
        layoutHeight = switch (shape) {
            case QUAD, BLOB -> 1.0f;
            case BIRD -> 1.3f;
            case CREEPER -> 1.7f;
            case TOOL -> 1.9f;
            case SPIDER -> 0.9f;
            case FURNACE -> 1.8f;
            default -> 2.0f;
        };
        build();
        stage();
    }

    // ===================================================================================== fatalities

    private static String[] seq(String s) {
        return s.split(" ");
    }

    private static Act[] acts(Act... a) {
        return a;
    }

    /** Every style's fatality. Input tokens: F/B = toward/away, U/D = W/S, LP MP HP = U I O, LK MK HK = J K L. */
    static Fatal fatalFor(FighterType style) {
        return switch (style) {
            case STEVE -> new Fatal("MINE AND CRAFT", seq("B F D HP"), acts(Act.APPROACH, Act.XRAY, Act.LAUNCH_SLAM));
            case SKELETON -> new Fatal("PINCUSHION", seq("F B F LP"), acts(Act.XRAY, Act.RAIN));
            case VILLAGER -> new Fatal("FINAL OFFER", seq("D F B HP"), acts(Act.APPROACH, Act.XRAY, Act.CRUSH));
            case ALCHEMIST -> new Fatal("MELTDOWN BREW", seq("B B F MP"), acts(Act.POTION_RAIN, Act.DISSOLVE));
            case PIG -> new Fatal("PIGSTEP STAMPEDE", seq("D D F HK"), acts(Act.HORDE));
            case CHICKEN -> new Fatal("PECKING ORDER", seq("U D U LK"), acts(Act.APPROACH, Act.XRAY, Act.HORDE));
            case GOAT -> new Fatal("SUMMIT DROP", seq("F F D HP"), acts(Act.APPROACH, Act.XRAY, Act.LAUNCH_SLAM));
            case OAK -> new Fatal("ROOTED", seq("D U D HK"), acts(Act.XRAY, Act.IMPALE_GROUND));
            case IRON_ORE -> new Fatal("SMELTER CRUSH", seq("B F B HP"), acts(Act.APPROACH, Act.XRAY, Act.CRUSH));
            case STONE_PICKAXE -> new Fatal("STRIP MINE", seq("F D F MP"), acts(Act.APPROACH, Act.XRAY, Act.DECAP));
            case ZOMBIE -> new Fatal("THE HORDE", seq("B B D LP"), acts(Act.APPROACH, Act.XRAY, Act.HORDE));
            case COW -> new Fatal("MOOONSHOT", seq("D U U HK"), acts(Act.APPROACH, Act.MOONSHOT));
            case DONKEY -> new Fatal("KICK OF THE CENTURY", seq("B F F HK"), acts(Act.APPROACH, Act.XRAY, Act.KICK_AWAY));
            case CREEPER -> new Fatal("TOTAL DETONATION", seq("D D D HP"), acts(Act.APPROACH, Act.EXPLODE));
            case SLIME -> new Fatal("ENGULFED", seq("U U D MK"), acts(Act.APPROACH, Act.ENGULF, Act.DISSOLVE));
            case SPIDER -> new Fatal("COCOONED", seq("F F B LP"), acts(Act.APPROACH, Act.COCOON, Act.HORDE));
            case IRON_SWORD -> new Fatal("SMITE", seq("B F D HK"), acts(Act.APPROACH, Act.XRAY, Act.IMPALE_SKY));
            case FURNACE -> new Fatal("SMELTED ALIVE", seq("F B D HP"), acts(Act.APPROACH, Act.BURN));
            case COAL_ORE -> new Fatal("FIREDAMP", seq("D F D HP"), acts(Act.APPROACH, Act.XRAY, Act.EXPLODE));
            case LEATHERWORKER -> new Fatal("TANNED HIDE", seq("B F B MK"), acts(Act.LASSO, Act.XRAY, Act.LAUNCH_SLAM));
            case BRAWLER -> new Fatal("BONE BREAKER", seq("F D B HP"), acts(Act.APPROACH, Act.XRAY, Act.LAUNCH_SLAM));
        };
    }

    private float dur(Act act) {
        return switch (act) {
            case APPROACH -> 0.9f;
            case XRAY -> T_INTRO + fin.blows * T_BLOW + T_SNAP;
            case LAUNCH_SLAM -> 2.7f;
            case MOONSHOT -> 4.0f;
            case KICK_AWAY -> 3.8f;
            case RAIN -> 2.9f;
            case POTION_RAIN -> 1.7f;
            case CRUSH -> 2.3f;
            case IMPALE_SKY, IMPALE_GROUND -> 2.7f;
            case EXPLODE -> 2.1f;
            case BURN -> 3.2f;
            case DISSOLVE -> 3.0f;
            case HORDE -> 3.6f;
            case DECAP -> 2.8f;
            case COCOON -> 1.9f;
            case ENGULF -> 1.5f;
            case LASSO -> 1.4f;
        };
    }

    /** Where in the act (0..1) the kill happens. */
    private static float climax(Act act) {
        return switch (act) {
            case LAUNCH_SLAM -> 0.72f;
            case MOONSHOT -> 0.86f;
            case KICK_AWAY -> 0.9f;
            case RAIN -> 0.8f;
            case CRUSH -> 0.42f;
            case IMPALE_SKY, IMPALE_GROUND -> 0.3f;
            case EXPLODE -> 0.55f;
            case BURN -> 0.85f;
            case DISSOLVE, HORDE -> 0.55f;
            case DECAP -> 0.25f;
            default -> 1f;
        };
    }

    // ===================================================================================== data

    static Finisher finisherFor(FighterType style) {
        return switch (style) {
            case STEVE -> new Finisher("PICKAXE TO THE DOME", Bone.SKULL, 2, new ItemStack(Items.DIAMOND_PICKAXE));
            case SKELETON -> new Finisher("RIB COLLECTOR", Bone.RIB, 3, new ItemStack(Items.BOW));
            case VILLAGER -> new Finisher("FINAL OFFER", Bone.JAW, 2, new ItemStack(Items.EMERALD_BLOCK));
            case ALCHEMIST -> new Finisher("BRITTLE BONES", Bone.ALL, 1, new ItemStack(Items.SPLASH_POTION));
            case PIG -> new Finisher("HOG PILE", Bone.SPINE, 2, new ItemStack(Items.PORKCHOP));
            case CHICKEN -> new Finisher("PECK FRACTURE", Bone.SKULL, 3, new ItemStack(Items.FEATHER));
            case GOAT -> new Finisher("RAM RUPTURE", Bone.RIB, 2, new ItemStack(Items.GOAT_HORN));
            case OAK -> new Finisher("TIMBER SNAP", Bone.LEG, 2, new ItemStack(Items.OAK_LOG));
            case IRON_ORE -> new Finisher("SPINE SMELTER", Bone.SPINE, 3, new ItemStack(Items.RAW_IRON_BLOCK));
            case STONE_PICKAXE -> new Finisher("DEEP MINE", Bone.SKULL, 2, new ItemStack(Items.STONE_PICKAXE));
            case ZOMBIE -> new Finisher("BONE APPETIT", Bone.ARM, 3, new ItemStack(Items.ROTTEN_FLESH));
            case COW -> new Finisher("UDDER DESTRUCTION", Bone.RIB, 2, new ItemStack(Items.MILK_BUCKET));
            case DONKEY -> new Finisher("KICK OF DOOM", Bone.JAW, 1, new ItemStack(Items.SADDLE));
            case CREEPER -> new Finisher("TOTAL DETONATION", Bone.ALL, 1, new ItemStack(Items.TNT));
            case SLIME -> new Finisher("STICKY SITUATION", Bone.SPINE, 2, new ItemStack(Items.SLIME_BLOCK));
            case SPIDER -> new Finisher("EIGHT-LEGGED SNAP", Bone.LEG, 3, new ItemStack(Items.SPIDER_EYE));
            case LEATHERWORKER -> new Finisher("WHIPLASH", Bone.SPINE, 2, new ItemStack(Items.LEAD));
            case IRON_SWORD -> new Finisher("FINAL CUT", Bone.SPINE, 3, new ItemStack(Items.IRON_SWORD));
            case FURNACE -> new Finisher("SMELTED ALIVE", Bone.ALL, 1, new ItemStack(Items.FIRE_CHARGE));
            case COAL_ORE -> new Finisher("COAL CRUSHER", Bone.RIB, 2, new ItemStack(Items.COAL_BLOCK));
            case BRAWLER -> new Finisher("BONE BREAKER", Bone.RIB, 2, new ItemStack(Items.BONE));
        };
    }

    static Death deathFor(FighterType body, FightAnimator.Body model) {
        return switch (body) {
            case SKELETON -> Death.BONE_PILE;
            case ZOMBIE -> Death.SINK;
            case VILLAGER -> Death.EMERALD_SPILL;
            case ALCHEMIST -> Death.POTION_POP;
            case PIG -> Death.BELLY_UP;
            case COW -> Death.MILK_SPILL;
            case CHICKEN -> Death.FEATHER_BURST;
            case GOAT -> Death.HORN_SNAP;
            case DONKEY -> Death.BUCK_COLLAPSE;
            case CREEPER -> Death.FIZZLE;
            case OAK -> Death.TIMBER;
            case IRON_ORE -> Death.CRUMBLE;
            case STONE_PICKAXE -> Death.SNAP;
            case SLIME -> Death.SPLIT;
            case SPIDER -> Death.CURL;
            case LEATHERWORKER -> Death.TANNED;
            case IRON_SWORD -> Death.BLADE_BREAK;
            case FURNACE -> Death.BURN_OUT;
            case COAL_ORE -> Death.CRUMBLE;
            default -> model == FightAnimator.Body.QUAD ? Death.BELLY_UP
                    : model == FightAnimator.Body.BIRD ? Death.FEATHER_BURST : Death.COLLAPSE;
        };
    }

    private void build() {
        switch (shape) {
            case QUAD -> {
                add(Bone.SKULL, 0.82f, 0.62f, 0, 0.38f, 0.34f, 0.34f);
                add(Bone.JAW, 0.86f, 0.54f, 0, 0.3f, 0.08f, 0.26f);
                for (int i = 0; i < 6; i++) add(Bone.SPINE, -0.6f + i * 0.22f, 0.86f, 0, 0.16f, 0.1f, 0.1f);
                for (int i = 0; i < 4; i++) add(Bone.RIB, -0.25f + i * 0.17f, 0.5f, 0, 0.06f, 0.36f, 0.42f);
                add(Bone.PELVIS, -0.62f, 0.76f, 0, 0.22f, 0.14f, 0.36f);
                for (int s = -1; s <= 1; s += 2) {
                    add(Bone.LEG, 0.45f, 0.05f, s * 0.15f, 0.1f, 0.72f, 0.1f);
                    add(Bone.LEG, -0.55f, 0.05f, s * 0.15f, 0.1f, 0.72f, 0.1f);
                }
            }
            case CREEPER -> {
                add(Bone.SKULL, 0, 1.25f, 0, 0.44f, 0.4f, 0.42f);
                add(Bone.JAW, 0, 1.17f, 0.06f, 0.3f, 0.07f, 0.26f);
                for (int i = 0; i < 6; i++) add(Bone.SPINE, 0, 0.42f + i * 0.12f, -0.08f, 0.1f, 0.08f, 0.1f);
                for (int i = 0; i < 4; i++) add(Bone.RIB, 0, 0.6f + i * 0.12f, 0.02f, 0.4f, 0.05f, 0.24f);
                add(Bone.PELVIS, 0, 0.36f, 0, 0.36f, 0.08f, 0.3f);
                for (int s = -1; s <= 1; s += 2) {
                    add(Bone.LEG, s * 0.12f, 0, 0.16f, 0.1f, 0.34f, 0.1f);
                    add(Bone.LEG, s * 0.12f, 0, -0.16f, 0.1f, 0.34f, 0.1f);
                }
            }
            case FURNACE -> {
                // inside a furnace: a hot core in a grate, on stubby stone legs
                add(Bone.SKULL, 0, 0.85f, 0, 0.42f, 0.42f, 0.42f);
                for (int i = 0; i < 3; i++) add(Bone.RIB, 0, 0.66f + i * 0.28f, 0, 0.9f, 0.05f, 0.9f);
                add(Bone.SPINE, 0, 0.6f, -0.42f, 0.1f, 1.0f, 0.1f);
                add(Bone.JAW, 0, 0.66f, 0.45f, 0.6f, 0.08f, 0.06f);
                for (int sd = -1; sd <= 1; sd += 2) {
                    add(Bone.LEG, sd * 0.25f, 0, 0, 0.12f, 0.55f, 0.12f);
                    add(Bone.ARM, sd * 0.72f, 0.4f, 0, 0.1f, 0.55f, 0.1f);
                }
            }
            case BLOB -> {
                // a slime has no skeleton: just its gooey core and a few bubbles
                add(Bone.SKULL, 0, 0.28f, 0, 0.42f, 0.42f, 0.42f);
                add(Bone.RIB, 0.28f, 0.62f, 0.12f, 0.1f, 0.1f, 0.1f);
                add(Bone.RIB, -0.25f, 0.7f, -0.15f, 0.08f, 0.08f, 0.08f);
                add(Bone.RIB, 0.1f, 0.12f, -0.3f, 0.07f, 0.07f, 0.07f);
            }
            case SPIDER -> {
                add(Bone.SKULL, 0.56f, 0.3f, 0, 0.3f, 0.3f, 0.3f);
                add(Bone.JAW, 0.74f, 0.22f, 0, 0.1f, 0.12f, 0.26f);
                for (int i = 0; i < 3; i++) add(Bone.SPINE, 0.28f - i * 0.16f, 0.36f, 0, 0.14f, 0.1f, 0.12f);
                for (int i = 0; i < 3; i++) add(Bone.RIB, -0.3f - i * 0.16f, 0.2f, 0, 0.06f, 0.42f, 0.5f);
                add(Bone.PELVIS, -0.78f, 0.3f, 0, 0.14f, 0.2f, 0.26f);
                for (int sd = -1; sd <= 1; sd += 2) {
                    for (int i = 0; i < 4; i++) {
                        float x = 0.3f - i * 0.18f;
                        add(Bone.LEG, x, 0.4f, sd * 0.32f, 0.06f, 0.06f, 0.4f);
                        add(Bone.LEG, x, 0f, sd * 0.52f, 0.06f, 0.44f, 0.06f);
                    }
                }
            }
            case TOOL -> {
                for (int i = 0; i < 6; i++) add(Bone.SPINE, 0, 0.05f + i * 0.22f, 0, 0.12f, 0.2f, 0.12f);
                add(Bone.SKULL, 0, 1.4f, 0, 0.9f, 0.18f, 0.18f);
                add(Bone.JAW, -0.42f, 1.24f, 0, 0.14f, 0.18f, 0.16f);
                add(Bone.RIB, 0.42f, 1.24f, 0, 0.14f, 0.18f, 0.16f);
            }
            default -> {
                float k = shape == Shape.BIRD ? 0.65f : 1f;
                add(Bone.SKULL, 0, 1.58f * k, 0, 0.42f * k, 0.38f * k, 0.4f * k);
                add(Bone.JAW, 0, 1.49f * k, 0.04f * k, 0.3f * k, 0.09f * k, 0.28f * k);
                for (int i = 0; i < 5; i++) add(Bone.SPINE, 0, (0.92f + i * 0.11f) * k, -0.1f * k, 0.11f * k, 0.08f * k, 0.11f * k);
                for (int i = 0; i < 4; i++) {
                    add(Bone.RIB, 0, (1.06f + i * 0.1f) * k, 0.02f * k, (0.56f - i * 0.03f) * k, 0.05f * k, 0.3f * k);
                }
                add(Bone.PELVIS, 0, 0.84f * k, 0, 0.5f * k, 0.1f * k, 0.24f * k);
                for (int s = -1; s <= 1; s += 2) {
                    add(Bone.ARM, s * 0.4f * k, 1.0f * k, 0, 0.1f * k, 0.44f * k, 0.1f * k);
                    add(Bone.ARM, s * 0.42f * k, 0.58f * k, 0.02f * k, 0.09f * k, 0.4f * k, 0.09f * k);
                    add(Bone.LEG, s * 0.15f * k, 0.44f * k, 0, 0.12f * k, 0.4f * k, 0.12f * k);
                    add(Bone.LEG, s * 0.15f * k, 0.04f * k, 0, 0.1f * k, 0.38f * k, 0.1f * k);
                }
            }
        }
    }

    private void add(Bone tag, float x, float y, float z, float sx, float sy, float sz) {
        pieces.add(new Piece(tag, x, y, z, sx, sy, sz));
    }


    /** Puts both fighters on the ground, facing each other: the winner a few steps back for FINISH HIM. */
    private void stage() {
        int side = winner.x <= loser.x ? -1 : 1;
        float gap = (winner.hurtWidth() + loser.hurtWidth()) / 2f + 45;
        float lo = session.engine.arenaMin + 40, hi = session.engine.arenaMax - 40;
        nearX = Mth.clamp(loser.x + side * gap, lo, hi);
        farX = Mth.clamp(loser.x + side * (gap + 120), lo, hi);
        winner.x = farX;
        for (Fighter f : new Fighter[]{winner, loser}) {
            f.y = 0;
            f.vx = f.vy = 0;
            f.prevX = f.x;
            f.prevY = 0;
            f.flying = false;
        }
        winner.facingRight = loser.x > winner.x;
        loser.facingRight = !winner.facingRight;
        loserX0 = loser.x;
    }

    private boolean targets(Bone tag) {
        if (fin.target == Bone.ALL) return true;
        Bone t = fin.target;
        if (shape == Shape.TOOL && (t == Bone.ARM || t == Bone.LEG || t == Bone.RIB)) t = Bone.SPINE;
        if ((shape == Shape.QUAD || shape == Shape.CREEPER || shape == Shape.SPIDER) && t == Bone.ARM) t = Bone.LEG;
        if (shape == Shape.BLOB) return tag == Bone.SKULL; // every blow lands on the core
        return tag == t;
    }


    // ===================================================================================== timeline

    private float xrayStart() {
        return xrayAt;
    }

    private float blowTime(int i) {
        return xrayStart() + T_INTRO + i * T_BLOW + T_IMPACT;
    }

    private float xrayEnd() {
        return xrayAt >= NEVER ? NEVER : xrayStart() + T_INTRO + fin.blows * T_BLOW + T_SNAP;
    }

    private float total() {
        return outroAt + T_STAMP;
    }

    private float firstStart() {
        if (decidedAt < 0) return NEVER;
        return fatality && !runs.isEmpty() ? runs.get(0).start() : deathAt;
    }

    public boolean active(float now) {
        return decidedAt < 0 || now - start < total();
    }

    /** SPACE/ENTER: during FINISH HIM let them fall; later jump to the outro, then to the end. */
    public void skip(float now) {
        float a = now - start;
        if (decidedAt < 0) {
            resolve(false, a);
            return;
        }
        if (a < outroAt) start = now - outroAt;
        else start = now - total();
    }

    private boolean inXray(float a) {
        return a >= xrayStart() && a < xrayEnd();
    }

    private float deathP(float a) {
        return Mth.clamp((a - deathAt) / T_DEATH, 0, 1);
    }

    private Run currentRun(float a) {
        for (Run r : runs) if (a >= r.start() && a < r.end()) return r;
        return null;
    }

    /** The FINISH HIM decision: a fatality, or let them fall (the victim's own death). */
    private void resolve(boolean doFatality, float a) {
        if (decidedAt >= 0) return;
        decidedAt = a;
        fatality = doFatality && fatal != null;
        if (!fatality) {
            deathAt = a + 0.5f;
            outroAt = deathAt + T_DEATH;
            return;
        }
        float t = a + 0.75f;
        for (Act act : fatal.acts()) {
            float d = dur(act);
            runs.add(new Run(act, t, d));
            if (act == Act.XRAY) xrayAt = t;
            t += d;
        }
        Run last = runs.get(runs.size() - 1);
        killAt = last.start() + last.dur() * climax(last.act());
        outroAt = t + T_REMAINS;
        float w = bodyHeight() * 0.22f, h = bodyHeight();
        for (int j = 0; j < RAIN_HITS; j++) {
            rainTargets[j][0] = (random.nextFloat() - 0.5f) * 2 * w;
            rainTargets[j][1] = (0.2f + random.nextFloat() * 0.7f) * h;
            rainTargets[j][2] = (random.nextFloat() - 0.5f) * w;
            rainTargets[j][3] = -150 + random.nextFloat() * 50;
        }
        Vec3 p = feet();
        play(SoundEvents.ANVIL_LAND, p, 0.9f, 0.5f);
        play(SoundEvents.WITHER_SHOOT, p, 0.8f, 0.5f);
        play(SoundEvents.WARDEN_SONIC_CHARGE, p, 0.7f, 0.7f);
        flash = 0.9f;
    }

    /** True while the player (the winner) can enter the fatality. */
    public boolean wantsInput() {
        return decidedAt < 0 && winner.index == 0;
    }

    /** A key pressed during FINISH HIM. */
    public void key(int key) {
        if (!wantsInput() || fatal == null) return;
        String tok = switch (key) {
            case GLFW.GLFW_KEY_W -> "U";
            case GLFW.GLFW_KEY_S -> "D";
            case GLFW.GLFW_KEY_A -> winner.facingRight ? "B" : "F";
            case GLFW.GLFW_KEY_D -> winner.facingRight ? "F" : "B";
            case GLFW.GLFW_KEY_U -> "LP";
            case GLFW.GLFW_KEY_I -> "MP";
            case GLFW.GLFW_KEY_O -> "HP";
            case GLFW.GLFW_KEY_J -> "LK";
            case GLFW.GLFW_KEY_K -> "MK";
            case GLFW.GLFW_KEY_L -> "HK";
            default -> null;
        };
        if (tok == null) return;
        String[] s = fatal.input();
        Vec3 p = session.worldOf(winner);
        if (tok.equals(s[inputIndex])) {
            inputIndex++;
            inputFlash = 0.3f;
            play(SoundEvents.NOTE_BLOCK_BIT.value(), p, 1f, 0.7f + inputIndex * 0.25f);
            if (inputIndex >= s.length) resolve(true, lastNow - start);
        } else {
            inputIndex = tok.equals(s[0]) ? 1 : 0;
            wrongFlash = 0.3f;
            play(SoundEvents.NOTE_BLOCK_BASS.value(), p, 1f, 0.6f);
        }
    }

    /** The key a fatality token means for the winner's current side. */
    private String keyOf(String tok) {
        return switch (tok) {
            case "U" -> "W";
            case "D" -> "S";
            case "F" -> winner.facingRight ? "D" : "A";
            case "B" -> winner.facingRight ? "A" : "D";
            case "LP" -> "U";
            case "MP" -> "I";
            case "HP" -> "O";
            case "LK" -> "J";
            case "MK" -> "K";
            default -> "L";
        };
    }

    // ===================================================================================== geometry

    private float scaleOf(Fighter f, LivingEntity e) {
        return f.index == 1 ? session.enemyRenderScale : session.playerRenderScale;
    }

    private Vec3 feet() {
        return session.worldOf(loser);
    }

    private float bodyHeight() {
        return loserE.getBbHeight() * scaleOf(loser, loserE);
    }

    private float k() {
        return bodyHeight() / layoutHeight;
    }

    private float yawRad() {
        return -loserE.yBodyRot * Mth.DEG_TO_RAD;
    }

    /** Local skeleton point to world. */
    private Vec3 toWorld(float x, float y, float z) {
        Vector3f v = new Vector3f(x, y, z);
        if (shape == Shape.QUAD || shape == Shape.SPIDER) v.rotateY(-Mth.HALF_PI);
        v.mul(k());
        v.rotateY(yawRad());
        Vec3 f = feet();
        return new Vec3(f.x + v.x, f.y + v.y, f.z + v.z);
    }

    private Vec3 anchor() {
        float x = 0, y = 0, z = 0;
        int n = 0;
        for (Piece p : pieces) {
            if (!targets(p.tag)) continue;
            x += p.x;
            y += p.y + p.sy / 2f;
            z += p.z;
            n++;
        }
        if (n == 0) return feet().add(0, bodyHeight() * 0.6, 0);
        return toWorld(x / n, y / n, z / n);
    }

    private Vec3 facing() {
        return session.arena.axis.scale(loser.facingRight ? 1 : -1);
    }


    private float winnerHeight() {
        return winnerE.getBbHeight() * scaleOf(winner, winnerE);
    }

    /** From the winner toward (and past) the victim. */
    private Vec3 dirAway() {
        return session.arena.axis.scale(loser.x >= winner.x ? 1 : -1);
    }

    /** Where the victim's feet are right now (launched, kicked away...). */
    private Vec3 victimPos() {
        Vec3 d = dirAway();
        return feet().add(d.x * vAlong, vLift, d.z * vAlong);
    }

    private static void setX(Fighter f, float x) {
        f.x = x;
        f.prevX = x;
    }

    // ===================================================================================== camera

    public Shot shot(float now) {
        float a = now - start;
        int sgn = winner.x >= loser.x ? 1 : -1; // + = the camera swings toward the winner's side
        Vec3 chest = feet().add(0, bodyHeight() * 0.6, 0);
        float baseShake = Math.max(shakeAmt, killShake(a));
        if (decidedAt < 0 || a < firstStart()) {
            // FINISH HIM: a low, wide two-shot slowly pushing in
            float p = Mth.clamp(a / 3f, 0, 1);
            Vec3 mid = session.worldOf(winner).lerp(feet(), 0.5).add(0, 1.0, 0);
            return new Shot(mid, Mth.lerp(ease(p), 6.2f, 4.7f), sgn * (14 + 6 * Math.sin(a * 0.6)), 0.05,
                    (float) Math.sin(a * 0.8) * 1.5f, a < 0.05f, 0);
        }
        if (a >= outroAt) {
            // the winner, from the front, low: the victory shot
            float p = Mth.clamp((a - outroAt) / T_STAMP, 0, 1);
            Vec3 wc = session.worldOf(winner).add(0, winnerHeight() * 0.6, 0);
            return new Shot(wc, Mth.lerp(ease(p), 2.5f, 3.6f), -sgn * (52 - 12 * p), 0.0, 0, a - outroAt < 0.04f, 0);
        }
        if (!fatality) {
        // death: wide, orbiting
        float dp = deathP(a);
        double dist = (death == Death.TIMBER ? 7.0 : 3.9) * Math.max(0.8, k() * 0.9);
        Vec3 focus = feet().add(0, bodyHeight() * (death == Death.SINK ? 0.3 : 0.45), 0);
        if (death == Death.TIMBER) focus = focus.add(facing().scale(-bodyHeight() * 0.3 * dp));
        float crash = deathCrashTime();
        float since = dp - crash;
        float shake = crash >= 0 && since >= 0 && since < 0.12f ? 12f * (1 - since / 0.12f) : 0;
        boolean cut = a - deathAt < 0.04f && a - deathAt >= 0;
        return new Shot(focus, dist, -sgn * (20 + 24 * dp), 0.9, 0, cut, shake); // the victim's side: nothing in the way

        }
        if (inXray(a)) {
        if (a < xrayStart() + T_INTRO) {
            float p = (a - xrayStart()) / T_INTRO;
            return new Shot(anchor(), Mth.lerp(ease(p), 2.8f, 2.2f), sgn * 8, 0.25, 0, a - xrayStart() < 0.05f, 0);
        }
        if (a < xrayEnd()) {
            int i = Math.min(fin.blows - 1, (int) ((a - xrayStart() - T_INTRO) / T_BLOW));
            float segStart = xrayStart() + T_INTRO + i * T_BLOW;
            float p = Mth.clamp((a - segStart) / T_BLOW, 0, 1);
            double[][] angles = {{35, 1.9, 0.1}, {-60, 1.7, -0.25}, {5, 1.55, 0.95}};
            double[] ang = angles[i % angles.length];
            float sinceImpact = a - blowTime(i);
            float shake = sinceImpact >= 0 && sinceImpact < 0.35f ? 14f * (1 - sinceImpact / 0.35f) : 0;
            boolean last = a > blowTime(fin.blows - 1);
            double orbit = sgn * (ang[0] + p * 10 + (last ? (a - blowTime(fin.blows - 1)) * 14 : 0));
            double dist = ang[1] * Mth.lerp(ease(p), 1f, 0.86f) * k() * 0.75 + 0.6;
            return new Shot(anchor(), dist, orbit, ang[2], (float) Math.sin(a * 2) * 2, a - segStart < 0.04f, shake);
        }

        }
        Run r = currentRun(a);
        if (r == null) {
            // the remains: slow pull back
            Run last = runs.get(runs.size() - 1);
            float p = Mth.clamp((a - last.end()) / T_REMAINS, 0, 1);
            return new Shot(victimPos().add(0, 0.5, 0), 4.4 + p * 1.2, -sgn * (30 + 18 * p), 1.4, 0,
                    a - last.end() < 0.04f, baseShake);
        }
        return shotFor(r, a, sgn, baseShake);
    }

    private Shot shotFor(Run r, float a, int sgn, float shake) {
        float p = r.p(a);
        boolean cut = a - r.start() < 0.04f;
        float h = bodyHeight();
        Vec3 chest = feet().add(0, h * 0.6, 0);
        Vec3 vic = victimPos().add(0, h * 0.55, 0);
        return switch (r.act()) {
            case APPROACH -> new Shot(session.worldOf(winner).lerp(feet(), 0.6).add(0, 0.9, 0),
                    Mth.lerp(ease(p), 3.9f, 2.7f), sgn * (75 - 30 * p), -0.1, 0, cut, shake);
            case LAUNCH_SLAM, MOONSHOT, KICK_AWAY -> {
                double maxY = feet().y + (r.act() == Act.LAUNCH_SLAM ? 12 : 9);
                Vec3 f = vic.y > maxY ? new Vec3(vic.x, maxY, vic.z) : vic;
                boolean landed = p >= climax(r.act());
                yield new Shot(f, landed ? 3.6 : 4.8, -sgn * (25 + 20 * p), landed ? 1.1 : -1.0, 0,
                        cut || Math.abs(a - killAt) < 0.03f, shake);
            }
            case RAIN, POTION_RAIN -> new Shot(chest, 4.7, -sgn * (28 + 25 * p), 2.0, 0, cut, shake);
            case CRUSH -> p < climax(r.act())
                    ? new Shot(chest.add(0, 0.9, 0), 3.4, -sgn * 20, -1.2, 0, cut, shake)
                    : new Shot(feet().add(0, 0.4, 0), 3.0, sgn * 25, 1.0, 0, Math.abs(a - killAt) < 0.03f, shake);
            case IMPALE_SKY, IMPALE_GROUND -> new Shot(vic, 3.5, -sgn * (15 + 80 * p), -0.6, 0, cut, shake);
            case EXPLODE -> new Shot(chest, p < 0.55f ? Math.max(h * 2.2f, Mth.lerp(p / 0.55f, 3.6f, 2.5f)) : Math.max(5.4, h * 3.5),
                    -sgn * 18, 0.4, 0,
                    cut || Math.abs(a - killAt) < 0.03f, shake);
            case BURN, DISSOLVE, ENGULF, COCOON -> new Shot(chest, Math.max(2.9, h * 2.3), -sgn * (10 + 110 * p), 0.3, 0, cut, shake);
            case HORDE -> new Shot(chest, 5.6, -sgn * (20 + 35 * p), 2.6, 0, cut, shake);
            case DECAP -> {
                if (headDebris != null && p > 0.27f && p < 0.62f) yield new Shot(headDebris.pos, 2.4, sgn * 35, 0.2, 0, false, shake);
                yield new Shot(feet().add(0, h * 0.85, 0), 2.3, sgn * 40, 0.5, 0, cut, shake);
            }
            case LASSO -> new Shot(session.worldOf(winner).lerp(feet(), 0.5).add(0, 1, 0), 5.0, sgn * 8, 0.3, 0, cut, shake);
            case XRAY -> new Shot(chest, 3, 0, 0.5, 0, cut, shake);
        };
    }

    private float killShake(float a) {
        float s = a - killAt;
        return s >= 0 && s < 0.45f ? 18f * (1 - s / 0.45f) : 0;
    }

    /** Death progress at which the big impact happens (for shake), or -1. */
    private float deathCrashTime() {
        return switch (death) {
            case TIMBER -> 0.78f;
            case COLLAPSE, EMERALD_SPILL, POTION_POP, TANNED -> 0.78f;
            case CRUMBLE -> 0.3f;
            case SPLIT -> 0.4f;
            case BURN_OUT -> 0.45f;
            case CURL -> 0.55f;
            case FIZZLE -> 0.45f;
            case BELLY_UP, MILK_SPILL, HORN_SNAP, BUCK_COLLAPSE -> 0.62f;
            default -> -1f;
        };
    }


    /** Fog for the shot: {near, far, r, g, b}, or null for normal. */
    public float[] fog(float now, float normalFar) {
        float a = now - start;
        if (decidedAt >= 0 && a >= total()) return null;
        if (inXray(a)) return new float[]{2.5f, 9f, 0.0f, 0.035f, 0.11f};
        float in = ease(Mth.clamp(a / 1.2f, 0, 1));
        float near = Mth.lerp(in, normalFar * 0.5f, 3f), far = Mth.lerp(in, normalFar, 17f);
        if (decidedAt >= 0 && a >= outroAt) {
            float o = ease((a - outroAt) / T_STAMP);
            near = Mth.lerp(o, near, normalFar * 0.75f);
            far = Mth.lerp(o, far, normalFar);
        }
        return new float[]{near, far, 0.06f, 0.0f, 0.01f};
    }

    /** How strongly the fog colour is overridden (fades out with the outro). */
    public float fogColorMix(float now) {
        float a = now - start;
        if (decidedAt >= 0 && a >= total()) return 0;
        float in = ease(Mth.clamp(a / 0.8f, 0, 1));
        float out = decidedAt >= 0 && a >= outroAt ? 1 - ease((a - outroAt) / T_STAMP) : 1;
        return in * out;
    }

    // ===================================================================================== per-frame logic

    private boolean once(String key) {
        return fired.add(key);
    }

    public void tick(float now) {
        float dt = Mth.clamp(now - lastNow, 0, 0.1f);
        lastNow = now;
        float a = now - start;
        flash = Math.max(0, flash - dt * 6f);
        redFlash = Math.max(0, redFlash - dt * 3f);
        inputFlash = Math.max(0, inputFlash - dt);
        wrongFlash = Math.max(0, wrongFlash - dt);
        shakeAmt = Math.max(0, shakeAmt - dt * 30f);
        jolt = Math.max(0, jolt - dt * 0.6f);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 f = feet();
        if (once("finishhim")) {
            play(SoundEvents.WARDEN_ROAR, f, 0.6f, 1.25f);
            play(SoundEvents.RAVAGER_ROAR, f, 0.8f, 0.55f);
            play(SoundEvents.LIGHTNING_BOLT_THUNDER, f, 0.35f, 0.6f);
        }
        if (a < firstStart() || (fatality && a < outroAt)) {
            if (a >= nextBeat) { // the tension heartbeat
                nextBeat = a + (decidedAt < 0 ? 0.85f : 1.1f);
                play(SoundEvents.WARDEN_HEARTBEAT, f, 1.6f, 0.75f);
            }
            if (a >= nextGlow) { // a pale spotlight on the victim
                nextGlow = a + 0.18f;
                session.fx.quads.glow(f.add(0, 0.08, 0), 2.8f, 0.3f, 0x6A4038);
            }
        }
        if (decidedAt < 0) {
            if (winner.index != 0 && a >= T_FH_AI) resolve(true, a);
            else if (winner.index == 0 && a >= T_FH) resolve(false, a);
        }
        if (fatality) {
            computeBody(a);
            blows(a);
            effects(a, dt);
            if (a >= killAt && once("kill")) kill(runs.get(runs.size() - 1));
            if (a >= killAt && goreOn() && fleshy()) bloodPool = Math.min(1f, bloodPool + dt * 0.45f);
        } else if (decidedAt >= 0 && a >= deathAt) {
            deathTick(a, dt);
        }
        if (decidedAt >= 0 && a >= outroAt) outroTick(a);
        stepDebris(dt);
        LivingEntity horde = hordeDummy();
        if (horde != null) horde.walkAnimation.update(1.4f, 0.5f);
    }

    private void blows(float a) {
        while (blowsDone < fin.blows && a >= blowTime(blowsDone)) {
            blowsDone++;
            boolean last = blowsDone == fin.blows;
            Vec3 p = anchor();
            flash = 1f;
            ParticleOptions shard = new BlockParticleOption(ParticleTypes.BLOCK, boneMat);
            burst(shard, p, last ? 60 : 28, 0.25, 0.6);
            burst(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(boneMat.is(Blocks.BONE_BLOCK) ? Items.BONE
                    : boneMat.getBlock().asItem())), p, last ? 16 : 6, 0.2, 0.5);
            burst(ParticleTypes.CRIT, p, 20, 0.3, 0.8);
            gore(p, last ? 50 : 22, 0.4, 1.0);
            session.fx.quads.glow(p, last ? 3.5f : 2.2f, 0.35f, 0xFFFFFF);
            session.fx.quads.ring(p, 0.3f, last ? 4f : 2.5f, 0.35f, 0xBFE8FF);
            play(SoundEvents.BONE_BLOCK_BREAK, p, 2f, last ? 0.55f : 0.8f);
            play(SoundEvents.PLAYER_ATTACK_CRIT, p, 1.5f, 0.6f);
            play(SoundEvents.SKELETON_HURT, p, 1.2f, last ? 0.5f : 0.75f);
            if (last) {
                play(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, p, 1.4f, 0.8f);
                play(SoundEvents.ANVIL_LAND, p, 0.8f, 0.5f);
                if (fin.target() == Bone.ALL) play(SoundEvents.GENERIC_EXPLODE.value(), p, 0.8f, 1.2f);
            } else {
                play(SoundEvents.WARDEN_HEARTBEAT, p, 1.5f, 1.1f);
            }
        }
        if (!snapFx && a > blowTime(fin.blows - 1) + 0.3f) {
            snapFx = true;
            burst(new BlockParticleOption(ParticleTypes.BLOCK, boneMat), anchor(), 30, 0.2, 0.2);
        }
    }

    /** The victim's (and winner's) body state, recomputed from the acts every frame. */
    private void computeBody(float a) {
        vLift = vAlong = vSpin = vFall = pillarH = cocoon = slimeCube = puddle = webs = 0;
        vSquash = vScale = wScale = 1;
        vHidden = vHeadless = false;
        float h = bodyHeight();
        boolean engulfed = false;
        for (Run r : runs) {
            if (!r.started(a)) break;
            float p = r.p(a);
            switch (r.act()) {
                case APPROACH -> setX(winner, Mth.lerp(ease(p), farX, nearX));
                case LASSO -> {
                    float s = Math.signum(loserX0 - farX);
                    float gap = (winner.hurtWidth() + loser.hurtWidth()) / 2f + 45;
                    setX(loser, Mth.lerp(ease(Mth.clamp((p - 0.3f) / 0.4f, 0, 1)), loserX0, farX + s * gap));
                    if (p > 0.3f && p < 0.7f) vSpin = 40 * (float) Math.sin((p - 0.3f) / 0.4f * Math.PI);
                }
                case LAUNCH_SLAM, MOONSHOT -> {
                    boolean moon = r.act() == Act.MOONSHOT;
                    float hgt = moon ? 40 : winner.moves.type() == FighterType.GOAT ? 9 : 5.5f;
                    float up = moon ? 0.4f : 0.45f, land = climax(r.act());
                    if (p < 0.1f) {
                        vSquash = 1 - 0.15f * (p / 0.1f);
                    } else if (p < up) {
                        float q = (p - 0.1f) / (up - 0.1f);
                        vLift = hgt * easeOut(q);
                        vSpin = 330 * q;
                    } else if (moon && p < 0.62f) {
                        vLift = hgt;
                        vHidden = true;
                    } else if (p < land) {
                        float s0 = moon ? 0.62f : up;
                        float q = (p - s0) / (land - s0);
                        vLift = (moon ? 30 : hgt) * (1 - easeIn(q));
                        vSpin = 330 + 210 * q;
                    } else {
                        vFall = 90;
                        vSquash = p < land + 0.04f ? 0.75f : 1f;
                    }
                }
                case KICK_AWAY -> {
                    if (p >= 0.06f && p < 0.45f) {
                        float q = (p - 0.06f) / 0.39f;
                        vAlong = 30 * easeOut(q);
                        vLift = 10 * (float) Math.sin(q * Math.PI * 0.8);
                        vSpin = 720 * q;
                    } else if (p >= 0.45f && p < 0.62f) {
                        vHidden = true;
                        vAlong = 30;
                        vLift = 6;
                    } else if (p >= 0.62f && p < 0.9f) {
                        float q = (p - 0.62f) / 0.28f;
                        vLift = 24 * (1 - easeIn(q));
                        vSpin = 540 * q;
                    } else if (p >= 0.9f) {
                        vFall = 90;
                    }
                }
                case RAIN -> {
                    if (p >= 0.8f) vFall = 90 * ease((p - 0.8f) / 0.14f);
                }
                case CRUSH -> {
                    if (p >= climax(r.act())) vSquash = 0.12f;
                }
                case IMPALE_SKY -> {
                    if (p >= 0.3f) vLift = h * 0.3f * ease((p - 0.3f) / 0.15f);
                }
                case IMPALE_GROUND -> {
                    if (p >= 0.3f) {
                        pillarH = Math.max(1.6f, h * 1.3f) * easeOut(Mth.clamp((p - 0.3f) / 0.08f, 0, 1));
                        vLift = Math.max(0, pillarH - h * 0.35f);
                    }
                }
                case EXPLODE -> {
                    if (p < 0.55f) {
                        float q = p / 0.55f;
                        vScale = 1 + 0.4f * q + (float) Math.sin(a * 60) * 0.05f * q;
                        if (winner.moves.type() == FighterType.CREEPER) wScale = 1 + 0.3f * q;
                    } else {
                        vHidden = true;
                    }
                }
                case BURN -> {
                    float q = Mth.clamp((p - 0.3f) / 0.55f, 0, 1);
                    vScale = 1 - 0.45f * q;
                    vLift = -h * 0.08f * q;
                    if (p >= 0.85f) vHidden = true;
                }
                case ENGULF -> {
                    engulfed = true;
                    slimeCube = ease(p);
                }
                case DISSOLVE -> {
                    slimeCube = engulfed ? 1 - ease(Mth.clamp((p - 0.6f) / 0.4f, 0, 1)) : 0;
                    float q = Mth.clamp(p / 0.55f, 0, 1);
                    vLift = -h * 0.45f * q;
                    vSquash = 1 - 0.55f * q;
                    puddle = ease(Mth.clamp(p / 0.4f, 0, 1));
                    if (p >= 0.55f) vHidden = true;
                }
                case HORDE -> {
                    if (p >= 0.45f) vHidden = true;
                }
                case COCOON -> {
                    webs = p;
                    if (p >= 0.7f) {
                        cocoon = 1;
                        vHidden = true;
                    }
                }
                case DECAP -> {
                    if (p >= 0.25f) vHeadless = true;
                    if (p >= 0.7f) vFall = 88 * easeIn(Mth.clamp((p - 0.7f) / 0.2f, 0, 1));
                }
                default -> {
                }
            }
        }
        if (decidedAt >= 0 && a >= total() && vLift > 0) { // the props are gone: lie down where you were
            vLift = 0;
            vFall = 90;
        }
    }

    /** Sounds and particles along each act (the kill itself is in {@link #kill}). */
    private void effects(float a, float dt) {
        float h = bodyHeight();
        Vec3 f = feet();
        Vec3 mid = victimPos().add(0, h * 0.5, 0);
        for (int i = 0; i < runs.size(); i++) {
            Run r = runs.get(i);
            if (!r.started(a)) break;
            float p = r.p(a);
            boolean live = a < r.end();
            String k = i + ":";
            switch (r.act()) {
                case APPROACH -> {
                    if (once(k + "go")) play(SoundEvents.PLAYER_ATTACK_SWEEP, session.worldOf(winner), 1.2f, 0.6f);
                    if (live && random.nextFloat() < 0.6f) burst(ParticleTypes.CLOUD, session.worldOf(winner).add(0, 0.1, 0), 1, 0.3, 0.05);
                }
                case LASSO -> {
                    if (once(k + "throw")) play(SoundEvents.LEASH_KNOT_PLACE, session.worldOf(winner), 1.4f, 0.8f);
                    if (p >= 0.3f && once(k + "hit")) {
                        play(SoundEvents.PLAYER_ATTACK_KNOCKBACK, mid, 1.4f, 0.6f);
                        play(SoundEvents.LEASH_KNOT_BREAK, mid, 1.2f, 0.6f);
                        jolt = 0.08f;
                    }
                    if (p >= 0.62f && once(k + "crack")) {
                        play(SoundEvents.PLAYER_ATTACK_SWEEP, mid, 1.6f, 1.6f);
                        play(SoundEvents.ARROW_HIT, mid, 1.4f, 0.6f);
                        gore(mid, 20, 0.4, 0.8);
                    }
                }
                case LAUNCH_SLAM, MOONSHOT, KICK_AWAY -> {
                    float hitAt = r.act() == Act.KICK_AWAY ? 0.06f : 0.09f;
                    if (p >= hitAt && once(k + "hit")) {
                        gore(f.add(0, h * 0.6, 0), 45, 0.5, 1.4);
                        burst(ParticleTypes.CRIT, f.add(0, h * 0.6, 0), 30, 0.4, 1.2);
                        play(SoundEvents.PLAYER_ATTACK_STRONG, mid, 1.6f, 0.5f);
                        play(SoundEvents.GENERIC_EXPLODE.value(), mid, 0.5f, 1.6f);
                        session.fx.quads.ring(f.add(0, h * 0.6, 0), 0.3f, 4f, 0.35f, 0xFFFFFF);
                        shakeAmt = 12;
                        flash = 0.6f;
                    }
                    boolean twinkleAt = r.act() == Act.MOONSHOT ? p >= 0.4f : r.act() == Act.KICK_AWAY && p >= 0.45f;
                    if (twinkleAt && once(k + "twinkle")) {
                        session.fx.quads.glow(victimPos().add(0, 1, 0), 3.2f, 0.7f, 0xFFFFFF);
                        play(SoundEvents.AMETHYST_BLOCK_CHIME, f, 2f, 2f);
                        play(SoundEvents.AMETHYST_BLOCK_CHIME, f, 2f, 1.5f);
                    }
                    boolean fallAt = r.act() == Act.MOONSHOT ? p >= 0.62f : r.act() == Act.KICK_AWAY && p >= 0.62f;
                    if (fallAt && once(k + "whistle")) play(SoundEvents.FIREWORK_ROCKET_LAUNCH, f, 1.6f, 0.5f);
                    if (live && vLift > 1 && random.nextFloat() < 0.5f) gore(mid, 2, 0.3, 0.2); // a trail
                }
                case RAIN -> {
                    for (int j = 0; j < RAIN_HITS; j++) {
                        float hit = 0.06f + j * 0.7f / RAIN_HITS;
                        if (p >= hit && once(k + "h" + j)) {
                            stuck.add(new Stuck(new ItemStack(Items.ARROW), rainTargets[j][0], rainTargets[j][1],
                                    rainTargets[j][2], rainTargets[j][3]));
                            Vec3 at = stuckWorld(rainTargets[j][0], rainTargets[j][1], rainTargets[j][2]);
                            gore(at, 6, 0.15, 0.6);
                            jolt = 0.05f;
                            play(SoundEvents.ARROW_HIT, at, 1.2f, 0.8f + random.nextFloat() * 0.4f);
                            if (j % 4 == 0) play(SoundEvents.SKELETON_SHOOT, session.worldOf(winner), 1f, 1.2f);
                        }
                    }
                }
                case POTION_RAIN -> {
                    int[] cols = {0x4E9331, 0x5A6C81, 0xCD5CAB, 0x932423, 0x4E9331};
                    for (int j = 0; j < 5; j++) {
                        if (p >= 0.12f + j * 0.15f && once(k + "pot" + j)) {
                            Vec3 at = mid.add((random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.6);
                            burst(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT,
                                    0xFF000000 | cols[j]), at, 30, 0.6, 0.4);
                            burst(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SPLASH_POTION)), at, 10, 0.3, 0.6);
                            play(SoundEvents.SPLASH_POTION_BREAK, at, 1.6f, 0.8f + j * 0.1f);
                            jolt = 0.06f;
                        }
                    }
                }
                case CRUSH -> {
                    if (once(k + "fall")) play(SoundEvents.ANVIL_PLACE, f, 0.6f, 0.5f);
                }
                case IMPALE_SKY -> {
                    if (once(k + "fall")) play(SoundEvents.TRIDENT_THUNDER.value(), f, 0.7f, 1.2f);
                    if (a > killAt && live && random.nextFloat() < 0.5f) gore(mid, 3, 0.2, 0.1);
                }
                case IMPALE_GROUND -> {
                    if (p < 0.3f && random.nextFloat() < 0.7f) {
                        burst(new BlockParticleOption(ParticleTypes.BLOCK, groundBlock(f)), f.add(0, 0.1, 0), 4, 0.6, 0.3);
                        if (once(k + "rumble")) play(SoundEvents.ROOTED_DIRT_BREAK, f, 1.6f, 0.5f);
                    }
                    if (a > killAt && live && random.nextFloat() < 0.5f) gore(mid, 3, 0.2, 0.1);
                }
                case EXPLODE -> {
                    if (once(k + "hiss")) play(SoundEvents.CREEPER_PRIMED, mid, 1.6f, 0.7f);
                    if (live && p < 0.55f && random.nextFloat() < 0.35f) session.fx.quads.glow(mid, 2.2f, 0.08f, 0xFFFFFF);
                    if (live && p < 0.55f && random.nextFloat() < 0.4f) burst(ParticleTypes.SMOKE, mid, 2, 0.4, 0.1);
                }
                case BURN -> {
                    if (once(k + "ignite")) {
                        play(SoundEvents.FIRECHARGE_USE, mid, 1.6f, 0.6f);
                        play(SoundEvents.BLAZE_SHOOT, mid, 1.2f, 0.5f);
                    }
                    if (live && p < 0.88f) {
                        int n = 2 + (int) (p * 10);
                        burst(ParticleTypes.FLAME, victimPos().add(0, h * 0.5 * vScale, 0), n, 0.8 * vScale, 0.3);
                        if (random.nextFloat() < 0.4f) burst(ParticleTypes.LARGE_SMOKE, victimPos().add(0, h * vScale, 0), 1, 0.4, 0.1);
                        if (random.nextFloat() < 0.08f) play(SoundEvents.FIRE_AMBIENT, mid, 1.4f, 0.8f);
                    }
                }
                case ENGULF -> {
                    if (once(k + "squish")) play(SoundEvents.SLIME_SQUISH, mid, 1.8f, 0.5f);
                    if (live && random.nextFloat() < 0.6f) burst(ParticleTypes.ITEM_SLIME, mid, 3, 0.8, 0.3);
                }
                case DISSOLVE -> {
                    if (live && p < 0.7f) {
                        burst(new DustParticleOptions(new Vector3f(0.4f, 0.9f, 0.2f), 1.2f), f.add(0, 0.15, 0), 4, 1.2, 0.2);
                        if (random.nextFloat() < 0.5f) burst(ParticleTypes.BUBBLE_POP, f.add(0, 0.2, 0), 3, 1.0, 0.2);
                        if (random.nextFloat() < 0.12f) play(SoundEvents.LAVA_POP, f, 1.2f, 1.4f);
                    }
                }
                case HORDE -> {
                    if (once(k + "call")) play(hordeSound(), f, 2f, 0.8f);
                    if (live && p > 0.3f && p < 0.8f && random.nextFloat() < 0.35f) {
                        gore(f.add(0, 0.5, 0), 8, 0.9, 0.8);
                        if (random.nextFloat() < 0.3f) play(SoundEvents.GENERIC_EAT, f, 1.2f, 0.6f + random.nextFloat() * 0.4f);
                    }
                    if (p >= 0.8f && once(k + "scatter")) play(hordeSound(), f, 1.6f, 1.2f);
                }
                case COCOON -> {
                    for (int j = 0; j < 8; j++) if (p >= j * 0.085f && once(k + "web" + j)) play(SoundEvents.WOOL_PLACE, mid, 1.2f, 0.8f + j * 0.08f);
                    if (p >= 0.7f && once(k + "wrapped")) {
                        play(SoundEvents.SPIDER_AMBIENT, mid, 1.8f, 0.7f);
                        burst(ParticleTypes.POOF, mid, 16, 0.6, 0.1);
                    }
                }
                case DECAP -> {
                    if (p >= 0.18f && once(k + "whoosh")) play(SoundEvents.PLAYER_ATTACK_SWEEP, mid, 1.8f, 0.5f);
                    if (live && p > 0.25f && p < 0.78f) {
                        Vec3 neck = victimPos().add(0, h * 0.86, 0);
                        if (goreOn()) {
                            var level = Minecraft.getInstance().level;
                            for (int n = 0; n < 4; n++) {
                                level.addParticle(goreDust(), neck.x, neck.y, neck.z, (random.nextDouble() - 0.5) * 0.3,
                                        0.4 + random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.3);
                            }
                        } else {
                            burst(ParticleTypes.POOF, neck, 1, 0.1, 0.1);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    /** The fatality's kill: the big moment of its last act, then the victim's own remains (bones, drops). */
    private void kill(Run r) {
        float h = bodyHeight();
        Vec3 f = feet();
        Vec3 mid = victimPos().add(0, h * 0.5, 0);
        flash = 1f;
        redFlash = goreOn() ? 1f : 0;
        shakeAmt = 18;
        play(SoundEvents.PLAYER_ATTACK_CRIT, mid, 1.6f, 0.5f);
        switch (r.act()) {
            case LAUNCH_SLAM, MOONSHOT, KICK_AWAY -> {
                crater(f);
                gore(f.add(0, 0.3, 0), 80, 1.4, 1.2);
                play(SoundEvents.GENERIC_EXPLODE.value(), f, 1f, 0.7f);
                play(SoundEvents.ANVIL_LAND, f, 1.2f, 0.5f);
                play(SoundEvents.BONE_BLOCK_BREAK, f, 2f, 0.5f);
            }
            case RAIN -> {
                gore(mid, 40, 0.6, 0.8);
                play(SoundEvents.PLAYER_BIG_FALL, f, 1.6f, 0.7f);
            }
            case CRUSH -> {
                for (int i = 0; i < 24; i++) {
                    double ang = i / 24.0 * Math.PI * 2;
                    gore(f.add(Math.cos(ang) * 0.9, 0.15, Math.sin(ang) * 0.9), 4, 0.2, 1.0);
                }
                gibs(f.add(0, 0.3, 0), 6, 3f);
                crater(f);
                play(SoundEvents.ANVIL_LAND, f, 2f, 0.5f);
                play(SoundEvents.BONE_BLOCK_BREAK, f, 2f, 0.6f);
                play(SoundEvents.GENERIC_EXPLODE.value(), f, 0.6f, 0.6f);
            }
            case IMPALE_SKY -> {
                gore(mid, 70, 0.5, 1.6);
                play(SoundEvents.TRIDENT_HIT, mid, 2f, 0.6f);
                play(SoundEvents.ANVIL_LAND, f, 1.2f, 0.7f);
                crater(f);
            }
            case IMPALE_GROUND -> {
                gore(mid, 70, 0.5, 1.6);
                burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState()), f.add(0, 0.5, 0), 50, 0.8, 1.0);
                play(SoundEvents.WOOD_BREAK, f, 2f, 0.5f);
                play(SoundEvents.ROOTED_DIRT_BREAK, f, 2f, 0.6f);
            }
            case EXPLODE -> {
                gibs(mid, 18, 5.5f);
                spawnHead(mid.add(0, h * 0.3, 0), 7f);
                for (int i = 0; i < 4; i++) spawnItem(new ItemStack(Items.BONE), mid, 4f, 0.5f);
                gore(mid, 160, 1.8, 2.2);
                burst(ParticleTypes.EXPLOSION_EMITTER, mid, 1, 0, 0);
                burst(ParticleTypes.EXPLOSION, mid, 6, 1.6, 0);
                burst(ParticleTypes.LARGE_SMOKE, mid, 30, 1.4, 0.3);
                if (winner.moves.type() == FighterType.COAL_ORE) burst(ParticleTypes.FLAME, mid, 60, 1.6, 0.8);
                play(SoundEvents.GENERIC_EXPLODE.value(), mid, 2.4f, 0.7f);
                play(SoundEvents.GENERIC_EXPLODE.value(), mid, 1.6f, 0.5f);
                crater(f);
            }
            case BURN -> {
                BlockState[] ash = {Blocks.GRAY_CONCRETE_POWDER.defaultBlockState(), Blocks.BLACK_CONCRETE_POWDER.defaultBlockState(),
                        Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState()};
                for (int i = 0; i < 10; i++) spawnBlock(ash[i % 3], mid, 1.2f, 0.18f + random.nextFloat() * 0.12f);
                for (int i = 0; i < 3; i++) spawnItem(new ItemStack(Items.BONE), mid, 1.2f, 0.5f);
                spawnItem(new ItemStack(Items.CHARCOAL), mid, 1.2f, 0.5f);
                burst(ParticleTypes.LARGE_SMOKE, mid, 40, 1.0, 0.2);
                burst(ParticleTypes.ASH, mid, 60, 1.4, 0.2);
                play(SoundEvents.FIRE_EXTINGUISH, mid, 1.8f, 0.5f);
            }
            case DISSOLVE -> {
                for (int i = 0; i < 6; i++) spawnItem(new ItemStack(Items.BONE), f.add(0, 0.2, 0), 1.6f, 0.5f);
                spawnHead(f.add(0, 0.3, 0), 2f);
                burst(new DustParticleOptions(new Vector3f(0.4f, 0.9f, 0.2f), 1.6f), f.add(0, 0.3, 0), 50, 1.6, 0.6);
                play(SoundEvents.LAVA_EXTINGUISH, f, 1.6f, 0.6f);
                play(SoundEvents.SLIME_DEATH, f, 1.4f, 0.5f);
            }
            case HORDE -> {
                gore(f.add(0, 0.5, 0), 90, 1.4, 1.4);
                gibs(f.add(0, 0.5, 0), 8, 3f);
                for (int i = 0; i < 4; i++) spawnItem(new ItemStack(Items.BONE), f.add(0, 0.5, 0), 2.6f, 0.5f);
                play(SoundEvents.PLAYER_BURP, f, 1.6f, 0.6f);
                play(SoundEvents.BONE_BLOCK_BREAK, f, 2f, 0.6f);
            }
            case DECAP -> {
                spawnHead(f.add(0, h * 0.88, 0), 7.5f);
                gore(f.add(0, h * 0.86, 0), 60, 0.3, 1.4);
                play(SoundEvents.PLAYER_ATTACK_SWEEP, mid, 2f, 0.4f);
                play(SoundEvents.BONE_BLOCK_BREAK, mid, 2f, 0.8f);
            }
            default -> {
            }
        }
        deathMoment(mid, f, h); // the victim's own remains: bones, drops, its own sounds
        play(deathSound(), mid, 1.6f, 0.6f);
    }

    private void outroTick(float a) {
        Vec3 p = session.worldOf(winner);
        if (fatality && once("stamp")) {
            play(SoundEvents.WITHER_SPAWN, p, 0.5f, 0.8f);
            play(SoundEvents.LIGHTNING_BOLT_THUNDER, p, 0.7f, 0.9f);
        }
        if (a - outroAt >= 0.9f && once("wins")) play(SoundEvents.PLAYER_LEVELUP, p, 1f, 0.6f);
        if (perfect() && a - outroAt >= 1.6f && once("flawless")) play(SoundEvents.BEACON_POWER_SELECT, p, 1.2f, 0.7f);
    }

    private boolean perfect() {
        return winner.hp >= winner.maxHp - 0.5f;
    }

    // ===================================================================================== victim flavour: gore, gibs, heads

    private static boolean goreOn() {
        return SFConfig.FINISHER_GORE.get();
    }

    private Gore goreKind() {
        return switch (loser.body) {
            case SKELETON -> Gore.BONE;
            case ZOMBIE -> Gore.ROTTEN;
            case SLIME -> Gore.GOO;
            case CREEPER -> Gore.CREEPER;
            case OAK, STONE_PICKAXE -> Gore.WOOD;
            case IRON_ORE, COAL_ORE, FURNACE -> Gore.STONE;
            case IRON_SWORD -> Gore.METAL;
            default -> Gore.FLESH;
        };
    }

    private boolean fleshy() {
        Gore g = goreKind();
        return g == Gore.FLESH || g == Gore.ROTTEN;
    }

    private ParticleOptions goreDust() {
        return switch (goreKind()) {
            case ROTTEN -> new DustParticleOptions(new Vector3f(0.36f, 0.05f, 0.03f), 1.6f);
            case GOO, CREEPER -> new DustParticleOptions(new Vector3f(0.35f, 0.85f, 0.2f), 1.5f);
            case BONE -> new DustParticleOptions(new Vector3f(0.9f, 0.88f, 0.8f), 1.2f);
            case WOOD -> new DustParticleOptions(new Vector3f(0.55f, 0.4f, 0.2f), 1.2f);
            case STONE -> new DustParticleOptions(new Vector3f(0.45f, 0.45f, 0.45f), 1.2f);
            case METAL -> new DustParticleOptions(new Vector3f(0.85f, 0.85f, 0.9f), 1.0f);
            default -> new DustParticleOptions(new Vector3f(0.62f, 0.02f, 0.02f), 1.6f);
        };
    }

    /** A spray of whatever the victim is made of (blood, bone chips, wood, stone, sparks, slime). */
    private void gore(Vec3 p, int n, double spread, double speed) {
        if (!goreOn()) {
            burst(ParticleTypes.POOF, p, n / 4 + 1, spread, speed * 0.3);
            burst(ParticleTypes.CRIT, p, n / 2 + 1, spread, speed);
            return;
        }
        burst(goreDust(), p, n, spread, speed);
        switch (goreKind()) {
            case FLESH -> burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), p, n / 3, spread, speed);
            case ROTTEN -> burst(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH)), p, n / 4, spread, speed);
            case GOO -> burst(ParticleTypes.ITEM_SLIME, p, n / 2, spread, speed);
            case CREEPER -> burst(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GUNPOWDER)), p, n / 3, spread, speed);
            case BONE -> burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), p, n / 2, spread, speed);
            case WOOD -> burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), p, n / 2, spread, speed);
            case STONE -> burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), p, n / 2, spread, speed);
            case METAL -> {
                burst(ParticleTypes.CRIT, p, n / 2, spread, speed * 1.4);
                burst(ParticleTypes.ELECTRIC_SPARK, p, n / 3, spread, speed);
            }
        }
    }

    /** Chunks of the victim, in its own colours. */
    private BlockState[] palette() {
        return switch (loser.body) {
            case STEVE, BRAWLER -> new BlockState[]{Blocks.CYAN_WOOL.defaultBlockState(), Blocks.BLUE_WOOL.defaultBlockState(),
                    Blocks.BROWN_WOOL.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState()};
            case VILLAGER -> new BlockState[]{Blocks.BROWN_WOOL.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState()};
            case ALCHEMIST -> new BlockState[]{Blocks.PURPLE_WOOL.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState()};
            case LEATHERWORKER -> new BlockState[]{Blocks.BROWN_WOOL.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState()};
            case PIG -> new BlockState[]{Blocks.PINK_CONCRETE.defaultBlockState(), Blocks.PINK_TERRACOTTA.defaultBlockState()};
            case COW -> new BlockState[]{Blocks.BROWN_WOOL.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState(), Blocks.BLACK_WOOL.defaultBlockState()};
            case CHICKEN -> new BlockState[]{Blocks.WHITE_WOOL.defaultBlockState(), Blocks.RED_WOOL.defaultBlockState(), Blocks.YELLOW_WOOL.defaultBlockState()};
            case GOAT -> new BlockState[]{Blocks.WHITE_WOOL.defaultBlockState(), Blocks.LIGHT_GRAY_WOOL.defaultBlockState()};
            case DONKEY -> new BlockState[]{Blocks.GRAY_WOOL.defaultBlockState(), Blocks.BROWN_WOOL.defaultBlockState()};
            case ZOMBIE -> new BlockState[]{Blocks.GREEN_CONCRETE.defaultBlockState(), Blocks.CYAN_WOOL.defaultBlockState(), Blocks.BLUE_WOOL.defaultBlockState()};
            case SKELETON -> new BlockState[]{Blocks.BONE_BLOCK.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState()};
            case CREEPER -> new BlockState[]{Blocks.LIME_CONCRETE.defaultBlockState(), Blocks.GREEN_CONCRETE.defaultBlockState()};
            case SLIME -> new BlockState[]{Blocks.SLIME_BLOCK.defaultBlockState()};
            case SPIDER -> new BlockState[]{Blocks.BLACK_WOOL.defaultBlockState(), Blocks.GRAY_WOOL.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState()};
            case OAK -> new BlockState[]{Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState()};
            case IRON_ORE -> new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState()};
            case COAL_ORE -> new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(), Blocks.COAL_BLOCK.defaultBlockState()};
            case STONE_PICKAXE -> new BlockState[]{Blocks.OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
            case IRON_SWORD -> new BlockState[]{Blocks.IRON_BLOCK.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState()};
            case FURNACE -> new BlockState[]{Blocks.COBBLESTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState()};
        };
    }

    private void gibs(Vec3 at, int n, float speed) {
        BlockState[] pal = palette();
        for (int i = 0; i < n; i++) spawnBlock(pal[i % pal.length], at, speed, 0.16f + random.nextFloat() * 0.2f);
    }

    /** The victim's head, flying off: a real mob head where Minecraft has one, otherwise a cube of it. */
    private void spawnHead(Vec3 at, float speed) {
        Item head = switch (loser.body) {
            case STEVE, BRAWLER -> Items.PLAYER_HEAD;
            case SKELETON -> Items.SKELETON_SKULL;
            case ZOMBIE -> Items.ZOMBIE_HEAD;
            case CREEPER -> Items.CREEPER_HEAD;
            default -> null;
        };
        Debris d;
        if (head != null) d = spawnItem(new ItemStack(head), at, speed, 0.9f);
        else d = spawnBlock(palette()[0], at, speed, 0.42f);
        d.vel = new Vec3(dirAway().x * 1.5 + (random.nextDouble() - 0.5), speed, dirAway().z * 1.5 + (random.nextDouble() - 0.5));
        headDebris = d;
    }

    private void crater(Vec3 f) {
        BlockState g = groundBlock(f);
        for (int i = 0; i < 12; i++) spawnBlock(g, f.add(0, 0.1, 0), 3.5f, 0.18f + random.nextFloat() * 0.2f);
        burst(new BlockParticleOption(ParticleTypes.BLOCK, g), f.add(0, 0.2, 0), 60, 1.6, 0.8);
        burst(ParticleTypes.CLOUD, f.add(0, 0.2, 0), 20, 1.6, 0.2);
        session.fx.quads.ring(f.add(0, 0.15, 0), 0.4f, 6f, 0.45f, 0xE8DCC0);
    }

    private BlockState groundBlock(Vec3 f) {
        var level = Minecraft.getInstance().level;
        BlockState b = level == null ? Blocks.DIRT.defaultBlockState() : level.getBlockState(BlockPos.containing(f.add(0, -0.4, 0)));
        if (b.isAir() || !b.getFluidState().isEmpty() || b.is(Blocks.GRASS_BLOCK)) return Blocks.DIRT.defaultBlockState();
        return b;
    }

    private LivingEntity hordeDummy() {
        if (!fatality) return null;
        return switch (winner.moves.type()) {
            case PIG -> session.pigDummy;
            case CHICKEN -> session.chickenDummy;
            case ZOMBIE -> session.zombieDummy;
            case SPIDER -> session.spiderDummy;
            default -> null;
        };
    }

    private SoundEvent hordeSound() {
        return switch (winner.moves.type()) {
            case PIG -> SoundEvents.PIG_AMBIENT;
            case CHICKEN -> SoundEvents.CHICKEN_AMBIENT;
            case SPIDER -> SoundEvents.SPIDER_AMBIENT;
            default -> SoundEvents.ZOMBIE_AMBIENT;
        };
    }

    /** A point on the victim's body (lateral, up, toward-winner), following falls. */
    private Vec3 stuckWorld(float lx, float ly, float lz) {
        Vec3 ax = session.arena.normal, fw = dirAway().scale(-1);
        Vector3f v = new Vector3f((float) (ax.x * lx + fw.x * lz), ly, (float) (ax.z * lx + fw.z * lz));
        if (vFall != 0) v.rotateAxis(-vFall * Mth.DEG_TO_RAD, (float) -fw.z, 0, (float) fw.x);
        Vec3 b = victimPos();
        return new Vec3(b.x + v.x, b.y + v.y, b.z + v.z);
    }

    private void deathTick(float a, float dt) {
        float dp = deathP(a);
        Vec3 f = feet();
        float h = bodyHeight();
        Vec3 mid = f.add(0, h * 0.5, 0);
        if (!deathStarted) {
            deathStarted = true;
            play(deathSound(), mid, 1.6f, 0.8f);
            switch (death) {
                case FIZZLE -> play(SoundEvents.CREEPER_PRIMED, mid, 1.5f, 0.8f);
                case TIMBER -> play(SoundEvents.WOOD_HIT, mid, 1.5f, 0.5f);
                case CRUMBLE -> play(SoundEvents.STONE_HIT, mid, 1.5f, 0.5f);
                default -> {
                }
            }
        }
        float mt = switch (death) {
            case BONE_PILE -> 0.15f;
            case FEATHER_BURST -> 0.12f;
            case FIZZLE -> 0.45f;
            case CRUMBLE -> 0.3f;
            case SNAP -> 0.2f;
            case BLADE_BREAK -> 0.2f;
            case BURN_OUT -> 0.45f;
            case HORN_SNAP -> 0.15f;
            case BUCK_COLLAPSE -> 0.4f;
            case EMERALD_SPILL, POTION_POP, MILK_SPILL -> 0.6f;
            case BELLY_UP -> 0.55f;
            case TIMBER -> 0.78f;
            case SINK -> 0.3f;
            case COLLAPSE -> 0.78f;
            case SPLIT -> 0.4f;
            case CURL -> 0.55f;
            case TANNED -> 0.6f;
        };
        if (!deathMid && dp >= mt) {
            deathMid = true;
            deathMoment(mid, f, h);
        }
        if (!deathEnd && dp >= 0.999f) {
            deathEnd = true;
            if (death == Death.SINK) burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), f, 20, 0.4, 0.2);
        }
        // continuous bits
        if (death == Death.SINK && dp > 0.3f && dp < 1f && random.nextFloat() < 0.5f) {
            burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), f.add(0, 0.1, 0), 2, 0.3, 0.15);
        }
        if (death == Death.FIZZLE && dp < 0.45f && random.nextFloat() < 0.4f) burst(ParticleTypes.SMOKE, mid, 1, 0.3, 0.05);
        if (death == Death.BURN_OUT && dp < 0.45f && random.nextFloat() < 0.6f) {
            burst(ParticleTypes.LARGE_SMOKE, mid.add(0, h * 0.3, 0), 1, 0.3, 0.05); // sputtering out
            if (random.nextFloat() < 0.3f) burst(ParticleTypes.FLAME, mid, 2, 0.4, 0.1);
        }
        if ((death == Death.BELLY_UP || death == Death.MILK_SPILL || death == Death.COLLAPSE) && dp > 0.8f && random.nextFloat() < 0.2f) {
            Vec3 st = f.add(Math.cos(a * 6) * 0.4, h * 0.5 + 0.3, Math.sin(a * 6) * 0.4);
            Minecraft.getInstance().level.addParticle(ParticleTypes.CRIT, st.x, st.y, st.z, 0, 0.02, 0);
        }
    }

    /** The one big moment of each death: pieces fly, things pop. */
    private void deathMoment(Vec3 mid, Vec3 f, float h) {
        switch (death) {
            case BONE_PILE -> {
                for (int i = 0; i < 12; i++) spawnItem(new ItemStack(Items.BONE), mid, 2.2f, 0.55f);
                spawnItem(new ItemStack(Items.SKELETON_SKULL), f.add(0, h * 0.85, 0), 1.6f, 0.75f);
                spawnItem(new ItemStack(Items.BOW), mid, 1.5f, 0.7f);
                burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), mid, 40, 0.4, 0.5);
                play(SoundEvents.SKELETON_DEATH, mid, 1.5f, 0.8f);
                for (int i = 0; i < 3; i++) play(SoundEvents.BONE_BLOCK_HIT, mid, 1.2f, 0.7f + i * 0.2f);
            }
            case FEATHER_BURST -> {
                burst(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.FEATHER)), mid, 70, 0.5, 0.9);
                for (int i = 0; i < 12; i++) {
                    Debris d = spawnItem(new ItemStack(Items.FEATHER), mid, 2.6f, 0.45f);
                    d.feather = true;
                }
                spawnItem(new ItemStack(Items.EGG), mid, 0.8f, 0.5f);
                burst(ParticleTypes.POOF, mid, 20, 0.3, 0.1);
                play(SoundEvents.CHICKEN_DEATH, mid, 1.5f, 1f);
                play(SoundEvents.CHICKEN_EGG, mid, 1.5f, 0.8f);
            }
            case FIZZLE -> {
                for (int i = 0; i < 9; i++) spawnItem(new ItemStack(Items.GUNPOWDER), mid, 2f, 0.5f);
                burst(ParticleTypes.LARGE_SMOKE, mid, 26, 0.5, 0.15);
                burst(ParticleTypes.POOF, mid, 20, 0.4, 0.1);
                play(SoundEvents.FIRE_EXTINGUISH, mid, 1.5f, 1.4f);
                play(SoundEvents.CREEPER_DEATH, mid, 1.5f, 1.2f);
                session.fx.quads.glow(mid, 3f, 0.3f, 0xFFFFFF);
            }
            case CRUMBLE -> {
                boolean coal = loser.body == FighterType.COAL_ORE;
                BlockState[] chunks = coal
                        ? new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState(), Blocks.COAL_BLOCK.defaultBlockState()}
                        : new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState()};
                for (int i = 0; i < 9; i++) spawnBlock(chunks[i % chunks.length], mid, 2.2f, 0.3f + random.nextFloat() * 0.15f);
                for (int i = 0; i < 4; i++) spawnItem(new ItemStack(coal ? Items.COAL : Items.RAW_IRON), mid, 1.8f, 0.5f);
                if (coal) burst(ParticleTypes.LARGE_SMOKE, mid, 20, 0.6, 0.1);
                burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), mid, 60, 0.5, 0.5);
                play(SoundEvents.STONE_BREAK, mid, 2f, 0.6f);
                play(SoundEvents.ANVIL_LAND, mid, 1f, 0.6f);
            }
            case BLADE_BREAK -> {
                spawnItem(new ItemStack(Items.IRON_SWORD), f.add(0, h * 0.8, 0), 2.6f, 0.9f);
                for (int i = 0; i < 7; i++) spawnItem(new ItemStack(Items.IRON_NUGGET), mid, 2.2f, 0.4f);
                spawnItem(new ItemStack(Items.STICK), mid, 1.6f, 0.6f);
                burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), mid, 30, 0.3, 0.5);
                burst(ParticleTypes.CRIT, mid, 30, 0.5, 0.8);
                play(SoundEvents.ITEM_BREAK, mid, 2f, 0.7f);
                play(SoundEvents.ANVIL_LAND, mid, 1f, 1.6f);
            }
            case BURN_OUT -> {
                BlockState[] chunks = {Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                        Blocks.MAGMA_BLOCK.defaultBlockState()};
                for (int i = 0; i < 7; i++) spawnBlock(chunks[i % chunks.length], mid, 2f, 0.3f + random.nextFloat() * 0.12f);
                for (int i = 0; i < 5; i++) spawnItem(new ItemStack(Items.CHARCOAL), mid, 1.8f, 0.45f);
                spawnItem(new ItemStack(Items.FURNACE), mid, 1.4f, 0.6f);
                burst(ParticleTypes.LARGE_SMOKE, mid, 40, 0.7, 0.15);
                burst(ParticleTypes.FLAME, mid, 30, 0.6, 0.4);
                play(SoundEvents.FIRE_EXTINGUISH, mid, 1.8f, 0.6f);
                play(SoundEvents.STONE_BREAK, mid, 2f, 0.6f);
            }
            case SNAP -> {
                for (int i = 0; i < 2; i++) spawnItem(new ItemStack(Items.STICK), mid, 2.2f, 0.7f);
                for (int i = 0; i < 2; i++) spawnBlock(Blocks.COBBLESTONE.defaultBlockState(), f.add(0, h * 0.75, 0), 2f, 0.3f);
                spawnItem(new ItemStack(Items.STONE_PICKAXE), f.add(0, h * 0.8, 0), 2.6f, 0.9f);
                burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), mid, 30, 0.3, 0.4);
                play(SoundEvents.ITEM_BREAK, mid, 2f, 0.6f);
                play(SoundEvents.WOOD_BREAK, mid, 1.5f, 0.8f);
            }
            case HORN_SNAP -> {
                for (int i = 0; i < 2; i++) spawnItem(new ItemStack(Items.GOAT_HORN), f.add(0, h * 0.9, 0), 2.4f, 0.7f);
                play(SoundEvents.GOAT_HORN_BREAK, mid, 2f, 0.9f);
            }
            case BUCK_COLLAPSE -> {
                spawnBlock(Blocks.CHEST.defaultBlockState(), mid, 1.6f, 0.45f);
                spawnItem(new ItemStack(Items.SADDLE), mid, 1.8f, 0.7f);
                for (int i = 0; i < 3; i++) spawnItem(new ItemStack(Items.APPLE), mid, 2f, 0.5f);
                play(SoundEvents.DONKEY_CHEST, mid, 1.5f, 1f);
            }
            case EMERALD_SPILL -> {
                for (int i = 0; i < 8; i++) spawnItem(new ItemStack(Items.EMERALD), mid, 2f, 0.45f);
                burst(ParticleTypes.HAPPY_VILLAGER, mid, 20, 0.5, 0.3);
                play(SoundEvents.VILLAGER_NO, mid, 1.5f, 0.6f);
                play(SoundEvents.AMETHYST_BLOCK_CHIME, mid, 1.5f, 0.8f);
            }
            case POTION_POP -> {
                int[] cols = {0x4E9331, 0x5A6C81, 0xCD5CAB, 0x932423};
                for (int i = 0; i < 4; i++) {
                    Vec3 q = mid.add((random.nextDouble() - 0.5) * 1.2, random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 1.2);
                    burst(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | cols[i]),
                            q, 20, 0.3, 0.3);
                }
                for (int i = 0; i < 4; i++) spawnItem(new ItemStack(Items.GLASS_BOTTLE), mid, 1.8f, 0.45f);
                play(SoundEvents.SPLASH_POTION_BREAK, mid, 2f, 0.8f);
                play(SoundEvents.GLASS_BREAK, mid, 1.5f, 1f);
            }
            case MILK_SPILL -> {
                spawnItem(new ItemStack(Items.BUCKET), mid, 1.5f, 0.6f);
                burst(new net.minecraft.core.particles.DustParticleOptions(new Vector3f(1, 1, 1), 1.4f), f.add(0, 0.1, 0), 40, 0.8, 0.05);
                play(SoundEvents.BUCKET_EMPTY, mid, 1.5f, 0.9f);
                play(SoundEvents.COW_DEATH, mid, 1.5f, 0.7f);
            }
            case BELLY_UP -> {
                for (int i = 0; i < 3; i++) spawnItem(new ItemStack(Items.CARROT), mid, 1.6f, 0.5f);
                play(SoundEvents.PIG_DEATH, mid, 1.5f, 0.7f);
            }
            case TIMBER -> {
                burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()), f.add(facing().scale(-h * 0.8)).add(0, 0.4, 0), 80, 1.2, 0.3);
                for (int i = 0; i < 4; i++) spawnBlock(Blocks.OAK_LOG.defaultBlockState(), f.add(facing().scale(-h * 0.5)).add(0, 0.5, 0), 1.6f, 0.35f);
                for (int i = 0; i < 3; i++) spawnItem(new ItemStack(Items.APPLE), f.add(facing().scale(-h * 0.8)).add(0, 0.5, 0), 1.8f, 0.5f);
                play(SoundEvents.WOOD_BREAK, mid, 2f, 0.5f);
                play(SoundEvents.GENERIC_EXPLODE.value(), mid, 0.5f, 0.6f);
                play(SoundEvents.AZALEA_LEAVES_BREAK, mid, 2f, 0.7f);
            }
            case COLLAPSE -> {
                burst(ParticleTypes.CLOUD, f.add(0, 0.1, 0), 14, 0.6, 0.05);
                play(SoundEvents.PLAYER_BIG_FALL, mid, 1.5f, 0.8f);
            }
            case SPLIT -> {
                for (int i = 0; i < 4; i++) spawnBlock(Blocks.SLIME_BLOCK.defaultBlockState(), mid, 2.6f, 0.3f + random.nextFloat() * 0.12f);
                for (int i = 0; i < 7; i++) spawnItem(new ItemStack(Items.SLIME_BALL), mid, 2.2f, 0.45f);
                burst(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)), mid, 50, 0.6, 0.8);
                burst(ParticleTypes.ITEM_SLIME, mid, 40, 0.8, 0.4);
                play(SoundEvents.SLIME_DEATH, mid, 1.6f, 0.7f);
                play(SoundEvents.SLIME_SQUISH, mid, 1.8f, 0.6f);
                play(SoundEvents.HONEY_BLOCK_BREAK, mid, 1.2f, 0.8f);
            }
            case CURL -> {
                for (int i = 0; i < 6; i++) spawnItem(new ItemStack(Items.STRING), mid, 1.8f, 0.45f);
                for (int i = 0; i < 2; i++) spawnItem(new ItemStack(Items.SPIDER_EYE), mid, 1.6f, 0.5f);
                spawnBlock(Blocks.COBWEB.defaultBlockState(), mid, 1.2f, 0.4f);
                play(SoundEvents.SPIDER_DEATH, mid, 1.6f, 0.8f);
            }
            case TANNED -> {
                for (int i = 0; i < 6; i++) spawnItem(new ItemStack(Items.LEATHER), mid, 2f, 0.45f);
                Item[] armor = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_BOOTS};
                for (Item it : armor) spawnItem(new ItemStack(it), f.add(0, h * 0.7, 0), 2.2f, 0.55f);
                spawnItem(new ItemStack(Items.SADDLE), mid, 1.5f, 0.6f);
                play(SoundEvents.VILLAGER_DEATH, mid, 1.5f, 0.8f);
                play(SoundEvents.ARMOR_EQUIP_LEATHER.value(), mid, 1.5f, 0.7f);
            }
            case SINK -> {
                for (int i = 0; i < 5; i++) spawnItem(new ItemStack(Items.ROTTEN_FLESH), mid, 1.5f, 0.5f);
                play(SoundEvents.ZOMBIE_DEATH, mid, 1.5f, 0.8f);
                play(SoundEvents.GRAVEL_BREAK, mid, 1.5f, 0.6f);
            }
        }
        if (death == Death.COLLAPSE || death == Death.EMERALD_SPILL || death == Death.POTION_POP || death == Death.TANNED) {
            burst(ParticleTypes.CLOUD, f.add(0, 0.1, 0), 10, 0.6, 0.05);
            play(SoundEvents.PLAYER_BIG_FALL, mid, 1.5f, 0.8f);
        }
    }

    private SoundEvent deathSound() {
        return switch (loser.body) {
            case SKELETON -> SoundEvents.SKELETON_HURT;
            case ZOMBIE -> SoundEvents.ZOMBIE_HURT;
            case VILLAGER, ALCHEMIST -> SoundEvents.VILLAGER_HURT;
            case PIG -> SoundEvents.PIG_HURT;
            case COW -> SoundEvents.COW_HURT;
            case CHICKEN -> SoundEvents.CHICKEN_HURT;
            case GOAT -> SoundEvents.GOAT_SCREAMING_HURT;
            case DONKEY -> SoundEvents.DONKEY_HURT;
            case CREEPER -> SoundEvents.CREEPER_HURT;
            case SLIME -> SoundEvents.SLIME_HURT;
            case SPIDER -> SoundEvents.SPIDER_HURT;
            case LEATHERWORKER -> SoundEvents.VILLAGER_HURT;
            case OAK -> SoundEvents.WOOD_HIT;
            case IRON_ORE, STONE_PICKAXE, FURNACE, COAL_ORE -> SoundEvents.STONE_HIT;
            case IRON_SWORD -> SoundEvents.ANVIL_HIT;
            default -> SoundEvents.PLAYER_HURT;
        };
    }


    // ===================================================================================== debris

    private Debris spawnItem(ItemStack stack, Vec3 at, float speed, float scale) {
        Debris d = new Debris();
        d.item = stack;
        init(d, at, speed, scale);
        return d;
    }

    private Debris spawnBlock(BlockState state, Vec3 at, float speed, float scale) {
        Debris d = new Debris();
        d.block = state;
        init(d, at, speed, scale);
        return d;
    }

    private void init(Debris d, Vec3 at, float speed, float scale) {
        double ang = random.nextDouble() * Math.PI * 2;
        double out = speed * (0.4 + random.nextDouble() * 0.6);
        d.pos = at;
        d.vel = new Vec3(Math.cos(ang) * out, speed * (0.8 + random.nextDouble() * 0.7), Math.sin(ang) * out);
        d.scale = scale;
        d.spin = (random.nextFloat() - 0.5f) * 900f;
        d.axis.set(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();
        d.drag = 0.2f;
        debris.add(d);
    }

    private void stepDebris(float dt) {
        double floor = feet().y;
        for (Debris d : debris) {
            float g = d.feather ? 1.6f : 14f;
            float drag = d.feather ? 2.2f : d.drag;
            d.vel = d.vel.add(0, -g * dt, 0).scale(Math.max(0, 1 - drag * dt));
            if (d.feather) d.vel = d.vel.add(Math.sin(lastNow * 3 + d.spin) * 0.6 * dt, 0, Math.cos(lastNow * 2.6 + d.spin) * 0.6 * dt);
            d.pos = d.pos.add(d.vel.scale(dt));
            double bottom = floor + d.scale * 0.12;
            if (d.pos.y < bottom) {
                d.pos = new Vec3(d.pos.x, bottom, d.pos.z);
                if (d.vel.y < -1.5 && !d.feather) {
                    d.vel = new Vec3(d.vel.x * 0.6, -d.vel.y * 0.32, d.vel.z * 0.6);
                    d.spin *= 0.5f;
                } else {
                    d.vel = new Vec3(d.vel.x * 0.85, 0, d.vel.z * 0.85);
                    d.spin *= 0.85f;
                }
            }
            d.rot += d.spin * dt;
        }
    }


    // ===================================================================================== animation hooks

    @Override
    public Float pose(LivingEntity e, FightAnimator.Body body, float[] t, float[] cur, float time) {
        float a = lastNow - start;
        if (e == winnerE) return winnerPose(body, t, cur, a);
        if (e != loserE) return null;
        neutral(t, cur);
        if (!fatality && decidedAt >= 0 && a >= deathAt) {
        float dp = deathP(a);
        switch (death) {
            case COLLAPSE, EMERALD_SPILL, POTION_POP, TANNED -> {
                float knees = Mth.clamp((dp - 0.25f) / 0.3f, 0, 1);
                t[RLX] = -0.9f * knees; t[LLX] = -0.9f * knees; t[SQUASH] = 1 - 0.12f * knees;
                t[RAX] = -0.2f; t[LAX] = -0.2f; t[RAZ] = 0.3f + 0.6f * knees; t[LAZ] = -0.3f - 0.6f * knees;
                t[HX] = 0.5f;
            }
            case BELLY_UP, MILK_SPILL, BUCK_COLLAPSE, HORN_SNAP -> {
                if (dp > 0.6f) {
                    float tw = Mth.sin(time * 26f) * 0.6f * (1.2f - dp);
                    t[RAX] = -0.4f + tw; t[RLX] = 0.4f - tw; t[HX] = -0.5f;
                }
            }
            case SINK -> {
                t[RAX] = -2.6f; t[LAX] = -2.4f; t[HX] = -0.4f; // reaching up as it sinks
            }
            case CURL -> {
                // legs flail on its back, then curl in
                float curl = ease(Mth.clamp((dp - 0.55f) / 0.3f, 0, 1));
                float tw = dp > 0.35f ? Mth.sin(time * 30f) * 0.5f * (1 - curl) : 0;
                t[RAX] = -0.6f - 0.9f * curl + tw; t[RLX] = 0.6f + 0.9f * curl - tw; t[RAZ] = 0.4f * curl; t[HX] = -0.4f;
            }
            default -> {
            }
        }
        return 8f;

        }
        if (vLift > 0.3f || vSpin != 0 || vFall > 10) {
            // limp, flailing
            t[RAX] = -2.6f + Mth.sin(a * 9) * 0.4f; t[LAX] = -2.4f - Mth.sin(a * 9) * 0.4f;
            t[RLX] = 0.5f; t[LLX] = -0.4f; t[HX] = 0.6f;
            return 12f;
        }
        // dazed: head lolling, arms limp, swaying (FINISH HIM)
        t[HX] = -0.35f + Mth.sin(a * 3) * 0.15f;
        t[RAX] = -0.15f; t[LAX] = -0.15f; t[RAZ] = 0.25f; t[LAZ] = -0.25f;
        t[LEAN] = -8 + Mth.sin(a * 2.5f) * 5;
        return 6f;
    }

    private Float winnerPose(FightAnimator.Body body, float[] t, float[] cur, float a) {
        neutral(t, cur);
        if (decidedAt >= 0 && a >= outroAt) {
            // victory
            float pump = Math.abs(Mth.sin(a * 5f));
            if (body == FightAnimator.Body.QUAD || body == FightAnimator.Body.BIRD) {
                t[UP] = pump * 0.14f;
                t[HX] = -0.4f + Mth.sin(a * 8f) * 0.3f;
                t[RAZ] = 0.6f + pump;
            } else {
                t[RAX] = -2.9f + pump * 0.3f; t[LAX] = -0.2f; t[RAZ] = -0.1f; t[LAZ] = -0.4f;
                t[UP] = pump * 0.08f;
            }
            return 12f;
        }
        AnimPose pose = AnimPose.JAB;
        boolean strike = false;
        if (fatality && a >= firstStart()) {
            if (inXray(a)) {
                pose = body == FightAnimator.Body.QUAD ? AnimPose.CHARGE : body == FightAnimator.Body.BIRD ? AnimPose.PECK : AnimPose.HOOK;
                for (int i = 0; i < fin.blows; i++) {
                    float s = a - blowTime(i);
                    if (s > -0.1f && s < 0.3f) strike = true;
                }
            } else {
                Run r = currentRun(a);
                if (r == null) r = runs.get(runs.size() - 1);
                float p = r.p(a);
                switch (r.act()) {
                    case APPROACH -> { pose = AnimPose.DASH; strike = true; }
                    case LAUNCH_SLAM, MOONSHOT -> { pose = AnimPose.UPPERCUT; strike = p < 0.3f; }
                    case KICK_AWAY -> { pose = AnimPose.ROUNDHOUSE; strike = p < 0.25f; }
                    case RAIN, POTION_RAIN, CRUSH, IMPALE_SKY -> { pose = AnimPose.SHOOT_UP; strike = true; }
                    case IMPALE_GROUND -> { pose = AnimPose.STOMP; strike = p > 0.2f && p < 0.5f; }
                    case EXPLODE -> { pose = AnimPose.SCREAM; strike = true; }
                    case BURN, COCOON -> { pose = AnimPose.SHOOT; strike = true; }
                    case ENGULF, DISSOLVE -> { pose = AnimPose.SUMMON; strike = true; }
                    case HORDE -> { pose = AnimPose.SUMMON; strike = p < 0.5f; }
                    case DECAP -> { pose = AnimPose.HOOK; strike = p > 0.15f && p < 0.45f; }
                    case LASSO -> { pose = p < 0.3f ? AnimPose.THROW : AnimPose.THROW_BACK; strike = true; }
                    default -> {
                    }
                }
            }
        }
        AnimClips.Clip c = body == FightAnimator.Body.QUAD ? AnimClips.quadruped(pose)
                : body == FightAnimator.Body.BIRD ? AnimClips.bird(pose) : AnimClips.humanoid(pose);
        apply(t, strike ? c.strike() : c.windup());
        return strike ? 30f : 10f;
    }

    private static void neutral(float[] t, float[] cur) {
        t[LEAN] = 0; t[FLIP] = 0; t[SPIN] = 0; t[UP] = 0; t[FWD] = 0; t[SQUASH] = 1;
        cur[FLIP] = 0;
        cur[SPIN] = 0;
    }

    private static void apply(float[] t, float[] key) {
        for (int i = 0; i < key.length; i++) if (!Float.isNaN(key[i])) t[i] = key[i];
    }


    @Override
    public void transform(LivingEntity e, PoseStack ps, Vec3 face, float height) {
        float a = lastNow - start;
        if (e == winnerE) {
            if (wScale != 1) ps.scale(wScale, wScale, wScale);
            return;
        }
        if (e != loserE) return;
        float ax = (float) face.z, az = (float) -face.x; // lateral axis: positive = fall forward
        if (fatality) {
            Vec3 away = dirAway();
            ps.translate(away.x * vAlong, vLift, away.z * vAlong);
            if (jolt > 0) ps.translate((random.nextFloat() - 0.5f) * jolt, 0, (random.nextFloat() - 0.5f) * jolt);
            if (vSpin != 0) {
                ps.translate(0, height / 2, 0);
                ps.mulPose(new Quaternionf().rotateAxis(-vSpin * Mth.DEG_TO_RAD, ax, 0, az));
                ps.translate(0, -height / 2, 0);
            }
            if (vFall != 0) ps.mulPose(new Quaternionf().rotateAxis(-vFall * Mth.DEG_TO_RAD, ax, 0, az)); // backwards
            if (vSquash != 1) ps.scale(1 + (1 - vSquash) * 0.9f, vSquash, 1 + (1 - vSquash) * 0.9f);
            if (vScale != 1) ps.scale(vScale, vScale, vScale);
            return;
        }
        if (decidedAt < 0 || a < deathAt) return;
        float dp = deathP(a);
        switch (death) {
            case COLLAPSE, EMERALD_SPILL, POTION_POP, TANNED -> {
                float knees = Mth.clamp((dp - 0.25f) / 0.3f, 0, 1);
                float fall = easeIn(Mth.clamp((dp - 0.55f) / 0.23f, 0, 1));
                float bounce = dp > 0.78f ? (float) Math.sin((dp - 0.78f) / 0.22f * Math.PI) * 0.06f * (1 - (dp - 0.78f) / 0.22f) : 0;
                ps.translate(0, -0.18 * knees * height / 1.8 + bounce, 0);
                if (dp < 0.25f) ps.mulPose(new Quaternionf().rotateAxis(-dp / 0.25f * 10 * Mth.DEG_TO_RAD, ax, 0, az));
                ps.mulPose(new Quaternionf().rotateAxis(fall * 88 * Mth.DEG_TO_RAD, ax, 0, az));
            }
            case TIMBER -> {
                float creak = dp < 0.15f ? (float) Math.sin(dp * 140) * 0.02f : 0;
                float fall = easeIn(Mth.clamp((dp - 0.15f) / 0.63f, 0, 1));
                ps.translate(creak, 0, 0);
                ps.mulPose(new Quaternionf().rotateAxis(-fall * 90 * Mth.DEG_TO_RAD, ax, 0, az));
            }
            case SINK -> {
                float sway = dp < 0.3f ? (float) Math.sin(dp * 40) * 6 : 0;
                float sink = ease(Mth.clamp((dp - 0.3f) / 0.7f, 0, 1));
                ps.mulPose(new Quaternionf().rotateAxis((sway + sink * 12) * Mth.DEG_TO_RAD, ax, 0, az));
                ps.translate(0, -sink * height * 1.15, 0);
            }
            case BELLY_UP, MILK_SPILL, CURL -> {
                float wob = dp < 0.3f ? (float) Math.sin(dp * 60) * 8 : 0;
                float roll = ease(Mth.clamp((dp - 0.3f) / 0.32f, 0, 1));
                ps.translate(0, height * 0.5, 0);
                ps.mulPose(new Quaternionf().rotateAxis((wob + roll * 180) * Mth.DEG_TO_RAD, (float) face.x, 0, (float) face.z));
                ps.translate(0, -height * 0.5 + roll * height * 0.05, 0);
            }
            case HORN_SNAP -> {
                float topple = easeIn(Mth.clamp((dp - 0.3f) / 0.32f, 0, 1));
                ps.mulPose(new Quaternionf().rotateAxis(topple * 88 * Mth.DEG_TO_RAD, (float) face.x, 0, (float) face.z));
            }
            case BUCK_COLLAPSE -> {
                float rear = dp < 0.3f ? (float) Math.sin(dp / 0.3f * Math.PI) : 0;
                float topple = easeIn(Mth.clamp((dp - 0.35f) / 0.27f, 0, 1));
                ps.mulPose(new Quaternionf().rotateAxis(-rear * 32 * Mth.DEG_TO_RAD, ax, 0, az));
                ps.mulPose(new Quaternionf().rotateAxis(topple * 88 * Mth.DEG_TO_RAD, (float) face.x, 0, (float) face.z));
            }
            case SPLIT -> {
                // jiggles, flattens... and splits
                float q = ease(Mth.clamp(dp / 0.4f, 0, 1));
                float jig = (float) Math.sin(dp * 90) * 0.08f * (1 - q);
                ps.scale(1 + 0.5f * q + jig, 1 - 0.55f * q - jig, 1 + 0.5f * q + jig);
            }
            case FIZZLE -> {
                float swell = Mth.clamp(dp / 0.45f, 0, 1);
                float s = 1 + swell * 0.35f + (float) Math.sin(dp * 120) * 0.03f * swell;
                ps.scale(s, 1 + swell * 0.15f, s);
            }
            case CRUMBLE, SNAP, BONE_PILE, FEATHER_BURST, BLADE_BREAK, BURN_OUT -> {
                float shake = (float) Math.sin(dp * 160) * 0.03f;
                ps.translate(shake, 0, shake);
                if (death == Death.FEATHER_BURST) ps.scale(1 + dp * 2.5f, 1 + dp * 2.5f, 1 + dp * 2.5f);
            }
        }

    }

    @Override
    public boolean headless(LivingEntity e) {
        return e == loserE && vHeadless;
    }

    /** True while the victim's real body is replaced (x-ray) or gone (blown up, burnt, popped, crumbled...). */
    public boolean hides(LivingEntity e) {
        if (e != loserE) return false;
        float a = lastNow - start;
        if (inXray(a)) return true;
        if (fatality) return vHidden;
        if (decidedAt < 0 || a < deathAt) return false;
        float dp = deathP(a);
        return switch (death) {
            case BONE_PILE -> dp >= 0.15f;
            case FEATHER_BURST -> dp >= 0.12f;
            case FIZZLE -> dp >= 0.45f;
            case CRUMBLE -> dp >= 0.3f;
            case SNAP -> dp >= 0.2f;
            case SPLIT -> dp >= 0.4f;
            case BLADE_BREAK -> dp >= 0.2f;
            case BURN_OUT -> dp >= 0.45f;
            case SINK -> dp >= 0.999f;
            default -> false;
        };

    }

    // ===================================================================================== world rendering

    public void renderWorld(PoseStack ps, Vec3 cam, MultiBufferSource.BufferSource buf) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float a = lastNow - start;
        if (inXray(a)) renderXray(ps, cam, buf, a);
        if (fatality) renderActs(ps, cam, buf, a);
        for (Debris d : debris) {
            ps.pushPose();
            ps.translate(d.pos.x - cam.x, d.pos.y - cam.y, d.pos.z - cam.z);
            ps.mulPose(new Quaternionf().rotateAxis(d.rot * Mth.DEG_TO_RAD, d.axis));
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(d.pos));
            if (d.item != null) {
                ps.scale(d.scale, d.scale, d.scale);
                mc.getItemRenderer().renderStatic(d.item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf,
                        mc.level, 0);
            } else {
                ps.scale(d.scale, d.scale, d.scale);
                ps.translate(-0.5, -0.5, -0.5);
                mc.getBlockRenderer().renderSingleBlock(d.block, ps, buf, light, OverlayTexture.NO_OVERLAY);
            }
            ps.popPose();
        }
        buf.endBatch();
    }

    private void renderActs(PoseStack ps, Vec3 cam, MultiBufferSource.BufferSource buf, float a) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher br = mc.getBlockRenderer();
        float h = bodyHeight();
        Vec3 f = feet();
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(f.add(0, 0.5, 0)));
        // pools on the ground
        if (bloodPool > 0) flat(ps, cam, buf, br, f, 1.9f * bloodPool, Blocks.RED_STAINED_GLASS.defaultBlockState(), 0.012f);
        if (puddle > 0) flat(ps, cam, buf, br, f, 2.3f * puddle, Blocks.LIME_STAINED_GLASS.defaultBlockState(), 0.02f);
        // arrows stuck in the victim
        if (!vHidden) {
            for (Stuck s : stuck) {
                Vec3 w = stuckWorld(s.lx(), s.ly(), s.lz());
                ps.pushPose();
                ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
                ps.mulPose(Axis.YP.rotation((float) Math.atan2(cam.x - w.x, cam.z - w.z)));
                ps.mulPose(Axis.ZP.rotationDegrees(s.rot() + vFall));
                ps.scale(0.7f, 0.7f, 0.7f);
                mc.getItemRenderer().renderStatic(s.item(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
                ps.popPose();
            }
        }
        // the big props (falling sword, crushing block, pillar, cocoon, slime cube) leave with the cinematic;
        // the gore, bones and arrows stay as remains
        if (decidedAt >= 0 && a >= total()) {
            buf.endBatch();
            return;
        }
        for (int i = 0; i < runs.size(); i++) {
            Run r = runs.get(i);
            if (!r.started(a)) break;
            float p = r.p(a);
            switch (r.act()) {
                case RAIN, POTION_RAIN -> {
                    boolean potions = r.act() == Act.POTION_RAIN;
                    int n = potions ? 5 : RAIN_HITS;
                    for (int j = 0; j < n; j++) {
                        float hit = potions ? 0.12f + j * 0.15f : 0.06f + j * 0.7f / RAIN_HITS;
                        float s = (p - (hit - 0.1f)) / 0.1f;
                        if (s < 0 || s >= 1) continue;
                        Vec3 tgt = potions ? victimPos().add(0, h * 0.6, 0) : stuckWorld(rainTargets[j][0], rainTargets[j][1], rainTargets[j][2]);
                        Vec3 w = tgt.add(0, 9 * (1 - s), 0);
                        item(ps, cam, buf, w, new ItemStack(potions ? Items.SPLASH_POTION : Items.ARROW), potions ? a * 600 : 135, 0.8f, light); // point down
                    }
                }
                case CRUSH -> {
                    BlockState block = (winner.moves.type() == FighterType.VILLAGER ? Blocks.EMERALD_BLOCK
                            : winner.moves.type() == FighterType.IRON_ORE ? Blocks.RAW_IRON_BLOCK : Blocks.ANVIL).defaultBlockState();
                    float cl = climax(r.act());
                    float size = Math.max(1.6f, h * 0.9f);
                    double y = p < cl ? f.y + h + 12 * (1 - easeIn(p / cl)) : f.y + h * 0.12;
                    ps.pushPose();
                    ps.translate(f.x - cam.x, y - cam.y, f.z - cam.z);
                    ps.scale(size, size, size);
                    ps.translate(-0.5, 0, -0.5);
                    br.renderSingleBlock(block, ps, buf, light, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                }
                case IMPALE_SKY -> {
                    float s = Mth.clamp(p / 0.3f, 0, 1);
                    double tipY = f.y - 0.3 + 14 * (1 - easeIn(s));
                    Vec3 w = new Vec3(f.x, tipY + 2.6, f.z);
                    item(ps, cam, buf, w, new ItemStack(Items.IRON_SWORD), 135, 5.5f, 0xF000F0); // point down (the sprite is seen mirrored)
                }
                case IMPALE_GROUND -> {
                    if (pillarH > 0) {
                        int n = (int) Math.ceil(pillarH / 0.5f);
                        for (int j = 0; j < n; j++) {
                            float seg = Math.min(0.5f, pillarH - j * 0.5f);
                            ps.pushPose();
                            ps.translate(f.x - cam.x, f.y + j * 0.5 - cam.y, f.z - cam.z);
                            ps.scale(0.5f - j * 0.04f, seg, 0.5f - j * 0.04f);
                            ps.translate(-0.5, 0, -0.5);
                            br.renderSingleBlock(Blocks.OAK_LOG.defaultBlockState(), ps, buf, light, OverlayTexture.NO_OVERLAY);
                            ps.popPose();
                        }
                    }
                }
                case LASSO -> {
                    if (p < 0.32f) {
                        Vec3 from = session.worldOf(winner).add(0, winnerHeight() * 0.6, 0);
                        Vec3 to = feet().add(0, h * 0.7, 0);
                        item(ps, cam, buf, from.lerp(to, p / 0.32f), new ItemStack(Items.LEAD), a * 900, 1.2f, light);
                    }
                }
                case HORDE -> {
                    if (a < r.end()) renderHorde(ps, cam, buf, p, light); // then they're gone
                }
                default -> {
                }
            }
        }
        // webs, the cocoon, the slime cube
        if (webs > 0 && cocoon <= 0) {
            int n = (int) (webs / 0.085f);
            for (int j = 0; j < Math.min(8, n); j++) {
                double ang = j * 0.8 + a * 0.6;
                Vec3 w = f.add(Math.cos(ang) * 0.35, h * (0.2 + 0.1 * j), Math.sin(ang) * 0.35);
                item(ps, cam, buf, w, new ItemStack(Items.COBWEB), j * 40, 1.0f, light);
            }
        }
        if (cocoon > 0) {
            float wob = (float) Math.sin(a * 7) * 4;
            ps.pushPose();
            ps.translate(f.x - cam.x, f.y - cam.y, f.z - cam.z);
            ps.mulPose(Axis.ZP.rotationDegrees(wob));
            float cw = Math.max(0.5f, h * 0.42f);
            ps.scale(cw, h * 0.9f, cw);
            ps.translate(-0.5, 0, -0.5);
            br.renderSingleBlock(Blocks.WHITE_WOOL.defaultBlockState(), ps, buf, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        if (slimeCube > 0) {
            float size = Math.max(1.3f, h * 1.25f) * slimeCube;
            ps.pushPose();
            ps.translate(f.x - cam.x, f.y - cam.y + (float) Math.abs(Math.sin(a * 5)) * 0.05, f.z - cam.z);
            ps.scale(size, size * (0.95f + 0.05f * (float) Math.sin(a * 6)), size);
            ps.translate(-0.5, 0, -0.5);
            br.renderSingleBlock(Blocks.SLIME_BLOCK.defaultBlockState(), ps, buf, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        buf.endBatch();
    }

    private void renderHorde(PoseStack ps, Vec3 cam, MultiBufferSource.BufferSource buf, float p, int light) {
        LivingEntity d = hordeDummy();
        if (d == null) return;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Vec3 f = feet(), axis = session.arena.axis, normal = session.arena.normal;
        int n = 7;
        float k = Math.min(1.2f, Math.max(0.7f, bodyHeight() / Math.max(0.5f, d.getBbHeight())));
        for (int i = 0; i < n; i++) {
            double ang = i / (double) n * Math.PI * 2;
            Vec3 ring = f.add(Math.cos(ang) * 0.75, 0, Math.sin(ang) * 0.75);
            Vec3 out = f.add(axis.scale((i % 2 == 0 ? -1 : 1) * 8)).add(normal.scale((i - 3) * 0.4));
            Vec3 pos;
            double y = 0;
            boolean leaving = p >= 0.8f;
            if (p < 0.3f) pos = out.lerp(ring, easeOut(p / 0.3f));
            else if (!leaving) {
                pos = ring.add((random.nextDouble() - 0.5) * 0.05, 0, (random.nextDouble() - 0.5) * 0.05);
                y = (i % 3) * 0.32 * k + Math.abs(Math.sin(lastNow * 10 + i)) * 0.15;
            } else pos = ring.lerp(out, (p - 0.8f) / 0.2f);
            Vec3 look = leaving ? pos.subtract(f) : f.subtract(pos);
            float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
            d.setPos(pos.x, pos.y + y, pos.z);
            d.setYRot(yaw);
            d.yRotO = yaw;
            d.yBodyRot = d.yBodyRotO = yaw;
            d.yHeadRot = d.yHeadRotO = yaw;
            ps.pushPose();
            ps.translate(pos.x - cam.x, pos.y + y - cam.y, pos.z - cam.z);
            ps.scale(k, k, k);
            dispatcher.render(d, 0, 0, 0, yaw, 1f, ps, buf, light);
            ps.popPose();
        }
    }

    private void item(PoseStack ps, Vec3 cam, MultiBufferSource buf, Vec3 w, ItemStack stack, float rotZ, float scale, int light) {
        Minecraft mc = Minecraft.getInstance();
        ps.pushPose();
        ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(cam.x - w.x, cam.z - w.z)));
        ps.mulPose(Axis.ZP.rotationDegrees(rotZ));
        ps.scale(scale, scale, scale);
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
        ps.popPose();
    }

    private void flat(PoseStack ps, Vec3 cam, MultiBufferSource buf, BlockRenderDispatcher br, Vec3 f, float size, BlockState s, float lift) {
        ps.pushPose();
        ps.translate(f.x - cam.x, f.y + lift - cam.y, f.z - cam.z);
        ps.scale(size, 0.02f, size);
        ps.translate(-0.5, 0, -0.5);
        br.renderSingleBlock(s, ps, buf, LevelRenderer.getLightColor(Minecraft.getInstance().level, BlockPos.containing(f.add(0, 0.5, 0))),
                OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private void renderXray(PoseStack ps, Vec3 cam, MultiBufferSource.BufferSource buf, float a) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher br = mc.getBlockRenderer();
        Vec3 f = feet();
        int done = 0;
        for (int i = 0; i < fin.blows; i++) if (a >= blowTime(i)) done++;
        float snap = Mth.clamp((a - blowTime(fin.blows - 1)) / 0.6f, 0, 1);
        int crack = done == 0 ? -1 : Math.min(9, done * 9 / fin.blows);
        float fadeIn = Mth.clamp((a - xrayStart()) / 0.25f, 0, 1);

        ps.pushPose();
        ps.translate(f.x - cam.x, f.y - cam.y, f.z - cam.z);
        ps.mulPose(Axis.YP.rotation(yawRad()));
        float k = k() * (0.85f + 0.15f * fadeIn);
        ps.scale(k, k, k);
        if (shape == Shape.QUAD || shape == Shape.SPIDER) ps.mulPose(Axis.YP.rotation(-Mth.HALF_PI));
        // flinch on every impact
        for (int i = 0; i < done; i++) {
            float s = a - blowTime(i);
            if (s < 0.25f) ps.translate(Math.sin(s * 90) * 0.03 * (1 - s / 0.25f), 0, 0);
        }
        for (Piece p : pieces) {
            boolean target = targets(p.tag) || (fin.target == Bone.SKULL && p.tag == Bone.JAW && snap > 0);
            if (target && snap > 0) {
                float sep = ease(snap) * (fin.target == Bone.ALL ? 0.12f : 0.26f);
                boolean vertical = p.sy >= p.sx && p.sy >= p.sz;
                for (int half = -1; half <= 1; half += 2) {
                    ps.pushPose();
                    float ox = vertical ? half * sep * 0.35f : half * sep, oy = vertical ? half * sep : -snap * 0.08f;
                    ps.translate(p.x + ox, p.y + oy + p.sy / 2f, p.z + half * sep * 0.2f);
                    ps.mulPose(Axis.ZP.rotationDegrees(half * ease(snap) * 32f));
                    ps.mulPose(Axis.XP.rotationDegrees(half * ease(snap) * 14f));
                    ps.translate(-p.sx / 2f, -p.sy / 2f, -p.sz / 2f);
                    if (vertical) ps.translate(0, half > 0 ? p.sy / 2f : 0, 0);
                    else ps.translate(half > 0 ? p.sx / 2f : 0, 0, 0);
                    ps.scale(vertical ? p.sx : p.sx / 2f, vertical ? p.sy / 2f : p.sy, p.sz);
                    bone(ps, br, buf, crack);
                    ps.popPose();
                }
            } else {
                ps.pushPose();
                ps.translate(p.x - p.sx / 2f, p.y, p.z - p.sz / 2f);
                ps.scale(p.sx, p.sy, p.sz);
                bone(ps, br, buf, target ? crack : -1);
                ps.popPose();
            }
        }
        buf.endBatch();
        fleshBoxes(ps, br, buf);
        buf.endBatch();
        ps.popPose();

        // the winner's weapon swinging in for each blow
        for (int i = 0; i < fin.blows; i++) {
            float s = a - blowTime(i) + 0.3f;
            if (s < 0 || s > 0.62f) continue;
            float p = Mth.clamp(s / 0.3f, 0, 1);
            Vec3 an = anchor();
            Vec3 from = an.add(session.arena.axis.scale((winner.x > loser.x ? 1 : -1) * 1.8)).add(0, 1.0, 0)
                    .add(session.arena.normal.scale(session.camera.side() * 0.3));
            Vec3 at = from.lerp(an, easeIn(p));
            ps.pushPose();
            ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            ps.mulPose(Axis.YP.rotation((float) Math.atan2(cam.x - at.x, cam.z - at.z)));
            ps.mulPose(Axis.ZP.rotationDegrees((winner.x > loser.x ? 1 : -1) * (-100 + p * 150)));
            ps.scale(1.5f, 1.5f, 1.5f);
            mc.getItemRenderer().renderStatic(fin.weapon, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf,
                    mc.level, 0);
            ps.popPose();
        }
        buf.endBatch();
    }

    private void bone(PoseStack ps, BlockRenderDispatcher br, MultiBufferSource.BufferSource buf, int crack) {
        br.renderSingleBlock(boneMat, ps, buf, 0xF000F0, OverlayTexture.NO_OVERLAY);
        if (crack >= 0) {
            var vc = new SheetedDecalTextureGenerator(buf.getBuffer(ModelBakery.DESTROY_TYPES.get(crack)), ps.last(), 1.0f);
            br.renderBreakingTexture(boneMat, BlockPos.ZERO, Minecraft.getInstance().level, ps, vc);
        }
    }

    private void fleshBoxes(PoseStack ps, BlockRenderDispatcher br, MultiBufferSource buf) {
        switch (shape) {
            case QUAD -> {
                box(ps, br, buf, -0.78f, 0.3f, -0.3f, 1.5f, 0.72f, 0.6f);
                box(ps, br, buf, 0.62f, 0.5f, -0.22f, 0.46f, 0.5f, 0.44f);
            }
            case CREEPER -> {
                box(ps, br, buf, -0.25f, 0.36f, -0.16f, 0.5f, 0.8f, 0.32f);
                box(ps, br, buf, -0.26f, 1.16f, -0.26f, 0.52f, 0.52f, 0.52f);
                box(ps, br, buf, -0.25f, 0f, -0.26f, 0.5f, 0.36f, 0.52f);
            }
            case BLOB -> box(ps, br, buf, -0.5f, 0f, -0.5f, 1f, 1f, 1f);
            case FURNACE -> {
                box(ps, br, buf, -0.55f, 0.55f, -0.55f, 1.1f, 1.1f, 1.1f);
                box(ps, br, buf, -0.4f, 0f, -0.16f, 0.8f, 0.55f, 0.32f);
            }
            case SPIDER -> {
                box(ps, br, buf, -0.12f, 0.24f, -0.2f, 0.46f, 0.32f, 0.4f);
                box(ps, br, buf, 0.38f, 0.16f, -0.2f, 0.36f, 0.36f, 0.4f);
                box(ps, br, buf, -0.92f, 0.1f, -0.32f, 0.72f, 0.56f, 0.64f);
            }
            case TOOL -> {
                box(ps, br, buf, -0.1f, 0f, -0.1f, 0.2f, 1.35f, 0.2f);
                box(ps, br, buf, -0.5f, 1.2f, -0.12f, 1.0f, 0.38f, 0.24f);
            }
            default -> {
                float k = shape == Shape.BIRD ? 0.65f : 1f;
                box(ps, br, buf, -0.34f * k, 0.8f * k, -0.2f * k, 0.68f * k, 0.72f * k, 0.4f * k);
                box(ps, br, buf, -0.27f * k, 1.52f * k, -0.25f * k, 0.54f * k, 0.5f * k, 0.5f * k);
                box(ps, br, buf, -0.56f * k, 0.55f * k, -0.12f * k, 0.22f * k, 0.95f * k, 0.24f * k);
                box(ps, br, buf, 0.34f * k, 0.55f * k, -0.12f * k, 0.22f * k, 0.95f * k, 0.24f * k);
                box(ps, br, buf, -0.3f * k, 0f, -0.14f * k, 0.6f * k, 0.82f * k, 0.28f * k);
            }
        }
    }

    private void box(PoseStack ps, BlockRenderDispatcher br, MultiBufferSource buf, float x, float y, float z, float sx, float sy, float sz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(sx, sy, sz);
        br.renderSingleBlock(flesh, ps, buf, 0xF000F0, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }


    // ===================================================================================== HUD

    public void renderHud(GuiGraphics g, Font font, com.streetfighter.client.FightHud hud, int w, int h, float now) {
        float a = now - start;
        boolean deciding = decidedAt < 0;
        float outro = !deciding && a >= outroAt ? (a - outroAt) : -1;
        float bars = ease(Mth.clamp(a / 0.35f, 0, 1)) * (outro > 0 ? 1 - ease(Mth.clamp((outro - 2.6f) / 1f, 0, 1)) : 1);
        int bh = (int) (h * 0.12f * bars);
        // a blood-dark vignette
        for (int i = 0; i < 7; i++) {
            int inset = i * Math.max(4, w / 55);
            int al = (int) (30 * bars);
            g.fill(0, 0, inset + 4, h, argb(al, 0x180000));
            g.fill(w - inset - 4, 0, w, h, argb(al, 0x180000));
        }
        if (inXray(a)) {
            g.fill(0, 0, w, h, argb(40, 0x0A3A8A));
            for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, argb(22, 0x7FD8FF));
            hud.outlined(g, "X-RAY", 34, bh + 14, 1.6f, 0xFF7FE8FF, 0xFF00304A);
        }
        if (redFlash > 0) g.fill(0, 0, w, h, argb((int) (120 * redFlash), 0x8A0000));
        if (flash > 0) g.fill(0, 0, w, h, argb((int) (170 * flash * flash), 0xFFFFFF));
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fill(0, h - bh, w, h, 0xFF000000);

        // ---- FINISH HIM!
        if (deciding || a < firstStart()) {
            float scale, y;
            if (a < 1.3f) {
                float p = Mth.clamp(a / 0.22f, 0, 1);
                scale = Mth.lerp(ease(p), 9f, 5.2f);
                y = h * 0.4f;
            } else {
                float p = ease(Mth.clamp((a - 1.3f) / 0.4f, 0, 1));
                scale = Mth.lerp(p, 5.2f, 3.0f) * (1 + 0.04f * (float) Math.sin(a * 6));
                y = Mth.lerp(p, h * 0.4f, bh + h * 0.12f);
            }
            float jit = a < 0.6f ? (float) Math.sin(a * 90) * 3 : 0;
            hud.outlined(g, "FINISH HIM!", w / 2f + jit, y, scale, 0xFFFF2414, 0xFF000000);
            if (deciding && winner.index == 0 && fatal != null) inputPanel(g, font, hud, w, h, bh, a);
            else if (deciding) {
                String txt = winnerName() + " decides your fate...";
                hud.outlined(g, txt, w / 2f, h - bh - 30, 1.5f, 0xFFFFB0A0, 0xFF000000);
            }
        }
        // ---- the fatality's name
        if (fatality && a >= firstStart() && a < outroAt) {
            int lx = 18, ly = h - bh - 34;
            String label = "FATALITY", name = fatal.name();
            int tw = Math.max(font.width(label), (int) (font.width(name) * 1.6f)) + 20;
            g.fill(lx - 6, ly - 4, lx - 6 + tw, ly + 28, 0xB0000000);
            g.fill(lx - 6, ly - 4, lx - 3, ly + 28, 0xFFB00000);
            g.drawString(font, winnerName() + "'S " + label, lx, ly, 0xFFFF6A5A, true);
            g.pose().pushPose();
            g.pose().translate(lx, ly + 11, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            g.drawString(font, name, 0, 0, 0xFFFFD21A, true);
            g.pose().popPose();
        }
        // CRACK! / SNAP!
        for (int i = 0; i < fin.blows; i++) {
            float s = a - blowTime(i);
            if (s < 0 || s > 0.55f) continue;
            float r = s / 0.55f;
            boolean last = i == fin.blows - 1;
            float scale = (last ? 4.2f : 3f) * (1.25f - 0.25f * r);
            int al = (int) (255 * (1 - r * r));
            float x = w / 2f + (i % 2 == 0 ? -1 : 1) * w * 0.2f, y = h * 0.42f - i * 14;
            hud.outlined(g, last ? "SNAP!" : "CRACK!", x, y, scale, argb(al, last ? 0xFFD21A : 0xFF5A3A), argb(al, 0x000000));
        }

        // ---- no fatality: the victim's own death
        if (!fatality && !deciding && a >= deathAt && a < outroAt) {
            float dp = deathP(a);
            float showT = Mth.clamp((dp - 0.35f) / 0.15f, 0, 1);
            if (showT > 0) {
                hud.outlined(g, death.title, w / 2f, h - bh - 22, 2.2f, argb((int) (255 * showT), 0xFFFFFF), argb((int) (255 * showT), 0x000000));
            }
        }
        // ---- outro: FATALITY, X WINS, FLAWLESS VICTORY
        if (outro >= 0) {
            if (fatality) fatalityStamp(g, font, hud, w, h, outro);
            if (outro > 0.9f) {
                float p = ease(Mth.clamp((outro - 0.9f) / 0.25f, 0, 1));
                hud.outlined(g, winnerName() + " WINS", w / 2f + (1 - p) * w * 0.4f, h * (fatality ? 0.6f : 0.42f), 3.2f,
                        argb((int) (255 * p), 0xFFFFFF), argb((int) (255 * p), 0x000000));
            }
            if (perfect() && outro > 1.6f) {
                float p = ease(Mth.clamp((outro - 1.6f) / 0.25f, 0, 1));
                boolean blink = (int) (outro * 6) % 2 == 0;
                hud.outlined(g, "FLAWLESS VICTORY", w / 2f, h * (fatality ? 0.7f : 0.52f), 2.4f * (0.8f + 0.2f * p),
                        argb((int) (255 * p), blink ? 0xFFD21A : 0xFFFFFF), argb((int) (255 * p), 0x5A2A00));
            }
        }
        String hint = deciding ? "" : "SPACE: skip";
        g.drawString(font, hint, w - font.width(hint) - 8, h - 10, 0xFF888888, true);
    }

    private String winnerName() {
        String n = winner.index == 0 || winner.moves.type() == FighterType.BRAWLER ? winner.name : winner.moves.type().displayName;
        return n.toUpperCase();
    }

    /** The fatality input: the keys to press, lighting up as you go, and the time left. */
    private void inputPanel(GuiGraphics g, Font font, com.streetfighter.client.FightHud hud, int w, int h, int bh, float a) {
        String[] s = fatal.input();
        float ks = 2f;
        int gapPx = 10;
        int total = 0;
        for (String tok : s) total += (int) (Ui.keycapWidth(font, keyOf(tok)) * ks) + gapPx;
        total -= gapPx;
        int pw = Math.max(total + 40, 260), ph = 74;
        int x0 = w / 2 - pw / 2, y0 = h - bh - ph - 8;
        Ui.panel(g, x0, y0, x0 + pw, y0 + ph, 0xFFB00000);
        String title = "FATALITY: " + fatal.name();
        g.drawString(font, title, w / 2 - font.width(title) / 2, y0 + 6, 0xFFFF5A4A, true);
        int kx = w / 2 - total / 2, ky = y0 + 20;
        for (int i = 0; i < s.length; i++) {
            String key = keyOf(s[i]);
            int kw = (int) (Ui.keycapWidth(font, key) * ks);
            g.pose().pushPose();
            g.pose().translate(kx, ky, 0);
            g.pose().scale(ks, ks, 1);
            Ui.keycap(g, font, key, 0, 0);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(0, 0, 5);
            if (i < inputIndex) g.fill(kx, ky, kx + kw, ky + (int) (11 * ks), 0x9030E040);
            else if (i == inputIndex && (int) (a * 4) % 2 == 0) g.fill(kx - 2, ky - 2, kx + kw + 2, ky, 0xFFFFD21A);
            if (wrongFlash > 0 && i == inputIndex) g.fill(kx, ky, kx + kw, ky + (int) (11 * ks), 0x80FF2020);
            g.pose().popPose();
            kx += kw + gapPx;
        }
        float left = 1 - Mth.clamp(a / T_FH, 0, 1);
        int bx = x0 + 12, by = y0 + ph - 18, bw = pw - 24;
        g.fill(bx, by, bx + bw, by + 4, 0xFF301010);
        g.fill(bx, by, bx + (int) (bw * left), by + 4, left < 0.3f && (int) (a * 8) % 2 == 0 ? 0xFFFFFFFF : 0xFFE02020);
        String hint = "press the keys in order   [SPACE] let them fall";
        Ui.rich(g, font, hint, w / 2 - Ui.richWidth(font, hint) / 2, by + 7, 0xFFAAAAAA);
    }

    /** FATALITY in blood red, slammed in, dripping. */
    private void fatalityStamp(GuiGraphics g, Font font, com.streetfighter.client.FightHud hud, int w, int h, float o) {
        float p = Mth.clamp(o / 0.2f, 0, 1);
        float scale = Mth.lerp(ease(p), 9f, 5.4f);
        float cy = h * 0.34f;
        if (o < 0.8f) g.fill(0, 0, w, h, argb((int) (90 * (1 - o / 0.8f)), 0x500000));
        float jit = o < 0.4f ? (float) Math.sin(o * 80) * 3 : 0;
        // drips
        float tw = font.width("FATALITY") * scale;
        Random r = new Random(42);
        for (int i = 0; i < 14; i++) {
            float x = w / 2f - tw / 2f + r.nextFloat() * tw;
            float speed = 0.4f + r.nextFloat() * 0.9f;
            float len = Math.max(0, o - 0.25f) * 26 * speed * scale / 5f;
            int top = (int) (cy + 3 * scale);
            int width = 2 + r.nextInt(3);
            g.fill((int) x, top, (int) x + width, top + (int) len, 0xFF8A0000);
            g.fill((int) x - 1, top + (int) len, (int) x + width + 1, top + (int) len + width + 1, 0xFFA00000);
        }
        hud.outlined(g, "FATALITY", w / 2f + jit, cy, scale, 0xFFC81010, 0xFF100000);
    }

    private static int argb(int a, int rgb) {
        return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float easeIn(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * x;
    }

    private void burst(ParticleOptions type, Vec3 p, int count, double spread, double speed) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int i = 0; i < count; i++) {
            double dx = (random.nextDouble() - 0.5) * 2, dy = (random.nextDouble() - 0.5) * 2, dz = (random.nextDouble() - 0.5) * 2;
            level.addParticle(type, p.x + dx * spread * 0.5, p.y + dy * spread * 0.5, p.z + dz * spread * 0.5,
                    dx * speed * 0.3, dy * speed * 0.3 + 0.05, dz * speed * 0.3);
        }
    }

    private void play(SoundEvent s, Vec3 p, float vol, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch, false);
    }

    private static float easeOut(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x);
    }
}
