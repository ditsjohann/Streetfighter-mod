package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.streetfighter.client.FightSession;
import com.streetfighter.fight.BeamStruggle;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FightEvent;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.StatusEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Ki Clash's own film language, separate from the Street Fighter side view (after Sparking! ZERO / Budokai Tenkaichi
 * and the anime):
 * <ul>
 *   <li><b>Chase camera</b>: an angled shot from behind your shoulder that swings around when you cross sides.</li>
 *   <li><b>Sky shots</b>: when the fight goes up, the camera drops below and looks up at the fighters.</li>
 *   <li><b>Power-up push-in</b> while someone charges ki: low angle, rumble, ground rocks floating up.</li>
 *   <li><b>Beam struggle</b>: a slow orbit around the point where the beams meet.</li>
 *   <li><b>Transformation</b>: slow motion, the camera circles the fighter, the sky turns gold.</li>
 *   <li><b>Smash / clash</b>: anime impact frames (a flash of black and white), the camera whips after the victim,
 *       and hard landings leave a crater of flying ground chunks.</li>
 * </ul>
 */
public final class KiDirector {
    private final FightSession s;
    private final Random random = new Random();
    private float orbit = Float.NaN;
    private final float[] chargeTime = new float[2];
    private final float[] lastVy = new float[2];
    private final float[] power = new float[2];
    private float now, lastNow;

    private float impact, impactLen;
    private long impactSeed;
    private float speed;
    /** Seconds into the transformation shot; only runs once the super freeze is over. -1 when none. */
    private float transformAge = -1;
    private int transformer;
    private float smashT = -1;
    private int smashVictim;
    private float clashT = -1;
    private Vec3 clashPos = Vec3.ZERO;
    private boolean cutNext;
    private float rumble;

    private static final class Chunk {
        BlockState block;
        Vec3 pos, vel;
        float rot, spin, scale, age, life;
        final Vector3f axis = new Vector3f();
        int owner = -1;       // >= 0: a floating rock held up by that fighter's charge
        boolean floating, landed;
    }

    private final List<Chunk> chunks = new ArrayList<>();

    public KiDirector(FightSession s) {
        this.s = s;
    }

    // ===================================================================================== events

    public void onEvent(FightEvent ev) {
        FightEngine e = s.engine;
        if (e == null) return;
        Fighter f = ev.fighter() >= 0 ? e.fighter(ev.fighter()) : null;
        switch (ev.type()) {
            case RUSH_CLASH -> {
                clashT = now;
                clashPos = s.arena.toWorldFlat(ev.x(), ev.y());
                impactFrame(0.16f);
                Vec3 p = front(clashPos);
                s.fx.quads.ring(p, 0.3f, 7f, 0.45f, 0xFFFFFF);
                s.fx.quads.ring(p, 0.2f, 4.5f, 0.35f, 0xFFE070);
                s.fx.quads.glow(p, 4f, 0.3f, 0xFFFFFF);
                burst(ParticleTypes.EXPLOSION, clashPos, 2, 0.2, 0);
                burst(ParticleTypes.CRIT, clashPos, 30, 0.4, 1.2);
                play(SoundEvents.ANVIL_LAND, clashPos, 1f, 1.6f);
                play(SoundEvents.PLAYER_ATTACK_KNOCKBACK, clashPos, 1.6f, 0.5f);
                play(SoundEvents.GENERIC_EXPLODE.value(), clashPos, 0.6f, 1.4f);
                s.hud.callout("CLASH!", 0, 0xFFFFFFFF);
                s.hud.callout("CLASH!", 1, 0xFFFFFFFF);
                s.fovKick = Math.max(s.fovKick, 0.08f);
                // shockwave scatters the ground below
                float gy = (float) s.arena.floorAt(ev.x());
                if (clashPos.y - gy < 2.5) crater(ev.x(), 0.6f);
            }
            case SMASH -> {
                if (f == null) break;
                smashT = now;
                smashVictim = f.index;
                impactFrame(0.12f);
                s.camera.kick(s.arena.axis.scale(f.vx >= 0 ? 1 : -1), 0.9f);
                play(SoundEvents.PLAYER_ATTACK_SWEEP, s.worldOf(f), 1.5f, 0.5f);
            }
            case TRANSFORM -> {
                if (f == null) break;
                transformAge = 0;
                transformer = f.index;
                cutNext = true;
                impactFrame(0.22f);
                play(SoundEvents.LIGHTNING_BOLT_THUNDER, s.worldOf(f), 1f, 1.2f);
                // the ground around them lifts
                for (int i = 0; i < 14; i++) rock(f, true);
            }
            case BEAM_STRUGGLE -> {
                impactFrame(0.14f);
                cutNext = true;
            }
            case BEAM_STRUGGLE_END -> impactFrame(0.18f);
            case THUD -> {
                if (f == null) break;
                float v = -lastVy[f.index];
                if (v > 5f || f.launched || smashT >= 0 && now - smashT < 2.5f && smashVictim == f.index) {
                    float k = Mth.clamp(v / 12f, 0.5f, 1.2f);
                    crater(f.x, k);
                    s.engine.shake = Math.max(s.engine.shake, 6 + 5 * k);
                    if (k > 0.8f) impactFrame(0.08f);
                }
            }
            case TELEPORT -> {
                if (f == null) break;
                // vanish: a sharp zip instead of ender particles
                Vec3 from = s.arena.toWorldFlat(ev.x(), ev.y() + 60), to = s.worldOf(f).add(0, 1, 0);
                s.fx.quads.glow(front(from), 2.4f, 0.15f, 0xFFFFFF);
                s.fx.quads.glow(front(to), 2.0f, 0.15f, 0xFFFFFF);
                burst(ParticleTypes.CLOUD, from, 8, 0.4, 0.2);
                play(SoundEvents.ILLUSIONER_MIRROR_MOVE, to, 1.4f, 1.6f);
                speed = Math.max(speed, 0.9f);
            }
            case HIT -> {
                if (f != null && (ev.text() != null || ev.heavy() && f.launched)) impactFrame(0.06f);
            }
            default -> {
            }
        }
    }

