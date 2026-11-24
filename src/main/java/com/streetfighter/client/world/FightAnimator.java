package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.HitLevel;
import com.streetfighter.fight.Move;
import com.streetfighter.mixin.QuadrupedModelAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.streetfighter.client.world.AnimClips.*;

/**
 * Keyframed, blended fighter animation.
 * <ul>
 *   <li>Each move plays a clip from {@link AnimClips}: guard -> wind-up (eased in over the startup frames)
 *       -> strike (snapped in during the last startup frames and held while active) -> follow-through -> guard,
 *       all timed to the move's real frame data and the sub-frame simulation alpha.</li>
 *   <li>Every channel is smoothed with frame-rate independent exponential blending, so transitions between
 *       states never pop.</li>
 *   <li>Springs add hit recoil and landing squash; a hitstop shudder sells impacts.</li>
 *   <li>Walk cycles are driven by actual distance travelled (no vanilla walk animation, which jitters when
 *       driven faster than 20 Hz).</li>
 * </ul>
 * Model limbs are posed through mixins on the model's {@code renderToBuffer}; whole-body transforms through
 * {@code RenderLivingEvent}.
 */
public final class FightAnimator {
    public enum Body { HUMANOID, VILLAGER, QUAD, BIRD, TREE, TOOL, OTHER }

    public static boolean suspended;

    /** Lets a finisher cinematic take over poses and whole-body transforms. */
    public interface FinisherHook {
        /** Overrides the target pose of {@code e}; returns the blend rate, or null to leave it alone. */
        Float pose(LivingEntity e, Body body, float[] t, float[] cur, float time);

        /** Extra whole-body transform (before scaling), e.g. toppling over. */
        void transform(LivingEntity e, PoseStack ps, Vec3 facing, float height);

        /** True when this entity's head has been cut off (its head parts are hidden). */
        default boolean headless(LivingEntity e) {
            return false;
        }
    }

    public static FinisherHook finisherHook;

    /** Per-frame inputs from the fight session. */
    public record Ctx(float dt, float time, float alpha, boolean hitstop, double alongAxis, float scale, Vec3 facing,
                      boolean victoryPose) {
    }

    public static final class State {
        public final float[] cur = new float[N];
        final float[] tgt = new float[N];
        boolean init;
        public Body body = Body.OTHER;
        public Vec3 facing = new Vec3(1, 0, 0);
        public float scale = 1f;
        public float bodyHeight = 1.8f;
        public boolean crouch;
        public boolean aggressive;
        float recoil, recoilV, squashS, squashV;
        Fighter.State lastState;
        float walkPhase;
        double lastAlong = Double.NaN;
        float time;
        float shudder;
        private final List<ModelPart> savedParts = new ArrayList<>();
        private final List<PartPose> savedPoses = new ArrayList<>();
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static State current;
    private static Object currentModel;
    private static LivingEntity currentEntity;
    private static final List<ModelPart> hiddenParts = new ArrayList<>();
    private static float savedAttack, savedAttackO;
    private static boolean savedAggressive, savedCrouching;

    private FightAnimator() {
    }

    public static State state(LivingEntity e) {
        return STATES.computeIfAbsent(e.getId(), k -> new State());
    }

    /** The animation state of a fighter, or null when the entity isn't in a fight. */
    public static State stateIfPresent(LivingEntity e) {
        State s = STATES.get(e.getId());
        return s != null && s.init ? s : null;
    }

    public static void clear() {
        STATES.clear();
        current = null;
        currentModel = null;
    }

    public static Body bodyOf(LivingEntity e) {
        var renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        if ((Object) renderer instanceof TreantRenderer || (Object) renderer instanceof OreGolemRenderer
                || (Object) renderer instanceof FurnaceRenderer) return Body.TREE;
        if ((Object) renderer instanceof PickaxeRenderer) return Body.TOOL;
        if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> ler) {
            Object m = ler.getModel();
            if (m instanceof HumanoidModel<?>) return Body.HUMANOID;
            if (m instanceof VillagerModel<?> || m instanceof net.minecraft.client.model.IllagerModel<?>) return Body.VILLAGER;
            if (m instanceof QuadrupedModel<?> || m instanceof net.minecraft.client.model.HorseModel<?>
                    || m instanceof net.minecraft.client.model.CreeperModel<?>) return Body.QUAD;
            if (m instanceof net.minecraft.client.model.ChickenModel<?>) return Body.BIRD;
            if (m instanceof net.minecraft.client.model.SpiderModel<?>) return Body.QUAD;
        }
        return Body.OTHER;
    }

    // ------------------------------------------------------------------ events from the fight

    public static void onHit(LivingEntity e, boolean heavy, HitLevel level) {
        if (e == null) return;
        State s = STATES.get(e.getId());
        if (s == null) return;
        if (level == HitLevel.LOW) s.recoilV += heavy ? 260 : 160;     // knees buckle forward
        else s.recoilV -= heavy ? 520 : 320;                            // head snaps back
        s.squashV -= heavy ? 1.6f : 0.9f;
        s.shudder = heavy ? 1f : 0.6f;
    }

    public static void onLand(LivingEntity e, boolean heavy) {
        if (e == null) return;
        State s = STATES.get(e.getId());
        if (s != null) s.squashV -= heavy ? 3.2f : 1.8f;
    }

