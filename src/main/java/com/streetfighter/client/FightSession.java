package com.streetfighter.client;

import com.streetfighter.client.world.Arena;
import com.streetfighter.client.world.FightAnimator;
import com.streetfighter.client.world.FightCamera;
import com.streetfighter.client.world.WorldFx;
import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.AnimPose;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FightInput;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.Fighter.State;
import com.streetfighter.fight.FighterStats;
import com.streetfighter.fight.FighterType;
import com.streetfighter.fight.Move;
import com.streetfighter.fight.Moveset;
import com.streetfighter.fight.Movesets;
import com.streetfighter.fight.ai.FighterAI;
import com.streetfighter.network.FightResultPacket;
import com.streetfighter.network.FightSyncPacket;
import com.streetfighter.network.ModNetwork;
import com.streetfighter.network.OpenFightPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A street fight happening in the real world. The 2D simulation ({@link FightEngine}) runs on the client at
 * 60 fps; every frame its result is projected onto a line through the terrain ({@link Arena}): the real player
 * and the real mob are moved, posed and filmed from the side by {@link FightCamera}.
 */
public final class FightSession {
    public enum Stage { SELECT, FIGHT, OUTRO }

    private static final double STEP = 1.0 / 60.0;
    private static FightSession current;

    public final OpenFightPacket info;
    public final FighterType enemyType;
    public final AIDifficulty difficulty;
    public final List<String> unlocked;
    public final List<FighterType> styles = new ArrayList<>();
    public final LivingEntity enemyEntity;
    public final Arena arena;
    public final FightCamera camera;
    public final WorldFx fx;

    public Stage stage = Stage.SELECT;
    public FightEngine engine;
    public FighterType playerStyle = FighterType.STEVE;
    public boolean paused;
    public boolean resultSent;
    public int matchEndFrames;
    public float time;
    public float playerRenderScale = 1f, enemyRenderScale = 1f;

    // dummies used to draw projectiles/assists with real models
    public LivingEntity golemDummy, pigDummy;
    public Arrow arrowDummy;

    private final float startSeparation;
    private final float selectPlayerX, selectEnemyX;
    private final boolean savedHideGui;
    private final double savedDamageTilt;
    private final boolean savedBob;
    private long lastNanos = System.nanoTime();
    private double accumulator;
    private int syncTimer;
    private FightInput pendingInput = new FightInput();
    public final HudState hud = new HudState();

    /** Small bits of HUD state that need per-step timing (callouts, flashes, cut-ins). */
    public static final class HudState {
        public final List<String[]> callouts = new ArrayList<>(); // {text, side, color}
        public final List<int[]> calloutTimers = new ArrayList<>();
        public int flash;
        public int superBanner;
        public String superText = "";
        public int superOwner;
        public float letterbox;

        public void callout(String text, int side, int color) {
            callouts.add(new String[]{text, String.valueOf(side), String.valueOf(color)});
            calloutTimers.add(new int[]{90});
            if (callouts.size() > 6) {
                callouts.remove(0);
                calloutTimers.remove(0);
            }
        }

        void step() {
            if (flash > 0) flash--;
            if (superBanner > 0) superBanner--;
            for (int i = calloutTimers.size() - 1; i >= 0; i--) {
                if (--calloutTimers.get(i)[0] <= 0) {
                    callouts.remove(i);
                    calloutTimers.remove(i);
                }
            }
        }
    }

    private FightSession(OpenFightPacket info, LivingEntity enemyEntity) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        this.info = info;
        this.enemyType = FighterType.byId(info.enemyType());
        AIDifficulty d = AIDifficulty.byId(info.difficulty());
        this.difficulty = d == null ? AIDifficulty.NORMAL : d;
        this.unlocked = info.unlockedStyles();
        for (FighterType t : FighterType.values()) if (t == FighterType.STEVE || t.learnable) styles.add(t);
        this.enemyEntity = enemyEntity;

