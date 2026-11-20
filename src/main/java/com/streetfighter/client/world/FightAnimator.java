package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
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

/**
 * Drives the look of fighters in the world: whole-body transforms (lean, knockdowns, squash, scale, hops)
 * through {@code RenderLivingEvent}, and limb poses (punches, kicks, guard, uppercuts...) through the model
 * mixins, which call {@link #applyModelPose(Object)} right before a model is drawn.
 */
public final class FightAnimator {
    public enum Pose {
        GUARD, WALK, CROUCH, BLOCK, JUMP, PUNCH, KICK, UPPERCUT, SLAM, THROW, SHOOT, LUNGE, GRAB, TAUNT,
        HIT, KNOCKDOWN, KO, VICTORY
    }

    /** Per-entity animation state, rewritten every frame by the fight session. */
    public static final class State {
        public Pose pose = Pose.GUARD;
        public float progress;     // 0..1 through the current move (or state)
        public boolean striking;   // in active frames
        public float time;         // seconds, for idle bobbing
        public Vec3 facing = new Vec3(1, 0, 0); // world-space facing direction (horizontal unit)
        public float tilt;         // degrees, + leans toward facing
        public float flip;         // degrees, roll about the body centre (belly-up knockdowns)
        public float forward;      // blocks along facing
        public float up;           // blocks
        public float squash = 1f;
        public float scale = 1f;
        public float attackAnim = -1f;
        public boolean aggressive;
        public boolean crouch;
        public float bodyHeight = 1.8f;

        private final List<ModelPart> savedParts = new ArrayList<>();
        private final List<PartPose> savedPoses = new ArrayList<>();
    }

    /** Set while fighters are drawn in GUI portraits, so world transforms don't apply there. */
    public static boolean suspended;

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static State current;
    private static Object currentModel;
    private static float savedAttack, savedAttackO;
    private static boolean savedAggressive, savedCrouching;

    private FightAnimator() {
    }

    public static State state(LivingEntity entity) {
        return STATES.computeIfAbsent(entity.getId(), k -> new State());
    }

    public static boolean has(LivingEntity entity) {
        return STATES.containsKey(entity.getId());
    }

    public static void clear() {
        STATES.clear();
        current = null;
        currentModel = null;
    }

    // ------------------------------------------------------------------ RenderLivingEvent hooks

    public static void pre(LivingEntity entity, EntityModel<?> model, PoseStack ps) {
        State s = STATES.get(entity.getId());
        if (s == null) return;
        current = s;
        currentModel = model;
        s.savedParts.clear();
        s.savedPoses.clear();

        savedAttack = entity.attackAnim;
        savedAttackO = entity.oAttackAnim;
        if (s.attackAnim >= 0) {
            entity.attackAnim = s.attackAnim;
            entity.oAttackAnim = s.attackAnim;
        }
        if (entity instanceof Mob mob) {
            savedAggressive = mob.isAggressive();
            if (savedAggressive != s.aggressive) mob.setAggressive(s.aggressive);
        }
        if (model instanceof HumanoidModel<?> h) {
            savedCrouching = h.crouching;
            h.crouching = s.crouch;
        }

        ps.pushPose();
        Vec3 f = s.facing;
        ps.translate(f.x * s.forward, s.up - (s.crouch && model instanceof HumanoidModel<?> ? 0.12 : 0), f.z * s.forward);
        // lean axis = up x facing, so a positive angle tips the head toward the facing direction
        float ax = (float) f.z, az = (float) -f.x;
        if (s.tilt != 0) ps.mulPose(new Quaternionf().rotateAxis(s.tilt * Mth.DEG_TO_RAD, ax, 0, az));
        if (s.flip != 0) {
            float half = s.bodyHeight * s.scale * s.squash / 2f;
            ps.translate(0, half, 0);
            ps.mulPose(new Quaternionf().rotateAxis(s.flip * Mth.DEG_TO_RAD, ax, 0, az));
            ps.translate(0, -half, 0);
        }
        ps.scale(s.scale, s.scale * s.squash, s.scale);
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
        current = null;
        currentModel = null;
    }

    // ------------------------------------------------------------------ model poses (called from mixins)

