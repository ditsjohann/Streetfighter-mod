package com.streetfighter.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.AnimPose;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FightEvent;
import com.streetfighter.fight.FightInput;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.Fighter.State;
import com.streetfighter.fight.FighterStats;
import com.streetfighter.fight.FighterType;
import com.streetfighter.fight.Move;
import com.streetfighter.fight.Moveset;
import com.streetfighter.fight.Movesets;
import com.streetfighter.fight.Projectile;
import com.streetfighter.fight.ProjectileVisual;
import com.streetfighter.fight.ai.FighterAI;
import com.streetfighter.network.FightResultPacket;
import com.streetfighter.network.ModNetwork;
import com.streetfighter.network.OpenFightPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The 2D street fighting screen: character select, the fight itself, and the result.
 * The simulation lives in {@link FightEngine}; this class handles input, timing, sound and drawing.
 */
public class FightScreen extends Screen {
    public static final float UNITS_PER_BLOCK = 66f;
    private static final double STEP_SECONDS = 1.0 / 60.0;

    private enum Stage { SELECT, FIGHT }

    /** Maps arena coordinates to screen pixels for the current frame. */
    public record Mapper(float camX, float scale, float groundY, float shakeX, float shakeY) {
        public float sx(float x) {
            return (x - camX) * scale + shakeX;
        }

        public float sy(float y) {
            return groundY - y * scale + shakeY;
        }
    }

    private final OpenFightPacket info;
    private final FighterType enemyType;
    private final AIDifficulty difficulty;
    private final List<FighterType> styles = new ArrayList<>();
    private final List<String> unlocked;
    private final StageRenderer stage;
    private final FightFx fx = new FightFx();

    private Stage screenStage = Stage.SELECT;
    private int selected;
    private FightEngine engine;
    private FighterType playerStyle = FighterType.STEVE;
    private boolean paused;
    private boolean resultSent;
    private int matchEndFrames;
    private long lastNanos;
    private double accumulator;
    private float camX = -1;
    private float animTime;
    private int superBannerFrames;
    private String superBannerText = "";
    private int superBannerOwner;

    private LivingEntity enemyEntity;
    private LivingEntity golemDummy;
    private LivingEntity pigDummy;
    private float enemyRenderScale = 1f;
    private float playerRenderScale = 1f;

    // latched button presses (so very quick taps are never lost between frames)
    private boolean pLight, pHeavy, pSp1, pSp2, pSuper, pJump;
    private final FightInput input = new FightInput();

    public FightScreen(OpenFightPacket info) {
        super(Component.literal("Street Fight"));
        this.info = info;
        this.enemyType = FighterType.byId(info.enemyType());
        AIDifficulty d = AIDifficulty.byId(info.difficulty());
        this.difficulty = d == null ? AIDifficulty.NORMAL : d;
        this.unlocked = info.unlockedStyles();
        for (FighterType t : FighterType.values()) {
            if (t == FighterType.STEVE || t.learnable) styles.add(t);
        }
        this.stage = new StageRenderer(StageRenderer.Theme.forEnemy(enemyType));
    }

    private boolean isUnlocked(FighterType t) {
        return t == FighterType.STEVE || unlocked.contains(t.id);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (enemyEntity == null && mc.level != null) {
            Entity e = mc.level.getEntity(info.entityId());
            enemyEntity = e instanceof LivingEntity le ? le : createDummy(enemyType);
            golemDummy = (LivingEntity) EntityType.IRON_GOLEM.create(mc.level);
            pigDummy = (LivingEntity) EntityType.PIG.create(mc.level);
        }
    }

    private LivingEntity createDummy(FighterType type) {
        Minecraft mc = Minecraft.getInstance();
        EntityType<?> et = switch (type) {
            case SKELETON -> EntityType.SKELETON;
            case VILLAGER -> EntityType.VILLAGER;
            case PIG -> EntityType.PIG;
            default -> EntityType.ZOMBIE;
        };
        Entity e = et.create(mc.level);
        return e instanceof LivingEntity le ? le : null;
    }

    // ------------------------------------------------------------------ match setup

