package com.streetfighter.client.world;

import com.streetfighter.client.FightSession;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FightEvent;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.FighterType;
import com.streetfighter.fight.HitLevel;
import com.streetfighter.fight.Projectile;
import com.streetfighter.fight.ProjectileVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/** Turns fight events into real Minecraft particles and positional sounds at the fighters' world positions. */
public final class WorldFx {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f, 0.82f, 0.15f), 1.2f);
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(1f, 0.2f, 0.15f), 1.2f);
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.35f, 0.9f, 1f), 1.0f);

    private final FightSession session;
    private final Random random = new Random();
    private int frame;

    public WorldFx(FightSession session) {
        this.session = session;
    }

    private ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private Vec3 at(float ax, float ay) {
        return session.arena.toWorldFlat(ax, ay);
    }

    private Vec3 ground(float ax) {
        return session.arena.toWorld(ax, 0, 0.1);
    }

    private BlockState groundState(float ax) {
        Vec3 g = ground(ax);
        BlockState s = level().getBlockState(BlockPos.containing(g.x, g.y - 0.2, g.z));
        return s.isAir() ? Blocks.DIRT.defaultBlockState() : s;
    }

    private void burst(ParticleOptions type, Vec3 p, int count, double speed, double spread) {
        ClientLevel lvl = level();
        if (lvl == null) return;
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5) * spread, oy = (random.nextDouble() - 0.5) * spread,
                    oz = (random.nextDouble() - 0.5) * spread;
            lvl.addParticle(type, p.x + ox, p.y + oy, p.z + oz,
                    (random.nextDouble() - 0.5) * speed, (random.nextDouble() - 0.2) * speed, (random.nextDouble() - 0.5) * speed);
        }
    }

    private void dust(float ax, int count, double strength) {
        ClientLevel lvl = level();
        if (lvl == null) return;
        BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, groundState(ax));
        Vec3 g = ground(ax);
        Vec3 axis = session.arena.axis, normal = session.arena.normal;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double vx = (axis.x * Math.cos(a) + normal.x * Math.sin(a)) * strength;
            double vz = (axis.z * Math.cos(a) + normal.z * Math.sin(a)) * strength;
            lvl.addParticle(block, g.x, g.y + 0.05, g.z, vx, 0.15 + random.nextDouble() * 0.2 * strength * 4, vz);
        }
    }

    private void sound(SoundEvent sound, Vec3 p, float volume, float pitch) {
        ClientLevel lvl = level();
        if (lvl != null) lvl.playLocalSound(p.x, p.y, p.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private void announce(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) sound(sound, mc.player.getEyePosition(), volume, pitch);
    }

    public void fightStart() {
        announce(SoundEvents.RAID_HORN.value(), 0.5f, 1.4f);
    }

    public void process(List<FightEvent> events) {
        for (FightEvent ev : events) handle(ev);
    }

    private void handle(FightEvent ev) {
        FightEngine engine = session.engine;
        Fighter f = ev.fighter() >= 0 ? engine.fighter(ev.fighter()) : null;
        FightSession.HudState hud = session.hud;
        switch (ev.type()) {
            case ROUND -> announce(SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1f);
            case FIGHT -> {
                announce(SoundEvents.BELL_BLOCK, 1f, 1.2f);
                announce(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 0.7f);
            }
            case MOVE_START -> {
                if (f == null || f.move == null) break;
                Vec3 p = at(f.x, 60);
                if (f.move.callout != null) hud.callout(f.move.callout, f.index, f.index == 0 ? 0xFF7FE0FF : 0xFFFF8A6A);
                if (f.move.special) {
                    sound(specialSound(f), p, 1f, 0.9f + random.nextFloat() * 0.2f);
                    burst(ParticleTypes.CRIT, at(f.x, 70), 6, 0.3, 0.6);
                } else {
                    sound(f.move.damage >= 60 ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_NODAMAGE, p, 0.6f, 1.1f);
                }
                if (f.move.leapLike() && f.grounded()) dust(f.x, 8, 0.12);
            }
            case SUPER -> {
                if (f == null) break;
                hud.superBanner = 80;
                hud.superText = ev.text() == null ? "SUPER!" : ev.text();
                hud.superOwner = ev.fighter();
                hud.flash = 8;
                Vec3 p = at(f.x, 70);
                burst(ParticleTypes.TOTEM_OF_UNDYING, p, 60, 1.2, 0.8);
                sound(SoundEvents.PLAYER_LEVELUP, p, 1f, 0.6f);
                sound(SoundEvents.TOTEM_USE, p, 0.6f, 1.4f);
                sound(specialSound(f), p, 1f, 1f);
            }
            case HIT -> {
                Vec3 p = at(ev.x(), ev.y());
                Fighter attacker = f != null ? engine.other(f) : null;
                int n = ev.heavy() ? 14 : 7;
                burst(ParticleTypes.CRIT, p, n, 0.9, 0.3);
                burst(ParticleTypes.DAMAGE_INDICATOR, p, ev.heavy() ? 3 : 1, 0.2, 0.3);
                if (ev.heavy()) {
                    level().addParticle(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 0, 0, 0);
                    sound(SoundEvents.PLAYER_ATTACK_CRIT, p, 1f, 0.85f);
                } else {
                    sound(SoundEvents.PLAYER_ATTACK_STRONG, p, 0.9f, 1.1f);
                }
                if (ev.text() != null) { // counter hit
                    hud.flash = Math.max(hud.flash, 3);
                    burst(ParticleTypes.ELECTRIC_SPARK, p, 14, 1.0, 0.4);
                    hud.callout(ev.text() + "!", attacker != null ? attacker.index : 0, 0xFFFFD21A);
                    sound(SoundEvents.ANVIL_PLACE, p, 0.35f, 1.8f);
                }
                LivingEntityHurt.hurt(f == engine.player ? Minecraft.getInstance().player : session.enemyEntity);
                if (attacker != null && attacker.move != null) {
                    if (attacker.move.isSuper && ev.heavy() && attacker.moves.type() == FighterType.STEVE) {
                        level().addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 0, 0, 0);
                        sound(SoundEvents.GENERIC_EXPLODE.value(), p, 1f, 0.8f);
                    }
                    if (attacker.move.level == HitLevel.GRAB) {
                        burst(ParticleTypes.HAPPY_VILLAGER, p, 14, 0.5, 0.8);
                        burst(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EMERALD)), p, 10, 0.4, 0.4);
                        sound(SoundEvents.VILLAGER_TRADE, p, 1f, 1f);
                    }
                }
                if (f != null && f.hp <= 0) hud.flash = 10;
            }
            case BLOCK -> {
                Vec3 p = at(ev.x(), ev.y());
                burst(ParticleTypes.ENCHANTED_HIT, p, 10, 0.6, 0.3);
                burst(CYAN, p, 6, 0.4, 0.4);
                sound(SoundEvents.SHIELD_BLOCK, p, 0.9f, 1.1f + random.nextFloat() * 0.2f);
            }
            case PROJECTILE -> {
                if (f == null || ev.visual() == null) break;
                Vec3 p = at(f.x, 80);
                switch (ev.visual()) {
                    case ARROW -> sound(SoundEvents.SKELETON_SHOOT, p, 1f, 1f);
                    case DIRT, BONE -> sound(SoundEvents.SNOWBALL_THROW, p, 1f, 0.8f);
                    case EMERALD -> sound(SoundEvents.EGG_THROW, p, 1f, 1.2f);
                    case GOLEM -> {
                        sound(SoundEvents.IRON_GOLEM_REPAIR, p, 1f, 0.8f);
                        sound(SoundEvents.BELL_BLOCK, p, 1f, 0.7f);
                    }
                    case PIG -> sound(SoundEvents.PIG_AMBIENT, p, 1f, 0.8f);
                    default -> {
                    }
                }
            }
            case BOUNCE -> {
                Vec3 p = ground(ev.x());
                sound(SoundEvents.EXPERIENCE_ORB_PICKUP, p, 0.6f, 1.6f);
                burst(ParticleTypes.HAPPY_VILLAGER, p.add(0, 0.2, 0), 4, 0.2, 0.3);
            }
            case POOF -> {
                dust(ev.x(), 6, 0.08);
                sound(SoundEvents.ARROW_HIT, ground(ev.x()), 0.6f, 1.2f);
            }
            case CLASH -> {
                Vec3 p = at(ev.x(), ev.y());
                hud.flash = Math.max(hud.flash, 3);
                burst(ParticleTypes.CRIT, p, 16, 1.2, 0.2);
                sound(SoundEvents.PLAYER_ATTACK_CRIT, p, 1f, 1.5f);
                if (ev.text() != null) hud.callout(ev.text(), ev.fighter() >= 0 ? ev.fighter() : 0, 0xFFFFFFFF);
            }
            case SLAM -> {
                Vec3 g = ground(ev.x());
                dust(ev.x(), ev.visual() == ProjectileVisual.GOLEM ? 50 : 26, 0.35);
                burst(ParticleTypes.CLOUD, g.add(0, 0.3, 0), 10, 0.3, 1.2);
                if (ev.visual() == ProjectileVisual.GOLEM) {
                    level().addParticle(ParticleTypes.EXPLOSION, g.x, g.y + 0.5, g.z, 0, 0, 0);
                    sound(SoundEvents.ANVIL_LAND, g, 1f, 0.6f);
                    sound(SoundEvents.IRON_GOLEM_ATTACK, g, 1f, 0.8f);
                } else {
                    sound(SoundEvents.GENERIC_BIG_FALL, g, 1f, 0.7f);
                }
            }
            case LAND -> dust(ev.x(), 5, 0.06);
            case THUD -> {
                Vec3 g = ground(ev.x());
                dust(ev.x(), 22, 0.2);
                burst(ParticleTypes.POOF, g.add(0, 0.3, 0), 5, 0.1, 0.8);
                sound(SoundEvents.GENERIC_BIG_FALL, g, 1f, 0.8f);
            }
            case JUMP -> dust(ev.x(), 4, 0.05);
            case KO -> {
                hud.flash = 14;
                Fighter loser = engine.player.hp <= 0 ? engine.player : engine.enemy;
                Vec3 p = at(loser.x, 60);
                level().addParticle(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 0, 0, 0);
                burst(ParticleTypes.FIREWORK, p, 40, 0.6, 0.5);
                sound(SoundEvents.GENERIC_EXPLODE.value(), p, 0.8f, 0.6f);
                announce(SoundEvents.WITHER_BREAK_BLOCK, 0.5f, 0.6f);
            }
            case TIME -> announce(SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 0.7f);
            case ROUND_WIN -> {
                if (ev.fighter() == engine.player.index) announce(SoundEvents.PLAYER_LEVELUP, 1f, 0.8f);
                else announce(SoundEvents.VILLAGER_NO, 1f, 0.8f);
                if (f != null) burst(f.index == 0 ? GOLD : CRIMSON, at(f.x, 70), 30, 0.5, 1.2);
            }
            case DRAW -> announce(SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 0.5f);
            case PERFECT -> {
                announce(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                if (f != null) burst(ParticleTypes.TOTEM_OF_UNDYING, at(f.x, 70), 50, 1.0, 1.0);
            }
            case MATCH_WIN -> {
                if (ev.fighter() == engine.player.index) {
                    announce(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    Vec3 p = at(engine.player.x, 100);
                    burst(ParticleTypes.FIREWORK, p, 60, 0.8, 1.0);
                    burst(ParticleTypes.TOTEM_OF_UNDYING, p, 40, 1.0, 1.0);
                } else {
                    announce(SoundEvents.WITHER_DEATH, 0.25f, 1.6f);
                }
            }
        }
    }

    /** Continuous effects: projectile trails, dash dust, super-ready auras. Called once per simulation step. */
    public void stepTrails() {
        frame++;
        FightEngine engine = session.engine;
        if (engine == null || level() == null) return;
        for (Projectile p : engine.projectiles) {
            if (p.delay > 0) {
                if (p.spec.fromSky && frame % 4 == 0) {
                    Vec3 g = ground(p.x);
                    level().addParticle(ParticleTypes.SMALL_FLAME, g.x, g.y + 0.05, g.z, 0, 0.02, 0); // landing marker
                }
                continue;
            }
            Vec3 w = at(p.x, p.y);
            switch (p.spec.visual) {
                case ARROW -> {
                    if (frame % 2 == 0) level().addParticle(ParticleTypes.CRIT, w.x, w.y, w.z, 0, 0, 0);
                }
                case DIRT -> {
                    if (frame % 3 == 0) burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), w, 1, 0.05, 0.2);
                }
                case EMERALD -> {
                    if (frame % 4 == 0) level().addParticle(ParticleTypes.HAPPY_VILLAGER, w.x, w.y, w.z, 0, 0, 0);
                }
                case GOLEM -> {
                    if (!p.landed && frame % 2 == 0) burst(ParticleTypes.CLOUD, at(p.x, p.y + 60), 1, 0.05, 0.6);
                }
                case PIG -> {
                    if (frame % 3 == 0) dust(p.x, 2, 0.08);
                }
                default -> {
                }
            }
        }
        for (Fighter f : new Fighter[]{engine.player, engine.enemy}) {
            if (f.state == Fighter.State.ATTACK && f.move != null && f.move.moveVx > 0 && f.grounded()
                    && Math.abs(f.vx) > 4 && frame % 3 == 0) {
                dust(f.x - f.dir() * 20, 2, 0.05);
                Vec3 w = at(f.x - f.dir() * 30, 30);
                level().addParticle(ParticleTypes.CLOUD, w.x, w.y, w.z, 0, 0.01, 0);
            }
            if (f.state == Fighter.State.ATTACK && f.move != null && f.move.moveVy > 0 && !f.grounded() && frame % 2 == 0) {
                Vec3 w = at(f.x, f.y + 90);
                if (f.moves.type() == FighterType.STEVE) {
                    burst(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.DIAMOND)), w, 2, 0.15, 0.3);
                } else {
                    level().addParticle(ParticleTypes.CRIT, w.x, w.y, w.z, 0, 0, 0);
                }
            }
            if (f.meter >= 100 && frame % 5 == 0 && engine.phase == FightEngine.Phase.FIGHTING) {
                double a = frame * 0.4 + f.index * 3;
                Vec3 base = at(f.x, 10 + (frame * 7 % 110));
                Vec3 off = session.arena.axis.scale(Math.cos(a) * 0.45).add(session.arena.normal.scale(Math.sin(a) * 0.45));
                Vec3 w = base.add(off);
                level().addParticle(f.index == 0 ? GOLD : CRIMSON, w.x, w.y, w.z, 0, 0.02, 0);
            }
            if (engine.freeze > 0 && engine.freezeOwner == f.index) {
                double a = frame * 0.5;
                for (int i = 0; i < 3; i++) {
                    double ang = a + i * 2.09;
                    Vec3 w = at(f.x, 20 + (frame * 4 + i * 40) % 120)
                            .add(session.arena.axis.scale(Math.cos(ang) * 0.7))
                            .add(session.arena.normal.scale(Math.sin(ang) * 0.7));
                    level().addParticle(ParticleTypes.END_ROD, w.x, w.y, w.z, 0, 0.03, 0);
                }
            }
            if (f.state == Fighter.State.WALK && frame % 12 == f.index * 6) dust(f.x, 1, 0.03);
        }
    }

    private SoundEvent specialSound(Fighter f) {
        if (f.index == 0) return SoundEvents.PLAYER_ATTACK_SWEEP;
        return switch (session.enemyType) {
            case SKELETON -> SoundEvents.SKELETON_AMBIENT;
            case VILLAGER -> SoundEvents.VILLAGER_AMBIENT;
            case PIG -> SoundEvents.PIG_AMBIENT;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
    }

    /** Triggers the vanilla red hurt flash + hurt sound on a real entity. */
    static final class LivingEntityHurt {
        static void hurt(net.minecraft.world.entity.LivingEntity e) {
            ClientLevel lvl = Minecraft.getInstance().level;
            if (e != null && lvl != null) e.handleDamageEvent(lvl.damageSources().generic());
        }
    }
}