    private void impactFrame(float len) {
        if (impact > 0 && impactLen > len) return;
        impact = impactLen = len;
        impactSeed = random.nextLong();
    }

    // ===================================================================================== per frame

    public void tick(float time, float dt) {
        lastNow = now;
        now = time;
        FightEngine e = s.engine;
        if (e == null) return;
        impact = Math.max(0, impact - dt);
        if (transformAge >= 0 && e.freeze <= 0) transformAge += dt;
        if (transformAge > 3f) transformAge = -1;
        rumble = 0;
        float sp = 0;
        for (Fighter f : new Fighter[]{e.player, e.enemy}) {
            int i = f.index;
            if (f.charging) chargeTime[i] += dt;
            else chargeTime[i] = 0;
            if (f.charging && f.grounded() && random.nextFloat() < dt * 9f) rock(f, false);
            if (f.charging) rumble = Math.max(rumble, Math.min(1f, chargeTime[i] / 1.2f));
            boolean dash = f.state == Fighter.State.ATTACK && f.move != null
                    && (f.move.homing || f.move == f.moves.dash);
            if (dash) sp = Math.max(sp, 1f);
            if (f.flying) sp = Math.max(sp, Mth.clamp((Math.abs(f.vx) - 4f) / 6f, 0, 0.8f));
            if (f.charging && i == 0) sp = Math.max(sp, 0.35f + 0.35f * rumble);
            lastVy[i] = f.vy;
            // power level readout
            float target = 1000 + f.meter * 90 + f.hp * 4;
            if (f.has(StatusEffect.SUPER_FORM)) target *= 2.5f;
            if (f.charging) target *= 1 + Math.min(1.5f, chargeTime[i]) * 0.4f;
            if (power[i] == 0) power[i] = target;
            power[i] += (target - power[i]) * (1 - (float) Math.exp(-(f.charging ? 6 : 2) * dt));
        }
        if (e.struggle != null) sp = Math.max(sp, 0.6f);
        if (smashT >= 0 && now - smashT < 0.6f) sp = Math.max(sp, 1f);
        if (transformAge >= 0 && transformAge < 1.6f) sp = Math.max(sp, 0.7f);
        speed += (sp - speed) * (1 - (float) Math.exp(-8 * dt));
        stepChunks(dt);
        if (rumble > 0.2f && random.nextFloat() < dt * 3) {
            for (Fighter f : new Fighter[]{e.player, e.enemy}) {
                if (f.charging && f.grounded()) dustRing(f.x, 6);
            }
        }
    }

