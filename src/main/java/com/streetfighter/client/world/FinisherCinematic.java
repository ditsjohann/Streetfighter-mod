package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.math.Axis;
import com.streetfighter.client.FightSession;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.FighterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.streetfighter.client.world.AnimClips.*;

/**
 * The match-ending finisher, staged in the world like a film sequence (inspired by Mortal Kombat's X-rays):
 * <ol>
 *   <li><b>FINISH</b>: time freezes, the camera cuts behind the winner's shoulder and pushes in while the world fades
 *       into dark fog and a heartbeat thumps.</li>
 *   <li><b>X-RAY</b>: the victim is replaced, in place, by an x-ray body (glass flesh around a block-built skeleton);
 *       the fog closes into a deep blue void. Every blow gets its own camera cut; the winner's weapon swings in as a
 *       3D model; impacts flash, shatter bone shards, spread cracks and shake the camera. The last blow snaps the
 *       bone in two.</li>
 *   <li><b>DEATH</b>: wide orbiting shot; every character dies its own way (bone pile, feather burst, timber,
 *       crumble, fizzle, sink, belly-up...), with simulated debris.</li>
 * </ol>
 */
public final class FinisherCinematic implements FightAnimator.FinisherHook {
    public static FinisherCinematic current;

    enum Bone { SKULL, JAW, SPINE, RIB, ARM, LEG, PELVIS, ALL }

    enum Shape { BIPED, QUAD, BIRD, CREEPER, TOOL, BLOB, SPIDER }

    public enum Death {
        COLLAPSE("KNOCKED OUT COLD"), EMERALD_SPILL("BANKRUPT"), POTION_POP("SHATTERED"), BONE_PILE("BONE PILE"),
        SINK("BACK TO THE GRAVE"), BELLY_UP("BELLY UP"), MILK_SPILL("SPILT MILK"), FEATHER_BURST("FEATHER EXPLOSION"),
        HORN_SNAP("DEHORNED"), BUCK_COLLAPSE("LAST KICK"), FIZZLE("FIZZLED OUT"), TIMBER("TIMBER!"),
        CRUMBLE("CRUMBLED"), SNAP("SNAPPED"), SPLIT("SPLIT UP"), CURL("CURLED UP"), TANNED("TANNED HIDE");
        final String title;

        Death(String title) {
            this.title = title;
        }
    }

    record Finisher(String name, Bone target, int blows, ItemStack weapon) {
    }

    private record Piece(Bone tag, float x, float y, float z, float sx, float sy, float sz) {
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

    // timeline (seconds)
    private static final float T_FINISH = 1.15f, T_INTRO = 0.5f, T_BLOW = 1.15f, T_IMPACT = 0.42f, T_SNAP = 0.85f,
            T_DEATH = 3.1f, T_OUTRO = 0.45f;

    private final FightSession session;
    private final Fighter winner, loser;
    private final LivingEntity winnerE, loserE;
    private final Finisher fin;
    private final Death death;
    private final Shape shape;
    private final BlockState boneMat, flesh;
    private final List<Piece> pieces = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();
    private final Random random = new Random();
    private float start;
    private final float layoutHeight;
    private int blowsDone;
    private boolean deathStarted, deathMid, deathEnd, snapFx;
    private float lastNow;
    private float flash;

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
        FighterType body = loser.body;
        this.death = deathFor(body, FightAnimator.bodyOf(loserE));
        this.shape = switch (body) {
            case PIG, GOAT, COW, DONKEY -> Shape.QUAD;
            case CHICKEN -> Shape.BIRD;
            case CREEPER -> Shape.CREEPER;
            case STONE_PICKAXE -> Shape.TOOL;
            case SLIME -> Shape.BLOB;
            case SPIDER -> Shape.SPIDER;
            default -> FightAnimator.bodyOf(loserE) == FightAnimator.Body.QUAD ? Shape.QUAD : Shape.BIPED;
        };
        this.boneMat = switch (body) {
            case OAK -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            case IRON_ORE -> Blocks.IRON_BLOCK.defaultBlockState();
            case STONE_PICKAXE -> Blocks.OAK_PLANKS.defaultBlockState();
            case SLIME -> Blocks.SLIME_BLOCK.defaultBlockState(); // no bones, just a gooey core
            default -> Blocks.BONE_BLOCK.defaultBlockState();
        };
        this.flesh = switch (body) {
            case OAK -> Blocks.GREEN_STAINED_GLASS.defaultBlockState();
            case IRON_ORE, STONE_PICKAXE -> Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            case ZOMBIE, CREEPER -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case SKELETON -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
            case SLIME -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case SPIDER -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case LEATHERWORKER -> Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
            default -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        };
        layoutHeight = switch (shape) {
            case QUAD -> 1.0f;
            case BIRD -> 1.3f;
            case CREEPER -> 1.7f;
            case TOOL -> 1.9f;
            case BLOB -> 1.0f;
            case SPIDER -> 0.9f;
            default -> 2.0f;
        };
        build();
        stage();
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