    private void startFight(FighterType style) {
        Minecraft mc = Minecraft.getInstance();
        playerStyle = style;

        Moveset pm = Movesets.get(style);
        FighterStats st = pm.stats();
        FighterStats body = Movesets.HUMANOID;
        FighterStats playerStats = new FighterStats(st.maxHp(), st.walkSpeed(), st.backSpeed(), st.jumpVy(), st.jumpVx(),
                st.gravity(), body.hurtWidth(), body.hurtHeight(), body.crouchHeight());
        String playerName = mc.player != null ? mc.player.getName().getString() : "Steve";
        Fighter player = new Fighter(0, playerName, FighterType.STEVE, pm, playerStats, 1f, 1f);
        playerRenderScale = mc.player != null ? body.hurtHeight() / (mc.player.getBbHeight() * UNITS_PER_BLOCK) : 1f;

        Moveset em = Movesets.get(enemyType);
        FighterStats es = em.stats();
        float bbH = enemyEntity != null ? Math.max(0.2f, enemyEntity.getBbHeight()) : 1.8f;
        float bbW = enemyEntity != null ? Math.max(0.2f, enemyEntity.getBbWidth()) : 0.6f;
        if (enemyType == FighterType.BRAWLER) {
            float h = clamp(bbH * UNITS_PER_BLOCK, 90, 190);
            float w = clamp(bbW * UNITS_PER_BLOCK, 36, 130);
            es = new FighterStats(es.maxHp(), 3.2f, 2.8f, 14.5f, 4.2f, 0.75f, w, h, h * 0.66f);
        }
        enemyRenderScale = es.hurtHeight() / (bbH * UNITS_PER_BLOCK);
        Fighter enemy = new Fighter(1, info.enemyName(), enemyType, em, es, info.hpScale(), info.damageScale());

        engine = new FightEngine(player, enemy, FighterAI.create(enemyType, difficulty), difficulty);
        screenStage = Stage.FIGHT;
        lastNanos = System.nanoTime();
        accumulator = 0;
        camX = engine.cameraX();
        fx.clear();
        play(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ input

    private boolean down(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }

    private void pollInput() {
        input.clear();
        input.left = down(GLFW.GLFW_KEY_A) || down(GLFW.GLFW_KEY_LEFT);
        input.right = down(GLFW.GLFW_KEY_D) || down(GLFW.GLFW_KEY_RIGHT);
        input.up = pJump || down(GLFW.GLFW_KEY_W) || down(GLFW.GLFW_KEY_UP) || down(GLFW.GLFW_KEY_SPACE);
        input.down = down(GLFW.GLFW_KEY_S) || down(GLFW.GLFW_KEY_DOWN);
        if (input.left && input.right) input.left = input.right = false;
        input.light = pLight;
        input.heavy = pHeavy;
        input.special1 = pSp1;
        input.special2 = pSp2;
        input.superMove = pSuper;
        pLight = pHeavy = pSp1 = pSp2 = pSuper = pJump = false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (screenStage == Stage.SELECT) return selectKey(key);

        if (engine.isOver()) {
            if (matchEndFrames > 45 && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE
                    || key == GLFW.GLFW_KEY_J)) {
                onClose();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_P) {
            paused = !paused;
            lastNanos = System.nanoTime();
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.5f);
            return true;
        }
        if (paused) {
            if (key == GLFW.GLFW_KEY_Q) {
                sendResult(false);
                onClose();
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_Z -> pLight = true;
            case GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_X -> pHeavy = true;
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
        switch (key) {
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> {
                selected = (selected + styles.size() - 1) % styles.size();
                play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
            }
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> {
                selected = (selected + 1) % styles.size();
                play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_J -> confirmSelection();
            case GLFW.GLFW_KEY_ESCAPE -> {
                // backing out before the first punch costs nothing
                resultSent = true;
                ModNetwork.sendToServer(new FightResultPacket(false, 0, 0, false, true));
                onClose();
            }
            default -> {
            }
        }
        return true;
    }

    private void confirmSelection() {
        FighterType t = styles.get(selected);
        if (isUnlocked(t)) {
            startFight(t);
        } else {
            play(SoundEvents.VILLAGER_NO, 1f, 0.6f);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (screenStage == Stage.SELECT) {
            int idx = cardAt(mx, my);
            if (idx >= 0) {
                if (idx == selected) confirmSelection();
                else {
                    selected = idx;
                    play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
                }
                return true;
            }
        } else if (engine != null && engine.isOver() && matchEndFrames > 45) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
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
        if (!resultSent) {
            if (screenStage == Stage.SELECT || engine == null) {
                resultSent = true;
                ModNetwork.sendToServer(new FightResultPacket(false, 0, 0, false, true));
            } else {
                sendResult(engine.isOver() && engine.playerWon());
            }
        }
    }

    private void sendResult(boolean won) {
        if (resultSent) return;
        resultSent = true;
        int rw = engine != null ? engine.player.roundsWon : 0;
        int rl = engine != null ? engine.enemy.roundsWon : 0;
        boolean perfect = won && rl == 0 && engine != null && engine.player.hp >= engine.player.maxHp;
        ModNetwork.sendToServer(new FightResultPacket(won, rw, rl, perfect, false));
    }

    // ------------------------------------------------------------------ simulation tick

    private void updateSimulation() {
        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1e9;
        lastNanos = now;
        if (paused || screenStage != Stage.FIGHT) return;
        accumulator += Math.min(dt, 0.25);
        int steps = 0;
        while (accumulator >= STEP_SECONDS && steps < 8) {
            accumulator -= STEP_SECONDS;
            steps++;
            pollInput();
            engine.step(input);
            processEvents();
            fx.step();
            animateEntities();
            float target = engine.cameraX();
            camX += (target - camX) * 0.15f;
            if (superBannerFrames > 0) superBannerFrames--;
            if (engine.isOver()) {
                if (!resultSent) sendResult(engine.playerWon());
                matchEndFrames++;
            }
        }
    }

    private void animateEntities() {
        Minecraft mc = Minecraft.getInstance();
        animateWalk(mc.player, engine.player);
        animateWalk(enemyEntity, engine.enemy);
        if (pigDummy != null) pigDummy.walkAnimation.update(1.2f, 0.5f);
        animTime += 1f / 60f;
    }

    private void animateWalk(LivingEntity e, Fighter f) {
        if (e == null) return;
        float speed = 0f;
        if (f.state == State.WALK) speed = Math.min(1f, Math.abs(f.vx) / Math.max(1f, f.stats.walkSpeed()));
        if (f.state == State.ATTACK && f.move != null && f.move.moveVx > 0 && f.grounded() && Math.abs(f.vx) > 1f) speed = 1.3f;
        e.walkAnimation.update(speed, 0.4f);
    }

    // ------------------------------------------------------------------ events -> sound & fx

    private void processEvents() {
        for (FightEvent ev : engine.events) handleEvent(ev);
        engine.events.clear();
    }

    private void handleEvent(FightEvent ev) {
        Fighter f = ev.fighter() >= 0 ? engine.fighter(ev.fighter()) : null;
        switch (ev.type()) {
            case ROUND -> play(SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1f);
            case FIGHT -> {
                play(SoundEvents.BELL_BLOCK, 1.2f, 0.8f);
                play(SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 0.8f);
            }
            case MOVE_START -> {
                if (f == null || f.move == null) break;
                Move m = f.move;
                if (m.callout != null) fx.text(f.x, f.y + f.hurtHeight() + 24, m.callout, f == engine.player ? 0xFF7FE0FF : 0xFFFF8A6A);
                if (m.special) play(specialSound(f), 1f, 0.9f);
                else play(m.damage >= 60 ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.1f, 0.5f);
            }
            case SUPER -> {
                if (f == null) break;
                superBannerFrames = 70;
                superBannerText = ev.text() == null ? "SUPER!" : ev.text();
                superBannerOwner = ev.fighter();
                fx.add(FightFx.Kind.FLASH, 0, 0, false);
                play(SoundEvents.PLAYER_LEVELUP, 0.6f, 1f);
                play(specialSound(f), 0.8f, 1f);
            }
            case HIT -> {
                fx.add(FightFx.Kind.SPARK, ev.x(), ev.y(), ev.heavy());
                play(ev.heavy() ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG, ev.heavy() ? 0.9f : 1.1f, 0.9f);
                LivingEntity victim = f == engine.player ? Minecraft.getInstance().player : enemyEntity;
                if (victim != null && Minecraft.getInstance().level != null) {
                    victim.handleDamageEvent(Minecraft.getInstance().level.damageSources().generic());
                }
                Fighter attacker = f != null ? engine.other(f) : null;
                if (attacker != null && attacker.move != null && attacker.move.isSuper && ev.heavy()
                        && attacker.moves.type() == FighterType.STEVE) {
                    fx.add(FightFx.Kind.EXPLOSION, ev.x(), ev.y(), true);
                    play(SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.7f);
                }
                if (attacker != null && attacker.move != null && attacker.move.level == com.streetfighter.fight.HitLevel.GRAB) {
                    play(SoundEvents.VILLAGER_TRADE, 1f, 1f);
                    fx.text(ev.x(), ev.y() + 40, "DEAL!", 0xFF5CFF7A);
                }
            }
            case BLOCK -> {
                fx.add(FightFx.Kind.BLOCK_SPARK, ev.x(), ev.y(), ev.heavy());
                play(SoundEvents.SHIELD_BLOCK, 1.2f, 0.7f);
            }
            case PROJECTILE -> {
                ProjectileVisual v = ev.visual();
                if (v == null) break;
                switch (v) {
                    case ARROW -> play(SoundEvents.SKELETON_SHOOT, 1f, 0.8f);
                    case DIRT, BONE -> play(SoundEvents.SNOWBALL_THROW, 0.8f, 0.8f);
                    case EMERALD -> play(SoundEvents.EGG_THROW, 1.2f, 0.8f);
                    case GOLEM -> play(SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 1f);
                    case PIG -> play(SoundEvents.PIG_AMBIENT, 0.8f, 1f);
                    default -> {
                    }
                }
            }
            case BOUNCE -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f, 0.5f);
            case POOF -> fx.add(FightFx.Kind.DUST, ev.x(), 0, false);
            case CLASH -> {
                fx.add(FightFx.Kind.CLASH, ev.x(), ev.y(), true);
                play(SoundEvents.PLAYER_ATTACK_CRIT, 1.5f, 0.8f);
            }
            case SLAM -> {
                fx.add(FightFx.Kind.SHOCKWAVE, ev.x(), 0, true);
                play(ev.visual() == ProjectileVisual.GOLEM ? SoundEvents.ANVIL_LAND : SoundEvents.GENERIC_BIG_FALL, 0.8f, 0.8f);
            }
            case LAND -> fx.add(FightFx.Kind.DUST, ev.x(), 0, false);
            case THUD -> {
                fx.add(FightFx.Kind.DUST, ev.x(), 0, true);
                play(SoundEvents.GENERIC_BIG_FALL, 0.9f, 0.8f);
            }
            case JUMP -> {
            }
            case KO -> {
                fx.add(FightFx.Kind.FLASH, 0, 0, false);
                play(SoundEvents.GENERIC_EXPLODE.value(), 1.3f, 0.5f);
            }
            case TIME -> play(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1f);
            case ROUND_WIN -> {
                if (ev.fighter() == engine.player.index) play(SoundEvents.PLAYER_LEVELUP, 1f, 0.7f);
                else play(SoundEvents.VILLAGER_NO, 0.8f, 0.7f);
            }
            case DRAW -> play(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f, 1f);
            case PERFECT -> play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.7f);
            case MATCH_WIN -> {
                if (ev.fighter() == engine.player.index) play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                else play(SoundEvents.ITEM_BREAK, 0.7f, 0.8f);
            }
        }
    }

    private SoundEvent specialSound(Fighter f) {
        if (f == engine.player) return SoundEvents.PLAYER_ATTACK_SWEEP;
        return switch (enemyType) {
            case SKELETON -> SoundEvents.SKELETON_AMBIENT;
            case VILLAGER -> SoundEvents.VILLAGER_AMBIENT;
            case PIG -> SoundEvents.PIG_AMBIENT;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the stage is our background
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateSimulation();
        if (screenStage == Stage.SELECT) {
            animTime += 0.016f;
            renderSelect(g, mouseX, mouseY);
            return;
        }
        float s = width / FightEngine.VIEW_WIDTH;
        float groundY = height * 0.84f;
        float shakeX = 0, shakeY = 0;
        if (engine.shake > 0 && !paused) {
            shakeX = (float) (Math.random() * 2 - 1) * engine.shake * s;
            shakeY = (float) (Math.random() * 2 - 1) * engine.shake * s * 0.6f;
        }
        Mapper m = new Mapper(camX, s, groundY, shakeX, shakeY);

        stage.render(g, m, width, height, animTime);
        if (engine.freeze > 0) g.fill(0, 0, width, height, 0x99000010);

        renderShadow(g, m, engine.player);
        renderShadow(g, m, engine.enemy);
        renderProjectileWarnings(g, m);

        // the attacking fighter is drawn last so its strike is on top
        Fighter first = engine.player.state == State.ATTACK ? engine.enemy : engine.player;
        Fighter second = engine.other(first);
        renderFighter(g, m, first);
        renderFighter(g, m, second);

        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        renderProjectiles(g, m);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        fx.render(g, font, m, width, height);
        renderHud(g);
        renderAnnouncer(g);
        if (superBannerFrames > 0) renderSuperBanner(g);
        if (paused) renderPause(g);
        if (engine.isOver() && matchEndFrames > 30) renderResult(g);
        g.pose().popPose();
    }

    private void renderShadow(GuiGraphics g, Mapper m, Fighter f) {
        float s = m.scale();
        float w = f.hurtWidth() * (1f - Math.min(0.6f, f.y / 300f));
        int x0 = (int) m.sx(f.x - w / 2f), x1 = (int) m.sx(f.x + w / 2f);
        int y = (int) m.sy(0);
        int h = Math.max(2, (int) (6 * s));
        g.fill(x0, y - h / 2, x1, y + h / 2, 0x55000000);
        g.fill(x0 + (x1 - x0) / 6, y - h, x1 - (x1 - x0) / 6, y + h, 0x30000000);
    }

    private void renderFighter(GuiGraphics g, Mapper m, Fighter f) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity entity = f == engine.player ? mc.player : enemyEntity;
        float renderScale = f == engine.player ? playerRenderScale : enemyRenderScale;
        float pxPerBlock = UNITS_PER_BLOCK * renderScale * m.scale();
        FighterRenderer.Pose pose = poseFor(f, m.scale());
        float x = m.sx(f.x), y = m.sy(f.y);

        if (entity == null) {
            int w = (int) (f.hurtWidth() * m.scale()), h = (int) (f.hurtHeight() * m.scale());
            g.fill((int) x - w / 2, (int) y - h, (int) x + w / 2, (int) y, f == engine.player ? 0xFF3A7BD5 : 0xFFD5503A);
            return;
        }
        // flashing while invulnerable after getting up
        if (f.invuln > 0 && (f.invuln / 3) % 2 == 0) return;
        FighterRenderer.render(g, entity, x, y, pxPerBlock, f.facingRight, pose);

        if (f.state == State.BLOCKSTUN || (f.canBlock() && f.holdingBack() && engine.other(f).isAttackingWithin(4))) {
            renderGuard(g, m, f);
        }
    }

    private FighterRenderer.Pose poseFor(Fighter f, float s) {
        FighterRenderer.Pose p = new FighterRenderer.Pose();
        float crouchRatio = f.stats.crouchHeight() / f.stats.hurtHeight();
        switch (f.state) {
            case CROUCH -> p.squashY = crouchRatio;
            case JUMP -> p.tilt = f.vy > 0 ? 6 : -4;
            case LANDING -> p.squashY = 0.9f;
            case HITSTUN -> {
                if (f.launched && !upright(f)) p.flip = -Math.min(180, 30 + f.stateAge * 8f);
                else p.tilt = f.launched ? -35 - Math.min(40, f.stateAge * 2) : -16;
                p.offsetX = (f.stateAge % 4 < 2 ? -1.5f : 1.5f) * s;
                if (f.wasCrouchingWhenHit && f.grounded()) p.squashY = crouchRatio;
            }
            case BLOCKSTUN -> {
                p.tilt = -7;
                if (f.input.down) p.squashY = crouchRatio;
            }
            case KNOCKDOWN -> {
                float t = f.stateTimer < 12 ? f.stateTimer / 12f : Math.min(1f, f.stateAge / 6f);
                if (upright(f)) p.tilt = -90 * t;
                else p.flip = -180 * t;
            }
            case KO -> {
                if (upright(f)) p.tilt = f.grounded() ? -90 : -40 - Math.min(50, f.stateAge * 3);
                else p.flip = f.grounded() ? -180 : -Math.min(180, f.stateAge * 12f);
            }
            case VICTORY -> {
                p.offsetY = Math.abs((float) Math.sin(animTime * 8)) * 10 * s;
                p.attackAnim = (animTime * 2) % 1f;
            }
            case ATTACK -> poseAttack(f, p, s, crouchRatio);
            default -> {
            }
        }
        if (f.state != State.VICTORY && engine.phase == FightEngine.Phase.ROUND_END && f.state == State.IDLE
                && engine.roundWinner == f.index) {
            p.offsetY = Math.abs((float) Math.sin(animTime * 8)) * 10 * s;
        }
        return p;
    }

    /** Humanoid bodies fall flat on their back; four-legged ones flip belly-up. */
    private boolean upright(Fighter f) {
        LivingEntity e = f == engine.player ? Minecraft.getInstance().player : enemyEntity;
        return e == null || FighterRenderer.isUpright(e);
    }

    private void poseAttack(Fighter f, FighterRenderer.Pose p, float s, float crouchRatio) {
        Move m = f.move;
        if (m == null) return;
        int fr = Math.max(0, f.curFrame);
        boolean startup = fr < m.startup;
        boolean active = !startup && fr < f.activeEnd;
        float swing = Math.min(1f, fr / (float) Math.max(1, m.startup + Math.min(m.active, 10)));
        if (m.crouching) p.squashY = crouchRatio;
        AnimPose pose = m.pose;
        switch (pose) {
            case PUNCH -> {
                p.tilt = startup ? -4 : active ? 12 : 4;
                p.offsetX = active ? 6 * s : 0;
                p.attackAnim = swing;
            }
            case KICK -> {
                p.tilt = startup ? 4 : active ? -20 : -6;
                p.offsetX = active ? 8 * s : 0;
                p.attackAnim = swing;
            }
            case LUNGE -> {
                p.tilt = startup ? -6 : active ? 22 : 6;
                p.offsetX = active ? 10 * s : 0;
                p.attackAnim = swing;
            }
            case UPPERCUT -> {
                p.squashY = startup ? Math.min(p.squashY, 0.82f) : p.squashY;
                p.tilt = startup ? 10 : active ? -14 : -4;
                p.spin = active ? (fr - m.startup) * 24f : 0;
                p.attackAnim = swing;
            }
            case THROW -> {
                p.tilt = startup ? -16 : 16;
                p.attackAnim = swing;
            }
            case SHOOT -> {
                p.tilt = startup ? -5 : 3;
                p.aggressive = true;
                p.attackAnim = swing;
            }
            case SPIN -> p.spin = active ? (fr - m.startup) * 30f : 0;
            case SLAM -> {
                p.tilt = startup ? -16 : active ? 26 : 10;
                p.attackAnim = swing;
            }
            case GRAB -> {
                p.tilt = startup ? -4 : 18;
                p.offsetX = active ? 12 * s : 0;
                p.attackAnim = swing;
            }
            case TAUNT -> {
                p.offsetY = Math.abs((float) Math.sin(fr * 0.4f)) * 12 * s;
                p.attackAnim = (fr % 20) / 20f;
            }
        }
        if (m.air) p.tilt += 8;
    }

    private void renderGuard(GuiGraphics g, Mapper m, Fighter f) {
        float s = m.scale();
        float gx = m.sx(f.x + f.dir() * (f.hurtWidth() / 2f + 8));
        float top = m.sy(f.y + f.hurtHeight()), bottom = m.sy(f.y);
        int w = Math.max(2, (int) (4 * s));
        int col = f.state == State.BLOCKSTUN ? 0xC06FD8FF : 0x606FD8FF;
        g.fill((int) gx - w / 2, (int) top, (int) gx + w / 2, (int) bottom, col);
    }

    private void renderProjectileWarnings(GuiGraphics g, Mapper m) {
        for (Projectile p : engine.projectiles) {
            if (!p.spec.fromSky || p.landed) continue;
            boolean blink = (p.delay / 4) % 2 == 0;
            float x = m.sx(p.x);
            float y = m.sy(0);
            int r = (int) ((p.spec.width / 2f) * m.scale());
            if (p.delay > 0 || p.y > 150) {
                g.fill((int) x - r, (int) y - 3, (int) x + r, (int) y + 2, blink ? 0xA0FF3030 : 0x60FF3030);
            }
        }
    }

    private void renderProjectiles(GuiGraphics g, Mapper m) {
        float s = m.scale();
        for (Projectile p : engine.projectiles) {
            if (p.delay > 0) continue;
            float cx = m.sx(p.x);
            switch (p.spec.visual) {
                case GOLEM -> {
                    if (golemDummy == null) break;
                    FighterRenderer.Pose pose = new FighterRenderer.Pose();
                    pose.attackAnim = p.landed ? Math.min(1f, p.landedTimer / 8f) : 0f;
                    Fighter target = engine.fighter(1 - p.owner);
                    float scale = UNITS_PER_BLOCK * (p.spec.height / (golemDummy.getBbHeight() * UNITS_PER_BLOCK)) * s;
                    g.pose().pushPose();
                    g.pose().translate(0, 0, -150);
                    FighterRenderer.render(g, golemDummy, cx, m.sy(p.y), scale, target.x >= p.x, pose);
                    g.pose().popPose();
                }
                case PIG -> {
                    if (pigDummy == null) break;
                    float scale = UNITS_PER_BLOCK * (p.spec.height / (pigDummy.getBbHeight() * UNITS_PER_BLOCK)) * s;
                    g.pose().pushPose();
                    g.pose().translate(0, 0, -150);
                    FighterRenderer.render(g, pigDummy, cx, m.sy(p.y), scale, p.vx >= 0, new FighterRenderer.Pose());
                    g.pose().popPose();
                }
                default -> {
                    ItemStack stack = switch (p.spec.visual) {
                        case ARROW -> new ItemStack(Items.ARROW);
                        case DIRT -> new ItemStack(Items.DIRT);
                        case EMERALD -> new ItemStack(Items.EMERALD);
                        case BONE -> new ItemStack(Items.BONE);
                        default -> new ItemStack(Items.SNOWBALL);
                    };
                    float cy = m.sy(p.y);
                    float rot;
                    if (p.spec.visual == ProjectileVisual.ARROW) {
                        double ang = Math.toDegrees(Math.atan2(-p.vy, p.vx));
                        rot = (float) ang + 45f;
                    } else {
                        rot = p.spec.spin ? p.age * 14f * (p.vx >= 0 ? 1 : -1) : 0;
                    }
                    float size = Math.max(p.spec.width, p.spec.height) * s * 1.25f;
                    float k = size / 16f;
                    g.pose().pushPose();
                    g.pose().translate(cx, cy, 0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
                    g.pose().scale(k, k, k);
                    g.renderItem(stack, -8, -8);
                    g.pose().popPose();
                }
            }
        }
    }

    // ------------------------------------------------------------------ HUD

    private void renderHud(GuiGraphics g) {
        int barW = Math.max(80, width / 2 - 46);
        int barH = Math.max(8, height / 40);
        int top = 8;
        int cx = width / 2;
        healthBar(g, engine.player, cx - 26 - barW, top, barW, barH, false);
        healthBar(g, engine.enemy, cx + 26, top, barW, barH, true);

        // names
        g.drawString(font, engine.player.name.toUpperCase(), cx - 26 - barW, top + barH + 4, 0xFFFFFFFF, true);
        String styleTag = playerStyle == FighterType.STEVE ? "" : " [" + playerStyle.displayName + " style]";
        if (!styleTag.isEmpty()) g.drawString(font, styleTag, cx - 26 - barW + font.width(engine.player.name.toUpperCase()), top + barH + 4, 0xFF9CDCFF, true);
        String en = engine.enemy.name.toUpperCase();
        g.drawString(font, en, cx + 26 + barW - font.width(en), top + barH + 4, 0xFFFFFFFF, true);

        // round markers
        for (int i = 0; i < FightEngine.ROUNDS_TO_WIN; i++) {
            int x = cx - 34 - i * 10;
            g.fill(x - 3, top + barH + 16, x + 4, top + barH + 23, 0xFF000000);
            g.fill(x - 2, top + barH + 17, x + 3, top + barH + 22, i < engine.player.roundsWon ? 0xFFFFD21A : 0xFF3A3A3A);
            int x2 = cx + 34 + i * 10;
            g.fill(x2 - 4, top + barH + 16, x2 + 3, top + barH + 23, 0xFF000000);
            g.fill(x2 - 3, top + barH + 17, x2 + 2, top + barH + 22, i < engine.enemy.roundsWon ? 0xFFFFD21A : 0xFF3A3A3A);
        }

        // timer
        int secs = (int) Math.ceil(engine.timer / 60f);
        String t = String.valueOf(Math.min(99, secs));
        g.fill(cx - 20, top - 4, cx + 20, top + 24, 0xC0000000);
        g.pose().pushPose();
        g.pose().translate(cx, top, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(font, t, 0, 0, secs <= 10 ? 0xFFFF5544 : 0xFFFFE070);
        g.pose().popPose();
        String diff = difficulty == AIDifficulty.HARD ? "HARD" : "NORMAL";
        g.drawCenteredString(font, diff, cx, top + 26, difficulty == AIDifficulty.HARD ? 0xFFFF6B5B : 0xFF8BEA8B);

        // super meters
        int mw = Math.max(60, width / 4);
        int mh = Math.max(6, barH - 2);
        int my = height - mh - 10;
        superMeter(g, engine.player, 10, my, mw, mh, false);
        superMeter(g, engine.enemy, width - 10 - mw, my, mw, mh, true);

        // combo counters
        combo(g, engine.player, false);
        combo(g, engine.enemy, true);

        if (engine.phase == FightEngine.Phase.INTRO || (engine.phase == FightEngine.Phase.FIGHTING && engine.round == 1
                && engine.timer > FightEngine.ROUND_FRAMES - 60 * 6)) {
            List<String> lines = wrap(List.of("A/D move", "W jump", "S crouch", "hold back = block", "J light", "K heavy",
                    "L / I specials", "O super", "ESC pause"), width - 24);
            int y = top + barH + 40;
            for (String line : lines) {
                int hw = font.width(line);
                g.fill(cx - hw / 2 - 4, y - 2, cx + hw / 2 + 4, y + 9, 0x90000000);
                g.drawCenteredString(font, line, cx, y, 0xFFDDDDDD);
                y += 11;
            }
        }
    }

    /** Joins words with spacing into as few lines as fit in {@code maxWidth}. */
    private List<String> wrap(List<String> parts, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String part : parts) {
            String candidate = cur.length() == 0 ? part : cur + "   " + part;
            if (font.width(candidate) > maxWidth && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(part);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void healthBar(GuiGraphics g, Fighter f, int x, int y, int w, int h, boolean rightSide) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFF5A0E0E);
        float lag = f.displayHp / f.maxHp, cur = f.hp / f.maxHp;
        int lagW = (int) (w * lag), curW = (int) (w * cur);
        boolean danger = cur < 0.25f;
        int col = danger ? ((int) (animTime * 6) % 2 == 0 ? 0xFFFF7A1A : 0xFFFFC21A) : 0xFFFFD21A;
        if (!rightSide) {
            g.fill(x + w - lagW, y, x + w, y + h, 0xFFFFFFFF);
            g.fill(x + w - curW, y, x + w, y + h, col);
            g.fill(x + w - curW, y, x + w, y + Math.max(1, h / 3), 0x60FFFFFF);
        } else {
            g.fill(x, y, x + lagW, y + h, 0xFFFFFFFF);
            g.fill(x, y, x + curW, y + h, col);
            g.fill(x, y, x + curW, y + Math.max(1, h / 3), 0x60FFFFFF);
        }
    }

    private void superMeter(GuiGraphics g, Fighter f, int x, int y, int w, int h, boolean rightSide) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFF141A3A);
        int fw = (int) (w * Math.min(1f, f.meter / 100f));
        boolean full = f.meter >= 100;
        int col = full ? ((int) (animTime * 8) % 2 == 0 ? 0xFF5CE1FF : 0xFFFFFFFF) : 0xFF2F7BFF;
        if (rightSide) g.fill(x + w - fw, y, x + w, y + h, col);
        else g.fill(x, y, x + fw, y + h, col);
        String label = full ? (f == engine.player ? "SUPER READY! [O]" : "SUPER READY!") : "SUPER";
        int lx = rightSide ? x + w - font.width(label) : x;
        g.drawString(font, label, lx, y - 11, full ? 0xFF8FEBFF : 0xFFB0B8D0, true);
    }

    private void combo(GuiGraphics g, Fighter attacker, boolean rightSide) {
        if (attacker.comboShown < 2 || attacker.comboTimer <= 0) return;
        String text = attacker.comboShown + " HITS!";
        float sc = 2f;
        int x = rightSide ? width - 16 : 16;
        int y = height / 3;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1f);
        int w = font.width(text);
        g.drawString(font, text, rightSide ? -w : 0, 0, 0xFFFFB020, true);
        g.pose().popPose();
    }

    private void bigText(GuiGraphics g, String text, float scale, int color, int y) {
        g.pose().pushPose();
        g.pose().translate(width / 2f, y, 0);
        g.pose().scale(scale, scale, 1f);
        int w = font.width(text);
        g.drawString(font, text, -w / 2, -4, 0xFF000000, false);
        g.drawString(font, text, -w / 2 + 1, -4 + 1, 0xFF000000, false);
        g.drawString(font, text, -w / 2, -5, color, false);
        g.pose().popPose();
    }

    private void renderAnnouncer(GuiGraphics g) {
        int t = engine.phaseTimer;
        int cy = (int) (height * 0.38f);
        float pop = Math.max(1f, 1.6f - t * 0.06f);
        switch (engine.phase) {
            case INTRO -> {
                if (t > 10) {
                    boolean finalRound = engine.player.roundsWon == FightEngine.ROUNDS_TO_WIN - 1
                            && engine.enemy.roundsWon == FightEngine.ROUNDS_TO_WIN - 1;
                    bigText(g, finalRound ? "FINAL ROUND" : "ROUND " + engine.round, 4f * Math.max(1f, 1.6f - (t - 10) * 0.06f), 0xFFFFE070, cy);
                    if (engine.round == 1) {
                        bigText(g, stage.theme().title.toUpperCase(), 1.3f, 0xFFDDDDDD, cy + 34);
                    }
                }
            }
            case FIGHT_CALL -> bigText(g, "FIGHT!", 5f * pop, 0xFFFF4A2A, cy);
            case KO -> bigText(g, engine.timeOver ? "TIME" : "K.O.", 6f * pop, 0xFFFF3A2A, cy);
            case ROUND_END -> {
                String text;
                int col;
                if (engine.roundWinner == -1) {
                    text = "DRAW";
                    col = 0xFFDDDDDD;
                } else if (engine.roundWinner == engine.player.index) {
                    text = "YOU WIN";
                    col = 0xFF5CFF7A;
                } else {
                    text = engine.enemy.name.toUpperCase() + " WINS";
                    col = 0xFFFF6A5A;
                }
                bigText(g, text, 3.5f, col, cy);
                if (engine.roundWinner >= 0 && engine.fighter(engine.roundWinner).hp >= engine.fighter(engine.roundWinner).maxHp) {
                    bigText(g, "PERFECT", 2.2f, 0xFFFFD21A, cy + 36);
                }
            }
            default -> {
            }
        }
    }

    private void renderSuperBanner(GuiGraphics g) {
        float p = 1f - superBannerFrames / 70f;
        int bandH = Math.max(24, height / 9);
        int y = (int) (height * 0.55f);
        g.fill(0, y, width, y + bandH, 0xC0101030);
        g.fill(0, y, width, y + 2, 0xFFFFD21A);
        g.fill(0, y + bandH - 2, width, y + bandH, 0xFFFFD21A);
        boolean left = superBannerOwner == engine.player.index;
        float slide = Math.min(1f, p * 4f);
        int tx = left ? (int) (-200 + slide * (width * 0.25f + 200)) : (int) (width + 200 - slide * (width * 0.25f + 200));
        g.pose().pushPose();
        g.pose().translate(left ? tx : tx, y + bandH / 2f, 0);
        g.pose().scale(2.5f, 2.5f, 1f);
        int w = font.width(superBannerText);
        g.drawString(font, superBannerText, left ? 0 : -w, -4, 0xFFFFE070, true);
        g.pose().popPose();
    }

    private void renderPause(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xA0000000);
        bigText(g, "PAUSED", 4f, 0xFFFFFFFF, height / 2 - 30);
        g.drawCenteredString(font, "ESC - resume        Q - forfeit (counts as a loss)", width / 2, height / 2 + 6, 0xFFDDDDDD);
        String moves = movesSummary(engine.player.moves);
        int y = height / 2 + 26;
        for (String line : moves.split("\n")) {
            g.drawCenteredString(font, line, width / 2, y, 0xFFB8C8FF);
            y += 11;
        }
    }

    private void renderResult(GuiGraphics g) {
        boolean won = engine.playerWon();
        g.fill(0, (int) (height * 0.28f), width, (int) (height * 0.72f), 0xB0000000);
        bigText(g, won ? "VICTORY!" : "DEFEAT", 5f, won ? 0xFFFFD21A : 0xFFFF4A3A, (int) (height * 0.38f));
        String score = engine.player.roundsWon + " - " + engine.enemy.roundsWon + "  vs  " + engine.enemy.name;
        g.drawCenteredString(font, score, width / 2, (int) (height * 0.47f), 0xFFFFFFFF);
        int y = (int) (height * 0.53f);
        if (won) {
            if (enemyType.learnable && !unlocked.contains(enemyType.id)) {
                bigText(g, "NEW STYLE LEARNED: " + enemyType.displayName.toUpperCase() + " - " + enemyType.title.toUpperCase(), 1.4f,
                        (int) (animTime * 6) % 2 == 0 ? 0xFF7FE0FF : 0xFFFFFFFF, y + 4);
            } else if (enemyType.learnable) {
                g.drawCenteredString(font, "You already know the " + enemyType.displayName + " style. Respect earned!", width / 2, y, 0xFFB8C8FF);
            } else {
                g.drawCenteredString(font, "Another mob bested. Beat Skeletons, Pigs and Villagers to learn their styles!", width / 2, y, 0xFFB8C8FF);
            }
        } else {
            g.drawCenteredString(font, "You'll be bruised for a while... train up and challenge them again!", width / 2, y, 0xFFFFB0A0);
        }
        if (matchEndFrames > 45 && (matchEndFrames / 20) % 2 == 0) {
            g.drawCenteredString(font, "Press SPACE to continue", width / 2, (int) (height * 0.65f), 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ character select

    private int cardSize() {
        return (int) clamp(Math.min(width / 9f, height / 6.5f), 24, 64);
    }

    private int cardsX() {
        int cs = cardSize();
        int total = styles.size() * cs + (styles.size() - 1) * 6;
        return width / 2 - total / 2;
    }

    private int cardsY() {
        return (int) (height * 0.50f);
    }

    private int cardAt(double mx, double my) {
        int cs = cardSize();
        int x0 = cardsX(), y0 = cardsY();
        for (int i = 0; i < styles.size(); i++) {
            int x = x0 + i * (cs + 6);
            if (mx >= x && mx < x + cs && my >= y0 && my < y0 + cs) return i;
        }
        return -1;
    }

    private ItemStack iconFor(FighterType t) {
        return switch (t) {
            case STEVE -> new ItemStack(Items.DIAMOND_PICKAXE);
            case SKELETON -> new ItemStack(Items.BOW);
            case VILLAGER -> new ItemStack(Items.EMERALD);
            case PIG -> new ItemStack(Items.CARROT);
            default -> new ItemStack(Items.ROTTEN_FLESH);
        };
    }

    private static String styleBlurb(FighterType t) {
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
                + "Air J " + ms.airLight().name + "   Air K " + ms.airHeavy().name + "\n"
                + "↓↘→+J or L: " + ms.special1().name + "     ↓↙←+J or I: " + ms.special2().name + "\n"
                + "O (full SUPER meter): " + ms.superMove().name;
    }

    private void renderSelect(GuiGraphics g, int mouseX, int mouseY) {
        float s = width / FightEngine.VIEW_WIDTH;
        Mapper m = new Mapper(FightEngine.ARENA_WIDTH / 2f - FightEngine.VIEW_WIDTH / 2f, s, height * 0.84f, 0, 0);
        stage.render(g, m, width, height, animTime);
        g.fill(0, 0, width, height, 0x88000000);

        float titleScale = width >= 420 ? 2f : 1.4f;
        bigText(g, "CHOOSE YOUR FIGHTING STYLE", titleScale, 0xFFFFD21A, 10);
        int subY = 10 + (int) (9 * titleScale);
        g.drawCenteredString(font, "A/D choose   ENTER fight   ESC back out   |   AI: " + difficulty.displayName.toUpperCase(),
                width / 2, subY, 0xFFBBBBBB);

        Minecraft mc = Minecraft.getInstance();
        FighterType sel = styles.get(selected);
        float bob = (float) Math.sin(animTime * 3) * 2;
        int cs = cardSize();
        int y0 = cardsY();

        // previews either side of the cards
        float feetY = y0 + cs * 0.9f;
        float previewH = Math.max(30, feetY - subY - 18);
        if (mc.player != null) {
            FighterRenderer.Pose pose = new FighterRenderer.Pose();
            pose.offsetY = bob;
            FighterRenderer.render(g, mc.player, width * 0.16f, feetY, previewH / mc.player.getBbHeight(), true, pose);
        }
        if (enemyEntity != null) {
            FighterRenderer.Pose pose = new FighterRenderer.Pose();
            pose.offsetY = -bob;
            float h = Math.max(1.8f, Math.min(3.0f, Math.max(enemyEntity.getBbHeight(), enemyEntity.getBbWidth() * 1.6f)));
            FighterRenderer.render(g, enemyEntity, width * 0.84f, feetY, previewH / h, false, pose);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        bigText(g, "VS", width >= 420 ? 3f : 2f, 0xFFFF4A2A, (int) (subY + 12 + (y0 - subY - 12) / 2f));
        g.drawCenteredString(font, info.enemyName(), (int) (width * 0.84f), (int) feetY + 4, 0xFFFF8A6A);
        g.drawCenteredString(font, mc.player != null ? mc.player.getName().getString() : "You", (int) (width * 0.16f),
                (int) feetY + 4, 0xFF7FE0FF);

        // cards
        int x0 = cardsX();
        for (int i = 0; i < styles.size(); i++) {
            FighterType t = styles.get(i);
            int x = x0 + i * (cs + 6);
            boolean isSel = i == selected;
            boolean hover = mouseX >= x && mouseX < x + cs && mouseY >= y0 && mouseY < y0 + cs;
            boolean unlockedT = isUnlocked(t);
            int border = isSel ? ((int) (animTime * 4) % 2 == 0 ? 0xFFFFD21A : 0xFFFFFFFF) : hover ? 0xFFAAAAAA : 0xFF444444;
            g.fill(x - 2, y0 - 2, x + cs + 2, y0 + cs + 2, border);
            g.fill(x, y0, x + cs, y0 + cs, unlockedT ? 0xFF22304A : 0xFF1A1A1A);
            float k = cs / 16f * 0.6f;
            g.pose().pushPose();
            g.pose().translate(x + cs / 2f, y0 + cs / 2f - 3, 0);
            g.pose().scale(k, k, 1f);
            g.renderItem(iconFor(t), -8, -8);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (!unlockedT) {
                g.fill(x, y0, x + cs, y0 + cs, 0xB0000000);
                g.drawCenteredString(font, "LOCKED", x + cs / 2, y0 + cs / 2 - 8, 0xFFFF6A5A);
            }
            float ts = Math.min(1f, (cs - 4) / (float) font.width(t.displayName));
            g.pose().pushPose();
            g.pose().translate(x + cs / 2f, y0 + cs - 9 * ts - 1, 0);
            g.pose().scale(ts, ts, 1f);
            g.drawCenteredString(font, t.displayName, 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
            g.pose().popPose();
        }

        // selected style info under the cards
        boolean open = isUnlocked(sel);
        int y = y0 + cs + 8;
        g.drawCenteredString(font, sel.displayName.toUpperCase() + " STYLE  -  " + sel.title, width / 2, y, open ? 0xFF7FE0FF : 0xFF888888);
        y += 11;
        g.drawCenteredString(font, open ? styleBlurb(sel) : "LOCKED - defeat a " + sel.displayName + " to learn this style.",
                width / 2, y, open ? 0xFFDDDDDD : 0xFFFF8A6A);
        y += 12;
        if (open) {
            Moveset ms = Movesets.get(sel);
            List<String> lines = wrap(List.of("L: " + ms.special1().name, "I: " + ms.special2().name,
                    "O: " + ms.superMove().name + " (full meter)"), width - 20);
            for (String line : lines) {
                if (y > height - 10) break;
                g.drawCenteredString(font, line, width / 2, y, 0xFFB8C8FF);
                y += 10;
            }
        }
        g.pose().popPose();
    }
}