    /** Slow motion for the transformation. */
    public float timeScale() {
        if (transformAge > 0 && transformAge < 1.2f && s.engine != null && s.engine.freeze <= 0) return 0.22f;
        return 1f;
    }

    // ===================================================================================== camera

    /** The Ki Clash camera for this frame, or null to use the standard one (intros, K.O.s, results). */
    public FinisherCinematic.Shot shot(float dt) {
        FightEngine e = s.engine;
        if (e == null || e.phase != FightEngine.Phase.FIGHTING || e.isOver()) return null;
        Fighter p = e.player, en = e.enemy;
        float px = s.renderX(p), ex = s.renderX(en), py = s.renderY(p), ey = s.renderY(en);
        Vec3 pw = s.arena.toWorld(px, py, 0.3), ew = s.arena.toWorld(ex, ey, 0.3);
        double sep = Math.abs(px - ex) / Arena_U, vsep = Math.abs(py - ey) / Arena_U;
        double air = (py + ey) / 2f / Arena_U;
        // over the player's shoulder, swinging around when sides switch
        float want = (ex >= px ? -1 : 1) * 32f;
        if (Float.isNaN(orbit)) orbit = want;
        orbit += (want - orbit) * (1 - (float) Math.exp(-2.4f * dt));
        Vec3 focus = pw.lerp(ew, 0.58).add(0, 1.0, 0);
        double dist = Mth.clamp(3.0 + Math.max(sep, vsep * 1.25) * 0.72, 4.0, 12.5);
        double height = 0.45 + dist * 0.07;
        float roll = 0, shake = 0, orb = orbit;
        // sky battle: drop below and look up at them
        if (air > 0.8) {
            height -= Math.min(2.2, (air - 0.8) * 0.45);
            roll = (float) Math.sin(now * 0.45) * 3f;
        }
        // powering up: push in on the charger, low angle, rumble
        int charger = chargeTime[0] >= chargeTime[1] ? 0 : 1;
        float ct = chargeTime[charger];
        if (ct > 0.3f) {
            float k = ease((ct - 0.3f) / 0.9f);
            Fighter c = e.fighter(charger);
            focus = focus.lerp(s.worldOf(c).add(0, 1.0, 0), 0.6 * k);
            dist *= 1 - 0.38 * k;
            height -= 0.55 * k;
            shake += 3.5f * k;
            orb += (charger == 0 ? 0 : -orbit * 2) * k * 0.5f; // the enemy's power-up is framed from their side
        }
        // fists clash: punch in on the impact
        if (clashT >= 0 && now - clashT < 0.55f) {
            float k = 1 - ease((now - clashT) / 0.55f);
            focus = focus.lerp(clashPos, 0.6 * k);
            dist *= 1 - 0.4 * k;
            roll += 7 * k * (random.nextBoolean() ? 1 : -1) * 0.3f;
        }
        // smash: whip after the victim
        if (smashT >= 0 && now - smashT < 0.8f) {
            float k = 1 - ease((now - smashT) / 0.8f);
            focus = focus.lerp(s.worldOf(e.fighter(smashVictim)).add(0, 0.8, 0), 0.65 * k);
            dist *= 1 - 0.2 * k;
        }
        // super freeze: on the user
        if (e.freeze > 0 && e.freezeOwner >= 0) {
            Fighter u = e.fighter(e.freezeOwner);
            focus = focus.lerp(s.worldOf(u).add(0, 1.1, 0), 0.75);
            dist *= 0.55;
            orb = (u.index == 0 ? -1 : 1) * (40f + 8f * (float) Math.sin(now * 1.4));
            height -= 0.3;
        }
        // beam struggle: slow orbit around the meeting point
        BeamStruggle st = e.struggle;
        if (st != null) {
            Vec3 meet = s.arena.toWorldFlat(st.meetX + st.progress * 60, st.meetY + 20);
            focus = meet;
            dist = 3.8 + sep * 0.15;
            orb = 50f * (float) Math.sin(now * 0.55);
            height = -0.15;
            roll = 6f * (float) Math.sin(now * 0.7);
            shake += 3 + Math.abs(st.progress) * 4;
        }
        // transformation: circle the fighter in slow motion
        boolean cut = cutNext;
        cutNext = false;
        if (transformAge >= 0 && transformAge < 1.7f) {
            float p2 = transformAge / 1.7f;
            Fighter tf = e.fighter(transformer);
            focus = s.worldOf(tf).add(0, 1.1, 0);
            dist = 3.0;
            orb = -70 + 140 * ease(p2);
            height = -0.55 + 0.4 * p2;
            roll = 0;
            shake += 5 * (1 - p2);
        }
        // never under the ground
        double ground = s.arena.floorAt((px + ex) / 2f) + 0.5;
        height = Math.max(height, ground - focus.y);
        shake += rumble * 2f;
        return new FinisherCinematic.Shot(focus, dist, orb, height, roll, cut, Math.max(shake, e.shake));
    }