    /** Puts both fighters on the ground, facing each other at striking distance, for the camera. */
    private void stage() {
        int side = winner.x <= loser.x ? -1 : 1;
        float gap = (winner.hurtWidth() + loser.hurtWidth()) / 2f + 45;
        winner.x = Mth.clamp(loser.x + side * gap, session.engine.arenaMin + 40, session.engine.arenaMax - 40);
        for (Fighter f : new Fighter[]{winner, loser}) {
            f.y = 0;
            f.vx = f.vy = 0;
            f.prevX = f.x;
            f.prevY = 0;
            f.flying = false;
        }
        winner.facingRight = loser.x > winner.x;
        loser.facingRight = !winner.facingRight;
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
        return T_FINISH;
    }

    private float blowTime(int i) {
        return xrayStart() + T_INTRO + i * T_BLOW + T_IMPACT;
    }

    private float xrayEnd() {
        return xrayStart() + T_INTRO + fin.blows * T_BLOW + T_SNAP;
    }

    private float deathEndT() {
        return xrayEnd() + T_DEATH;
    }

    private float total() {
        return deathEndT() + T_OUTRO;
    }

    public boolean active(float now) {
        return now - start < total();
    }

    /** SPACE/ENTER: jump to the death, or to the end. */
    public void skip(float now) {
        float a = now - start;
        if (a < xrayEnd()) start = now - xrayEnd();
        else start = now - total();
    }

    private boolean inXray(float a) {
        return a >= xrayStart() && a < xrayEnd();
    }