        this.arena = Arena.build(mc.level, player.position(), enemyEntity.position(), player.getLookAngle(),
                FightEngine.ARENA_WIDTH / 2f);
        float realSep = (float) (player.position().distanceTo(enemyEntity.position()) * Arena.UNITS_PER_BLOCK);
        this.startSeparation = Mth.clamp(realSep, 200, 400);
        float mid = (minX() + maxX()) / 2f;
        this.selectPlayerX = mid - startSeparation / 2f;
        this.selectEnemyX = mid + startSeparation / 2f;
        this.camera = new FightCamera(arena, player);
        this.fx = new WorldFx(this);

        golemDummy = (LivingEntity) EntityType.IRON_GOLEM.create(mc.level);
        pigDummy = (LivingEntity) EntityType.PIG.create(mc.level);
        arrowDummy = EntityType.ARROW.create(mc.level);

        float bbH = Math.max(0.2f, enemyEntity.getBbHeight());
        FighterStats es = enemyStats();
        enemyRenderScale = es.hurtHeight() / (bbH * Arena.UNITS_PER_BLOCK);
        playerRenderScale = 1f;

        savedHideGui = mc.options.hideGui;
        mc.options.hideGui = true;
        // the fight camera supplies its own shake; vanilla's hurt-tilt and walk bob would fight it
        savedDamageTilt = mc.options.damageTiltStrength().get();
        savedBob = mc.options.bobView().get();
        mc.options.damageTiltStrength().set(0.0);
        mc.options.bobView().set(false);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        camera.start();
    }

    /** Arena walls, widened if the terrain left too little room to fight. */
    private float minX() {
        float w = arena.maxX - arena.minX;
        return w >= 420 ? arena.minX : arena.minX - (420 - w) / 2f;
    }

    private float maxX() {
        float w = arena.maxX - arena.minX;
        return w >= 420 ? arena.maxX : arena.maxX + (420 - w) / 2f;
    }

    public static FightSession current() {
        return current;
    }

    public static void start(OpenFightPacket info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (current != null) current.cleanup();
        Entity e = mc.level.getEntity(info.entityId());
        if (!(e instanceof LivingEntity enemy)) {
            ModNetwork.sendToServer(new FightResultPacket(false, 0, 0, false, true));
            return;
        }
        current = new FightSession(info, enemy);
        mc.setScreen(new FightScreen(current));
    }

    public boolean isUnlocked(FighterType t) {
        return t == FighterType.STEVE || unlocked.contains(t.id);
    }

    private FighterStats enemyStats() {
        Moveset em = Movesets.get(enemyType);
        FighterStats es = em.stats();
        if (enemyType == FighterType.BRAWLER) {
            float bbH = Math.max(0.2f, enemyEntity.getBbHeight());
            float bbW = Math.max(0.2f, enemyEntity.getBbWidth());
            float h = Mth.clamp(bbH * Arena.UNITS_PER_BLOCK, 90, 190);
            float w = Mth.clamp(bbW * Arena.UNITS_PER_BLOCK, 36, 130);
            es = new FighterStats(es.maxHp(), 3.2f, 2.8f, 14.5f, 4.2f, 0.75f, w, h, h * 0.66f);
        }
        return es;
    }

    // ------------------------------------------------------------------ match flow

    public void beginFight(FighterType style) {
        Minecraft mc = Minecraft.getInstance();
        playerStyle = style;
        Moveset pm = Movesets.get(style);
        FighterStats st = pm.stats();
        FighterStats body = Movesets.HUMANOID;
        FighterStats playerStats = new FighterStats(st.maxHp(), st.walkSpeed(), st.backSpeed(), st.jumpVy(), st.jumpVx(),
                st.gravity(), body.hurtWidth(), body.hurtHeight(), body.crouchHeight());
        String playerName = mc.player != null ? mc.player.getName().getString() : "Steve";
        Fighter player = new Fighter(0, playerName, FighterType.STEVE, pm, playerStats, 1f, 1f);
        Fighter enemy = new Fighter(1, info.enemyName(), enemyType, Movesets.get(enemyType), enemyStats(),
                info.hpScale(), info.damageScale());
        engine = new FightEngine(player, enemy, FighterAI.create(enemyType, difficulty), difficulty, minX(), maxX(),
                startSeparation);
        stage = Stage.FIGHT;
        lastNanos = System.nanoTime();
        accumulator = 0;
        fx.fightStart();
    }

    public void setInput(FightInput in) {
        pendingInput = in;
    }

    /** Called once per rendered frame, before the world is drawn. */
    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || enemyEntity.isRemoved() && stage != Stage.OUTRO) {
            if (player == null || mc.level == null) {
                cleanup();
                return;
            }
        }
        long now = System.nanoTime();
        float dt = (float) Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        time += dt;

        if (stage == Stage.FIGHT && !paused) {
            accumulator += dt;
            int steps = 0;
            while (accumulator >= STEP && steps < 6) {
                accumulator -= STEP;
                steps++;
                FightScreen screen = mc.screen instanceof FightScreen fs ? fs : null;
                FightInput in = screen != null ? screen.pollInput() : new FightInput();
                engine.step(in);
                fx.process(engine.events);
                engine.events.clear();
                fx.stepTrails();
                hud.step();
                animateWalk(player, engine.player);
                animateWalk(enemyEntity, engine.enemy);
                if (pigDummy != null) pigDummy.walkAnimation.update(1.3f, 0.5f);
                if (engine.isOver()) {
                    if (!resultSent) sendResult(engine.playerWon());
                    matchEndFrames++;
                }
            }
        } else if (stage == Stage.SELECT) {
            hud.step();
        }

        if (stage != Stage.OUTRO) {
            placeAll(player);
            if (++syncTimer >= 6) {
                syncTimer = 0;
                syncMob();
            }
        } else {
            updateAnimator(player, null, true);
            updateAnimator(enemyEntity, null, false);
        }
        updateCamera(dt, player);
        float targetBox = stage == Stage.SELECT || stage == Stage.OUTRO
                || (engine != null && (engine.freeze > 0 || engine.phase == FightEngine.Phase.KO
                || (engine.phase == FightEngine.Phase.INTRO && engine.round == 1) || engine.isOver())) ? 1f : 0f;
        hud.letterbox += (targetBox - hud.letterbox) * Math.min(1f, dt * 6f);

        if (stage == Stage.OUTRO && camera.finishedLeaving()) cleanup();
    }

    /** Called at the start of every client tick so the player's position packet carries the fight position. */
    public void tickPre() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || stage == Stage.OUTRO) return;
        placeAll(mc.player);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.fallDistance = 0;
    }

    private void animateWalk(LivingEntity e, Fighter f) {
        if (e == null) return;
        float speed = 0f;
        if (f.state == State.WALK) speed = Math.min(1f, Math.abs(f.vx) / Math.max(1f, f.stats.walkSpeed()));
        if (f.state == State.ATTACK && f.move != null && f.move.moveVx > 0 && f.grounded() && Math.abs(f.vx) > 1f) speed = 1.4f;
        e.walkAnimation.update(speed, 0.4f);
    }

    private void placeAll(LocalPlayer player) {
        if (engine == null) {
            boolean faceRight = selectPlayerX < selectEnemyX;
            place(player, selectPlayerX, 0, faceRight, 0.3);
            place(enemyEntity, selectEnemyX, 0, !faceRight, enemyEntity.getBbWidth() / 2.0);
            updateAnimator(player, null, true);
            updateAnimator(enemyEntity, null, false);
        } else {
            place(player, engine.player.x, engine.player.y, engine.player.facingRight, 0.3);
            place(enemyEntity, engine.enemy.x, engine.enemy.y, engine.enemy.facingRight,
                    Math.min(0.6, enemyEntity.getBbWidth() * enemyRenderScale / 2.0));
            updateAnimator(player, engine.player, true);
            updateAnimator(enemyEntity, engine.enemy, false);
        }
    }

    private void place(LivingEntity e, float ax, float ay, boolean faceRight, double halfWidth) {
        if (e == null || e.isRemoved()) return;
        Vec3 w = arena.toWorld(ax, ay, halfWidth);
        e.setPos(w.x, w.y, w.z);
        e.xo = e.xOld = w.x;
        e.yo = e.yOld = w.y;
        e.zo = e.zOld = w.z;
        Vec3 face = arena.axis.scale(faceRight ? 1 : -1);
        Vec3 toCam = arena.normal.scale(camera.side());
        Vec3 look = face.scale(Math.cos(Math.toRadians(25))).add(toCam.scale(Math.sin(Math.toRadians(25)))).normalize();
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
        e.setYRot(yaw);
        e.yRotO = yaw;
        e.yBodyRot = e.yBodyRotO = yaw;
        e.yHeadRot = e.yHeadRotO = yaw;
        e.setXRot(0);
        e.xRotO = 0;
        e.setDeltaMovement(Vec3.ZERO);
        FightAnimator.state(e).facing = face;
    }

    public Vec3 worldOf(Fighter f) {
        return arena.toWorld(f.x, f.y, 0.3);
    }

    // ------------------------------------------------------------------ animation mapping

    private void updateAnimator(LivingEntity e, Fighter f, boolean isPlayer) {
        if (e == null) return;
        FightAnimator.State s = FightAnimator.state(e);
        s.time = time + (isPlayer ? 0 : 1.3f);
        s.bodyHeight = e.getBbHeight();
        s.scale = isPlayer ? playerRenderScale : enemyRenderScale;
        s.tilt = 0;
        s.flip = 0;
        s.forward = 0;
        s.up = 0;
        s.squash = 1f;
        s.attackAnim = -1f;
        s.aggressive = false;
        s.crouch = false;
        s.striking = false;
        s.progress = 0;
        boolean upright = FighterRenderer.isUpright(e);
        if (f == null) {
            if (stage == Stage.OUTRO && engine != null) {
                Fighter last = isPlayer ? engine.player : engine.enemy;
                boolean won = engine.isOver() && engine.matchWinner == last.index;
                s.pose = won ? FightAnimator.Pose.VICTORY : FightAnimator.Pose.GUARD;
                if (won) s.up = Math.abs(Mth.sin(time * 6f)) * 0.15f;
            } else {
                s.pose = FightAnimator.Pose.GUARD;
            }
            return;
        }
        float crouchRatio = f.stats.crouchHeight() / f.stats.hurtHeight();
        switch (f.state) {
            case IDLE -> s.pose = FightAnimator.Pose.GUARD;
            case WALK -> s.pose = FightAnimator.Pose.WALK;
            case CROUCH -> {
                s.pose = FightAnimator.Pose.CROUCH;
                s.crouch = true;
                if (!upright || !(e instanceof net.minecraft.world.entity.player.Player)) s.squash = Math.max(0.72f, crouchRatio);
            }
            case JUMP -> {
                s.pose = FightAnimator.Pose.JUMP;
                s.tilt = f.vy > 0 ? 8 : -6;
            }
            case LANDING -> {
                s.pose = FightAnimator.Pose.GUARD;
                s.squash = 0.88f;
            }
            case BLOCKSTUN -> {
                s.pose = FightAnimator.Pose.BLOCK;
                s.tilt = -8;
                if (f.input.down) {
                    s.crouch = true;
                    s.squash = 0.85f;
                }
            }
            case HITSTUN -> {
                s.pose = FightAnimator.Pose.HIT;
                if (f.launched && !upright) s.flip = -Math.min(180, 30 + f.stateAge * 8f);
                else s.tilt = f.launched ? -30 - Math.min(45, f.stateAge * 3) : -18 + (f.stateAge % 4 < 2 ? 3 : 0);
                if (f.wasCrouchingWhenHit && f.grounded()) s.squash = 0.8f;
            }
            case KNOCKDOWN -> {
                s.pose = FightAnimator.Pose.KNOCKDOWN;
                float t = f.stateTimer < 12 ? f.stateTimer / 12f : Math.min(1f, f.stateAge / 5f);
                if (upright) s.tilt = -90 * t;
                else s.flip = -180 * t;
            }
            case KO -> {
                s.pose = FightAnimator.Pose.KO;
                if (upright) s.tilt = f.grounded() ? -90 : -40 - Math.min(50, f.stateAge * 3);
                else s.flip = f.grounded() ? -180 : -Math.min(180, f.stateAge * 12f);
            }
            case VICTORY -> {
                s.pose = FightAnimator.Pose.VICTORY;
                s.up = Math.abs(Mth.sin(time * 6f)) * 0.15f;
            }
            case ATTACK -> attackPose(f, s, crouchRatio, upright);
        }
        if (f.state == State.IDLE && engine.phase == FightEngine.Phase.ROUND_END && engine.roundWinner == f.index) {
            s.pose = FightAnimator.Pose.VICTORY;
        }
        // flicker while invulnerable after getting up
        if (f.invuln > 0 && (f.invuln / 3) % 2 == 0) s.squash *= 0.98f;
    }

    private void attackPose(Fighter f, FightAnimator.State s, float crouchRatio, boolean upright) {
        Move m = f.move;
        if (m == null) return;
        int fr = Math.max(0, f.curFrame);
        boolean startup = fr < m.startup;
        boolean active = !startup && fr < f.activeEnd;
        s.striking = active;
        s.progress = Math.min(1f, fr / (float) Math.max(1, m.startup + Math.min(m.active, 10)));
        s.attackAnim = s.progress;
        if (m.crouching) {
            s.crouch = true;
            if (!upright) s.squash = Math.max(0.72f, crouchRatio);
        }
        AnimPose pose = m.pose;
        switch (pose) {
            case PUNCH -> {
                s.pose = m.crouching ? FightAnimator.Pose.PUNCH : FightAnimator.Pose.PUNCH;
                s.tilt = startup ? -4 : active ? 12 : 4;
                s.forward = active ? 0.12f : 0;
            }
            case KICK -> {
                s.pose = FightAnimator.Pose.KICK;
                s.tilt = startup ? 4 : active ? -16 : -6;
                s.forward = active ? 0.1f : 0;
            }
            case LUNGE -> {
                s.pose = FightAnimator.Pose.LUNGE;
                s.tilt = startup ? -6 : active ? 22 : 6;
                s.forward = active ? 0.15f : 0;
            }
            case UPPERCUT -> {
                s.pose = FightAnimator.Pose.UPPERCUT;
                s.squash = startup ? 0.85f : 1f;
                s.tilt = startup ? 10 : active ? -10 : -4;
            }
            case THROW -> {
                s.pose = FightAnimator.Pose.THROW;
                s.tilt = startup ? -14 : 14;
            }
            case SHOOT -> {
                s.pose = FightAnimator.Pose.SHOOT;
                s.aggressive = true;
                s.tilt = startup ? -4 : 2;
            }
            case SPIN, SLAM -> {
                s.pose = FightAnimator.Pose.SLAM;
                s.tilt = startup ? -14 : active ? 24 : 8;
            }
            case GRAB -> {
                s.pose = FightAnimator.Pose.GRAB;
                s.tilt = startup ? -4 : 16;
                s.forward = active ? 0.18f : 0;
            }
            case TAUNT -> {
                s.pose = FightAnimator.Pose.TAUNT;
                s.up = Math.abs(Mth.sin(fr * 0.4f)) * 0.18f;
            }
        }
        if (m.air) {
            s.tilt += 8;
            if (pose == AnimPose.KICK || pose == AnimPose.SLAM) s.pose = FightAnimator.Pose.KICK;
        }
    }

    // ------------------------------------------------------------------ camera

    private void updateCamera(float dt, LocalPlayer player) {
        float px, py, ex, ey;
        if (engine == null) {
            px = selectPlayerX;
            ex = selectEnemyX;
            py = ey = 0;
        } else {
            px = engine.player.x;
            ex = engine.enemy.x;
            py = engine.player.y;
            ey = engine.enemy.y;
        }
        Vec3 pw = arena.toWorld(px, py, 0.3), ew = arena.toWorld(ex, ey, 0.3);
        Vec3 focus = pw.add(ew).scale(0.5).add(0, 1.05, 0);
        double sep = Math.abs(px - ex) / Arena.UNITS_PER_BLOCK;
        double dist = Mth.clamp(2.6 + sep * 0.8, 4.2, 10.5);
        float roll = 0;
        if (engine == null) {
            dist = Math.max(dist, 7.5); // wider shot on the select screen so the panels don't cover anyone
            focus = focus.add(0, 0.35, 0);
        } else {
            if (engine.freeze > 0 && engine.freezeOwner >= 0) {
                Fighter user = engine.fighter(engine.freezeOwner);
                Vec3 uw = worldOf(user).add(0, 1.1, 0);
                focus = focus.lerp(uw, 0.75);
                dist *= 0.5;
                roll = (user.index == 0 ? -1 : 1) * 4f;
            } else if (engine.phase == FightEngine.Phase.KO && engine.phaseTimer < 100) {
                Fighter loser = engine.player.hp <= 0 ? engine.player : engine.enemy;
                focus = focus.lerp(worldOf(loser).add(0, 0.6, 0), 0.5);
                dist *= 0.75;
            } else if (engine.isOver() && engine.matchWinner >= 0) {
                focus = focus.lerp(worldOf(engine.fighter(engine.matchWinner)).add(0, 1.1, 0), 0.6);
                dist *= 0.7;
            }
        }
        float shake = engine == null || paused ? 0 : engine.shake;
        camera.update(focus, dist, shake, roll, dt, player);
    }

    // ------------------------------------------------------------------ server sync & results

    private void syncMob() {
        if (enemyEntity.isRemoved()) return;
        ModNetwork.sendToServer(new FightSyncPacket(enemyEntity.getX(), enemyEntity.getY(), enemyEntity.getZ(),
                enemyEntity.getYRot()));
    }

    public void sendResult(boolean won) {
        if (resultSent) return;
        resultSent = true;
        syncMob();
        int rw = engine != null ? engine.player.roundsWon : 0;
        int rl = engine != null ? engine.enemy.roundsWon : 0;
        boolean perfect = won && rl == 0 && engine != null && engine.player.hp >= engine.player.maxHp;
        ModNetwork.sendToServer(new FightResultPacket(won, rw, rl, perfect, false));
    }

    public void cancel() {
        if (!resultSent) {
            resultSent = true;
            ModNetwork.sendToServer(new FightResultPacket(false, 0, 0, false, true));
        }
    }

    /** The screen closed: fly the camera back to the player's eyes, then clean up. */
    public void leave() {
        Minecraft mc = Minecraft.getInstance();
        if (stage == Stage.OUTRO) return;
        if (!resultSent) {
            if (engine == null) cancel();
            else sendResult(engine.isOver() && engine.playerWon());
        }
        stage = Stage.OUTRO;
        syncMob();
        LocalPlayer p = mc.player;
        if (p == null) {
            cleanup();
            return;
        }
        // look at the opponent when we get our eyes back
        Vec3 to = enemyEntity.position().add(0, enemyEntity.getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        p.setYRot(yaw);
        p.yRotO = yaw;
        p.setXRot(pitch);
        p.xRotO = pitch;
        p.setNoGravity(false);
        camera.leave(p);
    }

    /** Immediately restores everything (also used on disconnect). */
    public void cleanup() {
        Minecraft mc = Minecraft.getInstance();
        FightCamera.reset();
        FightAnimator.clear();
        mc.options.hideGui = savedHideGui;
        mc.options.damageTiltStrength().set(savedDamageTilt);
        mc.options.bobView().set(savedBob);
        if (mc.player != null) mc.player.setNoGravity(false);
        if (current == this) current = null;
    }

    public static void abortAll() {
        if (current != null) {
            current.cancel();
            current.cleanup();
        }
    }
}