    private static final float Arena_U = Arena.UNITS_PER_BLOCK;

    // ===================================================================================== atmosphere

    /** Fog/sky tint {r, g, b, strength}: a warm battle haze, gold for a transformation, white-blue in a beam clash. */
    public float[] fogTint() {
        FightEngine e = s.engine;
        if (e == null) return null;
        float gold = 0;
        for (Fighter f : new Fighter[]{e.player, e.enemy}) {
            if (f.has(StatusEffect.SUPER_FORM)) gold = Math.max(gold, 0.25f);
            if (f.charging) gold = Math.max(gold, 0.15f + 0.2f * Math.min(1, chargeTime[f.index]));
        }
        if (transformAge >= 0 && transformAge < 2.2f) gold = Math.max(gold, 0.6f * (1 - transformAge / 2.2f) + 0.25f);
        if (e.struggle != null) return new float[]{0.75f, 0.9f, 1f, 0.4f};
        if (gold > 0) return new float[]{1f, 0.78f, 0.25f, gold};
        return new float[]{0.95f, 0.72f, 0.5f, 0.16f};
    }

    // ===================================================================================== HUD overlay

    public void renderOverlay(GuiGraphics g, Font font, com.streetfighter.client.FightHud hud, int w, int h) {
        FightEngine e = s.engine;
        if (e == null) return;
        // power-up glow from the edges
        if (rumble > 0) {
            Fighter c = e.fighter(chargeTime[0] >= chargeTime[1] ? 0 : 1);
            int col = c.has(StatusEffect.SUPER_FORM) ? 0xFFC020 : c.moves.type() == com.streetfighter.fight.FighterType.VILLAGER ? 0x3CFF8A : 0x5CC8FF;
            int a = (int) (rumble * (70 + 30 * Math.sin(now * 14)));
            int edge = h / 5;
            g.fillGradient(0, h - edge, w, h, argb(0, col), argb(a, col));
            g.fillGradient(0, 0, w, edge / 2, argb(a / 2, col), argb(0, col));
        }
        if (speed > 0.05f) speedLines(g, w, h, speed);
        // transformation: gold wash and lightning flickers
        if (transformAge >= 0 && transformAge < 2f) {
            float p = transformAge / 2f;
            g.fill(0, 0, w, h, argb((int) (70 * (1 - p)), 0xFFC020));
            if (random.nextFloat() < 0.25f * (1 - p)) g.fill(0, 0, w, h, argb(90, 0xFFFFFF));
        }
        // power levels
        for (Fighter f : new Fighter[]{e.player, e.enemy}) {
            boolean show = f.charging || f.has(StatusEffect.SUPER_FORM);
            if (!show) continue;
            String v = String.format("%,d", (int) (power[f.index] + (f.charging ? random.nextInt(97) : 0)));
            String label = "POWER";
            int y = (int) (h * 0.22f);
            int x = f.index == 0 ? 16 : w - 16 - Math.max(font.width(label), (int) (font.width(v) * 1.5f));
            g.fill(x - 4, y - 3, x + Math.max(font.width(label), (int) (font.width(v) * 1.5f)) + 4, y + 24, 0x90000000);
            g.drawString(font, label, x, y, 0xFF9FE8A0, true);
            g.pose().pushPose();
            g.pose().translate(x, y + 10, 0);
            g.pose().scale(1.5f, 1.5f, 1);
            g.drawString(font, v, 0, 0, f.has(StatusEffect.SUPER_FORM) ? 0xFFFFD21A : 0xFF7CFF8A, true);
            g.pose().popPose();
        }
        // anime impact frame: inverted black flash with white burst lines, then a white pop
        if (impact > 0) {
            float p = 1 - impact / impactLen;
            if (p < 0.6f) {
                g.fill(0, 0, w, h, 0xE6000000);
                Random r = new Random(impactSeed);
                int cx = w / 2 + r.nextInt(w / 6) - w / 12, cy = h / 2 + r.nextInt(h / 6) - h / 12;
                for (int i = 0; i < 26; i++) {
                    float ang = r.nextFloat() * 360f;
                    float len = (0.4f + r.nextFloat() * 0.7f) * Math.max(w, h);
                    float thick = 1 + r.nextFloat() * 4;
                    g.pose().pushPose();
                    g.pose().translate(cx, cy, 0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
                    g.fill((int) (len * 0.12f), (int) -thick, (int) len, (int) thick, 0xFFFFFFFF);
                    g.pose().popPose();
                }
                g.fill(cx - 10, cy - 10, cx + 10, cy + 10, 0xFFFFFFFF);
            } else {
                g.fill(0, 0, w, h, argb((int) (200 * (1 - (p - 0.6f) / 0.4f)), 0xFFFFFF));
            }
        }
    }

    private void speedLines(GuiGraphics g, int w, int h, float k) {
        int n = (int) (40 * k);
        int cx = w / 2, cy = h / 2;
        float r0 = Math.max(w, h) * (0.62f - 0.12f * k);
        for (int i = 0; i < n; i++) {
            float ang = random.nextFloat() * 360f;
            float len = Math.max(w, h) * (0.2f + random.nextFloat() * 0.4f);
            float thick = 0.5f + random.nextFloat() * 1.5f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
            g.fill((int) r0, (int) -thick, (int) (r0 + len), (int) Math.max(1, thick), argb((int) (110 * k), 0xFFFFFF));
            g.pose().popPose();
        }
    }

    // ===================================================================================== rocks and craters

    private void rock(Fighter f, boolean burstUp) {
        if (chunks.size() > 70) return;
        Vec3 base = s.arena.toWorld(f.x, 0, 0.1);
        BlockState ground = groundBlock(base);
        Chunk c = new Chunk();
        c.block = ground;
        double ang = random.nextDouble() * Math.PI * 2, r = 0.7 + random.nextDouble() * 1.1;
        c.pos = base.add(Math.cos(ang) * r, 0.05, Math.sin(ang) * r);
        c.vel = new Vec3(0, burstUp ? 2.5 + random.nextDouble() * 2 : 0.5 + random.nextDouble() * 0.6, 0);
        c.scale = 0.1f + random.nextFloat() * 0.2f;
        c.spin = (random.nextFloat() - 0.5f) * 90f;
        c.axis.set(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();
        c.owner = f.index;
        c.floating = !burstUp;
        c.life = 12f;
        chunks.add(c);
    }

    private void crater(float ax, float k) {
        Vec3 base = s.arena.toWorld(ax, 0, 0.1);
        BlockState ground = groundBlock(base);
        int n = (int) (8 + 10 * k);
        for (int i = 0; i < n && chunks.size() < 90; i++) {
            Chunk c = new Chunk();
            c.block = ground;
            double ang = random.nextDouble() * Math.PI * 2, out = (1.5 + random.nextDouble() * 2.5) * k;
            c.pos = base.add(Math.cos(ang) * 0.4, 0.1, Math.sin(ang) * 0.4);
            c.vel = new Vec3(Math.cos(ang) * out, (3 + random.nextDouble() * 3) * k, Math.sin(ang) * out);
            c.scale = (0.12f + random.nextFloat() * 0.22f) * Math.max(0.7f, k);
            c.spin = (random.nextFloat() - 0.5f) * 720f;
            c.axis.set(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();
            c.life = 2.5f + random.nextFloat();
            chunks.add(c);
        }
        burst(new BlockParticleOption(ParticleTypes.BLOCK, ground), base.add(0, 0.2, 0), (int) (30 * k), 1.2, 0.6);
        dustRing(ax, (int) (20 * k));
        s.fx.quads.ring(front(base.add(0, 0.15, 0)), 0.4f, 5f * k, 0.4f, 0xE8DCC0);
        play(SoundEvents.GENERIC_EXPLODE.value(), base, 0.5f * k, 0.8f);
        play(ground.getSoundType().getBreakSound(), base, 1.5f, 0.6f);
    }

    private void dustRing(float ax, int n) {
        Vec3 base = s.arena.toWorld(ax, 0, 0.1).add(0, 0.1, 0);
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int i = 0; i < n; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.CLOUD, base.x, base.y, base.z, Math.cos(ang) * 0.25, 0.01, Math.sin(ang) * 0.25);
        }
    }

    private BlockState groundBlock(Vec3 base) {
        var level = Minecraft.getInstance().level;
        if (level == null) return Blocks.DIRT.defaultBlockState();
        BlockState b = level.getBlockState(BlockPos.containing(base.add(0, -0.4, 0)));
        if (b.isAir() || !b.getFluidState().isEmpty()) return Blocks.DIRT.defaultBlockState();
        if (b.is(Blocks.GRASS_BLOCK)) return Blocks.DIRT.defaultBlockState(); // chunks show dirt, not a grass top
        return b;
    }

    private void stepChunks(float dt) {
        FightEngine e = s.engine;
        Iterator<Chunk> it = chunks.iterator();
        while (it.hasNext()) {
            Chunk c = it.next();
            c.age += dt;
            boolean held = c.floating && c.owner >= 0 && e != null && e.fighter(c.owner).charging;
            double floor = s.arena.floorAt(s.arena.toArenaX(c.pos)) + c.scale * 0.5;
            if (held) {
                // drift upward, bobbing, slowing near the top
                double top = floor + 1.2 + (c.owner * 7 + c.scale * 31) % 1.3;
                double vy = c.pos.y < top ? 0.35 + (top - c.pos.y) * 0.4 : 0.05 * Math.sin(now * 3 + c.scale * 40);
                c.vel = new Vec3(Math.sin(now * 1.3 + c.scale * 50) * 0.05, vy, Math.cos(now * 1.1 + c.scale * 30) * 0.05);
            } else {
                c.floating = false;
                c.vel = c.vel.add(0, -13 * dt, 0).scale(Math.max(0, 1 - 0.3 * dt));
            }
            c.pos = c.pos.add(c.vel.scale(dt));
            if (!held && c.pos.y < floor) {
                c.pos = new Vec3(c.pos.x, floor, c.pos.z);
                if (c.vel.y < -2) c.vel = new Vec3(c.vel.x * 0.5, -c.vel.y * 0.3, c.vel.z * 0.5);
                else c.vel = new Vec3(c.vel.x * 0.8, 0, c.vel.z * 0.8);
                c.spin *= 0.8f;
                if (!c.landed) {
                    c.landed = true;
                    c.life = Math.min(c.life, c.age + 1.6f); // settle, then fade
                }
            }
            c.rot += c.spin * dt;
            if (c.age > c.life) it.remove();
        }
    }

    public void renderWorld(PoseStack ps, Vec3 cam, MultiBufferSource.BufferSource buf) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || chunks.isEmpty()) return;
        var br = mc.getBlockRenderer();
        for (Chunk c : chunks) {
            float fade = Mth.clamp((c.life - c.age) / 0.5f, 0, 1);
            float sc = c.scale * fade;
            if (sc <= 0.01f) continue;
            ps.pushPose();
            ps.translate(c.pos.x - cam.x, c.pos.y - cam.y, c.pos.z - cam.z);
            ps.mulPose(new Quaternionf().rotateAxis(c.rot * Mth.DEG_TO_RAD, c.axis));
            ps.scale(sc, sc, sc);
            ps.translate(-0.5, -0.5, -0.5);
            br.renderSingleBlock(c.block, ps, buf, LevelRenderer.getLightColor(mc.level, BlockPos.containing(c.pos)),
                    OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        buf.endBatch();
    }

    // ===================================================================================== helpers

    private Vec3 front(Vec3 p) {
        return p.add(s.arena.normal.scale(s.camera.side() * 0.45));
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static int argb(int a, int rgb) {
        return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void burst(ParticleOptions type, Vec3 p, int count, double spread, double speedK) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int i = 0; i < count; i++) {
            double dx = (random.nextDouble() - 0.5) * 2, dy = (random.nextDouble() - 0.5) * 2, dz = (random.nextDouble() - 0.5) * 2;
            level.addParticle(type, p.x + dx * spread * 0.5, p.y + dy * spread * 0.5, p.z + dz * spread * 0.5,
                    dx * speedK * 0.3, dy * speedK * 0.3 + 0.05, dz * speedK * 0.3);
        }
    }

    private void play(SoundEvent sound, Vec3 p, float vol, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(p.x, p.y, p.z, sound, SoundSource.PLAYERS, vol, pitch, false);
    }
}