    private float deathP(float a) {
        return Mth.clamp((a - xrayEnd()) / T_DEATH, 0, 1);
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

    // ===================================================================================== camera

    public Shot shot(float now) {
        float a = now - start;
        int sgn = winner.x >= loser.x ? 1 : -1; // camera swings toward the winner's side
        Vec3 chest = feet().add(0, bodyHeight() * 0.6, 0);
        if (a < xrayStart()) {
            float p = a / T_FINISH;
            return new Shot(chest, Mth.lerp(ease(p), 3.4f, 2.5f), sgn * (58 - 6 * p), 0.55, -4 * sgn, a < 0.05f, 0);
        }
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
        // death: wide, orbiting
        float dp = deathP(a);
        double dist = (death == Death.TIMBER ? 7.0 : 3.9) * Math.max(0.8, k() * 0.9);
        Vec3 focus = feet().add(0, bodyHeight() * (death == Death.SINK ? 0.3 : 0.45), 0);
        if (death == Death.TIMBER) focus = focus.add(facing().scale(-bodyHeight() * 0.3 * dp));
        float crash = deathCrashTime();
        float since = dp - crash;
        float shake = crash >= 0 && since >= 0 && since < 0.12f ? 12f * (1 - since / 0.12f) : 0;
        boolean cut = a - xrayEnd() < 0.04f && a - xrayEnd() >= 0;
        return new Shot(focus, dist, -sgn * (20 + 24 * dp), 0.9, 0, cut, shake); // the victim's side: nothing in the way
    }

    /** Death progress at which the big impact happens (for shake), or -1. */
    private float deathCrashTime() {
        return switch (death) {
            case TIMBER -> 0.78f;
            case COLLAPSE, EMERALD_SPILL, POTION_POP, TANNED -> 0.78f;
            case CRUMBLE -> 0.3f;
            case SPLIT -> 0.4f;
            case CURL -> 0.55f;
            case FIZZLE -> 0.45f;
            case BELLY_UP, MILK_SPILL, HORN_SNAP, BUCK_COLLAPSE -> 0.62f;
            default -> -1f;
        };
    }

    /** Fog for the shot: {near, far, r, g, b}, or null for normal. */
    public float[] fog(float now, float normalFar) {
        float a = now - start;
        if (a >= total()) return null;
        if (a < xrayStart()) {
            float p = ease(Mth.clamp(a / 0.6f, 0, 1));
            return new float[]{Mth.lerp(p, normalFar * 0.5f, 4f), Mth.lerp(p, normalFar, 20f), 0.02f, 0.02f, 0.04f};
        }
        if (inXray(a)) return new float[]{2.5f, 9f, 0.0f, 0.035f, 0.11f};
        float p = ease(Mth.clamp((a - xrayEnd()) / 0.8f, 0, 1));
        float out = a > deathEndT() ? ease((a - deathEndT()) / T_OUTRO) : 0;
        float far = Mth.lerp(p, 9f, 26f);
        far = Mth.lerp(out, far, normalFar);
        return new float[]{Mth.lerp(out, far * 0.3f, normalFar * 0.75f), far, 0.04f, 0.04f, 0.06f};
    }

    /** How strongly the fog colour is overridden (fades out with the outro). */
    public float fogColorMix(float now) {
        float a = now - start;
        if (a >= total()) return 0;
        float in = ease(Mth.clamp(a / 0.5f, 0, 1));
        float out = a > deathEndT() ? 1 - ease((a - deathEndT()) / T_OUTRO) : 1;
        float death = a >= xrayEnd() ? 0.75f : 1f;
        return in * out * death;
    }

    // ===================================================================================== per-frame logic

    public void tick(float now) {
        float dt = Mth.clamp(now - lastNow, 0, 0.1f);
        lastNow = now;
        float a = now - start;
        flash = Math.max(0, flash - dt * 6f);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (a < 0.05f && blowsDone == 0 && !deathStarted) {
            play(SoundEvents.WARDEN_HEARTBEAT, feet(), 2f, 0.8f);
            play(SoundEvents.WARDEN_SONIC_CHARGE, feet(), 0.6f, 0.6f);
        }
        // blows
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
            session.fx.quads.glow(p, last ? 3.5f : 2.2f, 0.35f, 0xFFFFFF);
            session.fx.quads.ring(p, 0.3f, last ? 4f : 2.5f, 0.35f, 0xBFE8FF);
            play(SoundEvents.BONE_BLOCK_BREAK, p, 2f, last ? 0.55f : 0.8f);
            play(SoundEvents.PLAYER_ATTACK_CRIT, p, 1.5f, 0.6f);
            play(SoundEvents.SKELETON_HURT, p, 1.2f, last ? 0.5f : 0.75f);
            if (last) {
                play(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, p, 1.4f, 0.8f);
                play(SoundEvents.ANVIL_LAND, p, 0.8f, 0.5f);
                if (fin.target == Bone.ALL) play(SoundEvents.GENERIC_EXPLODE.value(), p, 0.8f, 1.2f);
            } else {
                play(SoundEvents.WARDEN_HEARTBEAT, p, 1.5f, 1.1f);
            }
        }
        if (!snapFx && a > blowTime(fin.blows - 1) + 0.3f) {
            snapFx = true;
            burst(new BlockParticleOption(ParticleTypes.BLOCK, boneMat), anchor(), 30, 0.2, 0.2);
        }
        // death
        if (a >= xrayEnd()) deathTick(a, dt);
        stepDebris(dt);
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
                BlockState[] chunks = {Blocks.STONE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState()};
                for (int i = 0; i < 9; i++) spawnBlock(chunks[i % chunks.length], mid, 2.2f, 0.3f + random.nextFloat() * 0.15f);
                for (int i = 0; i < 4; i++) spawnItem(new ItemStack(Items.RAW_IRON), mid, 1.8f, 0.5f);
                burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), mid, 60, 0.5, 0.5);
                play(SoundEvents.STONE_BREAK, mid, 2f, 0.6f);
                play(SoundEvents.ANVIL_LAND, mid, 1f, 0.6f);
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
            case IRON_ORE, STONE_PICKAXE -> SoundEvents.STONE_HIT;
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

