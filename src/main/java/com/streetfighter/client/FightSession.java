package com.streetfighter.client;

import com.streetfighter.client.world.Arena;
import com.streetfighter.client.world.FightAnimator;
import com.streetfighter.client.world.FightCamera;
import com.streetfighter.client.world.WorldFx;
import com.streetfighter.fight.AIDifficulty;
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
    /** Brief FOV punch-in on big impacts (fraction of FOV). */
    public float fovKick;

    // dummies used to draw projectiles/assists with real models
    public LivingEntity golemDummy, pigDummy, goatDummy, zombieDummy, babyZombieDummy, cowDummy, donkeyDummy, creeperDummy;
    public net.minecraft.world.entity.animal.Chicken chickenDummy;
    /** The funny villager who throws props into the fight. */
    public net.minecraft.world.entity.npc.Villager propVillager;
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
        goatDummy = (LivingEntity) EntityType.GOAT.create(mc.level);
        zombieDummy = EntityType.ZOMBIE.create(mc.level);
        var baby = EntityType.ZOMBIE.create(mc.level);
        if (baby != null) baby.setBaby(true);
        babyZombieDummy = baby;
        cowDummy = EntityType.COW.create(mc.level);
        var donkey = EntityType.DONKEY.create(mc.level);
        if (donkey != null) donkey.setChest(true);
        donkeyDummy = donkey;
        creeperDummy = EntityType.CREEPER.create(mc.level);
        chickenDummy = EntityType.CHICKEN.create(mc.level);
        propVillager = EntityType.VILLAGER.create(mc.level);
        if (propVillager != null) {
            propVillager.setVillagerData(propVillager.getVillagerData()
                    .setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));
        }
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
        FighterAI ai = FighterAI.create(enemyType, difficulty);
        ai.passive = info.training() || System.getProperty("sf.passive") != null; // TEMP-TEST
        engine = new FightEngine(player, enemy, ai, difficulty, minX(), maxX(), startSeparation, arena);
        engine.training = info.training();
        stage = Stage.FIGHT;
        lastNanos = System.nanoTime();
        accumulator = 0;
        fx.fightStart();
    }

    public void setInput(FightInput in) {
        pendingInput = in;
    }

    /** Sub-step interpolation factor between the last two simulation frames (0..1). */
    public float alpha() {
        return stage == Stage.FIGHT && !paused && engine != null && engine.hitstop <= 0 && engine.freeze <= 0
                ? (float) Math.min(1.0, accumulator / STEP) : 1f;
    }

    public float renderX(Fighter f) {
        float a = alpha();
        return f.prevX + (f.x - f.prevX) * a;
    }

    public float renderY(Fighter f) {
        float a = alpha();
        return f.prevY + (f.y - f.prevY) * a;
    }

    /** Called once per rendered frame, before the world is drawn. */
    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            cleanup();
            return;
        }
        long now = System.nanoTime();
        float dt = (float) Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        time += dt;
        lastDt = dt;

        if (stage == Stage.FIGHT && !paused) {
            accumulator += dt * timeScale();
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
                if (pigDummy != null) pigDummy.walkAnimation.update(1.3f, 0.5f);
                if (goatDummy != null) goatDummy.walkAnimation.update(1.4f, 0.5f);
                if (zombieDummy != null) zombieDummy.walkAnimation.update(0.8f, 0.5f);
                if (cowDummy != null) cowDummy.walkAnimation.update(1.3f, 0.5f);
                if (donkeyDummy != null) donkeyDummy.walkAnimation.update(1.5f, 0.5f);
                if (creeperDummy != null) creeperDummy.walkAnimation.update(1.2f, 0.5f);
                // a creeper about to blow swells and flashes for real
                if (enemyEntity instanceof net.minecraft.world.entity.monster.Creeper cr) {
                    com.streetfighter.fight.Fighter ef = engine.enemy;
                    boolean fuse = ef.state == com.streetfighter.fight.Fighter.State.ATTACK && ef.move != null
                            && ef.move.selfDamage > 0 && ef.curFrame < ef.move.startup;
                    cr.setSwellDir(fuse ? 1 : -1);
                }
                if (babyZombieDummy != null) babyZombieDummy.walkAnimation.update(1.6f, 0.5f);
                if (chickenDummy != null) {
                    chickenDummy.oFlap = chickenDummy.flap;
                    chickenDummy.oFlapSpeed = chickenDummy.flapSpeed;
                    chickenDummy.flap += 0.9f;
                    chickenDummy.flapSpeed = 1f;
                }
                if (engine.isOver()) {
                    if (!resultSent) sendResult(engine.playerWon());
                    matchEndFrames++;
                }
            }
            if (steps == 6) accumulator = 0; // we fell far behind (lag spike): don't try to catch up
        } else if (stage == Stage.SELECT) {
            hud.step();
        }

        if (stage != Stage.OUTRO) {
            placeAll(player, dt);
            if (++syncTimer >= 6) {
                syncTimer = 0;
                syncMob();
            }
        }
        animate(player, enemyEntity, dt);
        fovKick = Math.max(0, fovKick - dt * 0.4f);
        fx.frame(dt);
        updateCamera(dt, player);
        float targetBox = stage == Stage.SELECT || stage == Stage.OUTRO
                || (engine != null && (engine.freeze > 0 || engine.phase == FightEngine.Phase.KO
                || (engine.phase == FightEngine.Phase.INTRO && engine.round == 1) || engine.isOver())) ? 1f : 0f;
        hud.letterbox += (targetBox - hud.letterbox) * Math.min(1f, dt * 6f);

        if (stage == Stage.OUTRO && camera.finishedLeaving()) cleanup();
    }

    private float lastDt = 1 / 60f;

    /** Called at the start of every client tick so the player's position packet carries the fight position. */
    public void tickPre() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || stage == Stage.OUTRO) return;
        placeAll(mc.player, 0);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.fallDistance = 0;
    }

    private final java.util.Map<Integer, Float> displayYaw = new java.util.HashMap<>();

    private void placeAll(LocalPlayer player, float dt) {
        if (engine == null) {
            boolean faceRight = selectPlayerX < selectEnemyX;
            place(player, selectPlayerX, 0, faceRight, 0.3, dt);
            place(enemyEntity, selectEnemyX, 0, !faceRight, enemyEntity.getBbWidth() / 2.0, dt);
        } else {
            place(player, renderX(engine.player), renderY(engine.player), engine.player.facingRight, 0.3, dt);
            place(enemyEntity, renderX(engine.enemy), renderY(engine.enemy), engine.enemy.facingRight,
                    Math.min(0.6, enemyEntity.getBbWidth() * enemyRenderScale / 2.0), dt);
        }
    }

    private void place(LivingEntity e, float ax, float ay, boolean faceRight, double halfWidth, float dt) {
        if (e == null || e.isRemoved()) return;
        Vec3 w = arena.toWorld(ax, ay, halfWidth);
        e.setPos(w.x, w.y, w.z);
        e.xo = e.xOld = w.x;
        e.yo = e.yOld = w.y;
        e.zo = e.zOld = w.z;
        Vec3 face = arena.axis.scale(faceRight ? 1 : -1);
        Vec3 toCam = arena.normal.scale(camera.side());
        Vec3 look = face.scale(Math.cos(Math.toRadians(25))).add(toCam.scale(Math.sin(Math.toRadians(25)))).normalize();
        float target = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
        // turn around smoothly instead of snapping 180 degrees when sides switch
        Float prev = displayYaw.get(e.getId());
        float yaw = target;
        if (prev != null && dt > 0) {
            float diff = Mth.wrapDegrees(target - prev);
            yaw = prev + diff * (1f - (float) Math.exp(-22f * dt));
        } else if (prev != null) {
            yaw = prev;
        }
        displayYaw.put(e.getId(), yaw);
        e.setYRot(yaw);
        e.yRotO = yaw;
        e.yBodyRot = e.yBodyRotO = yaw;
        e.yHeadRot = e.yHeadRotO = yaw;
        e.setXRot(0);
        e.xRotO = 0;
        e.setDeltaMovement(Vec3.ZERO);
        e.walkAnimation.setSpeed(0); // our own walk cycle drives the legs
    }

    public Vec3 worldOf(Fighter f) {
        return arena.toWorld(renderX(f), renderY(f), 0.3);
    }

    private void animate(LocalPlayer player, LivingEntity enemy, float dt) {
        boolean outro = stage == Stage.OUTRO;
        Fighter pf = engine != null && !outro ? engine.player : null;
        Fighter ef = engine != null && !outro ? engine.enemy : null;
        boolean pWin = outro && engine != null && engine.isOver() && engine.matchWinner == 0;
        boolean eWin = outro && engine != null && engine.isOver() && engine.matchWinner == 1;
        float a = alpha();
        boolean hs = engine != null && engine.hitstop > 0;
        float px = engine != null ? renderX(engine.player) : selectPlayerX;
        float ex = engine != null ? renderX(engine.enemy) : selectEnemyX;
        boolean pRight = engine != null ? engine.player.facingRight : selectPlayerX < selectEnemyX;
        boolean eRight = engine != null ? engine.enemy.facingRight : !pRight;
        FightAnimator.update(player, pf, new FightAnimator.Ctx(dt, time, a, hs, px / Arena.UNITS_PER_BLOCK,
                playerRenderScale, arena.axis.scale(pRight ? 1 : -1), pWin));
        if (enemy != null && !enemy.isRemoved()) {
            FightAnimator.update(enemy, ef, new FightAnimator.Ctx(dt, time + 1.3f, a, hs, ex / Arena.UNITS_PER_BLOCK,
                    enemyRenderScale, arena.axis.scale(eRight ? 1 : -1), eWin));
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
            px = renderX(engine.player);
            ex = renderX(engine.enemy);
            py = renderY(engine.player);
            ey = renderY(engine.enemy);
        }
        Vec3 pw = arena.toWorld(px, py, 0.3), ew = arena.toWorld(ex, ey, 0.3);
        Vec3 focus = pw.add(ew).scale(0.5).add(0, 1.05, 0);
        double sep = Math.abs(px - ex) / Arena.UNITS_PER_BLOCK;
        double dist = Mth.clamp(2.6 + sep * 0.8, 4.2, 10.5);
        float roll = 0, orbit = 0;
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
                orbit = (user.index == 0 ? -1 : 1) * (14f + 10f * (float) Math.sin(time * 1.4));
            } else if (lockedCinematic() != null) {
                Fighter user = lockedCinematic();
                Vec3 uw = worldOf(user).add(0, 0.9, 0);
                focus = focus.lerp(uw, 0.4);
                dist *= 0.62;
                roll = (float) Math.sin(time * 1.3) * 3f;
                orbit = (float) Math.sin(time * 0.8) * 20f;
            } else if (engine.phase == FightEngine.Phase.KO && engine.phaseTimer < 100) {
                Fighter loser = engine.player.hp <= 0 ? engine.player : engine.enemy;
                focus = focus.lerp(worldOf(loser).add(0, 0.6, 0), 0.5);
                dist *= engine.phaseTimer < 40 ? 0.55 : 0.75;
                orbit = (loser.index == 0 ? 1 : -1) * Math.min(22f, engine.phaseTimer * 0.6f);
            } else if (engine.isOver() && engine.matchWinner >= 0) {
                focus = focus.lerp(worldOf(engine.fighter(engine.matchWinner)).add(0, 1.1, 0), 0.6);
                dist *= 0.7;
            }
        }
        float shake = engine == null || paused ? 0 : engine.shake;
        camera.update(focus, dist, shake, roll, orbit, dt, player);
    }

    /** Slow motion for the K.O. moment (the first ~3/4 second of the K.O. phase plays at quarter speed). */
    /** The X-ray finisher playing right now, or null. */
    public XRayFinisher xray;

    public void startXRay(com.streetfighter.fight.Fighter winner, com.streetfighter.fight.Fighter loser) {
        if (!com.streetfighter.SFConfig.XRAY_FINISHERS.get()) return;
        xray = new XRayFinisher(winner.moves.type(), loser.body, winner.index == 0, time);
    }

    private float timeScale() {
        if (xray != null) {
            if (!xray.done(time)) return 0f; // time stops for the X-ray
            xray = null;
        }
        if (engine == null || paused) return 1f;
        if (engine.phase == FightEngine.Phase.KO && engine.phaseTimer < 40) return 0.28f;
        return 1f;
    }

    /** The fighter currently performing a cinematic (lock-on-hit) super that has connected, or null. */
    public Fighter lockedCinematic() {
        if (engine == null) return null;
        for (Fighter f : new Fighter[]{engine.player, engine.enemy}) {
            if (f.state == State.ATTACK && f.move != null && f.move.lockOnHit && f.moveConnected) return f;
        }
        return null;
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