    public static void applyModelPose(Object model) {
        State s = current;
        if (s == null || suspended || model != currentModel) return;
        if (model instanceof HumanoidModel<?> h) humanoid(h, s);
        else if (model instanceof VillagerModel<?> v) villager(v, s);
        else if (model instanceof QuadrupedModel<?> q) quadruped(q, s);
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

    private static float wave(State s, float speed, float amp) {
        return Mth.sin(s.time * speed) * amp;
    }

    private static void humanoid(HumanoidModel<?> m, State s) {
        float p = s.progress;
        float bob = wave(s, 7f, 0.06f);
        boolean legsFree = s.pose == Pose.WALK;
        // defaults: boxer's guard
        float raX = -1.25f + bob, raY = -0.35f, raZ = 0.1f;
        float laX = -1.4f - bob, laY = 0.45f, laZ = -0.1f;
        float rlX = 0.28f, llX = -0.28f, rlZ = 0.06f, llZ = -0.06f;
        float bodyY = 0.12f, headX = 0f, headY = 0f;

        switch (s.pose) {
            case GUARD, WALK, CROUCH -> {
            }
            case BLOCK -> {
                raX = -1.8f; raY = -0.75f; raZ = 0f;
                laX = -1.8f; laY = 0.75f; laZ = 0f;
                headX = 0.25f;
            }
            case JUMP -> {
                rlX = -1.0f; llX = -0.35f;
                raX = -2.1f; laX = -2.3f;
            }
            case PUNCH -> {
                if (!s.striking && p < 0.5f) { raX = -0.8f; raY = 0.35f; bodyY = 0.35f; }
                else { raX = -1.62f; raY = -0.1f; raZ = 0; bodyY = -0.45f; }
            }
            case KICK -> {
                float k = s.striking ? 1f : Math.min(1f, p * 1.6f);
                rlX = -1.5f * k; llX = 0.25f;
                raX = -0.4f; raZ = 0.7f; laX = -0.4f; laZ = -0.7f;
                bodyY = -0.25f;
            }
            case UPPERCUT -> {
                if (!s.striking && p < 0.35f) { raX = -0.3f; raY = 0.2f; rlX = 0.5f; llX = -0.5f; }
                else { raX = -3.05f; raY = 0f; raZ = -0.15f; laX = -0.5f; laZ = -0.4f; rlX = -0.7f; llX = 0.35f; headX = -0.4f; }
            }
            case SLAM -> {
                if (!s.striking && p < 0.6f) { raX = -2.9f; laX = -2.7f; raY = 0; laY = 0; headX = -0.3f; }
                else { raX = -0.45f; laX = -0.6f; raY = -0.2f; laY = 0.2f; headX = 0.35f; }
            }
            case THROW -> {
                if (!s.striking && p < 0.5f) { raX = -2.7f; raZ = 0.35f; bodyY = 0.4f; }
                else { raX = -1.2f; raZ = 0f; bodyY = -0.35f; }
            }
            case SHOOT -> {
                raX = -1.57f; raY = -0.15f; raZ = 0;
                laX = -1.57f; laY = 0.45f; laZ = 0;
                headX = 0f;
            }
            case LUNGE -> {
                raX = -1.55f; raY = -0.15f; laX = -1.45f; laY = 0.15f;
                rlX = -0.7f; llX = 0.6f; headX = 0.2f;
            }
            case GRAB -> {
                float c = s.striking ? 1f : p;
                raX = -1.5f; raY = -0.15f - 0.35f * c; laX = -1.5f; laY = 0.15f + 0.35f * c;
            }
            case TAUNT -> {
                float pump = wave(s, 14f, 0.25f);
                raX = -2.8f + pump; raZ = -0.3f; laX = -2.8f - pump; laZ = 0.3f;
                headX = -0.35f;
            }
            case HIT -> {
                raX = -0.5f; raZ = 0.9f + bob; laX = -0.5f; laZ = -0.9f - bob;
                headX = -0.45f; rlX = 0.2f; llX = -0.1f;
            }
            case KNOCKDOWN, KO -> {
                raX = -0.2f; raZ = 1.3f; laX = -0.2f; laZ = -1.3f;
                rlX = 0.15f; llX = -0.25f; rlZ = 0.25f; llZ = -0.25f; headX = -0.3f; bodyY = 0;
            }
            case VICTORY -> {
                float pump = Math.abs(Mth.sin(s.time * 6f));
                raX = -2.7f - 0.35f * pump; raY = 0; raZ = -0.1f;
                laX = 0.1f; laY = 0; laZ = -0.45f;
                headX = -0.25f; rlX = 0.1f; llX = -0.1f; bodyY = 0;
            }
        }

        rot(s, m.rightArm, raX, raY, raZ);
        rot(s, m.leftArm, laX, laY, laZ);
        save(s, m.body);
        m.body.yRot = bodyY;
        if (s.pose == Pose.KICK || s.pose == Pose.LUNGE) m.body.xRot = s.pose == Pose.LUNGE ? 0.25f : -0.15f;
        save(s, m.head);
        m.head.xRot = headX + (s.crouch ? 0.2f : 0);
        m.head.yRot = headY;
        if (!legsFree && !s.crouch) {
            rot(s, m.rightLeg, rlX, 0, rlZ);
            rot(s, m.leftLeg, llX, 0, llZ);
        }
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
    }

    private static void villager(VillagerModel<?> m, State s) {
        ModelPart root = m.root();
        ModelPart head = m.getHead();
        ModelPart arms = child(root, "arms");
        ModelPart rightLeg = child(root, "right_leg");
        ModelPart leftLeg = child(root, "left_leg");
        float p = s.progress;
        float nod = wave(s, 5f, 0.08f);
        float headX = nod, armsX = -0.75f, armsY = 3.0f; // arms part rests at y=3, crossed
        float rl = 0.25f, ll = -0.25f;
        boolean legsFree = s.pose == Pose.WALK;
        switch (s.pose) {
            case PUNCH, LUNGE -> headX = s.striking ? 0.65f : -0.25f; // Nose Bonk
            case SLAM -> armsX = (!s.striking && p < 0.6f) ? -2.5f : 0.15f; // Emerald Slap
            case GRAB -> armsX = -1.45f;
            case THROW -> armsX = (!s.striking && p < 0.5f) ? -2.6f : -1.1f;
            case TAUNT -> { armsX = -2.9f + wave(s, 14f, 0.2f); headX = -0.45f; }
            case KICK -> { rl = -1.35f; ll = 0.2f; }
            case HIT -> { headX = -0.55f; armsX = -0.2f; }
            case BLOCK -> { armsX = -1.3f; headX = 0.3f; }
            case JUMP -> { rl = -0.8f; ll = -0.3f; }
            case KNOCKDOWN, KO -> { armsX = 0f; headX = -0.3f; }
            case VICTORY -> { headX = Mth.sin(s.time * 12f) * 0.3f; armsX = -2.6f; }
            case UPPERCUT -> armsX = s.striking ? -2.9f : -0.4f;
            default -> {
            }
        }
        save(s, head);
        head.xRot = headX;
        if (arms != null) {
            save(s, arms);
            arms.xRot = armsX;
            arms.y = armsY;
        }
        if (!legsFree && rightLeg != null && leftLeg != null) {
            rot(s, rightLeg, rl, 0, 0);
            rot(s, leftLeg, ll, 0, 0);
        }
    }

    private static void quadruped(QuadrupedModel<?> m, State s) {
        QuadrupedModelAccessor a = (QuadrupedModelAccessor) m;
        ModelPart head = a.streetfighter$head();
        float p = s.progress;
        float headX = wave(s, 4f, 0.08f);
        Float front = null, hind = null;
        switch (s.pose) {
            case PUNCH, LUNGE -> headX = s.striking ? 0.6f : -0.3f;           // snout poke / hog charge
            case SLAM -> {                                                    // hoof stomp / belly flop
                if (!s.striking && p < 0.6f) { front = -1.1f; headX = -0.4f; }
                else { front = 0.5f; hind = -0.4f; headX = 0.3f; }
            }
            case KICK -> hind = -0.9f;
            case TAUNT -> headX = -0.7f + wave(s, 12f, 0.15f);
            case HIT -> headX = -0.5f;
            case JUMP -> { front = -0.9f; hind = 0.9f; }
            case KNOCKDOWN, KO -> { front = Mth.sin(s.time * 22f) * 0.6f; hind = -front; }
            case VICTORY -> headX = Mth.sin(s.time * 10f) * 0.35f;
            case CROUCH, BLOCK -> headX = 0.45f;
            default -> {
            }
        }
        save(s, head);
        head.xRot = headX;
        if (front != null) {
            rot(s, a.streetfighter$rightFrontLeg(), front, 0, 0);
            rot(s, a.streetfighter$leftFrontLeg(), front, 0, 0);
        }
        if (hind != null) {
            rot(s, a.streetfighter$rightHindLeg(), hind, 0, 0);
            rot(s, a.streetfighter$leftHindLeg(), hind, 0, 0);
        }
    }

    private static ModelPart child(ModelPart root, String name) {
        try {
            return root.getChild(name);
        } catch (Exception e) {
            return null;
        }
    }
}