    private void spawnBlock(BlockState state, Vec3 at, float speed, float scale) {
        Debris d = new Debris();
        d.block = state;
        init(d, at, speed, scale);
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
        if (e == winnerE && a < xrayEnd()) {
            AnimClips.Clip c = body == FightAnimator.Body.QUAD ? AnimClips.quadruped(com.streetfighter.fight.AnimPose.CHARGE)
                    : body == FightAnimator.Body.BIRD ? AnimClips.bird(com.streetfighter.fight.AnimPose.PECK)
                    : AnimClips.humanoid(com.streetfighter.fight.AnimPose.HOOK);
            boolean striking = false;
            for (int i = 0; i < fin.blows; i++) {
                float s = a - blowTime(i);
                if (s > -0.1f && s < 0.3f) striking = true;
            }
            neutral(t, cur);
            apply(t, striking ? c.strike() : c.windup());
            return striking ? 40f : 10f;
        }
        if (e != loserE) return null;
        neutral(t, cur);
        if (a < xrayStart()) {
            // dazed: head lolling, arms limp
            t[HX] = -0.35f + Mth.sin(a * 3) * 0.15f;
            t[RAX] = -0.15f; t[LAX] = -0.15f; t[RAZ] = 0.25f; t[LAZ] = -0.25f;
            t[LEAN] = -8 + Mth.sin(a * 2.5f) * 4;
            return 6f;
        }
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
        if (e != loserE) return;
        float a = lastNow - start;
        if (a < xrayEnd()) return;
        float dp = deathP(a);
        float ax = (float) face.z, az = (float) -face.x; // lateral axis: positive = fall forward
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
            case CRUMBLE, SNAP, BONE_PILE, FEATHER_BURST -> {
                float shake = (float) Math.sin(dp * 160) * 0.03f;
                ps.translate(shake, 0, shake);
                if (death == Death.FEATHER_BURST) ps.scale(1 + dp * 2.5f, 1 + dp * 2.5f, 1 + dp * 2.5f);
            }
        }
    }

    /** True while the victim's real body is replaced (x-ray) or gone (fell apart, popped, crumbled...). */
    public boolean hides(LivingEntity e) {
        if (e != loserE) return false;
        float a = lastNow - start;
        if (inXray(a)) return true;
        if (a < xrayEnd()) return false;
        float dp = deathP(a);
        return switch (death) {
            case BONE_PILE -> dp >= 0.15f;
            case FEATHER_BURST -> dp >= 0.12f;
            case FIZZLE -> dp >= 0.45f;
            case CRUMBLE -> dp >= 0.3f;
            case SNAP -> dp >= 0.2f;
            case SPLIT -> dp >= 0.4f;
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
        float bars = ease(Mth.clamp(a / 0.35f, 0, 1)) * (1 - ease(Mth.clamp((a - deathEndT()) / T_OUTRO, 0, 1)));
        int bh = (int) (h * 0.13f * bars);
        // vignette
        for (int i = 0; i < 6; i++) {
            int inset = i * Math.max(4, w / 60);
            int al = (int) (26 * bars);
            g.fill(0, 0, inset + 4, h, argb(al, 0));
            g.fill(w - inset - 4, 0, w, h, argb(al, 0));
        }
        if (inXray(a)) {
            g.fill(0, 0, w, h, argb(40, 0x0A3A8A));
            for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, argb(22, 0x7FD8FF));
            hud.outlined(g, "X-RAY", 34, bh + 14, 1.6f, 0xFF7FE8FF, 0xFF00304A);
        }
        if (flash > 0) g.fill(0, 0, w, h, argb((int) (170 * flash * flash), 0xFFFFFF));
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fill(0, h - bh, w, h, 0xFF000000);

        // FINISH! slam
        if (a < xrayStart() + 0.3f) {
            float p = Mth.clamp(a / 0.22f, 0, 1);
            float scale = Mth.lerp(ease(p), 7f, 3.6f);
            float jit = a < 0.35f ? (float) Math.sin(a * 90) * 2 : 0;
            int al = (int) (255 * Mth.clamp((xrayStart() + 0.3f - a) / 0.3f, 0, 1));
            hud.outlined(g, "FINISH!", w / 2f + jit, h * 0.4f, scale, argb(al, 0xFF3A2A), argb(al, 0x000000));
        }
        // lower third: who and how
        if (a > 0.3f && a < xrayEnd()) {
            String who = winner.moves.type().displayName.toUpperCase();
            int lx = 18, ly = h - bh - 34;
            int tw = Math.max(font.width(who), (int) (font.width(fin.name) * 1.6f)) + 20;
            g.fill(lx - 6, ly - 4, lx - 6 + tw, ly + 28, 0xB0000000);
            g.fill(lx - 6, ly - 4, lx - 3, ly + 28, 0xFFFFD21A);
            g.drawString(font, who, lx, ly, 0xFFBBBBBB, true);
            g.pose().pushPose();
            g.pose().translate(lx, ly + 11, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            g.drawString(font, fin.name, 0, 0, 0xFFFFD21A, true);
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
        // death title + FINISHED stamp
        if (a >= xrayEnd()) {
            float dp = deathP(a);
            float showT = Mth.clamp((dp - 0.35f) / 0.15f, 0, 1);
            if (showT > 0) {
                hud.outlined(g, death.title, w / 2f, h - bh - 22, 2.2f, argb((int) (255 * showT), 0xFFFFFF), argb((int) (255 * showT), 0x000000));
            }
            float st = Mth.clamp((dp - 0.72f) / 0.08f, 0, 1);
            if (st > 0) {
                float sc = Mth.lerp(ease(st), 6f, 3.4f);
                hud.outlined(g, "FINISHED!", w / 2f, h * 0.3f, sc, argb((int) (255 * st), 0xFFD21A), argb((int) (255 * st), 0x6A1000));
            }
        }
        g.drawString(font, "SPACE: skip", w - font.width("SPACE: skip") - 8, h - 10, 0xFF888888, true);
    }

    // ===================================================================================== helpers

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
}
