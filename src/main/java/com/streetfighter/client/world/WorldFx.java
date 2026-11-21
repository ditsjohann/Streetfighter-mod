package com.streetfighter.client.world;

import com.streetfighter.client.FightSession;
import com.streetfighter.fight.AnimPose;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.FightEvent;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.FighterType;
import com.streetfighter.fight.HitLevel;
import com.streetfighter.fight.Move;
import com.streetfighter.fight.Projectile;
import com.streetfighter.fight.ProjectileVisual;
import com.streetfighter.network.ModNetwork;
import com.streetfighter.network.WallBreakPacket;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/** Turns fight events into real particles, additive effects, positional sounds and animation impulses. */
public final class WorldFx {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f, 0.82f, 0.15f), 1.2f);
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(1f, 0.2f, 0.15f), 1.2f);
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.35f, 0.9f, 1f), 1.0f);
    private static final DustParticleOptions PARRY_GREEN = new DustParticleOptions(new Vector3f(0.4f, 1f, 0.55f), 1.1f);

    public static final int C_P1 = 0x5CD8FF, C_P2 = 0xFF6A4A, C_GOLD = 0xFFD040, C_WHITE = 0xFFF4D8, C_BLOCK = 0x4FB8FF,
            C_PARRY = 0x4CFF8A, C_FIRE = 0xFF7A20;

    private final FightSession session;
    public final FxRenderer quads = new FxRenderer();
    private final Random random = new Random();
    private int frame;
    private final int[] lastSwooshSerial = {-1, -1};

    public WorldFx(FightSession session) {
        this.session = session;
        quads.setPlane(session.arena.axis);
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

    /** A point slightly toward the camera so effects draw in front of the fighters. */
    private Vec3 front(Vec3 p) {
        return p.add(session.arena.normal.scale(session.camera.side() * 0.45));
    }

    private LivingEntity entityOf(Fighter f) {
        return f.index == 0 ? Minecraft.getInstance().player : session.enemyEntity;
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
        dustOf(new BlockParticleOption(ParticleTypes.BLOCK, groundState(ax)), ground(ax), count, strength);
    }

    private void dustOf(BlockParticleOption block, Vec3 g, int count, double strength) {
        Vec3 axis = session.arena.axis, normal = session.arena.normal;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double vx = (axis.x * Math.cos(a) + normal.x * Math.sin(a)) * strength;
            double vz = (axis.z * Math.cos(a) + normal.z * Math.sin(a)) * strength;
            level().addParticle(block, g.x, g.y + 0.05, g.z, vx, 0.15 + random.nextDouble() * 0.2 * strength * 4, vz);
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

    private int colorOf(int index) {
        return index == 0 ? C_P1 : C_P2;
    }

    public void fightStart() {
        announce(SoundEvents.RAID_HORN.value(), 0.5f, 1.4f);
    }

    public void frame(float dt) {
        quads.step(dt);
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
                Move m = f.move;
                Vec3 p = at(f.x, 60);
                if (m.callout != null) hud.callout(m.callout, f.index, f.index == 0 ? 0xFF7FE0FF : 0xFFFF8A6A);
                if (m.special) {
                    sound(specialSound(f), p, 1f, 0.9f + random.nextFloat() * 0.2f);
                    if (m.strength == 3) {
                        quads.glow(front(at(f.x, 70)), 2.2f, 0.35f, C_GOLD);
                        burst(ParticleTypes.ELECTRIC_SPARK, at(f.x, 70), 10, 0.5, 0.8);
                        hud.callout("EX!", f.index, 0xFFFFD21A);
                    } else {
                        burst(ParticleTypes.CRIT, at(f.x, 70), 6, 0.3, 0.6);
                    }
                } else if (m.kind == Move.Kind.DRIVE_IMPACT) {
                    quads.glow(front(at(f.x, 70)), 2.6f, 0.5f, 0x3CFF6A);
                    burst(PARRY_GREEN, at(f.x, 70), 20, 0.4, 1.0);
                    sound(SoundEvents.WARDEN_SONIC_CHARGE, p, 0.7f, 1.6f);
                } else {
                    sound(m.damage >= 60 ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_NODAMAGE, p, 0.6f, 1.1f);
                }
                if (m.leapLike() && f.grounded()) dust(f.x, 8, 0.12);
            }
            case SUPER -> {
                if (f == null) break;
                hud.superBanner = 80;
                hud.superText = ev.text() == null ? "SUPER!" : ev.text();
                hud.superOwner = ev.fighter();
                hud.flash = 8;
                Vec3 p = at(f.x, 70);
                burst(ParticleTypes.TOTEM_OF_UNDYING, p, 60, 1.2, 0.8);
                quads.ring(front(p), 0.5f, 6f, 0.6f, C_GOLD);
                session.fovKick = 0.1f;
                quads.glow(front(p), 4f, 0.8f, colorOf(f.index));
                sound(SoundEvents.PLAYER_LEVELUP, p, 1f, 0.6f);
                sound(SoundEvents.TOTEM_USE, p, 0.6f, 1.4f);
                sound(specialSound(f), p, 1f, 1f);
            }
            case HIT -> {
                Vec3 p = at(ev.x(), ev.y());
                Fighter attacker = f != null ? engine.other(f) : null;
                boolean superHit = attacker != null && attacker.move != null && attacker.move.isSuper();
                int sparkColor = ev.text() != null ? C_GOLD : superHit ? C_GOLD : ev.heavy() ? 0xFFB040 : C_WHITE;
                quads.spark(front(p), ev.heavy() ? 2.2f : 1.4f, sparkColor);
                if (ev.heavy()) {
                    quads.ring(front(p), 0.3f, 2.4f, 0.25f, sparkColor);
                    session.fovKick = Math.max(session.fovKick, superHit ? 0.05f : 0.03f);
                }
                burst(ParticleTypes.CRIT, p, ev.heavy() ? 14 : 7, 0.9, 0.3);
                burst(ParticleTypes.DAMAGE_INDICATOR, p, ev.heavy() ? 3 : 1, 0.2, 0.3);
                if (ev.heavy()) {
                    level().addParticle(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 0, 0, 0);
                    sound(SoundEvents.PLAYER_ATTACK_CRIT, p, 1f, 0.85f);
                } else {
                    sound(SoundEvents.PLAYER_ATTACK_STRONG, p, 0.9f, 1.1f);
                }
                if (ev.text() != null) {
                    hud.flash = Math.max(hud.flash, 3);
                    burst(ParticleTypes.ELECTRIC_SPARK, p, 14, 1.0, 0.4);
                    hud.callout(ev.text() + "!", attacker != null ? attacker.index : 0, 0xFFFFD21A);
                    sound(SoundEvents.ANVIL_PLACE, p, 0.35f, 1.8f);
                }
                if (f != null) {
                    LivingEntity victim = entityOf(f);
                    hurt(victim);
                    FightAnimator.onHit(victim, ev.heavy(), f.lastHitLevel);
                }
                if (attacker != null && attacker.move != null) {
                    if (attacker.move.isSuper() && ev.heavy() && attacker.moves.type() == FighterType.STEVE) {
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
                quads.spark(front(p), 1.3f, C_BLOCK);
                quads.ring(front(p), 0.2f, 1.2f, 0.2f, C_BLOCK);
                burst(ParticleTypes.ENCHANTED_HIT, p, 10, 0.6, 0.3);
                burst(CYAN, p, 6, 0.4, 0.4);
                sound(SoundEvents.SHIELD_BLOCK, p, 0.9f, 1.1f + random.nextFloat() * 0.2f);
                if (f != null) FightAnimator.onBlock(entityOf(f));
            }
            case PARRY -> {
                Vec3 p = at(ev.x(), ev.y());
                quads.ring(front(p), 0.3f, 1.8f, 0.3f, C_PARRY);
                quads.glow(front(p), 1.6f, 0.25f, C_PARRY);
                burst(PARRY_GREEN, p, 12, 0.5, 0.5);
                sound(SoundEvents.AMETHYST_BLOCK_HIT, p, 1f, 1.4f);
                if (f != null) hud.callout("PARRY", f.index, 0xFF6CFF9A);
            }
            case PERFECT_PARRY -> {
                Vec3 p = at(ev.x(), ev.y());
                quads.ring(front(p), 0.4f, 4.5f, 0.5f, C_PARRY);
                quads.glow(front(p), 3f, 0.5f, 0xFFFFFF);
                burst(ParticleTypes.END_ROD, p, 20, 0.6, 0.6);
                hud.flash = 6;
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, p, 1f, 1.6f);
                sound(SoundEvents.BELL_RESONATE, p, 0.8f, 1.8f);
                if (f != null) hud.callout("PERFECT PARRY!", f.index, 0xFF6CFF9A);
            }
            case ARMOR -> {
                Vec3 p = at(ev.x(), ev.y());
                quads.spark(front(p), 1.6f, 0x9CFFB0);
                sound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), p, 1f, 0.8f);
                if (f != null) hud.callout("ARMOR", f.index, 0xFF9CFFB0);
            }
            case CRUMPLE -> {
                if (f == null) break;
                hud.callout("CRUMPLE!", engine.other(f).index, 0xFF6CFF9A);
                quads.ring(front(at(f.x, 60)), 0.5f, 3.5f, 0.45f, 0x3CFF6A);
                sound(SoundEvents.WARDEN_SONIC_BOOM, at(f.x, 60), 0.4f, 1.6f);
            }
            case WALL_SPLAT, WALL_BOUNCE, WALL_BREAK -> wallImpact(ev, f);
            case WALL_JUMP -> {
                if (f == null) break;
                boolean right = f.x > (engine.arenaMin + engine.arenaMax) / 2f;
                Vec3 w = session.arena.wallPoint(right, f.y + 40);
                dustOf(new BlockParticleOption(ParticleTypes.BLOCK, session.arena.wallState(right, f.y + 40)), w, 10, 0.12);
                sound(SoundEvents.PLAYER_SMALL_FALL, w, 1f, 1.2f);
                hud.callout("WALL JUMP", f.index, 0xFFCCCCCC);
            }
            case HAZARD -> {
                if (f == null) break;
                Vec3 p = at(f.x, Math.max(20, ev.y()));
                if ("FIRE".equals(ev.text())) {
                    burst(ParticleTypes.FLAME, p, ev.heavy() ? 24 : 8, 0.15, 0.6);
                    burst(ParticleTypes.LARGE_SMOKE, p, 4, 0.05, 0.5);
                    sound(SoundEvents.PLAYER_HURT_ON_FIRE, p, 1f, 1f);
                    if (ev.heavy()) hud.callout("BURNED!", engine.other(f).index, 0xFFFF9A40);
                } else {
                    burst(ParticleTypes.DAMAGE_INDICATOR, p, 6, 0.3, 0.6);
                    sound(SoundEvents.SWEET_BERRY_BUSH_BREAK, p, 1f, 0.8f);
                    if (ev.heavy()) hud.callout("SPIKED!", engine.other(f).index, 0xFFFF9A40);
                }
                hurt(entityOf(f));
            }
            case PROJECTILE -> {
                if (f == null || ev.visual() == null) break;
                Vec3 p = at(f.x, 80);
                switch (ev.visual()) {
                    case ARROW -> sound(SoundEvents.SKELETON_SHOOT, p, 1f, 1f);
                    case BIG_ARROW -> {
                        sound(SoundEvents.CROSSBOW_SHOOT, p, 1f, 0.6f);
                        quads.ring(front(at(f.x + f.dir() * 40, 92)), 0.2f, 2f, 0.3f, 0xFFFFFF);
                    }
                    case DIRT, COBBLE, BONE -> sound(SoundEvents.SNOWBALL_THROW, p, 1f, 0.8f);
                    case EMERALD -> sound(SoundEvents.EGG_THROW, p, 1f, 1.2f);
                    case TNT -> sound(SoundEvents.TNT_PRIMED, p, 1f, 1.2f);
                    case SKULL -> sound(SoundEvents.WITHER_SHOOT, p, 0.7f, 1f);
                    case GOLEM -> {
                        sound(SoundEvents.IRON_GOLEM_REPAIR, p, 1f, 0.8f);
                        sound(SoundEvents.BELL_BLOCK, p, 1f, 0.7f);
                    }
                    case BELL -> {
                        sound(SoundEvents.BELL_BLOCK, p, 1f, 1f);
                        sound(SoundEvents.RAID_HORN.value(), p, 0.4f, 1.5f);
                    }
                    case PIG -> sound(SoundEvents.PIG_AMBIENT, p, 1f, 0.8f);
                    default -> {
                    }
                }
            }
            case EXPLOSION -> {
                Vec3 p = at(ev.x(), ev.y());
                level().addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 0, 0, 0);
                quads.ring(front(p), 0.5f, 5f, 0.4f, C_FIRE);
                quads.glow(front(p), 4f, 0.35f, C_FIRE);
                sound(SoundEvents.GENERIC_EXPLODE.value(), p, 1f, 1f);
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
                quads.spark(front(p), 2.4f, 0xFFFFFF);
                quads.ring(front(p), 0.3f, 2.5f, 0.3f, 0xFFFFFF);
                burst(ParticleTypes.CRIT, p, 16, 1.2, 0.2);
                sound(SoundEvents.PLAYER_ATTACK_CRIT, p, 1f, 1.5f);
            }
            case TECH -> {
                Vec3 p = at(ev.x(), ev.y());
                quads.spark(front(p), 2f, 0xFFFFFF);
                sound(SoundEvents.SHIELD_BLOCK, p, 1f, 0.7f);
                hud.callout("THROW TECH!", ev.fighter() >= 0 ? ev.fighter() : 0, 0xFFFFFFFF);
            }
            case CALLOUT -> {
                if (ev.text() != null) hud.callout(ev.text(), ev.fighter() >= 0 ? ev.fighter() : 0, 0xFFFFE070);
            }
            case SLAM -> {
                Vec3 g = ground(ev.x());
                boolean big = ev.visual() == ProjectileVisual.GOLEM || ev.heavy();
                dust(ev.x(), big ? 50 : 26, 0.35);
                burst(ParticleTypes.CLOUD, g.add(0, 0.3, 0), 10, 0.3, 1.2);
                quads.ring(front(g.add(0, 0.2, 0)), 0.5f, big ? 6f : 3f, 0.4f, 0xE8DCC0);
                if (ev.visual() == ProjectileVisual.GOLEM) {
                    if (session.golemDummy != null) session.golemDummy.handleEntityEvent((byte) 4);
                    level().addParticle(ParticleTypes.EXPLOSION, g.x, g.y + 0.5, g.z, 0, 0, 0);
                    sound(SoundEvents.ANVIL_LAND, g, 1f, 0.6f);
                    sound(SoundEvents.IRON_GOLEM_ATTACK, g, 1f, 0.8f);
                } else {
                    sound(SoundEvents.GENERIC_BIG_FALL, g, 1f, 0.7f);
                }
                if (f != null && ev.visual() == null) FightAnimator.onLand(entityOf(f), true);
            }
            case GROUND_BOUNCE -> {
                if (f == null) break;
                dust(f.x, 30, 0.3);
                quads.ring(front(ground(f.x).add(0, 0.2, 0)), 0.4f, 3f, 0.35f, 0xE8DCC0);
                sound(SoundEvents.GENERIC_BIG_FALL, ground(f.x), 1f, 0.6f);
                hud.callout("GROUND BOUNCE!", engine.other(f).index, 0xFFFFE070);
            }
            case LAND -> {
                dust(ev.x(), 5, 0.06);
                if (f != null) FightAnimator.onLand(entityOf(f), false);
            }
            case THUD -> {
                Vec3 g = ground(ev.x());
                dust(ev.x(), 22, 0.2);
                burst(ParticleTypes.POOF, g.add(0, 0.3, 0), 5, 0.1, 0.8);
                sound(SoundEvents.GENERIC_BIG_FALL, g, 1f, 0.8f);
                if (f != null) FightAnimator.onLand(entityOf(f), true);
            }
            case JUMP -> dust(ev.x(), 4, 0.05);
            case KO -> {
                hud.flash = 14;
                Fighter loser = engine.player.hp <= 0 ? engine.player : engine.enemy;
                Vec3 p = at(loser.x, 60);
                level().addParticle(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 0, 0, 0);
                quads.ring(front(p), 0.5f, 8f, 0.7f, 0xFFFFFF);
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
            case WHIFF -> {
            }
        }
    }

    private void wallImpact(FightEvent ev, Fighter f) {
        if (f == null) return;
        boolean right = "R".equals(ev.text()) || (ev.text() == null || ev.text().isEmpty())
                && f.x > (session.engine.arenaMin + session.engine.arenaMax) / 2f;
        float h = Math.max(30, f.y + 50);
        Vec3 w = session.arena.wallPoint(right, h);
        BlockState wallBlock = session.arena.wallState(right, h);
        BlockParticleOption blockDust = new BlockParticleOption(ParticleTypes.BLOCK,
                wallBlock.isAir() ? Blocks.STONE.defaultBlockState() : wallBlock);
        int n = ev.type() == FightEvent.Type.WALL_BOUNCE ? (ev.heavy() ? 18 : 8) : 34;
        dustOf(blockDust, w, n, 0.25);
        quads.ring(front(w), 0.4f, ev.type() == FightEvent.Type.WALL_BOUNCE ? 2f : 4f, 0.35f, 0xFFFFFF);
        SoundEvent hit = wallBlock.getSoundType().getBreakSound();
        sound(hit, w, 1f, 0.8f);
        FightSession.HudState hud = session.hud;
        Fighter attacker = session.engine.other(f);
        switch (ev.type()) {
            case WALL_SPLAT -> {
                hud.callout("WALL SPLAT!", attacker.index, 0xFFFFD21A);
                sound(SoundEvents.ANVIL_LAND, w, 0.6f, 0.7f);
                FightAnimator.onHit(entityOf(f), true, HitLevel.MID);
            }
            case WALL_BREAK -> {
                hud.callout("WALL BREAK!", attacker.index, 0xFFFFD21A);
                List<BlockPos> blocks = session.arena.breakableWallBlocks(right, h);
                if (!blocks.isEmpty()) ModNetwork.sendToServer(new WallBreakPacket(blocks));
                burst(new BlockParticleOption(ParticleTypes.BLOCK, wallBlock), w, 30, 0.6, 0.8);
            }
            default -> {
            }
        }
    }

    private void hurt(LivingEntity e) {
        ClientLevel lvl = level();
        if (e != null && lvl != null) e.handleDamageEvent(lvl.damageSources().generic());
    }

    /** Continuous effects, once per simulation step. */
    public void stepTrails() {
        frame++;
        FightEngine engine = session.engine;
        if (engine == null || level() == null) return;
        for (Projectile p : engine.projectiles) {
            if (p.delay > 0) {
                if (p.spec.fromSky && frame % 4 == 0) {
                    Vec3 g = ground(p.x);
                    level().addParticle(ParticleTypes.SMALL_FLAME, g.x, g.y + 0.05, g.z, 0, 0.02, 0);
                }
                continue;
            }
            Vec3 w = at(p.x, p.y);
            switch (p.spec.visual) {
                case ARROW -> {
                    if (frame % 2 == 0) level().addParticle(ParticleTypes.CRIT, w.x, w.y, w.z, 0, 0, 0);
                }
                case BIG_ARROW -> {
                    level().addParticle(ParticleTypes.END_ROD, w.x, w.y, w.z, 0, 0, 0);
                    if (frame % 2 == 0) quads.streak(front(w), 2.5f, p.vx >= 0, 0xBFE8FF);
                }
                case DIRT, COBBLE -> {
                    if (frame % 3 == 0) burst(new BlockParticleOption(ParticleTypes.BLOCK,
                            (p.spec.visual == ProjectileVisual.DIRT ? Blocks.DIRT : Blocks.COBBLESTONE).defaultBlockState()), w, 1, 0.05, 0.2);
                }
                case EMERALD -> {
                    if (frame % 4 == 0) level().addParticle(ParticleTypes.HAPPY_VILLAGER, w.x, w.y, w.z, 0, 0, 0);
                }
                case TNT -> {
                    if (frame % 2 == 0) level().addParticle(ParticleTypes.SMOKE, w.x, w.y + 0.3, w.z, 0, 0.02, 0);
                }
                case SKULL -> {
                    if (frame % 2 == 0) level().addParticle(ParticleTypes.SMOKE, w.x, w.y, w.z, 0, 0.02, 0);
                    if (frame % 3 == 0) level().addParticle(ParticleTypes.SOUL, w.x, w.y, w.z, 0, 0.02, 0);
                }
                case GOLEM -> {
                    if (!p.landed && frame % 2 == 0) burst(ParticleTypes.CLOUD, at(p.x, p.y + 60), 1, 0.05, 0.6);
                }
                case PIG -> {
                    if (frame % 3 == 0) dust(p.x, 2, 0.08);
                }
                case BELL, SHOCKWAVE -> {
                    dust(p.x, 3, 0.18);
                    if (frame % 3 == 0) quads.ring(front(ground(p.x).add(0, 0.6, 0)), 0.4f, 1.8f, 0.25f, C_GOLD);
                }
                default -> {
                }
            }
        }
        for (Fighter f : new Fighter[]{engine.player, engine.enemy}) {
            Move m = f.move;
            boolean active = f.state == Fighter.State.ATTACK && m != null && f.curFrame >= m.startup && f.curFrame < f.activeEnd;
            // strike swoosh, once per move, on the first active frame of anything with weight
            if (active && lastSwooshSerial[f.index] != f.moveSerial && (m.strength >= 1 || m.special || m.air)
                    && m.damage > 0) {
                lastSwooshSerial[f.index] = f.moveSerial;
                float angle = swooshAngle(m.pose);
                float y = (m.hy0 + m.hy1) / 2f;
                Vec3 center = front(at(f.x + f.dir() * (m.hx0 + m.hx1) / 2f * 0.6f, f.y + y));
                int color = m.isSuper() ? C_GOLD : m.strength == 3 ? C_GOLD : colorOf(f.index);
                quads.swoosh(center, m.strength >= 2 || m.special ? 2.2f : 1.6f, angle, f.facingRight, color);
            }
            if (f.state == Fighter.State.ATTACK && m != null && m.moveVx > 0 && f.grounded()
                    && Math.abs(f.vx) > 4 && frame % 3 == 0) {
                dust(f.x - f.dir() * 20, 2, 0.05);
                quads.streak(front(at(f.x - f.dir() * 40, 60)), 1.8f, f.facingRight, 0xFFFFFF);
            }
            if (f.state == Fighter.State.ATTACK && m != null && m.moveVy > 0 && !f.grounded() && frame % 2 == 0) {
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
            if (f.state == Fighter.State.PARRY && frame % 3 == 0) {
                burst(PARRY_GREEN, at(f.x, 70), 2, 0.1, 1.0);
            }
            if (f.state == Fighter.State.WALK && frame % 12 == f.index * 6) dust(f.x, 1, 0.03);
            if (f.state == Fighter.State.WALK && engine.env.floor(f.x) == com.streetfighter.fight.FightEnvironment.Floor.WATER
                    && frame % 6 == 0) {
                Vec3 g = ground(f.x);
                level().addParticle(ParticleTypes.SPLASH, g.x, g.y + 0.1, g.z, 0, 0.1, 0);
            }
        }
    }

    /** Per-frame auras (rebuilt each frame, not stored). */
    public List<FxRenderer.Fx> auras() {
        List<FxRenderer.Fx> out = new java.util.ArrayList<>();
        FightEngine engine = session.engine;
        if (engine == null) return out;
        float t = session.time;
        for (Fighter f : new Fighter[]{engine.player, engine.enemy}) {
            Vec3 c = front(session.worldOf(f).add(0, 1.0, 0)).subtract(session.arena.normal.scale(session.camera.side() * 0.9));
            if (f.meter >= 100 && engine.phase == FightEngine.Phase.FIGHTING) {
                float pulse = 1.9f + 0.25f * (float) Math.sin(t * 6 + f.index);
                out.add(FxRenderer.transientGlow(c, pulse * (0.8f + 0.1f * f.bars()), f.index == 0 ? 0x2C6CA0 : 0xA03020));
            }
            if (f.state == Fighter.State.PARRY) out.add(FxRenderer.transientGlow(c, 2.4f, 0x1F9F4A));
            if (engine.freeze > 0 && engine.freezeOwner == f.index) out.add(FxRenderer.transientGlow(c, 4f, 0xC09020));
            if (f.state == Fighter.State.ATTACK && f.move != null && f.armorLeft > 0) out.add(FxRenderer.transientGlow(c, 2.2f, 0x2C9F50));
        }
        return out;
    }

    private static float swooshAngle(AnimPose pose) {
        return switch (pose) {
            case UPPERCUT, ANTI_AIR -> 70;
            case OVERHEAD, SLAM, AIR_SLAM, STOMP, AXE_KICK -> -60;
            case SWEEP, LOW_KICK, POKE_KICK, SLIDE -> -15;
            case ROUNDHOUSE, SPIN_KICK, FLYING_KICK -> 20;
            case HOOK -> 10;
            default -> 0;
        };
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
}