    public static void onBlock(LivingEntity e) {
        if (e == null) return;
        State s = STATES.get(e.getId());
        if (s != null) s.recoilV -= 140;
    }

    // ------------------------------------------------------------------ per-frame update

    public static void update(LivingEntity e, Fighter f, Ctx ctx) {
        State s = state(e);
        if (!s.init) {
            s.body = bodyOf(e);
            float[] g = guard(s.body);
            System.arraycopy(g, 0, s.cur, 0, N);
            s.init = true;
        }
        s.time = ctx.time();
        s.scale = ctx.scale();
        s.facing = ctx.facing();
        s.bodyHeight = e.getBbHeight();
        s.crouch = false;
        s.aggressive = false;
        float[] t = s.tgt;
        float[] guard = guard(s.body);
        System.arraycopy(guard, 0, t, 0, N);
        // breathing / idle bounce
        float tm = ctx.time();
        t[RAX] += Mth.sin(tm * 3.1f) * 0.05f;
        t[LAX] -= Mth.sin(tm * 3.1f) * 0.05f;
        t[UP] += (Mth.sin(tm * 5.5f) * 0.5f + 0.5f) * 0.025f;
        t[HX] += Mth.sin(tm * 2.3f) * 0.03f;
        float rate = 16f;

        // walk phase from distance actually travelled along the arena (render-interpolated)
        double along = ctx.alongAxis();
        if (!Double.isNaN(s.lastAlong)) {
            double d = along - s.lastAlong;
            double towardFacing = d * (s.facing.x * 0 + 1); // along is already signed in arena space
            s.walkPhase += (float) Math.abs(towardFacing) * 5.2f * (f != null && isBackwards(f) ? -1 : 1);
        }
        s.lastAlong = along;

        if (f == null) {
            if (ctx.victoryPose()) victory(s, t, tm);
        } else {
            if (f.state != s.lastState) {
                if (f.state == Fighter.State.JUMP) s.squashV += 2.6f;            // stretch on take-off
                if (s.lastState == Fighter.State.JUMP) s.squashV -= 1.4f;        // settle on landing
                s.lastState = f.state;
            }
            rate = pose(s, f, t, ctx, guard);
        }
        if (finisherHook != null) {
            Float r = finisherHook.pose(e, s.body, t, s.cur, tm);
            if (r != null) rate = r;
        }

        // springs
        float dt = Math.min(0.05f, ctx.dt());
        s.recoilV += (-220f * s.recoil - 16f * s.recoilV) * dt;
        s.recoil += s.recoilV * dt;
        s.squashV += (-260f * s.squashS - 18f * s.squashV) * dt;
        s.squashS += s.squashV * dt;
        if (s.shudder > 0) s.shudder = Math.max(0, s.shudder - dt * 4);

        // exponential blend toward the target
        float k = 1f - (float) Math.exp(-rate * dt);
        float kRoot = 1f - (float) Math.exp(-Math.min(rate, 12f) * dt);
        for (int i = 0; i < N; i++) {
            float target = t[i];
            if (Float.isNaN(target)) continue;
            float kk = (i == LEAN || i == FLIP) ? kRoot : k;
            if (i == SPIN) kk = 1f; // spins are already continuous
            s.cur[i] += (target - s.cur[i]) * kk;
        }
    }

    private static boolean isBackwards(Fighter f) {
        return f.state == Fighter.State.WALK && Math.signum(f.vx) != f.dir();
    }

    private static float[] guard(Body b) {
        return b == Body.QUAD ? quadGuard() : b == Body.BIRD ? birdGuard() : humanoidGuard();
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float easeOut(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static void blendInto(float[] t, float[] a, float[] b, float w, float[] guard) {
        for (int i = 0; i < N; i++) {
            float va = Float.isNaN(a[i]) ? guard[i] : a[i];
            float vb = Float.isNaN(b[i]) ? guard[i] : b[i];
            t[i] = va + (vb - va) * w;
        }
    }

    /** Fills the target pose for a fighter. Returns the blend rate. */
    private static float pose(State s, Fighter f, float[] t, Ctx ctx, float[] guard) {
        Body body = s.body;
        boolean quad = body == Body.QUAD;
        boolean bird = body == Body.BIRD;
        if (bird) {
            Float r = birdPose(s, f, t, ctx);
            if (r != null) return r;
        }
        if (f.charging) {
            // powering up: fists clenched at the sides, head back, trembling
            t[RAX] = -0.35f; t[RAZ] = 0.55f; t[LAX] = -0.35f; t[LAZ] = -0.55f; t[RAY] = 0; t[LAY] = 0;
            t[RLZ] = 0.2f; t[LLZ] = -0.2f; t[HX] = -0.35f; t[LEAN] = -4; t[SQUASH] = 0.96f;
            t[FWD] = Mth.sin(ctx.time() * 70f) * 0.015f;
            t[FLIP] = 0;
            s.cur[FLIP] = 0;
            return 20f;
        }
        if (f.flying && f.state == Fighter.State.JUMP) {
            // Ki Clash flight: hover upright when still, lean into the direction of travel when moving fast
            float along = f.vx * f.dir();
            float speed = Mth.clamp(along / com.streetfighter.fight.FightEngine.FLY_SPEED, -1f, 1f);
            t[LEAN] = speed > 0 ? 55f * speed : 15f * speed;
            t[LEAN] -= Mth.clamp(f.vy / 5f, -1f, 1f) * 10f;
            t[RLX] = 0.35f + Mth.sin(ctx.time() * 2.2f) * 0.08f; t[LLX] = 0.15f - Mth.sin(ctx.time() * 2.2f) * 0.08f;
            t[RLZ] = 0.05f; t[LLZ] = -0.05f;
            if (speed > 0.6f) { // superman: one fist forward
                t[RAX] = -2.9f; t[RAY] = 0; t[RAZ] = 0; t[LAX] = 0.3f; t[LAZ] = -0.2f;
            }
            t[UP] += Mth.sin(ctx.time() * 2.4f) * 0.05f;
            t[FLIP] = 0;
            s.cur[FLIP] = 0;
            return 12f;
        }
        float tm = ctx.time();
        switch (f.state) {
            case IDLE, LANDING -> {
                t[UP] += Mth.sin(tm * 6.5f) * 0.02f;
                if (body == Body.OTHER && f.moves.type() == com.streetfighter.fight.FighterType.SLIME) {
                    float j = Math.abs(Mth.sin(tm * 4.2f));
                    t[SQUASH] = 0.86f + 0.14f * j; // a constant jelly wobble
                    t[UP] = j * 0.05f;
                    return 18f;
                }
                if (body == Body.HUMANOID) stance(f.moves.type(), t, tm);
                else if (quad) t[HX] += Mth.sin(tm * 1.7f) > 0.6f ? 0.35f : 0; // grazing head bob
                return 14f;
            }
            case WALK -> {
                float ph = s.walkPhase;
                if (body == Body.TOOL || body == Body.OTHER && f.moves.type() == com.streetfighter.fight.FighterType.SLIME) {
                    // hops along (the pickaxe on its handle, the slime squishing between hops)
                    t[UP] = Math.abs(Mth.sin(ph * 0.8f)) * 0.3f;
                    t[LEAN] = (Math.signum(f.vx) == f.dir() ? 10 : -6);
                    return 22f;
                }
                if (quad) {
                    t[RAZ] = Mth.sin(ph) * 0.75f;
                } else {
                    t[RLX] = Mth.sin(ph) * 0.7f;
                    t[LLX] = -Mth.sin(ph) * 0.7f;
                    t[RLZ] = 0;
                    t[LLZ] = 0;
                    t[RAX] += Mth.sin(ph) * 0.08f;
                    t[LAX] -= Mth.sin(ph) * 0.08f;
                }
                t[UP] = Math.abs(Mth.sin(ph)) * 0.035f;
                t[LEAN] = (Math.signum(f.vx) == f.dir() ? 7 : -2);
                return 18f;
            }
            case CROUCH -> {
                s.crouch = true;
                if (body != Body.HUMANOID) t[SQUASH] = 0.74f;
                t[RAX] = -1.05f;
                t[LAX] = -1.25f;
                return 18f;
            }
            case JUMP -> {
                float air = 2 * f.stats.jumpVy() / f.stats.gravity();
                float p = Mth.clamp(f.stateAge / Math.max(1f, air), 0, 1);
                boolean moving = Math.abs(f.vx) > 1f;
                if (quad) {
                    t[RAX] = -0.9f;
                    t[RLX] = 0.9f;
                } else if (f.vy > 0) {
                    t[RLX] = -1.15f; t[LLX] = -0.45f; t[RAX] = -2.0f; t[LAX] = -2.2f; t[LEAN] = 6;
                } else {
                    t[RLX] = -0.35f; t[LLX] = 0.2f; t[RAX] = -1.6f; t[LAX] = -1.7f;
                }
                if (moving && p > 0.12f && p < 0.85f) {
                    // SF-style somersault on forward/back jumps
                    float dirSign = Math.signum(f.vx) == f.dir() ? 1 : -1;
                    t[FLIP] = dirSign * 360f * ease((p - 0.12f) / 0.73f);
                    s.cur[FLIP] = t[FLIP];
                    t[RLX] = -1.6f; t[LLX] = -1.6f; t[RAX] = -0.6f; t[LAX] = -0.6f; // tuck
                    return 30f;
                }
                t[FLIP] = 0;
                s.cur[FLIP] = 0;
                return 16f;
            }
            case BLOCKSTUN -> {
                if (quad) {
                    t[HX] = 0.5f; t[LEAN] = -8; t[SQUASH] = 0.9f;
                } else {
                    t[RAX] = -1.8f; t[RAY] = -0.75f; t[RAZ] = 0; t[LAX] = -1.8f; t[LAY] = 0.75f; t[LAZ] = 0;
                    t[HX] = 0.25f; t[LEAN] = -9;
                }
                if (f.input.down) s.crouch = true;
                return 30f;
            }
            case PARRY -> {
                t[RAX] = -1.65f; t[RAY] = -0.6f; t[LAX] = -1.65f; t[LAY] = 0.6f; t[LEAN] = -5; t[HX] = 0.2f;
                return 30f;
            }
            case HITSTUN -> {
                boolean low = f.lastHitLevel == HitLevel.LOW;
                if (f.launched) {
                    if (quad || bird || body == Body.OTHER || body == Body.TOOL) t[FLIP] = -Math.min(200, 40 + f.stateAge * 9f);
                    else t[LEAN] = -40 - Math.min(50, f.stateAge * 3f);
                    t[RAX] = -0.4f; t[RAZ] = 1.1f; t[LAX] = -0.4f; t[LAZ] = -1.1f; t[RLX] = 0.4f; t[LLX] = -0.3f;
                    t[HX] = -0.5f;
                    return 14f;
                }
                if (low && !quad) {
                    t[LEAN] = 16; t[HX] = 0.45f; t[RAX] = -0.4f; t[LAX] = -0.4f; t[RAY] = 0.3f; t[LAY] = -0.3f;
                    t[RLX] = 0.3f; t[LLX] = -0.15f;
                } else if (f.lastHitLevel == HitLevel.MID && !f.lastHitHeavy && !quad) {
                    // gut shot: fold forward around the hit
                    t[LEAN] = 20; t[HX] = 0.55f; t[RAX] = -0.7f; t[LAX] = -0.7f; t[RAY] = 0.5f; t[LAY] = -0.5f;
                    t[RLX] = 0.2f; t[LLX] = -0.2f;
                } else {
                    t[LEAN] = f.lastHitHeavy ? -24 : -15; t[HX] = -0.5f;
                    t[RAX] = -0.5f; t[RAZ] = 0.9f; t[LAX] = -0.5f; t[LAZ] = -0.9f;
                }
                if (f.wasCrouchingWhenHit && f.grounded()) s.crouch = true;
                if (ctx.hitstop()) t[FWD] = ((int) (tm * 60) % 2 == 0 ? 1 : -1) * 0.035f * Math.max(0.4f, s.shudder);
                return 26f;
            }
            case KNOCKDOWN, KO -> {
                boolean gettingUp = f.state == Fighter.State.KNOCKDOWN && f.stateTimer < 12;
                if (quad || bird || body == Body.OTHER || body == Body.TOOL) {
                    t[FLIP] = gettingUp ? 0 : -180;
                    t[RAX] = Mth.sin(tm * 22f) * 0.6f;
                    t[RLX] = -t[RAX];
                } else {
                    t[LEAN] = gettingUp ? 0 : -90;
                    t[RAX] = -0.2f; t[RAZ] = 1.3f; t[LAX] = -0.2f; t[LAZ] = -1.3f;
                    t[RLX] = 0.15f; t[LLX] = -0.25f; t[RLZ] = 0.25f; t[LLZ] = -0.25f; t[HX] = -0.3f; t[BY] = 0;
                    if (!f.grounded() && f.state == Fighter.State.KO) t[LEAN] = -40 - Math.min(50, f.stateAge * 3f);
                }
                return gettingUp ? 12f : 9f;
            }
            case WALL_SPLAT -> {
                if (quad || bird || body == Body.OTHER || body == Body.TOOL) {
                    t[FLIP] = -70; t[RAX] = -1.3f; t[RLX] = 1.3f;
                } else {
                    t[LEAN] = -10; t[RAX] = -2.7f; t[RAZ] = 0.7f; t[LAX] = -2.7f; t[LAZ] = -0.7f;
                    t[RLZ] = 0.35f; t[LLZ] = -0.35f; t[HX] = -0.35f; t[BY] = 0; t[RAY] = 0; t[LAY] = 0;
                }
                return 30f;
            }
            case CRUMPLE -> {
                float p = 1f - f.stateTimer / 62f;
                t[LEAN] = 10 + 40 * p; t[SQUASH] = 1f - 0.35f * p; t[HX] = 0.6f;
                t[RAX] = -0.2f; t[LAX] = -0.2f; t[RAY] = 0; t[LAY] = 0; t[RAZ] = 0.2f; t[LAZ] = -0.2f;
                t[RLX] = 0.4f * p; t[LLX] = -0.4f * p;
                return 6f;
            }
            case VICTORY -> {
                victory(s, t, tm);
                return 12f;
            }
            case ATTACK -> {
                return attack(s, f, t, ctx, guard);
            }
            default -> {
                return 16f;
            }
        }
    }

    /** Chicken-specific states (walk waddle, flapping jumps and glides); null = use the shared pose. */
    private static Float birdPose(State s, Fighter f, float[] t, Ctx ctx) {
        float tm = ctx.time();
        switch (f.state) {
            case IDLE, LANDING -> {
                t[HX] += Mth.sin(tm * 4f) > 0.85f ? 0.5f : 0; // idle head bob
                t[UP] += Mth.sin(tm * 6.5f) * 0.02f;
                return 14f;
            }
            case WALK -> {
                float ph = s.walkPhase * 1.6f;
                t[RLX] = Mth.sin(ph) * 0.9f;
                t[LLX] = -Mth.sin(ph) * 0.9f;
                t[HX] = 0.15f + Mth.cos(ph * 2) * 0.25f;
                t[LEAN] = (Math.signum(f.vx) == f.dir() ? 8 : -4);
                t[UP] = Math.abs(Mth.sin(ph)) * 0.04f;
                t[RAZ] = 0.25f;
                return 20f;
            }
            case CROUCH -> {
                t[SQUASH] = 0.72f;
                t[HX] = 0.3f;
                t[RAZ] = 0.05f;
                return 18f;
            }
            case JUMP -> {
                float speed = f.gliding ? 22f : f.vy > 0 ? 40f : 30f;
                t[RAZ] = (f.gliding ? 1.25f : 0.9f) + Mth.sin(tm * speed) * (f.gliding ? 0.25f : 0.7f);
                t[RLX] = f.vy > 0 ? 0.6f : -0.2f;
                t[LLX] = t[RLX];
                t[LEAN] = f.gliding ? 14 : f.vy > 0 ? -8 : 4;
                t[HX] = -0.2f;
                t[FLIP] = 0;
                s.cur[FLIP] = 0;
                return 30f;
            }
            case BLOCKSTUN, PARRY -> {
                t[RAZ] = 1.3f;
                t[HX] = 0.5f;
                t[LEAN] = -12;
                t[SQUASH] = 0.88f;
                return 30f;
            }
            case VICTORY -> {
                float pump = Math.abs(Mth.sin(tm * 7f));
                t[RAZ] = 0.6f + Mth.sin(tm * 30f) * 0.8f;
                t[HX] = -0.6f;
                t[UP] = pump * 0.18f;
                return 16f;
            }
            default -> {
                return null;
            }
        }
    }

    /** Learned styles change how a humanoid stands: zombie arms, archer stance, heavy ore stance... */
    private static void stance(com.streetfighter.fight.FighterType style, float[] t, float tm) {
        float bob = Mth.sin(tm * 3f);
        switch (style) {
            case ZOMBIE -> {
                t[RAX] = -1.55f + bob * 0.05f; t[LAX] = -1.5f - bob * 0.05f; t[RAY] = 0; t[LAY] = 0;
                t[RAZ] = 0; t[LAZ] = 0; t[LEAN] = 8; t[HX] = 0.2f;
            }
            case SKELETON -> {
                t[RAX] = -1.45f; t[RAY] = -0.1f; t[LAX] = -1.5f; t[LAY] = 0.55f; t[LEAN] = 0;
            }
            case IRON_ORE -> {
                t[RLZ] = 0.28f; t[LLZ] = -0.28f; t[RAX] = -0.6f; t[LAX] = -0.6f; t[RAZ] = 0.35f; t[LAZ] = -0.35f;
                t[SQUASH] = 0.94f; t[LEAN] = 6;
            }
            case OAK -> {
                t[RAZ] = 0.7f + bob * 0.08f; t[LAZ] = -0.7f - bob * 0.08f; t[RAX] = -0.5f; t[LAX] = -0.5f;
            }
            case SLIME -> {
                t[SQUASH] = 0.9f + Math.abs(bob) * 0.06f; t[RAX] = -0.5f; t[LAX] = -0.5f; t[RAZ] = 0.5f; t[LAZ] = -0.5f;
            }
            case SPIDER -> {
                t[LEAN] = 22; t[SQUASH] = 0.9f; t[RAX] = -1.2f + bob * 0.1f; t[LAX] = -1.2f - bob * 0.1f;
                t[RAZ] = 0.35f; t[LAZ] = -0.35f; t[HX] = -0.3f;
            }
            case LEATHERWORKER -> {
                t[RAX] = -0.7f; t[LAX] = -0.4f; t[RAZ] = 0.15f; t[LAZ] = -0.2f; t[LEAN] = 4;
            }
            case CREEPER -> {
                t[SQUASH] = 0.9f; t[LEAN] = 14; t[RAX] = -0.4f; t[LAX] = -0.4f; t[HX] = 0.25f;
            }
            case STONE_PICKAXE -> {
                t[RAX] = -1.0f; t[LAX] = -1.0f; t[RAY] = -0.45f; t[LAY] = 0.45f; t[LEAN] = 6;
            }
            case CHICKEN -> {
                t[RAZ] = 0.5f + Math.abs(bob) * 0.3f; t[LAZ] = -0.5f - Math.abs(bob) * 0.3f; t[RAX] = -0.3f; t[LAX] = -0.3f;
            }
            case GOAT, PIG, COW, DONKEY -> {
                t[LEAN] = 18; t[HX] = 0.4f; t[RAX] = -0.9f; t[LAX] = -0.9f; // head-down charging stance
            }
            default -> {
            }
        }
    }

    private static void victory(State s, float[] t, float tm) {
        float pump = Math.abs(Mth.sin(tm * 6f));
        if (s.body == Body.BIRD) {
            t[RAZ] = 0.6f + Mth.sin(tm * 30f) * 0.8f;
            t[HX] = -0.6f;
            t[UP] = pump * 0.18f;
        } else if (s.body == Body.QUAD) {
            t[UP] = pump * 0.12f;
            t[HX] = Mth.sin(tm * 10f) * 0.35f;
        } else {
            t[RAX] = -2.75f - 0.35f * pump; t[RAY] = 0; t[RAZ] = -0.1f;
            t[LAX] = 0.1f; t[LAY] = 0; t[LAZ] = -0.45f;
            t[HX] = -0.25f; t[RLX] = 0.1f; t[LLX] = -0.1f; t[BY] = 0;
            t[UP] = pump * 0.1f;
            t[LEAN] = 0;
        }
    }

    private static float attack(State s, Fighter f, float[] t, Ctx ctx, float[] guard) {
        Move m = f.move;
        if (m == null) return 16f;
        AnimClips.Clip c = s.body == Body.QUAD ? AnimClips.quadruped(m.pose)
                : s.body == Body.BIRD ? AnimClips.bird(m.pose) : AnimClips.humanoid(m.pose);
        if (m.crouching) {
            s.crouch = true;
            if (s.body != Body.HUMANOID) guard[SQUASH] = 0.74f;
        }
        if (m.pose == com.streetfighter.fight.AnimPose.SHOOT || m.pose == com.streetfighter.fight.AnimPose.SHOOT_UP) {
            s.aggressive = true;
        }
        float fr = Math.max(0, f.curFrame) + ctx.alpha();
        int S = Math.max(1, m.startup);
        int activeEnd = f.activeEnd == Integer.MAX_VALUE ? Integer.MAX_VALUE : f.activeEnd;
        float rate;
        if (fr < S) {
            float p = fr / S;
            blendInto(t, guard, c.windup(), ease(p / 0.8f), guard);
            if (S >= 3 && fr > S - 2) {
                float[] tmp = t.clone();
                blendInto(t, tmp, c.strike(), easeOut((fr - (S - 2)) / 2f), guard);
            }
            rate = fr > S - 2 ? 40f : 22f;
        } else if (fr < activeEnd) {
            blendInto(t, c.strike(), c.strike(), 0, guard);
            float a = fr - S;
            switch (m.pose) {
                case SPIN_KICK -> t[SPIN] = a * 28f;
                case PECK -> {
                    if (m.hits > 1) {
                        float w = Mth.sin(a * 1.6f);
                        t[HX] += w * 0.6f;
                        t[FWD] += w * 0.05f;
                    }
                }
                case RUSH -> {
                    float w = Mth.sin(a * 0.9f);
                    if (s.body == Body.QUAD) t[HX] = 0.3f + w * 0.4f;
                    else if (s.body == Body.BIRD) {
                        t[HX] = 0.2f + w * 0.8f;
                        t[RAZ] = 0.8f + w * 0.6f;
                    }
                    else {
                        t[RAX] = -1.6f + w * 0.35f;
                        t[LAX] = -1.6f - w * 0.35f;
                        t[BY] = w * 0.5f;
                    }
                }
                default -> {
                }
            }
            rate = 40f;
        } else {
            int R = Math.max(1, m.recovery);
            float r = (fr - activeEnd) / R;
            float hold = 0.22f;
            if (r < hold) blendInto(t, c.strike(), c.strike(), 0, guard);
            else blendInto(t, c.strike(), guard, ease((r - hold) / (1 - hold)), guard);
            rate = 20f;
        }
        if (m.pose == com.streetfighter.fight.AnimPose.BACKFLIP) {
            float total = Math.max(1, m.startup + m.active + m.recovery);
            t[FLIP] = 360f * ease(fr / (total * 0.85f));
            s.cur[FLIP] = t[FLIP];
        }
        if (m.pose == com.streetfighter.fight.AnimPose.UPPERCUT && fr >= S && fr < activeEnd) {
            t[SPIN] = (fr - S) * 18f;
        }
        if (s.body == Body.BIRD && !f.grounded()) t[RAZ] = Math.max(t[RAZ], 0.6f) + Mth.sin(ctx.time() * 38f) * 0.5f;
        if (m.air && s.body != Body.QUAD && s.body != Body.BIRD) {
            if (Float.isNaN(c.strike()[RLX]) || t[RLX] > -0.4f) t[RLX] = -0.9f;
        }
        return rate;
    }

    // ------------------------------------------------------------------ RenderLivingEvent hooks

    public static void pre(LivingEntity entity, EntityModel<?> model, PoseStack ps) {
        State s = STATES.get(entity.getId());
        if (s == null || !s.init) return;
        current = s;
        currentModel = model;
        currentEntity = entity;
        s.savedParts.clear();
        s.savedPoses.clear();

        savedAttack = entity.attackAnim;
        savedAttackO = entity.oAttackAnim;
        entity.attackAnim = 0;
        entity.oAttackAnim = 0;
        if (entity instanceof Mob mob) {
            savedAggressive = mob.isAggressive();
            if (savedAggressive != s.aggressive) mob.setAggressive(s.aggressive);
        }
        if (model instanceof HumanoidModel<?> h) {
            savedCrouching = h.crouching;
            h.crouching = s.crouch;
        }
        float[] c = s.cur;
        Vec3 f = s.facing;
        ps.pushPose();
        float lean = c[LEAN] + s.recoil;
        ps.translate(f.x * c[FWD], c[UP] - (s.crouch && model instanceof HumanoidModel<?> ? 0.12 : 0), f.z * c[FWD]);
        if (c[SPIN] != 0) ps.mulPose(Axis.YP.rotationDegrees(c[SPIN]));
        float ax = (float) f.z, az = (float) -f.x;
        if (lean != 0) ps.mulPose(new Quaternionf().rotateAxis(lean * Mth.DEG_TO_RAD, ax, 0, az));
        if (c[FLIP] != 0) {
            float half = s.bodyHeight * s.scale * c[SQUASH] / 2f;
            ps.translate(0, half, 0);
            ps.mulPose(new Quaternionf().rotateAxis(c[FLIP] * Mth.DEG_TO_RAD, ax, 0, az));
            ps.translate(0, -half, 0);
        }
        if (finisherHook != null) finisherHook.transform(entity, ps, f, s.bodyHeight * s.scale);
        float squash = Mth.clamp(c[SQUASH] + s.squashS * 0.06f, 0.5f, 1.2f);
        float bulge = 1f + (1f - squash) * 0.35f; // keep volume: squash wider
        ps.scale(s.scale * bulge, s.scale * squash, s.scale * bulge);
    }

    public static void post(LivingEntity entity, EntityModel<?> model, PoseStack ps) {
        State s = STATES.get(entity.getId());
        if (s == null || s != current) return;
        ps.popPose();
        for (int i = 0; i < s.savedParts.size(); i++) s.savedParts.get(i).loadPose(s.savedPoses.get(i));
        s.savedParts.clear();
        s.savedPoses.clear();
        entity.attackAnim = savedAttack;
        entity.oAttackAnim = savedAttackO;
        if (entity instanceof Mob mob && mob.isAggressive() != savedAggressive) mob.setAggressive(savedAggressive);
        if (model instanceof HumanoidModel<?> h) h.crouching = savedCrouching;
        for (ModelPart p : hiddenParts) p.visible = true;
        hiddenParts.clear();
        current = null;
        currentModel = null;
        currentEntity = null;
    }

    // ------------------------------------------------------------------ model poses (called from mixins)

    public static void applyModelPose(Object model) {
        State s = current;
        if (s == null || suspended || model != currentModel) return;
        float[] c = s.cur;
        float headRecoil = -s.recoil * 0.008f;
        if (model instanceof HumanoidModel<?> m) {
            rot(s, m.rightArm, c[RAX], c[RAY], c[RAZ]);
            rot(s, m.leftArm, c[LAX], c[LAY], c[LAZ]);
            rot(s, m.rightLeg, c[RLX], 0, c[RLZ]);
            rot(s, m.leftLeg, c[LLX], 0, c[LLZ]);
            save(s, m.body);
            m.body.xRot = c[BX] + (s.crouch ? 0.5f : 0);
            m.body.yRot = c[BY];
            save(s, m.head);
            m.head.xRot = c[HX] + headRecoil + (s.crouch ? 0.15f : 0);
            m.head.yRot = c[HY];
            save(s, m.hat);
            m.hat.copyFrom(m.head);
            if (m instanceof PlayerModel<?> pm) {
                save(s, pm.rightSleeve);
                save(s, pm.leftSleeve);
                save(s, pm.rightPants);
                save(s, pm.leftPants);
                save(s, pm.jacket);
                pm.rightSleeve.copyFrom(m.rightArm);
                pm.leftSleeve.copyFrom(m.leftArm);
                pm.rightPants.copyFrom(m.rightLeg);
                pm.leftPants.copyFrom(m.leftLeg);
                pm.jacket.copyFrom(m.body);
            }
        } else if (model instanceof VillagerModel<?> v) {
            ModelPart head = v.getHead();
            save(s, head);
            head.xRot = c[HX] + headRecoil;
            head.yRot = c[HY];
            ModelPart arms = child(v.root(), "arms");
            if (arms != null) {
                save(s, arms);
                arms.xRot = (c[RAX] + c[LAX]) / 2f * 0.85f + 0.45f;
            }
            ModelPart rl = child(v.root(), "right_leg"), ll = child(v.root(), "left_leg");
            if (rl != null && ll != null) {
                rot(s, rl, c[RLX], 0, c[RLZ] * 0.5f);
                rot(s, ll, c[LLX], 0, c[LLZ] * 0.5f);
            }
        } else if (model instanceof net.minecraft.client.model.ChickenModel<?> ch) {
            com.streetfighter.mixin.ChickenModelAccessor a = (com.streetfighter.mixin.ChickenModelAccessor) ch;
            ModelPart head = a.streetfighter$head();
            save(s, head);
            head.xRot = c[HX] + headRecoil;
            head.yRot = 0;
            for (ModelPart p : new ModelPart[]{a.streetfighter$beak(), a.streetfighter$redThing()}) {
                save(s, p);
                p.xRot = head.xRot;
                p.yRot = head.yRot;
            }
            rot(s, a.streetfighter$rightLeg(), c[RLX], 0, 0);
            rot(s, a.streetfighter$leftLeg(), c[LLX], 0, 0);
            rot(s, a.streetfighter$rightWing(), 0, 0, c[RAZ]);
            rot(s, a.streetfighter$leftWing(), 0, 0, -c[RAZ]);
        } else if (model instanceof net.minecraft.client.model.HorseModel<?> hm) {
            com.streetfighter.mixin.HorseModelAccessor a = (com.streetfighter.mixin.HorseModelAccessor) hm;
            ModelPart head = a.streetfighter$headParts();
            save(s, head);
            head.xRot = 0.52f + c[HX] + headRecoil;
            rot(s, a.streetfighter$rightFrontLeg(), c[RAX] + c[RAZ], 0, 0);
            rot(s, a.streetfighter$leftFrontLeg(), c[RAX] - c[RAZ], 0, 0);
            rot(s, a.streetfighter$rightHindLeg(), c[RLX] - c[RAZ], 0, 0);
            rot(s, a.streetfighter$leftHindLeg(), c[RLX] + c[RAZ], 0, 0);
        } else if (model instanceof net.minecraft.client.model.SpiderModel<?> sm) {
            // eight legs: a skittering gait from the walk phase; front legs rear up to strike, hind legs kick
            ModelPart root = sm.root();
            ModelPart head = child(root, "head");
            if (head != null) {
                save(s, head);
                head.xRot = c[HX] * 0.6f + headRecoil;
            }
            String[] names = {"front", "middle_front", "middle_hind", "hind"};
            float strike = Math.max(0, -c[RAX]), kick = Math.max(0, c[RLX]);
            for (int i = 0; i < 4; i++) {
                for (int side = -1; side <= 1; side += 2) {
                    ModelPart leg = child(root, (side < 0 ? "right_" : "left_") + names[i] + "_leg");
                    if (leg == null) continue;
                    save(s, leg);
                    float ph = s.walkPhase * 1.4f + (i % 2 == 0 ? 0 : Mth.PI) + (side < 0 ? 0 : Mth.PI);
                    leg.yRot += Mth.sin(ph) * 0.35f;
                    leg.zRot -= side * Math.abs(Mth.cos(ph)) * 0.2f; // vanilla: right legs lift with +zRot
                    float lift = i == 0 ? strike * 0.8f : i == 3 ? kick * 0.6f : 0;
                    leg.zRot -= side * lift;
                }
            }
        } else if (model instanceof net.minecraft.client.model.CreeperModel<?> cm) {
            com.streetfighter.mixin.CreeperModelAccessor a = (com.streetfighter.mixin.CreeperModelAccessor) cm;
            ModelPart head = a.streetfighter$head();
            save(s, head);
            head.xRot = c[HX] + headRecoil;
            rot(s, a.streetfighter$rightFrontLeg(), c[RAX] + c[RAZ], 0, 0);
            rot(s, a.streetfighter$leftFrontLeg(), c[RAX] - c[RAZ], 0, 0);
            rot(s, a.streetfighter$rightHindLeg(), c[RLX] - c[RAZ], 0, 0);
            rot(s, a.streetfighter$leftHindLeg(), c[RLX] + c[RAZ], 0, 0);
        } else if (model instanceof QuadrupedModel<?> q) {
            QuadrupedModelAccessor a = (QuadrupedModelAccessor) q;
            ModelPart head = a.streetfighter$head();
            save(s, head);
            head.xRot = c[HX] + headRecoil;
            rot(s, a.streetfighter$rightFrontLeg(), c[RAX] + c[RAZ], 0, 0);
            rot(s, a.streetfighter$leftFrontLeg(), c[RAX] - c[RAZ], 0, 0);
            rot(s, a.streetfighter$rightHindLeg(), c[RLX] - c[RAZ], 0, 0);
            rot(s, a.streetfighter$leftHindLeg(), c[RLX] + c[RAZ], 0, 0);
        }
        if (finisherHook != null && currentEntity != null && finisherHook.headless(currentEntity)) hideHead(model);
    }

    /** Decapitated by a fatality: hide every head part of the model (restored after rendering). */
    private static void hideHead(Object model) {
        List<ModelPart> heads = new ArrayList<>();
        if (model instanceof HumanoidModel<?> m) {
            heads.add(m.head);
            heads.add(m.hat);
        } else if (model instanceof VillagerModel<?> v) {
            heads.add(v.getHead());
        } else if (model instanceof net.minecraft.client.model.ChickenModel<?> ch) {
            com.streetfighter.mixin.ChickenModelAccessor a = (com.streetfighter.mixin.ChickenModelAccessor) ch;
            heads.add(a.streetfighter$head());
            heads.add(a.streetfighter$beak());
            heads.add(a.streetfighter$redThing());
        } else if (model instanceof net.minecraft.client.model.HorseModel<?> hm) {
            heads.add(((com.streetfighter.mixin.HorseModelAccessor) hm).streetfighter$headParts());
        } else if (model instanceof net.minecraft.client.model.CreeperModel<?> cm) {
            heads.add(((com.streetfighter.mixin.CreeperModelAccessor) cm).streetfighter$head());
        } else if (model instanceof QuadrupedModel<?> q) {
            heads.add(((QuadrupedModelAccessor) q).streetfighter$head());
        } else if (model instanceof net.minecraft.client.model.SpiderModel<?> sm) {
            ModelPart h = child(sm.root(), "head");
            if (h != null) heads.add(h);
        }
        for (ModelPart p : heads) {
            if (p != null && p.visible) {
                p.visible = false;
                hiddenParts.add(p);
            }
        }
    }

    private static void save(State s, ModelPart part) {
        if (!s.savedParts.contains(part)) {
            s.savedParts.add(part);
            s.savedPoses.add(part.storePose());
        }
    }

    private static void rot(State s, ModelPart part, float x, float y, float z) {
        save(s, part);
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    private static ModelPart child(ModelPart root, String name) {
        try {
            return root.getChild(name);
        } catch (Exception e) {
            return null;
        }
    }
}
