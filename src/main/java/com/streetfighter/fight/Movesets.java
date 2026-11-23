package com.streetfighter.fight;

import com.streetfighter.fight.InputBuffer.Button;
import com.streetfighter.fight.InputBuffer.Motion;
import com.streetfighter.fight.Move.Cancel;
import com.streetfighter.fight.Move.Kind;
import com.streetfighter.fight.Moveset.Role;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Every fighting style, at Street Fighter scale: 18 normals, command normals, target combos, four specials in
 * L/M/H/EX versions, three super levels, forward/back throws and a universal Drive Impact.
 * Frame data is at 60 fps; damage is out of ~1000 HP. Base frame data follows common SF conventions
 * (e.g. 4f jabs, 8-11f heavies, 24f+ recovery sweeps).
 */
public final class Movesets {
    private static final Map<FighterType, Moveset> SETS = new EnumMap<>(FighterType.class);

    public static final FighterStats HUMANOID = new FighterStats(1000, 3.4f, 2.8f, 15f, 4.2f, 0.75f, 44, 128, 84);

    static {
        SETS.put(FighterType.STEVE, steve());
        SETS.put(FighterType.SKELETON, skeleton());
        SETS.put(FighterType.VILLAGER, villager());
        SETS.put(FighterType.PIG, pig());
        SETS.put(FighterType.CHICKEN, chicken());
        SETS.put(FighterType.ALCHEMIST, alchemist());
        SETS.put(FighterType.GOAT, goat());
        SETS.put(FighterType.OAK, oak());
        SETS.put(FighterType.IRON_ORE, ironOre());
        SETS.put(FighterType.STONE_PICKAXE, stonePickaxe());
        SETS.put(FighterType.ZOMBIE, zombie());
        SETS.put(FighterType.COW, cow());
        SETS.put(FighterType.DONKEY, donkey());
        SETS.put(FighterType.CREEPER, creeper());
        SETS.put(FighterType.BRAWLER, brawler());
    }

    private Movesets() {
    }

    public static Moveset get(FighterType type) {
        return SETS.get(type);
    }

    // =====================================================================================================
    // shared builders
    // =====================================================================================================

    /** Per-character tuning applied on top of the base normals. */
    private record Tune(int startupDelta, float reach, float dmg, float heightScale, float xOffset) {
    }

    private static float pick(int s, float l, float m, float h, float ex) {
        return switch (s) {
            case 0 -> l;
            case 1 -> m;
            case 2 -> h;
            default -> ex;
        };
    }

    private static int pick(int s, int l, int m, int h, int ex) {
        return switch (s) {
            case 0 -> l;
            case 1 -> m;
            case 2 -> h;
            default -> ex;
        };
    }

    private static Move[] variants(IntFunction<Move.Builder> f, String name) {
        Move[] out = new Move[4];
        for (int s = 0; s < 4; s++) {
            Move.Builder b = f.apply(s).kind(Kind.SPECIAL).strength(s).cancel(Cancel.SUPER);
            if (s == 3) b.meter(50).name("EX " + name).callout("EX " + name + "!");
            else b.name(name).callout(s == 2 ? name + "!" : null);
            out[s] = b.build();
        }
        return out;
    }

    private static Move normal(Tune t, String name, AnimPose pose, int su, int ac, int rec, float dmg,
                               float x0, float x1, float y0, float y1, int hs, int bs, float push, Cancel c) {
        float hy = t.heightScale;
        return Move.builder(name).frames(Math.max(3, su + t.startupDelta), ac, rec).damage(dmg * t.dmg)
                .box(x0 + t.xOffset, x1 + t.xOffset + t.reach, y0 * hy, y1 * hy).stun(hs, bs).push(push).cancel(c)
                .pose(pose).build();
    }

    /** 18 normals from a base table, named per character. Order: stand LP..HK, crouch LP..HK, air LP..HK. */
    private static void normals(Moveset m, Tune t, String[] n) {
        m.stand.put(Button.LP, normal(t, n[0], AnimPose.JAB, 4, 2, 7, 30, 8, 60, 74, 112, 13, 9, 3.5f, Cancel.CHAIN));
        m.stand.put(Button.MP, normal(t, n[1], AnimPose.STRAIGHT, 6, 3, 12, 55, 10, 76, 70, 110, 17, 12, 5, Cancel.SPECIAL));
        m.stand.put(Button.HP, normal(t, n[2], AnimPose.HOOK, 9, 4, 18, 80, 14, 90, 56, 118, 21, 15, 7, Cancel.SPECIAL));
        m.stand.put(Button.LK, normal(t, n[3], AnimPose.SHIN_KICK, 5, 3, 9, 30, 10, 66, 40, 82, 13, 9, 3.5f, Cancel.CHAIN));
        m.stand.put(Button.MK, normal(t, n[4], AnimPose.FRONT_KICK, 8, 3, 14, 55, 14, 92, 40, 96, 17, 12, 6, Cancel.SPECIAL));
        m.stand.put(Button.HK, normal(t, n[5], AnimPose.ROUNDHOUSE, 11, 4, 20, 85, 18, 104, 62, 122, 21, 15, 8, Cancel.NONE));

        m.crouch.put(Button.LP, crouch(normal(t, n[6], AnimPose.LOW_JAB, 4, 2, 7, 25, 8, 58, 42, 72, 12, 8, 3, Cancel.CHAIN)));
        m.crouch.put(Button.MP, crouch(normal(t, n[7], AnimPose.BODY_BLOW, 6, 3, 11, 50, 8, 70, 44, 86, 16, 11, 5, Cancel.SPECIAL)));
        m.crouch.put(Button.HP, crouch(normal(t, n[8], AnimPose.ANTI_AIR, 8, 5, 20, 80, -10, 55, 60, 160, 22, 15, 3, Cancel.SPECIAL)
                .toBuilder().launch(7).box(-10 + t.xOffset, 55 + t.xOffset + t.reach, 55, 165).build()));
        m.crouch.put(Button.LK, crouch(normal(t, n[9], AnimPose.LOW_KICK, 5, 2, 9, 22, 10, 70, 0, 30, 12, 8, 3, Cancel.CHAIN)
                .toBuilder().level(HitLevel.LOW).build()));
        m.crouch.put(Button.MK, crouch(normal(t, n[10], AnimPose.POKE_KICK, 8, 3, 16, 50, 15, 100, 0, 30, 16, 11, 5, Cancel.SPECIAL)
                .toBuilder().level(HitLevel.LOW).build()));
        m.crouch.put(Button.HK, crouch(normal(t, n[11], AnimPose.SWEEP, 9, 4, 24, 85, 15, 110, 0, 25, 22, 15, 6, Cancel.NONE)
                .toBuilder().level(HitLevel.LOW).knockdown().build()));

        m.air.put(Button.LP, airNormal(normal(t, n[12], AnimPose.AIR_PUNCH, 4, 8, 3, 30, 5, 55, 50, 100, 14, 9, 3, Cancel.NONE)));
        m.air.put(Button.MP, airNormal(normal(t, n[13], AnimPose.AIR_PUNCH, 6, 6, 3, 55, 5, 65, 40, 95, 17, 11, 4, Cancel.NONE)));
        m.air.put(Button.HP, airNormal(normal(t, n[14], AnimPose.AIR_SLAM, 8, 6, 3, 80, 5, 75, 15, 90, 20, 13, 5, Cancel.NONE)));
        m.air.put(Button.LK, airNormal(normal(t, n[15], AnimPose.AIR_KICK, 4, 9, 3, 30, 5, 62, 15, 65, 14, 9, 3, Cancel.NONE)));
        m.air.put(Button.MK, airNormal(normal(t, n[16], AnimPose.AIR_KICK, 6, 7, 3, 55, 5, 75, 10, 60, 17, 11, 4, Cancel.NONE)));
        m.air.put(Button.HK, airNormal(normal(t, n[17], AnimPose.FLYING_KICK, 8, 6, 3, 85, 5, 85, 0, 55, 20, 13, 5, Cancel.NONE)));
    }

    private static Move crouch(Move m) {
        return m.toBuilder().crouching().build();
    }

    private static Move airNormal(Move m) {
        return m.toBuilder().air().level(HitLevel.HIGH).build();
    }

    private static Move commandNormal(String name, AnimPose pose, int su, int ac, int rec, float dmg, float x0, float x1,
                                      float y0, float y1, HitLevel level) {
        return Move.builder(name).kind(Kind.COMMAND_NORMAL).frames(su, ac, rec).damage(dmg).box(x0, x1, y0, y1)
                .level(level).stun(19, 13).push(6).pose(pose).build();
    }

    private static void universals(Moveset m, String fwdThrow, String backThrow, float throwDmg) {
        m.forwardThrow = Move.builder(fwdThrow).kind(Kind.THROW).frames(5, 3, 24).damage(throwDmg).box(5, 64, 20, 120)
                .level(HitLevel.GRAB).stun(30, 0).push(9).launch(8).knockdown().pose(AnimPose.THROW_FWD)
                .callout(fwdThrow + "!").build();
        m.backThrow = Move.builder(backThrow).kind(Kind.THROW).frames(5, 3, 24).damage(throwDmg + 10).box(5, 64, 20, 120)
                .level(HitLevel.GRAB).stun(30, 0).push(10).launch(8).knockdown().backThrow().wallSplat()
                .pose(AnimPose.THROW_BACK).callout(backThrow + "!").build();
        m.driveImpact = Move.builder("Drive Impact").kind(Kind.DRIVE_IMPACT).frames(24, 3, 30).damage(90)
                .box(10, 95, 30, 120).stun(26, 20).push(12).armor(2, 26).crumple().wallSplat().dash(3f, 10, 24)
                .pose(AnimPose.DRIVE_IMPACT).callout("DRIVE IMPACT!").build();
        // dash / backdash (double tap): speed follows the character; the backdash is briefly invincible
        // (SF: throw-invulnerable start, then airborne)
        float fwd = Math.max(6.5f, m.stats.walkSpeed() * 2.6f), back = Math.max(5.5f, m.stats.backSpeed() * 2.4f);
        m.dash = Move.builder("Dash").frames(1, 15, 5).damage(0).box(0, 0, 0, 0).dash(fwd, 1, 15)
                .pose(AnimPose.DASH).build();
        m.backdash = Move.builder("Backdash").frames(1, 1, 22).damage(0).box(0, 0, 0, 0).leap(4.5f, -back, 1)
                .invuln(0, 7).landingLag(3).pose(AnimPose.BACKDASH).build();
    }

    // =====================================================================================================
    // STEVE: shoto all-rounder
    // =====================================================================================================

    private static Moveset steve() {
        Moveset m = new Moveset(FighterType.STEVE, HUMANOID);
        Tune t = new Tune(0, 0, 1f, 1f, 0);
        normals(m, t, new String[]{"Jab", "Pickaxe Punch", "Haymaker", "Shin Tap", "Boot Kick", "Roundhouse",
                "Low Jab", "Gut Punch", "Diamond Rise", "Ankle Kick", "Shovel Poke", "Shovel Sweep",
                "Air Jab", "Air Straight", "Pickaxe Drop", "Air Knee", "Air Kick", "Creeper Kick"});

        m.commandNormals.add(new Moveset.CommandNormal(6, Button.MP,
                commandNormal("Pickaxe Overhead", AnimPose.OVERHEAD, 18, 3, 16, 60, 10, 80, 70, 140, HitLevel.HIGH)));
        m.commandNormals.add(new Moveset.CommandNormal(4, Button.HK,
                commandNormal("Shovel Spin", AnimPose.SPIN_KICK, 10, 10, 18, 40, 0, 85, 40, 110, HitLevel.MID)
                        .toBuilder().multiHit(2, 6).dash(3f, 8, 18).build()));

        Move craft = Move.builder("Craftsman Combo").kind(Kind.TARGET).frames(8, 3, 18).damage(70).box(12, 90, 56, 118)
                .stun(21, 15).push(7).cancelable().pose(AnimPose.HOOK).build();
        Move boot2 = Move.builder("Double Boot").kind(Kind.TARGET).frames(7, 3, 14).damage(45).box(14, 92, 40, 96)
                .stun(17, 12).push(5).pose(AnimPose.FRONT_KICK).build();
        Move boot3 = Move.builder("Triple Boot").kind(Kind.TARGET).frames(9, 4, 22).damage(70).box(18, 104, 62, 122)
                .stun(22, 15).push(8).knockdown().launch(6).pose(AnimPose.ROUNDHOUSE).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, craft));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LK), Button.MK, boot2));
        m.targetCombos.add(new Moveset.TargetCombo(boot2, Button.HK, boot3));

        m.specials.add(new Moveset.Special("Block Toss", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 14, 13, 12, 11), 2, pick(s, 26, 25, 24, 22)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(s == 3 ? ProjectileVisual.COBBLE : ProjectileVisual.DIRT)
                        .velocity(pick(s, 6.5f, 8.5f, 10.5f, 9.5f), pick(s, 7f, 7.5f, 6.5f, 7f)).gravity(pick(s, 0.3f, 0.32f, 0.26f, 0.28f))
                        .size(34, 34).offset(30, 100).damage(pick(s, 75f, 80f, 85f, 55f)).stun(20, 14).push(7).spin()
                        .multiHit(s == 3 ? 2 : 1, 10).lifetime(170).build(), pick(s, 14, 13, 12, 11))
                .pose(AnimPose.THROW), "Block Toss")));
        m.specials.add(new Moveset.Special("Diamond Uppercut", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(3, pick(s, 12, 14, 16, 16), pick(s, 22, 25, 28, 26)).damage(pick(s, 100f, 60f, 45f, 55f))
                .box(0, 58, 50, 160).stun(28, 16).push(3).launch(11).knockdown()
                .multiHit(pick(s, 1, 2, 3, 3), 5).leap(pick(s, 11f, 13f, 15f, 15f), 2.2f, 3)
                .invuln(0, pick(s, 4, 5, 6, 12)).landingLag(14).pose(AnimPose.UPPERCUT), "Diamond Uppercut")));
        m.specials.add(new Moveset.Special("Pickaxe Whirlwind", Motion.QCB, true, Role.RUSH, 2, variants(s -> Move.builder("")
                .frames(9, pick(s, 14, 21, 28, 34), 18).damage(40).box(0, 80, 50, 120).stun(18, 10).push(2)
                .multiHit(pick(s, 2, 3, 4, 5), 7).dash(pick(s, 4f, 5f, 6f, 6.5f), 9, pick(s, 23, 30, 37, 43))
                .launch(s >= 2 ? 8 : 0).projInvuln(s == 3 ? 0 : -1, s == 3 ? 30 : -1).pose(AnimPose.SPIN_KICK), "Pickaxe Whirlwind")));
        m.specials.add(new Moveset.Special("Shield Bash", Motion.QCB, false, Role.RUSH, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 12, 14, 16, 12), 10, pick(s, 22, 24, 26, 22))
                    .damage(pick(s, 80f, 90f, 100f, 110f)).box(10, 80, 20, 125).stun(22, 14).push(10).stopOnHit()
                    .dash(pick(s, 7f, 9f, 11f, 11f), pick(s, 12, 14, 16, 12), pick(s, 22, 24, 26, 22)).pose(AnimPose.LUNGE);
            if (s >= 2) b.armor(s == 3 ? 2 : 1, pick(s, 0, 0, 26, 22)).wallSplat().knockdown().launch(5);
            return b;
        }, "Shield Bash")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Crafting Combo").superLevel(1)
                .frames(6, 32, 28).damage(48).box(5, 80, 30, 125).stun(18, 10).push(1.5f).launch(12).knockdown()
                .multiHit(6, 6).dash(6.5f, 6, 26).invuln(0, 10).pose(AnimPose.RUSH).callout("CRAFTING COMBO!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("TNT Barrage").superLevel(2)
                .frames(10, 2, 36).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.TNT).velocity(7.5f, 6f, 8f, 10f).gravity(0.32f)
                        .size(40, 40).offset(25, 100).damage(95).stun(24, 16).push(8).launch(9).knockdown().explodes()
                        .count(3, 8).lifetime(150).build(), 10)
                .pose(AnimPose.THROW).callout("TNT BARRAGE!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Netherite Finale").superLevel(3)
                .frames(8, 72, 34).damage(38).box(5, 85, 20, 130).stun(30, 12).push(0.5f).launch(14).knockdown()
                .multiHit(10, 7).dash(9f, 8, 16).invuln(0, 14).lockOnHit().wallSplat().pose(AnimPose.RUSH)
                .callout("NETHERITE FINALE!").build()));
        universals(m, "Suplex", "Over the Shoulder", 120);
        return m;
    }

    // =====================================================================================================
    // SKELETON: zoner
    // =====================================================================================================

    private static Moveset skeleton() {
        FighterStats stats = new FighterStats(900, 3.7f, 3.5f, 15.5f, 4.6f, 0.75f, 40, 128, 84);
        Moveset m = new Moveset(FighterType.SKELETON, stats);
        Tune t = new Tune(-1, 10, 0.92f, 1f, 0);
        normals(m, t, new String[]{"Bone Jab", "Rib Strike", "Bow Bash", "Shin Rattle", "Femur Kick", "Skull Spinner",
                "Low Rattle", "Rib Poke", "Bow Lift", "Toe Tap", "Femur Poke", "Femur Sweep",
                "Air Jab", "Air Rib", "Bow Spike", "Air Knee", "Air Femur", "Skull Dive"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Bow Cleave", AnimPose.OVERHEAD, 19, 3, 17, 65, 10, 95, 70, 145, HitLevel.HIGH)));
        m.commandNormals.add(new Moveset.CommandNormal(3, Button.HK,
                commandNormal("Shin Splitter", AnimPose.POKE_KICK, 10, 3, 18, 55, 15, 125, 0, 28, HitLevel.LOW)
                        .toBuilder().crouching().build()));

        Move rattle = Move.builder("Rattle Combo").kind(Kind.TARGET).frames(5, 3, 12).damage(45).box(10, 80, 70, 110)
                .stun(16, 11).push(4).pose(AnimPose.STRAIGHT).build();
        Move barrage = Move.builder("Bone Barrage").kind(Kind.TARGET).frames(8, 4, 18).damage(70).box(14, 100, 56, 118)
                .stun(21, 15).push(7).cancelable().pose(AnimPose.HOOK).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LP), Button.MP, rattle));
        m.targetCombos.add(new Moveset.TargetCombo(rattle, Button.HP, barrage));

        m.specials.add(new Moveset.Special("Arrow Shot", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 12, 11, 10, 10), 2, pick(s, 20, 18, 18, 18)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(s == 3 ? ProjectileVisual.BIG_ARROW : ProjectileVisual.ARROW)
                        .velocity(pick(s, 9f, 12f, 15f, 14f), 0f).size(s == 3 ? 56 : 40, s == 3 ? 20 : 14).offset(35, 92)
                        .damage(pick(s, 58f, 62f, 66f, 50f)).stun(18, 12).push(6).multiHit(s == 3 ? 2 : 1, 6)
                        .lifetime(130).build(), pick(s, 12, 11, 10, 10))
                .pose(AnimPose.SHOOT), "Arrow Shot")));
        m.specials.add(new Moveset.Special("Triple Volley", Motion.QCB, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(13, 2, 26).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.ARROW)
                        .velocity(10.5f, s == 0 ? new float[]{0, 3, 6} : s == 1 ? new float[]{2, 5, 8}
                                : s == 2 ? new float[]{4, 7.5f, 11} : new float[]{0, 2.5f, 5, 7.5f, 10})
                        .gravity(0.08f).size(36, 14).offset(35, 96).damage(42).stun(16, 10).push(5)
                        .count(s == 3 ? 5 : 3, 0).lifetime(110).build(), 13)
                .pose(AnimPose.SHOOT_UP), "Triple Volley")));
        m.specials.add(new Moveset.Special("Bone Rattle", Motion.QCF, true, Role.LOW, 2, variants(s -> Move.builder("")
                .frames(10, 14, pick(s, 18, 20, 22, 18)).damage(pick(s, 55f, 62f, 70f, 75f)).box(15, 85, 0, 35)
                .level(HitLevel.LOW).stun(20, 14).push(6).stopOnHit().dash(pick(s, 6f, 8f, 10f, 10f), 10, 24)
                .crouching().pose(AnimPose.SLIDE).projInvuln(s == 3 ? 6 : -1, s == 3 ? 24 : -1)
                .knockdown(), "Bone Rattle")));
        m.specials.add(new Moveset.Special("Rattle Step", Motion.QCB, true, Role.EVADE, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(4, 1, pick(s, 20, 24, 28, 26)).damage(0).box(0, 0, 0, 0)
                    .leap(pick(s, 8f, 10f, 11f, 11f), -pick(s, 4f, 5.5f, 7f, 7f), 2).invuln(0, pick(s, 10, 12, 14, 18))
                    .pose(AnimPose.BACKFLIP);
            if (s == 3) b.projectile(ProjectileSpec.builder(ProjectileVisual.ARROW).velocity(13f, -2f).size(40, 14)
                    .offset(35, 100).damage(60).stun(18, 12).push(6).lifetime(120).build(), 16);
            return b;
        }, "Rattle Step")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Arrow Rain").superLevel(1)
                .frames(14, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.ARROW).velocity(0f, -15f).size(18, 40)
                        .damage(46).stun(18, 12).push(3).count(7, 6).fromSky(170, 520).lifetime(90).build(), 14)
                .pose(AnimPose.SHOOT_UP).callout("ARROW RAIN!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Sniper Shot").superLevel(2)
                .frames(16, 2, 36).damage(0).box(0, 0, 0, 0).invuln(0, 16)
                .projectile(ProjectileSpec.builder(ProjectileVisual.BIG_ARROW).velocity(22f, 0f).size(70, 24).offset(40, 92)
                        .damage(230).stun(30, 20).push(12).launch(9).knockdown().durable().wallSplat().lifetime(80).build(), 16)
                .pose(AnimPose.SHOOT).callout("SNIPER SHOT!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Wither Storm").superLevel(3)
                .frames(16, 2, 50).damage(0).box(0, 0, 0, 0).invuln(0, 20)
                .projectile(ProjectileSpec.builder(ProjectileVisual.SKULL).velocity(0f, -12f).size(40, 40)
                        .damage(48).stun(24, 14).push(3).launch(4).count(10, 5).fromSky(150, 560).explodes()
                        .lifetime(100).build(), 16)
                .pose(AnimPose.SUMMON).callout("WITHER STORM!").build()));
        universals(m, "Bone Toss", "Grave Throw", 110);
        return m;
    }

    // =====================================================================================================
    // VILLAGER: grappler
    // =====================================================================================================

    private static Moveset villager() {
        FighterStats stats = new FighterStats(1050, 2.9f, 2.6f, 14f, 3.8f, 0.75f, 46, 128, 86);
        Moveset m = new Moveset(FighterType.VILLAGER, stats);
        Tune t = new Tune(0, 0, 1.05f, 1f, 0);
        normals(m, t, new String[]{"Nose Bonk", "Robe Slap", "Emerald Slap", "Hmm Kick", "Shin Swat", "Robe Spin",
                "Low Bonk", "Gut Slap", "Hmm Rise", "Shin Tap", "Robe Poke", "Robe Sweep",
                "Air Bonk", "Air Slap", "Flying Nose", "Air Toe", "Air Kick", "Robe Drop"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Nose Smash", AnimPose.HEADBUTT, 13, 4, 18, 75, 10, 85, 85, 130, HitLevel.MID)
                        .toBuilder().dash(4f, 6, 14).cancelable().build()));
        m.commandNormals.add(new Moveset.CommandNormal(4, Button.HK,
                commandNormal("Robe Whip", AnimPose.SPIN_KICK, 11, 9, 18, 42, 0, 88, 30, 100, HitLevel.MID)
                        .toBuilder().multiHit(2, 6).build()));

        Move haggle = Move.builder("Haggle Slap").kind(Kind.TARGET).frames(8, 3, 18).damage(72).box(12, 90, 56, 118)
                .stun(21, 15).push(7).cancelable().pose(AnimPose.PALM).build();
        Move shins = Move.builder("Shin Barter").kind(Kind.TARGET).frames(7, 3, 15).damage(50).box(14, 90, 0, 35)
                .level(HitLevel.LOW).stun(17, 12).push(5).cancelable().crouching().pose(AnimPose.LOW_KICK).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, haggle));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LK), Button.MK, shins));

        m.specials.add(new Moveset.Special("Emerald Toss", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(12, 2, pick(s, 24, 22, 22, 20)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.EMERALD).velocity(pick(s, 5.5f, 6.8f, 8.5f, 7.5f), 7f)
                        .gravity(0.38f).bounce(0.72f).size(28, 28).offset(30, 95).damage(pick(s, 55f, 58f, 62f, 50f))
                        .stun(18, 12).push(6).spin().count(s == 3 ? 2 : 1, 10).lifetime(150).build(), 12)
                .pose(AnimPose.THROW), "Emerald Toss")));
        m.specials.add(new Moveset.Special("Bell Ringer", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(4, pick(s, 12, 14, 16, 16), pick(s, 22, 26, 30, 26)).damage(pick(s, 95f, 60f, 55f, 58f))
                .box(0, 60, 70, 165).stun(28, 16).push(3).launch(11).knockdown().multiHit(pick(s, 1, 2, 2, 3), 6)
                .leap(pick(s, 11f, 13.5f, 15f, 15f), 2f, 4).invuln(0, pick(s, 3, 5, 7, 12)).landingLag(14)
                .pose(AnimPose.HEADBUTT), "Bell Ringer")));
        m.specials.add(new Moveset.Special("Price Hike", Motion.QCB, true, Role.OVERHEAD, 2, variants(s -> {
            Move.Builder b = Move.builder("").frames(14, 1, 20).damage(pick(s, 85f, 92f, 100f, 105f)).box(-20, 85, -10, 70)
                    .level(HitLevel.HIGH).stun(24, 16).push(7).launch(5).knockdown().untilLanding()
                    .leap(9f, pick(s, 3f, 4.5f, 6f, 6f), 4).pose(AnimPose.STOMP);
            if (s == 3) b.groundBounce();
            return b;
        }, "Price Hike")));
        m.specials.add(new Moveset.Special("Trade Offer", Motion.HCB, false, Role.GRAB, 4, variants(s -> Move.builder("")
                .frames(pick(s, 6, 7, 8, 5), 3, 32).damage(pick(s, 130f, 145f, 160f, 200f))
                .box(5, pick(s, 70, 80, 90, 85), 20, 120).level(HitLevel.GRAB).stun(30, 0).push(10).launch(9).knockdown()
                .invuln(s == 3 ? 0 : -1, s == 3 ? 6 : -1).pose(AnimPose.GRAB).callout(s == 3 ? "EX Trade Offer!" : "Hmm? DEAL!"),
                "Trade Offer")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Iron Golem Assist").superLevel(1)
                .frames(10, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.GOLEM).velocity(0f, -13f).size(120, 190)
                        .damage(250).stun(40, 24).push(10).launch(10).knockdown().count(1, 0).fromSky(0, 480)
                        .durable().lifetime(80).build(), 10)
                .pose(AnimPose.SUMMON).callout("IRON GOLEM ASSIST!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Raid Alarm").superLevel(2)
                .frames(12, 2, 34).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.BELL).velocity(9f, 0f).size(70, 80).offset(30, 0)
                        .damage(70).stun(20, 14).push(4).launch(6).knockdown().multiHit(4, 7).durable().lifetime(90).build(), 12)
                .pose(AnimPose.TAUNT).callout("RAID ALARM!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Hero of the Village").superLevel(3)
                .frames(6, 70, 34).damage(45).box(5, 85, 20, 125).level(HitLevel.GRAB).stun(30, 0).push(0.5f).launch(13)
                .knockdown().multiHit(8, 8).invuln(0, 8).lockOnHit().wallSplat().pose(AnimPose.GRAB)
                .callout("HERO OF THE VILLAGE!").build()));
        universals(m, "Bad Deal", "Return Policy", 125);
        return m;
    }

    // =====================================================================================================
    // PIG: rushdown tank
    // =====================================================================================================

    private static Moveset pig() {
        FighterStats stats = new FighterStats(1100, 3.1f, 2.5f, 13f, 4.4f, 0.8f, 104, 84, 62);
        Moveset m = new Moveset(FighterType.PIG, stats);
        Tune t = new Tune(1, -4, 1.0f, 0.66f, 30);
        normals(m, t, new String[]{"Snout Poke", "Snout Jab", "Ham Slam", "Hoof Tap", "Hoof Kick", "Rump Bump",
                "Truffle Dig", "Low Snout", "Snout Lift", "Hoof Poke", "Mud Poke", "Mud Swipe",
                "Air Oink", "Air Snout", "Ham Drop", "Air Hoof", "Air Kick", "Belly Drop"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.MK,
                commandNormal("Hoof Overhead", AnimPose.STOMP, 17, 3, 16, 62, 30, 96, 10, 90, HitLevel.HIGH)));
        m.commandNormals.add(new Moveset.CommandNormal(3, Button.HK,
                commandNormal("Truffle Sweep", AnimPose.SWEEP, 10, 4, 24, 80, 30, 112, 0, 28, HitLevel.LOW)
                        .toBuilder().knockdown().crouching().build()));

        Move oink = Move.builder("Oink Rush").kind(Kind.TARGET).frames(5, 3, 12).damage(48).box(34, 96, 20, 72)
                .stun(17, 12).push(4).cancelable().pose(AnimPose.HEADBUTT).build();
        Move hammer = Move.builder("Ham Hammer").kind(Kind.TARGET).frames(9, 4, 20).damage(78).box(30, 100, 10, 80)
                .stun(22, 15).push(8).cancelable().pose(AnimPose.STOMP).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LP), Button.MP, oink));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, hammer));

        m.specials.add(new Moveset.Special("Hog Charge", Motion.QCF, false, Role.RUSH, 5, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 14, 12, 10, 10), 24, pick(s, 26, 28, 30, 24))
                    .damage(pick(s, 80f, 88f, 95f, 105f)).box(30, 96, 10, 78).stun(24, 16).push(14).launch(6).knockdown()
                    .stopOnHit().dash(pick(s, 8f, 10.5f, 13f, 13f), pick(s, 14, 12, 10, 10), pick(s, 38, 36, 34, 34))
                    .pose(AnimPose.CHARGE);
            if (s == 3) b.armor(1, 30).wallSplat();
            return b;
        }, "Hog Charge")));
        m.specials.add(new Moveset.Special("Snout Uppercut", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(4, pick(s, 12, 14, 16, 16), pick(s, 22, 26, 30, 26)).damage(pick(s, 95f, 105f, 115f, 125f))
                .box(20, 85, 30, 150).stun(28, 16).push(3).launch(11).knockdown()
                .leap(pick(s, 10f, 12f, 14f, 14f), 2.5f, 4).invuln(0, pick(s, 3, 5, 6, 12)).landingLag(14)
                .pose(AnimPose.ANTI_AIR), "Snout Uppercut")));
        m.specials.add(new Moveset.Special("Mud Slide", Motion.QCF, true, Role.LOW, 2, variants(s -> {
            Move.Builder b = Move.builder("").frames(8, 14, pick(s, 18, 20, 22, 18)).damage(pick(s, 60f, 66f, 72f, 80f))
                    .box(30, 100, 0, 35).level(HitLevel.LOW).stun(20, 14).push(6).knockdown()
                    .dash(pick(s, 6f, 8f, 10f, 10f), 8, 22).crouching().pose(AnimPose.SLIDE);
            if (s == 3) b.wallSplat();
            return b;
        }, "Mud Slide")));
        m.specials.add(new Moveset.Special("Belly Flop", Motion.QCB, true, Role.OVERHEAD, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(14, 1, 22).damage(pick(s, 88f, 95f, 102f, 110f)).box(-26, 98, -10, 70)
                    .level(HitLevel.HIGH).stun(24, 16).push(8).launch(5).knockdown().untilLanding()
                    .leap(pick(s, 12f, 14.5f, 16f, 15f), pick(s, 3f, 4.5f, 6f, 5f), 4).pose(AnimPose.BELLY_FLOP);
            if (s == 3) b.groundBounce().armor(1, 14);
            return b;
        }, "Belly Flop")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Stampede").superLevel(1)
                .frames(10, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.PIG).velocity(11.5f, 0f).size(90, 80).offset(-220, 0)
                        .damage(90).stun(22, 14).push(8).launch(7).knockdown().count(3, 16).durable().lifetime(160).build(), 10)
                .pose(AnimPose.SUMMON).callout("STAMPEDE!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Carrot Frenzy").superLevel(2)
                .frames(7, 60, 30).damage(34).box(30, 100, 10, 85).stun(28, 12).push(0.5f).launch(12).knockdown()
                .multiHit(8, 7).dash(8f, 7, 15).invuln(0, 10).lockOnHit().pose(AnimPose.RUSH).callout("CARROT FRENZY!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Pigstep Apocalypse").superLevel(3)
                .frames(18, 1, 34).damage(150).box(-60, 160, -10, 90).stun(40, 20).push(10).launch(13).knockdown()
                .groundBounce().untilLanding().leap(20f, 3f, 6).invuln(0, 30)
                .projectile(ProjectileSpec.builder(ProjectileVisual.PIG).velocity(12f, 0f).size(90, 80).offset(-260, 0)
                        .damage(80).stun(22, 14).push(8).launch(7).knockdown().count(5, 10).durable().lifetime(170).build(), 18)
                .pose(AnimPose.BELLY_FLOP).callout("PIGSTEP APOCALYPSE!").build()));
        universals(m, "Mud Toss", "Pig Pile", 125);
        return m;
    }

    // =====================================================================================================
    // CHICKEN: tiny aerial rushdown (double jump, glide)
    // =====================================================================================================

    private static Moveset chicken() {
        FighterStats stats = new FighterStats(960, 4.3f, 3.9f, 15.5f, 5.0f, 0.75f, 60, 80, 56);
        Moveset m = new Moveset(FighterType.CHICKEN, stats);
        m.airJumps = 1;
        m.glideFrames = 50;
        Tune t = new Tune(-1, 0, 1.06f, 0.62f, 10);
        normals(m, t, new String[]{"Peck", "Double Peck", "Wing Slap", "Talon Tap", "Claw Kick", "Spur Spin",
                "Low Peck", "Gizzard Jab", "Rooster Lift", "Toe Peck", "Scratch Poke", "Dust Sweep",
                "Air Peck", "Air Wing", "Beak Dive", "Air Talon", "Air Scratch", "Drumstick Drop"});
        // chicken punches are pecks and wing slaps
        m.stand.put(Button.LP, m.stand.get(Button.LP).toBuilder().pose(AnimPose.PECK).build());
        m.stand.put(Button.MP, m.stand.get(Button.MP).toBuilder().pose(AnimPose.PECK).frames(5, 7, 11).multiHit(2, 4).damage(30).build());
        m.stand.put(Button.HP, m.stand.get(Button.HP).toBuilder().pose(AnimPose.FLAP).build());
        m.crouch.put(Button.LP, m.crouch.get(Button.LP).toBuilder().pose(AnimPose.PECK).build());
        m.air.put(Button.LP, m.air.get(Button.LP).toBuilder().pose(AnimPose.PECK).build());
        m.air.put(Button.MP, m.air.get(Button.MP).toBuilder().pose(AnimPose.FLAP).build());
        m.air.put(Button.HP, m.air.get(Button.HP).toBuilder().pose(AnimPose.DIVE_KICK).box(0, 70, -10, 60).build());

        m.commandNormals.add(new Moveset.CommandNormal(6, Button.MP,
                commandNormal("Crest Strike", AnimPose.PECK, 16, 3, 16, 58, 10, 80, 30, 95, HitLevel.HIGH)
                        .toBuilder().leap(5f, 1.5f, 3).build()));
        m.commandNormals.add(new Moveset.CommandNormal(3, Button.MK,
                commandNormal("Scratch Slide", AnimPose.SLIDE, 9, 6, 18, 48, 10, 105, 0, 26, HitLevel.LOW)
                        .toBuilder().dash(4.5f, 6, 15).crouching().build()));

        Move flurry = Move.builder("Peck Flurry").kind(Kind.TARGET).frames(5, 9, 12).damage(18).box(10, 72, 30, 75)
                .stun(16, 11).push(1.5f).multiHit(3, 3).cancelable().pose(AnimPose.PECK).build();
        Move buffet = Move.builder("Wing Buffet").kind(Kind.TARGET).frames(8, 4, 20).damage(70).box(10, 88, 25, 90)
                .stun(22, 15).push(9).launch(6).knockdown().pose(AnimPose.FLAP).build();
        Move twister = Move.builder("Talon Twister").kind(Kind.TARGET).frames(7, 8, 16).damage(30).box(10, 85, 10, 60)
                .stun(17, 12).push(4).multiHit(2, 5).cancelable().pose(AnimPose.SPIN_KICK).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LP), Button.MP, flurry));
        m.targetCombos.add(new Moveset.TargetCombo(flurry, Button.HP, buffet));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LK), Button.MK, twister));

        m.specials.add(new Moveset.Special("Egg Toss", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 12, 11, 10, 10), 2, pick(s, 20, 19, 18, 18)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(s == 3 ? ProjectileVisual.GOLDEN_EGG : ProjectileVisual.EGG)
                        .velocity(pick(s, 7f, 9f, 11.5f, 10f), pick(s, 6f, 4.5f, 2.5f, 3.5f)).gravity(pick(s, 0.26f, 0.2f, 0.12f, 0.14f))
                        .size(24, 28).offset(25, 55).damage(pick(s, 48f, 52f, 56f, 40f)).stun(18, 12).push(5).spin()
                        .multiHit(s == 3 ? 3 : 1, 6).lifetime(140).build(), pick(s, 12, 11, 10, 10))
                .pose(AnimPose.PECK), "Egg Toss")));
        m.specials.add(new Moveset.Special("Rooster Rise", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(3, pick(s, 14, 16, 18, 20), pick(s, 20, 23, 26, 24)).damage(pick(s, 85f, 40f, 34f, 36f))
                .box(-5, 62, 20, 150).stun(26, 16).push(3).launch(10).knockdown()
                .multiHit(pick(s, 1, 3, 4, 5), 4).leap(pick(s, 10f, 12.5f, 14.5f, 15f), 1.8f, 3)
                .invuln(0, pick(s, 4, 5, 7, 14)).landingLag(12).pose(AnimPose.ANTI_AIR), "Rooster Rise")));
        m.specials.add(new Moveset.Special("Fowl Play", Motion.QCF, true, Role.RUSH, 2, variants(s -> Move.builder("")
                .frames(pick(s, 10, 9, 8, 8), pick(s, 14, 18, 22, 24), pick(s, 16, 18, 20, 16)).damage(pick(s, 26f, 26f, 26f, 28f))
                .box(10, 80, 0, 60).stun(16, 10).push(1.5f).multiHit(pick(s, 3, 4, 5, 6), 4)
                .dash(pick(s, 6f, 7.5f, 9f, 9f), pick(s, 10, 9, 8, 8), pick(s, 24, 27, 30, 32)).launch(s >= 2 ? 6 : 0)
                .knockdown().crouching().projInvuln(s == 3 ? 0 : -1, s == 3 ? 30 : -1).pose(AnimPose.PECK), "Fowl Play")));
        m.specials.add(new Moveset.Special("Feather Dive", Motion.QCB, true, Role.OVERHEAD, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(13, 1, 18).damage(pick(s, 70f, 78f, 86f, 92f)).box(-10, 70, -10, 60)
                    .level(HitLevel.HIGH).stun(22, 14).push(6).launch(4).knockdown().untilLanding()
                    .leap(pick(s, 11f, 12.5f, 14f, 14f), pick(s, 4f, 5.5f, 7f, 7f), 3).pose(AnimPose.DIVE_KICK);
            if (s == 3) b.groundBounce().invuln(0, 10);
            return b;
        }, "Feather Dive")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Egg Barrage").superLevel(1)
                .frames(10, 2, 36).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.GOLDEN_EGG).velocity(10f, 2f, 3.5f, 5f, 6.5f, 8f, 4f)
                        .gravity(0.2f).size(26, 30).offset(25, 60).damage(44).stun(18, 12).push(4).launch(3).spin()
                        .count(6, 5).lifetime(130).build(), 10)
                .pose(AnimPose.FLAP).callout("EGG BARRAGE!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Flock Call").superLevel(2)
                .frames(12, 2, 34).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.CHICKEN).velocity(10.5f, 0f, 1f, 0.2f, 0.6f, 1.4f, 0.3f)
                        .size(50, 50).offset(-200, 70).damage(55).stun(20, 14).push(5).launch(5).knockdown()
                        .count(6, 7).durable().lifetime(160).build(), 12)
                .pose(AnimPose.SCREAM).callout("FLOCK CALL!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Phoenix Fury").superLevel(3)
                .frames(6, 70, 34).damage(36).box(5, 80, 0, 90).stun(30, 12).push(0.5f).launch(15).knockdown()
                .multiHit(11, 6).dash(9.5f, 6, 14).invuln(0, 12).lockOnHit().pose(AnimPose.PECK)
                .callout("PHOENIX FURY!").build()));
        universals(m, "Wing Toss", "Coop Flip", 105);
        return m;
    }

    // =====================================================================================================
    // ALCHEMIST (cleric villager): status zoner, potions, lingering clouds, self-buffs
    // =====================================================================================================

    private static Moveset alchemist() {
        FighterStats stats = new FighterStats(950, 2.8f, 2.7f, 14f, 3.8f, 0.75f, 46, 128, 86);
        Moveset m = new Moveset(FighterType.ALCHEMIST, stats);
        Tune t = new Tune(0, 0, 0.95f, 1f, 0);
        normals(m, t, new String[]{"Flask Jab", "Robe Slap", "Bottle Bash", "Hmm Kick", "Shin Swat", "Robe Spin",
                "Low Flask", "Vial Poke", "Brew Rise", "Toe Tap", "Robe Poke", "Robe Sweep",
                "Air Flask", "Air Slap", "Bottle Drop", "Air Toe", "Air Kick", "Robe Drop"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Flask Smash", AnimPose.OVERHEAD, 18, 3, 17, 60, 10, 85, 70, 140, HitLevel.HIGH)
                        .toBuilder().hitEffect(StatusEffect.SLOW, 120).build()));
        m.commandNormals.add(new Moveset.CommandNormal(4, Button.MP,
                commandNormal("Vial Spray", AnimPose.PALM, 9, 8, 16, 26, 10, 82, 60, 120, HitLevel.MID)
                        .toBuilder().multiHit(2, 5).hitEffect(StatusEffect.POISON, 100).cancelable().build()));

        Move catalyst = Move.builder("Catalyst").kind(Kind.TARGET).frames(8, 3, 18).damage(70).box(12, 90, 56, 118)
                .stun(21, 15).push(7).cancelable().pose(AnimPose.PALM).build();
        Move reagent = Move.builder("Reagent Kick").kind(Kind.TARGET).frames(7, 3, 15).damage(48).box(14, 90, 0, 35)
                .level(HitLevel.LOW).stun(17, 12).push(5).cancelable().crouching().pose(AnimPose.LOW_KICK).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, catalyst));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LK), Button.MK, reagent));

        m.specials.add(new Moveset.Special("Splash Potion", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 13, 13, 14, 12), 2, pick(s, 22, 22, 24, 20)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.POTION)
                        .velocity(pick(s, 6f, 7.5f, 9f, 8f), pick(s, 7f, 6.5f, 5.5f, 6f)).gravity(0.3f).size(24, 30)
                        .offset(30, 105).damage(pick(s, 40f, 46f, 80f, 45f)).stun(18, 12).push(5).explodes().blast(110)
                        .effect(s == 0 ? StatusEffect.POISON : s == 1 ? StatusEffect.SLOW : s == 3 ? StatusEffect.POISON : null,
                                pick(s, 240, 180, 0, 300))
                        .count(s == 3 ? 2 : 1, 9).spin().lifetime(150).build(), pick(s, 13, 13, 14, 12))
                .pose(AnimPose.THROW), "Splash Potion")));
        m.specials.add(new Moveset.Special("Lingering Cloud", Motion.QCB, false, Role.TRAP, 2, variants(s -> Move.builder("")
                .frames(16, 2, pick(s, 24, 26, 28, 24)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.CLOUD).velocity(pick(s, 0.5f, 0.9f, 1.3f, 1f), 0f)
                        .size(s == 3 ? 150 : 120, 110).offset(pick(s, 90f, 150f, 210f, 150f), 0).damage(4)
                        .multiHit(99, 20).effect(s == 3 ? StatusEffect.SLOW : StatusEffect.POISON, s == 3 ? 150 : 120)
                        .aura().lifetime(pick(s, 160, 190, 220, 260)).build(), 16)
                .pose(AnimPose.THROW), "Lingering Cloud")));
        m.specials.add(new Moveset.Special("Brew Up", Motion.QCB, true, Role.BUFF, 4, variants(s -> Move.builder("")
                .frames(22, 1, 18).damage(0).box(0, 0, 0, 0)
                .selfEffect(pick(s, 600, 480, 360, 480), s == 0 ? new StatusEffect[]{StatusEffect.SPEED}
                        : s == 1 ? new StatusEffect[]{StatusEffect.STRENGTH} : s == 2 ? new StatusEffect[]{StatusEffect.REGEN}
                        : new StatusEffect[]{StatusEffect.SPEED, StatusEffect.STRENGTH, StatusEffect.REGEN})
                .pose(AnimPose.DRINK), "Brew Up")));
        m.specials.add(new Moveset.Special("Fizz Rocket", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(4, pick(s, 12, 14, 16, 16), pick(s, 22, 26, 30, 26)).damage(pick(s, 90f, 52f, 40f, 42f))
                .box(-15, 60, 40, 165).stun(28, 16).push(3).launch(11).knockdown().multiHit(pick(s, 1, 2, 3, 3), 5)
                .leap(pick(s, 11f, 13f, 15f, 15f), 1.6f, 4).invuln(0, pick(s, 3, 5, 7, 12)).landingLag(14)
                .hitEffect(s == 3 ? StatusEffect.POISON : null, 180).pose(AnimPose.UPPERCUT), "Fizz Rocket")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Potion Barrage").superLevel(1)
                .frames(10, 2, 36).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.POTION).velocity(8f, 4f, 5.5f, 7f, 8.5f, 10f)
                        .gravity(0.3f).size(24, 30).offset(30, 105).damage(48).stun(20, 14).push(5).launch(4).explodes()
                        .blast(100).effect(StatusEffect.POISON, 300).count(5, 5).spin().lifetime(150).build(), 10)
                .pose(AnimPose.THROW).callout("POTION BARRAGE!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Witch's Brew").superLevel(2)
                .frames(16, 2, 30).damage(0).box(0, 0, 0, 0).invuln(0, 20)
                .selfEffect(600, StatusEffect.STRENGTH, StatusEffect.SPEED, StatusEffect.REGEN)
                .projectile(ProjectileSpec.builder(ProjectileVisual.POTION).velocity(0f, 0f).size(30, 30).offset(80, 70)
                        .damage(170).stun(30, 18).push(10).launch(9).knockdown().explodes().blast(230)
                        .effect(StatusEffect.SLOW, 300).lifetime(2).build(), 16)
                .pose(AnimPose.DRINK).callout("WITCH'S BREW!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Elixir of the Ancients").superLevel(3)
                .frames(6, 70, 34).damage(44).box(5, 85, 20, 125).level(HitLevel.GRAB).stun(30, 0).push(0.5f).launch(13)
                .knockdown().multiHit(8, 8).invuln(0, 8).lockOnHit().wallSplat().hitEffect(StatusEffect.POISON, 480)
                .selfEffect(600, StatusEffect.REGEN).pose(AnimPose.GRAB).callout("ELIXIR OF THE ANCIENTS!").build()));
        universals(m, "Bad Reaction", "Spill", 115);
        return m;
    }

    // =====================================================================================================
    // GOAT: high-jumping rammer, huge knockback, wall splats, scream shockwave
    // =====================================================================================================

    private static Moveset goat() {
        FighterStats stats = new FighterStats(960, 2.9f, 2.5f, 18f, 4.4f, 0.8f, 80, 100, 72);
        Moveset m = new Moveset(FighterType.GOAT, stats);
        Tune t = new Tune(1, -4, 0.85f, 0.75f, 24);
        normals(m, t, new String[]{"Horn Poke", "Horn Jab", "Horn Hook", "Hoof Tap", "Hoof Kick", "Buck Kick",
                "Low Horn", "Gut Butt", "Horn Lift", "Hoof Poke", "Scree Poke", "Scree Sweep",
                "Air Horn", "Air Butt", "Summit Drop", "Air Hoof", "Air Buck", "Peak Stomp"});
        m.stand.put(Button.HP, m.stand.get(Button.HP).toBuilder().push(10).build());
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Skull Crusher", AnimPose.STOMP, 18, 3, 17, 68, 24, 105, 10, 100, HitLevel.HIGH)));
        m.commandNormals.add(new Moveset.CommandNormal(4, Button.HK,
                commandNormal("Mule Kick", AnimPose.THROW_BACK, 12, 4, 22, 82, 24, 118, 20, 80, HitLevel.MID)
                        .toBuilder().push(16).launch(5).knockdown().wallSplat().build()));

        Move butt = Move.builder("Double Butt").kind(Kind.TARGET).frames(8, 4, 18).damage(75).box(30, 108, 15, 85)
                .stun(22, 15).push(9).cancelable().pose(AnimPose.HEADBUTT).build();
        Move flurry = Move.builder("Hoof Flurry").kind(Kind.TARGET).frames(7, 8, 16).damage(32).box(30, 100, 0, 60)
                .stun(17, 12).push(4).multiHit(2, 5).cancelable().pose(AnimPose.FRONT_KICK).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, butt));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LK), Button.MK, flurry));

        m.specials.add(new Moveset.Special("Ram Charge", Motion.QCF, false, Role.RUSH, 5, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 18, 16, 14, 13), 24, pick(s, 28, 30, 32, 26))
                    .damage(pick(s, 78f, 86f, 96f, 106f)).box(30, 112, 10, 90).stun(26, 16).push(16).launch(7).knockdown()
                    .stopOnHit().dash(pick(s, 9f, 11f, 13.5f, 13.5f), pick(s, 18, 16, 14, 13), pick(s, 42, 40, 38, 37))
                    .pose(AnimPose.CHARGE);
            if (s >= 2) b.wallSplat();
            if (s == 3) b.armor(2, 34);
            return b;
        }, "Ram Charge")));
        m.specials.add(new Moveset.Special("Horn Toss", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(5, pick(s, 12, 14, 16, 16), pick(s, 26, 29, 32, 28)).damage(pick(s, 92f, 102f, 112f, 122f))
                .box(20, 95, 30, 170).stun(30, 16).push(4).launch(pick(s, 12f, 13.5f, 15f, 15f)).knockdown()
                .leap(pick(s, 9f, 11f, 13f, 13f), 2f, 5).invuln(0, pick(s, 4, 5, 7, 12)).landingLag(14)
                .pose(AnimPose.ANTI_AIR), "Horn Toss")));
        m.specials.add(new Moveset.Special("Screaming Bleat", Motion.QCB, false, Role.PROJECTILE, 2, variants(s -> Move.builder("")
                .frames(pick(s, 15, 14, 13, 12), 2, pick(s, 24, 24, 24, 20)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.SHOCKWAVE).velocity(pick(s, 6f, 7.5f, 9f, 8.5f), 0f)
                        .size(56, 70).offset(45, 0).damage(pick(s, 46f, 50f, 55f, 36f)).stun(20, 14).push(7)
                        .level(HitLevel.LOW).effect(StatusEffect.SLOW, pick(s, 90, 100, 110, 150))
                        .multiHit(s == 3 ? 2 : 1, 7).durable().lifetime(pick(s, 45, 60, 75, 80)).build(), pick(s, 15, 14, 13, 12))
                .pose(AnimPose.SCREAM), "Screaming Bleat")));
        m.specials.add(new Moveset.Special("Goat Leap", Motion.QCB, true, Role.OVERHEAD, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(15, 1, 20).damage(pick(s, 90f, 98f, 106f, 115f)).box(-20, 110, -10, 80)
                    .level(HitLevel.HIGH).stun(24, 16).push(9).launch(6).knockdown().untilLanding()
                    .leap(pick(s, 15f, 17f, 19f, 18f), pick(s, 3f, 4.5f, 6f, 5.5f), 4).pose(AnimPose.STOMP);
            if (s == 3) b.groundBounce().armor(1, 15);
            return b;
        }, "Goat Leap")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Avalanche").superLevel(1)
                .frames(14, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.SNOWBALL).velocity(0f, -13f).size(34, 34)
                        .damage(50).stun(22, 12).push(3).launch(3).effect(StatusEffect.SLOW, 180).count(9, 5).fromSky(110, 540)
                        .lifetime(100).build(), 14)
                .pose(AnimPose.SCREAM).callout("AVALANCHE!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Herd Charge").superLevel(2)
                .frames(10, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.GOAT).velocity(12.5f, 0f).size(90, 90).offset(-230, 0)
                        .damage(75).stun(26, 14).push(6).launch(7).count(3, 6).durable()
                        .lifetime(160).build(), 10)
                .pose(AnimPose.SCREAM).callout("HERD CHARGE!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Peak Breaker").superLevel(3)
                .frames(8, 66, 34).damage(40).box(30, 110, 10, 95).stun(30, 12).push(0.5f).launch(14).knockdown()
                .multiHit(9, 7).dash(10f, 8, 16).invuln(0, 14).lockOnHit().wallSplat().pose(AnimPose.CHARGE)
                .callout("PEAK BREAKER!").build()));
        universals(m, "Head Toss", "Cliff Drop", 125);
        return m;
    }

    // =====================================================================================================
    // OAK TREE: slow, huge, long-reaching bruiser with armor, apples and erupting roots
    // =====================================================================================================

    private static Moveset oak() {
        FighterStats stats = new FighterStats(1200, 2.6f, 2.3f, 13.5f, 3.4f, 0.8f, 64, 170, 112);
        Moveset m = new Moveset(FighterType.OAK, stats);
        Tune t = new Tune(2, 18, 1.15f, 1.15f, 6);
        normals(m, t, new String[]{"Twig Jab", "Branch Swat", "Log Hammer", "Root Tap", "Root Kick", "Trunk Spin",
                "Low Twig", "Bark Punch", "Canopy Rise", "Root Poke", "Root Lash", "Root Sweep",
                "Air Twig", "Air Branch", "Timber Drop", "Air Root", "Air Lash", "Stump Stomp"});
        // heavy branches don't flinch from a single jab
        m.stand.put(Button.HP, m.stand.get(Button.HP).toBuilder().armor(1, 12).build());

        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Timber!", AnimPose.SLAM, 22, 4, 22, 100, 10, 120, 0, 190, HitLevel.HIGH)
                        .toBuilder().knockdown().launch(3).groundBounce().build()));
        m.commandNormals.add(new Moveset.CommandNormal(3, Button.HK,
                commandNormal("Root Snare", AnimPose.POKE_KICK, 11, 4, 22, 58, 20, 165, 0, 26, HitLevel.LOW)
                        .toBuilder().crouching().hitEffect(StatusEffect.SLOW, 90).build()));

        Move barrage = Move.builder("Branch Barrage").kind(Kind.TARGET).frames(9, 4, 20).damage(78).box(14, 118, 60, 160)
                .stun(22, 15).push(9).cancelable().pose(AnimPose.HOOK).build();
        Move rumble = Move.builder("Root Rumble").kind(Kind.TARGET).frames(8, 8, 18).damage(34).box(14, 120, 0, 40)
                .level(HitLevel.LOW).stun(18, 12).push(5).multiHit(2, 5).cancelable().crouching().pose(AnimPose.STOMP).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, barrage));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LK), Button.MK, rumble));

        m.specials.add(new Moveset.Special("Apple Toss", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 14, 13, 12, 12), 2, pick(s, 24, 23, 22, 22)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.APPLE)
                        .velocity(pick(s, 6f, 8f, 10f, 9f), pick(s, 4f, 3f, 2f, 3f), 5f, 7f).gravity(pick(s, 0.26f, 0.24f, 0.2f, 0.22f))
                        .size(30, 30).offset(40, 140).damage(pick(s, 56f, 60f, 64f, 46f)).stun(19, 13).push(6).spin()
                        .count(s == 3 ? 3 : 1, 6).lifetime(170).build(), pick(s, 14, 13, 12, 12))
                .pose(AnimPose.THROW), "Apple Toss")));
        m.specials.add(new Moveset.Special("Root Eruption", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(pick(s, 12, 14, 16, 12), 2, pick(s, 26, 28, 30, 24)).damage(0).box(0, 0, 0, 0).invuln(0, s == 3 ? 12 : 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.ROOTS).velocity(0f, 0f)
                        .size(s == 3 ? 130 : 70, 190).offset(pick(s, 100f, 160f, 220f, 150f), 0)
                        .damage(pick(s, 80f, 86f, 92f, 100f)).stun(28, 16).push(3).launch(12).knockdown()
                        .durable().lifetime(28).build(), pick(s, 12, 14, 16, 12))
                .pose(AnimPose.STOMP), "Root Eruption")));
        m.specials.add(new Moveset.Special("Timber Rush", Motion.QCF, true, Role.RUSH, 2, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 16, 15, 14, 13), 22, pick(s, 26, 28, 30, 24))
                    .damage(pick(s, 82f, 90f, 100f, 112f)).box(15, 105, 20, 160).stun(24, 16).push(13).launch(6).knockdown()
                    .stopOnHit().dash(pick(s, 7f, 8.5f, 10f, 10f), pick(s, 16, 15, 14, 13), pick(s, 38, 37, 36, 35))
                    .pose(AnimPose.CHARGE);
            if (s >= 1) b.armor(s == 3 ? 2 : 1, pick(s, 0, 24, 26, 30));
            if (s == 3) b.wallSplat();
            return b;
        }, "Timber Rush")));
        m.specials.add(new Moveset.Special("Canopy Crash", Motion.QCB, true, Role.OVERHEAD, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(16, 1, 22).damage(pick(s, 92f, 100f, 110f, 118f)).box(-30, 120, -10, 120)
                    .level(HitLevel.HIGH).stun(24, 16).push(8).launch(5).knockdown().untilLanding()
                    .leap(pick(s, 12f, 13.5f, 15f, 15f), pick(s, 2.5f, 3.5f, 4.5f, 4f), 5).pose(AnimPose.SLAM);
            if (s == 3) b.groundBounce().armor(1, 16);
            return b;
        }, "Canopy Crash")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Orchard Rain").superLevel(1)
                .frames(14, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.APPLE).velocity(0f, -12f).size(30, 30)
                        .damage(46).stun(20, 12).push(3).launch(3).count(10, 5).fromSky(120, 540).lifetime(100).build(), 14)
                .pose(AnimPose.SUMMON).callout("ORCHARD RAIN!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Root Wave").superLevel(2)
                .frames(12, 2, 36).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.ROOTS).velocity(7f, 0f).size(70, 170).offset(60, 0)
                        .damage(62).stun(26, 14).push(3).launch(9).count(4, 7).durable().lifetime(70).build(), 12)
                .pose(AnimPose.STOMP).callout("ROOT WAVE!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Ancient Oak").superLevel(3)
                .frames(6, 72, 36).damage(44).box(5, 110, 20, 170).level(HitLevel.GRAB).stun(30, 0).push(0.5f).launch(14)
                .knockdown().multiHit(8, 8).invuln(0, 8).lockOnHit().groundBounce().pose(AnimPose.GRAB)
                .callout("ANCIENT OAK!").build()));
        universals(m, "Branch Toss", "Uproot Slam", 130);
        return m;
    }

    // =====================================================================================================
    // IRON ORE: a living ore vein. Super heavy armored bruiser: slow, huge damage, stone spikes and cave-ins
    // =====================================================================================================

    private static Moveset ironOre() {
        FighterStats stats = new FighterStats(1080, 2.1f, 1.9f, 12.5f, 3.2f, 0.85f, 74, 140, 96);
        Moveset m = new Moveset(FighterType.IRON_ORE, stats);
        Tune t = new Tune(2, 4, 1.0f, 1.05f, 6);
        normals(m, t, new String[]{"Pebble Jab", "Ore Punch", "Iron Hook", "Gravel Kick", "Boulder Boot", "Quarry Spin",
                "Low Pebble", "Ore Body Blow", "Stalagmite Rise", "Gravel Tap", "Bedrock Poke", "Landslide",
                "Air Pebble", "Air Ore", "Anvil Drop", "Air Gravel", "Air Boulder", "Meteor Stomp"});
        // iron skin: the heavies don't flinch from one light hit
        m.stand.put(Button.HP, m.stand.get(Button.HP).toBuilder().armor(1, 13).build());

        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Anvil Fist", AnimPose.OVERHEAD, 21, 3, 20, 92, 10, 95, 60, 160, HitLevel.HIGH)
                        .toBuilder().knockdown().build()));
        m.commandNormals.add(new Moveset.CommandNormal(4, Button.HK,
                commandNormal("Rockslide Kick", AnimPose.SWEEP, 12, 5, 26, 80, 15, 120, 0, 30, HitLevel.LOW)
                        .toBuilder().knockdown().crouching().build()));

        Move forge = Move.builder("Forge Combo").kind(Kind.TARGET).frames(10, 4, 20).damage(85).box(12, 100, 56, 130)
                .stun(22, 15).push(9).cancelable().pose(AnimPose.HOOK).build();
        Move smelt = Move.builder("Smelt Smash").kind(Kind.TARGET).frames(12, 4, 24).damage(95).box(10, 105, 40, 140)
                .stun(24, 16).push(10).knockdown().launch(5).pose(AnimPose.SLAM).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, forge));
        m.targetCombos.add(new Moveset.TargetCombo(forge, Button.HK, smelt));

        m.specials.add(new Moveset.Special("Ore Chunk", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 16, 15, 14, 14), 2, pick(s, 26, 25, 24, 24)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(s == 3 ? ProjectileVisual.COBBLE : ProjectileVisual.RAW_IRON)
                        .velocity(pick(s, 6f, 7.5f, 9f, 8f), pick(s, 5f, 4f, 3f, 4f)).gravity(pick(s, 0.24f, 0.22f, 0.18f, 0.2f))
                        .size(s == 3 ? 44 : 32, s == 3 ? 44 : 32).offset(35, 110).damage(pick(s, 72f, 78f, 84f, 70f))
                        .stun(21, 15).push(9).spin().multiHit(s == 3 ? 2 : 1, 8).lifetime(170).build(), pick(s, 16, 15, 14, 14))
                .pose(AnimPose.THROW), "Ore Chunk")));
        m.specials.add(new Moveset.Special("Stalagmite", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(pick(s, 13, 15, 17, 12), 2, pick(s, 26, 28, 30, 24)).damage(0).box(0, 0, 0, 0).invuln(0, s == 3 ? 12 : 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.SPIKE).velocity(0f, 0f)
                        .size(s == 3 ? 120 : 60, 180).offset(pick(s, 90f, 150f, 210f, 140f), 0)
                        .damage(pick(s, 88f, 94f, 100f, 108f)).stun(28, 16).push(3).launch(12).knockdown()
                        .durable().lifetime(26).build(), pick(s, 13, 15, 17, 12))
                .pose(AnimPose.STOMP), "Stalagmite")));
        m.specials.add(new Moveset.Special("Boulder Roll", Motion.QCF, true, Role.RUSH, 2, variants(s -> Move.builder("")
                .frames(pick(s, 17, 16, 15, 14), 26, pick(s, 26, 28, 30, 24)).damage(pick(s, 90f, 98f, 108f, 120f))
                .box(10, 95, 0, 110).stun(24, 16).push(14).launch(7).knockdown().stopOnHit()
                .dash(pick(s, 7.5f, 9f, 10.5f, 10.5f), pick(s, 17, 16, 15, 14), pick(s, 43, 42, 41, 40))
                .armor(s == 3 ? 2 : s == 2 ? 1 : 0, 40).wallSplat().pose(AnimPose.CHARGE), "Boulder Roll")));
        m.specials.add(new Moveset.Special("Smelter Grab", Motion.HCB, false, Role.GRAB, 4, variants(s -> Move.builder("")
                .frames(pick(s, 8, 9, 10, 7), 3, 38).damage(pick(s, 118f, 128f, 138f, 170f))
                .box(5, pick(s, 78, 88, 98, 92), 20, 140).level(HitLevel.GRAB).stun(32, 0).push(11).launch(10).knockdown()
                .invuln(s == 3 ? 0 : -1, s == 3 ? 7 : -1).pose(AnimPose.GRAB).callout(s == 3 ? "EX Smelter Grab!" : "SMELTED!"),
                "Smelter Grab")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Cave-In").superLevel(1)
                .frames(14, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.COBBLE).velocity(0f, -14f).size(40, 40)
                        .damage(52).stun(22, 12).push(3).launch(4).count(9, 5).fromSky(120, 560).spin().lifetime(100).build(), 14)
                .pose(AnimPose.SLAM).callout("CAVE-IN!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Minecart Rush").superLevel(2)
                .frames(12, 2, 38).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.MINECART).velocity(13f, 0f).size(80, 70).offset(-220, 0)
                        .damage(78).stun(26, 14).push(6).launch(7).count(3, 6).durable().lifetime(150).build(), 12)
                .pose(AnimPose.SUMMON).callout("MINECART RUSH!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Molten Core").superLevel(3)
                .frames(8, 68, 36).damage(46).box(5, 100, 20, 140).stun(30, 12).push(0.5f).launch(14).knockdown()
                .multiHit(9, 7).dash(8f, 8, 16).invuln(0, 14).lockOnHit().wallSplat().pose(AnimPose.RUSH)
                .callout("MOLTEN CORE!").build()));
        universals(m, "Ore Toss", "Mine Shaft Slam", 135);
        return m;
    }

    // =====================================================================================================
    // STONE PICKAXE: a tool that refuses to be crafted. Fast, mining-themed rushdown with drills and rubble
    // =====================================================================================================

    private static Moveset stonePickaxe() {
        FighterStats stats = new FighterStats(940, 4.0f, 3.6f, 15.5f, 4.8f, 0.78f, 44, 120, 80);
        Moveset m = new Moveset(FighterType.STONE_PICKAXE, stats);
        Tune t = new Tune(-1, 14, 1.06f, 1f, 0);
        normals(m, t, new String[]{"Tap Pick", "Chip Swing", "Mining Strike", "Handle Poke", "Handle Kick", "Pick Spin",
                "Low Tap", "Ore Chip", "Upward Mine", "Handle Tap", "Tunnel Poke", "Strip Sweep",
                "Air Tap", "Air Chip", "Drill Drop", "Air Handle", "Air Spin", "Pick Dive"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.MP,
                commandNormal("Overhead Mine", AnimPose.OVERHEAD, 17, 3, 16, 62, 10, 90, 70, 150, HitLevel.HIGH)));
        m.commandNormals.add(new Moveset.CommandNormal(3, Button.MK,
                commandNormal("Tunnel Dig", AnimPose.SLIDE, 9, 8, 18, 50, 10, 105, 0, 28, HitLevel.LOW)
                        .toBuilder().dash(5f, 6, 16).crouching().build()));

        Move mine2 = Move.builder("Double Mine").kind(Kind.TARGET).frames(5, 3, 12).damage(45).box(10, 85, 60, 120)
                .stun(16, 11).push(4).pose(AnimPose.STRAIGHT).build();
        Move mine3 = Move.builder("Triple Mine").kind(Kind.TARGET).frames(7, 3, 16).damage(58).box(10, 90, 50, 125)
                .stun(19, 13).push(5).cancelable().pose(AnimPose.OVERHEAD).build();
        Move mine4 = Move.builder("Bedrock Breaker").kind(Kind.TARGET).frames(9, 4, 22).damage(75).box(10, 95, 30, 130)
                .stun(22, 15).push(8).knockdown().launch(6).pose(AnimPose.SLAM).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LP), Button.MP, mine2));
        m.targetCombos.add(new Moveset.TargetCombo(mine2, Button.HP, mine3));
        m.targetCombos.add(new Moveset.TargetCombo(mine3, Button.HK, mine4));

        m.specials.add(new Moveset.Special("Rubble Toss", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 12, 11, 10, 10), 2, pick(s, 20, 19, 18, 18)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.COBBLE)
                        .velocity(pick(s, 8f, 10f, 12f, 11f), 3f, 4.5f, 1.5f).gravity(0.2f).size(22, 22).offset(30, 80)
                        .damage(pick(s, 26f, 28f, 30f, 26f)).stun(15, 10).push(3).spin()
                        .count(s == 3 ? 3 : 2, 4).lifetime(140).build(), pick(s, 12, 11, 10, 10))
                .pose(AnimPose.SWEEP), "Rubble Toss")));
        m.specials.add(new Moveset.Special("Uppick", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(3, pick(s, 12, 14, 16, 16), pick(s, 21, 24, 27, 24)).damage(pick(s, 92f, 52f, 40f, 44f))
                .box(0, 62, 50, 165).stun(26, 16).push(3).launch(11).knockdown().multiHit(pick(s, 1, 2, 3, 3), 5)
                .leap(pick(s, 11f, 13f, 15f, 15f), 2f, 3).invuln(0, pick(s, 4, 5, 7, 12)).landingLag(13)
                .pose(AnimPose.UPPERCUT), "Uppick")));
        m.specials.add(new Moveset.Special("Spin Mine", Motion.QCF, true, Role.RUSH, 2, variants(s -> Move.builder("")
                .frames(8, pick(s, 15, 22, 29, 34), 17).damage(34).box(0, 82, 40, 125).stun(17, 10).push(2)
                .multiHit(pick(s, 3, 4, 5, 6), 6).dash(pick(s, 4.5f, 5.5f, 6.5f, 7f), 8, pick(s, 23, 30, 37, 42))
                .launch(s >= 2 ? 8 : 0).knockdown().projInvuln(s == 3 ? 0 : -1, s == 3 ? 30 : -1).pose(AnimPose.SPIN_KICK), "Spin Mine")));
        m.specials.add(new Moveset.Special("Drill Dive", Motion.QCB, true, Role.OVERHEAD, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(12, 1, 18).damage(pick(s, 80f, 88f, 96f, 104f)).box(-10, 80, -10, 70)
                    .level(HitLevel.HIGH).stun(22, 14).push(6).launch(4).knockdown().untilLanding()
                    .leap(pick(s, 12f, 13.5f, 15f, 15f), pick(s, 3.5f, 5f, 6.5f, 6f), 3).pose(AnimPose.DIVE_KICK);
            if (s == 3) b.groundBounce().invuln(0, 10);
            return b;
        }, "Drill Dive")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Strip Mine").superLevel(1)
                .frames(6, 34, 28).damage(36).box(5, 85, 30, 130).stun(18, 10).push(1.5f).launch(12).knockdown()
                .multiHit(7, 5).dash(7f, 6, 28).invuln(0, 10).pose(AnimPose.SPIN_KICK).callout("STRIP MINE!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Cave Collapse").superLevel(2)
                .frames(12, 2, 38).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.COBBLE).velocity(0f, -14f).size(42, 42)
                        .damage(42).stun(22, 12).push(3).launch(4).count(10, 4).fromSky(100, 560).spin().lifetime(100).build(), 12)
                .pose(AnimPose.SLAM).callout("CAVE COLLAPSE!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Diamond Strike").superLevel(3)
                .frames(6, 70, 34).damage(40).box(5, 90, 20, 130).stun(30, 12).push(0.5f).launch(15).knockdown()
                .multiHit(10, 6).dash(9.5f, 6, 14).invuln(0, 12).lockOnHit().wallSplat().pose(AnimPose.OVERHEAD)
                .callout("DIAMOND STRIKE!").build()));
        universals(m, "Pick Hook", "Mine Cart Flip", 110);
        return m;
    }

    // =====================================================================================================
    // ZOMBIE: undead grappler. Bites drain your super meter (HUNGER), hordes, command grabs
    // =====================================================================================================

    private static Moveset zombie() {
        FighterStats stats = new FighterStats(1150, 2.5f, 2.3f, 13.5f, 3.6f, 0.78f, 46, 130, 86);
        Moveset m = new Moveset(FighterType.ZOMBIE, stats);
        Tune t = new Tune(1, 4, 1.05f, 1f, 0);
        normals(m, t, new String[]{"Claw Jab", "Rotten Swipe", "Grave Hook", "Shamble Kick", "Stagger Boot", "Undead Spin",
                "Low Claw", "Gut Rake", "Grave Rise Punch", "Ankle Bite", "Crawl Poke", "Grave Sweep",
                "Air Claw", "Air Swipe", "Brain Drop", "Air Shamble", "Air Boot", "Corpse Dive"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Headbutt of the Dead", AnimPose.HEADBUTT, 16, 4, 18, 72, 10, 85, 80, 135, HitLevel.HIGH)
                        .toBuilder().dash(3f, 6, 14).build()));
        m.commandNormals.add(new Moveset.CommandNormal(3, Button.HP,
                commandNormal("Ankle Grab Bite", AnimPose.LOW_JAB, 11, 4, 20, 55, 10, 95, 0, 35, HitLevel.LOW)
                        .toBuilder().crouching().hitEffect(StatusEffect.HUNGER, 150).build()));

        Move maul = Move.builder("Zombie Maul").kind(Kind.TARGET).frames(8, 3, 18).damage(72).box(12, 92, 56, 125)
                .stun(21, 15).push(7).cancelable().pose(AnimPose.HOOK).build();
        Move feast = Move.builder("Feast").kind(Kind.TARGET).frames(9, 6, 20).damage(40).box(10, 80, 70, 125)
                .stun(22, 15).push(3).multiHit(2, 5).hitEffect(StatusEffect.HUNGER, 180).pose(AnimPose.HEADBUTT).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, maul));
        m.targetCombos.add(new Moveset.TargetCombo(maul, Button.HK, feast));

        m.specials.add(new Moveset.Special("Flesh Toss", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 13, 12, 11, 11), 2, pick(s, 24, 23, 22, 21)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.ROTTEN_FLESH)
                        .velocity(pick(s, 6.5f, 8f, 9.5f, 9f), pick(s, 5f, 4f, 3f, 4f)).gravity(0.22f).size(30, 26).offset(30, 105)
                        .damage(pick(s, 46f, 50f, 54f, 42f)).stun(18, 12).push(5).spin()
                        .effect(StatusEffect.HUNGER, pick(s, 150, 180, 210, 300)).count(s == 3 ? 2 : 1, 8)
                        .lifetime(150).build(), pick(s, 13, 12, 11, 11))
                .pose(AnimPose.THROW), "Flesh Toss")));
        m.specials.add(new Moveset.Special("Grave Rise", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(5, pick(s, 12, 14, 16, 16), pick(s, 24, 27, 30, 26)).damage(pick(s, 96f, 106f, 116f, 126f))
                .box(0, 65, 40, 165).stun(28, 16).push(3).launch(12).knockdown()
                .leap(pick(s, 10f, 12f, 14f, 14f), 1.8f, 5).invuln(0, pick(s, 5, 6, 8, 14)).landingLag(15)
                .hitEffect(s == 3 ? StatusEffect.HUNGER : null, 240).pose(AnimPose.ANTI_AIR), "Grave Rise")));
        m.specials.add(new Moveset.Special("Lunge Bite", Motion.QCF, true, Role.RUSH, 2, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 13, 12, 11, 11), 14, pick(s, 22, 24, 26, 20))
                    .damage(pick(s, 76f, 84f, 92f, 100f)).box(15, 90, 50, 125).stun(24, 16).push(8).stopOnHit()
                    .dash(pick(s, 7f, 9f, 11f, 11f), pick(s, 13, 12, 11, 11), pick(s, 27, 26, 25, 25))
                    .hitEffect(StatusEffect.HUNGER, pick(s, 150, 180, 210, 300)).pose(AnimPose.LUNGE);
            if (s == 3) b.armor(1, 25);
            return b;
        }, "Lunge Bite")));
        m.specials.add(new Moveset.Special("Grave Grab", Motion.HCB, false, Role.GRAB, 4, variants(s -> Move.builder("")
                .frames(pick(s, 6, 7, 8, 5), 3, 32).damage(pick(s, 135f, 150f, 165f, 205f))
                .box(5, pick(s, 72, 82, 92, 88), 20, 125).level(HitLevel.GRAB).stun(30, 0).push(9).launch(8).knockdown()
                .hitEffect(StatusEffect.HUNGER, 240).invuln(s == 3 ? 0 : -1, s == 3 ? 6 : -1).pose(AnimPose.GRAB)
                .callout(s == 3 ? "EX Grave Grab!" : "BRAAAINS!"), "Grave Grab")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("The Horde").superLevel(1)
                .frames(12, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.ZOMBIE).velocity(6.5f, 0f).size(60, 125).offset(-200, 0)
                        .damage(48).stun(26, 14).push(3).count(4, 10).durable()
                        .effect(StatusEffect.HUNGER, 180).lifetime(200).build(), 12)
                .pose(AnimPose.SUMMON).callout("THE HORDE!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Baby Zombie Blitz").superLevel(2)
                .frames(10, 2, 36).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.BABY_ZOMBIE).velocity(14f, 0f).size(40, 66).offset(-160, 0)
                        .damage(60).stun(24, 12).push(4).launch(7).count(4, 6).durable().lifetime(140).build(), 10)
                .pose(AnimPose.TAUNT).callout("BABY ZOMBIE BLITZ!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Apocalypse Feast").superLevel(3)
                .frames(6, 70, 34).damage(44).box(5, 85, 20, 125).level(HitLevel.GRAB).stun(30, 0).push(0.5f).launch(13)
                .knockdown().multiHit(8, 8).invuln(0, 8).lockOnHit().wallSplat().hitEffect(StatusEffect.HUNGER, 600)
                .selfEffect(480, StatusEffect.REGEN).pose(AnimPose.GRAB).callout("APOCALYPSE FEAST!").build()));
        universals(m, "Corpse Toss", "Grave Dig", 125);
        return m;
    }

    // =====================================================================================================
    // COW: mad cow. Tanky bruiser; milk cleanses its debuffs; stampedes and milk floods
    // =====================================================================================================

    private static Moveset cow() {
        FighterStats stats = new FighterStats(1060, 2.6f, 2.3f, 13f, 3.8f, 0.8f, 104, 92, 66);
        Moveset m = new Moveset(FighterType.COW, stats);
        Tune t = new Tune(1, -2, 1.05f, 0.7f, 30);
        normals(m, t, new String[]{"Snout Bop", "Moo Jab", "Horn Hook", "Hoof Tap", "Hoof Kick", "Buck Spin",
                "Grass Graze", "Low Moo", "Horn Lift", "Hoof Poke", "Cud Poke", "Pasture Sweep",
                "Air Moo", "Air Horn", "Cowpat Drop", "Air Hoof", "Air Buck", "Belly Drop"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Horn Slam", AnimPose.STOMP, 18, 3, 18, 70, 30, 105, 10, 95, HitLevel.HIGH)));
        m.commandNormals.add(new Moveset.CommandNormal(4, Button.HK,
                commandNormal("Back Buck", AnimPose.THROW_BACK, 13, 4, 22, 84, 30, 120, 10, 80, HitLevel.MID)
                        .toBuilder().push(15).launch(5).knockdown().wallSplat().build()));

        Move udder = Move.builder("Udder Fury").kind(Kind.TARGET).frames(8, 4, 18).damage(74).box(30, 108, 15, 85)
                .stun(22, 15).push(8).cancelable().pose(AnimPose.HEADBUTT).build();
        Move hoofbeat = Move.builder("Hoofbeat").kind(Kind.TARGET).frames(7, 8, 16).damage(32).box(30, 100, 0, 55)
                .stun(17, 12).push(4).multiHit(2, 5).cancelable().pose(AnimPose.FRONT_KICK).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, udder));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.LK), Button.MK, hoofbeat));

        m.specials.add(new Moveset.Special("Milk Splash", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 16, 15, 14, 14), 2, pick(s, 28, 27, 26, 25)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.MILK)
                        .velocity(pick(s, 6.5f, 8f, 9.5f, 9f), pick(s, 5f, 4f, 3f, 4f)).gravity(0.24f).size(34, 34).offset(45, 80)
                        .damage(pick(s, 44f, 48f, 52f, 40f)).stun(18, 12).push(6).spin()
                        .count(s == 3 ? 2 : 1, 9).lifetime(160).build(), pick(s, 14, 13, 12, 12))
                .pose(AnimPose.SHOOT), "Milk Splash")));
        m.specials.add(new Moveset.Special("Moo-percut", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(5, pick(s, 12, 14, 16, 16), pick(s, 24, 27, 30, 26)).damage(pick(s, 96f, 106f, 116f, 126f))
                .box(25, 95, 20, 150).stun(28, 16).push(3).launch(12).knockdown()
                .leap(pick(s, 9f, 11f, 13f, 13f), 2f, 5).invuln(0, pick(s, 4, 5, 7, 12)).landingLag(15)
                .pose(AnimPose.ANTI_AIR).callout(s >= 2 ? "MOOOO!" : null), "Moo-percut")));
        m.specials.add(new Moveset.Special("Cattle Charge", Motion.QCF, true, Role.RUSH, 2, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 15, 13, 11, 11), 24, pick(s, 26, 28, 30, 24))
                    .damage(pick(s, 80f, 88f, 96f, 106f)).box(30, 105, 10, 85).stun(24, 16).push(14).launch(6).knockdown()
                    .stopOnHit().dash(pick(s, 8f, 10f, 12f, 12f), pick(s, 15, 13, 11, 11), pick(s, 39, 37, 35, 35))
                    .pose(AnimPose.CHARGE);
            if (s >= 2) b.armor(s == 3 ? 2 : 1, 30);
            return b;
        }, "Cattle Charge")));
        m.specials.add(new Moveset.Special("Milk Break", Motion.QCB, true, Role.BUFF, 4, variants(s -> Move.builder("")
                .frames(26, 1, 22).damage(0).box(0, 0, 0, 0).cleanse()
                .selfEffect(pick(s, 120, 150, 180, 240), s == 3 ? new StatusEffect[]{StatusEffect.REGEN, StatusEffect.STRENGTH}
                        : new StatusEffect[]{StatusEffect.REGEN})
                .pose(AnimPose.DRINK).callout(s == 3 ? "EX Milk Break!" : "*slurp*"), "Milk Break")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Cattle Drive").superLevel(1)
                .frames(10, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.COW).velocity(11f, 0f).size(100, 90).offset(-220, 0)
                        .damage(72).stun(26, 14).push(5).launch(6).count(3, 7).durable().lifetime(160).build(), 10)
                .pose(AnimPose.SUMMON).callout("CATTLE DRIVE!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Milk Flood").superLevel(2)
                .frames(12, 2, 38).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.MILK).velocity(0f, -13f).size(38, 38)
                        .damage(46).stun(22, 12).push(3).launch(3).effect(StatusEffect.SLOW, 120).count(9, 5)
                        .fromSky(120, 540).spin().lifetime(100).build(), 12)
                .pose(AnimPose.SCREAM).callout("MILK FLOOD!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Mooonshot").superLevel(3)
                .frames(8, 66, 36).damage(40).box(30, 110, 10, 95).stun(32, 12).push(0.5f).launch(22).knockdown()
                .multiHit(9, 7).dash(9f, 8, 16).invuln(0, 14).lockOnHit().pose(AnimPose.CHARGE)
                .callout("MOOONSHOT!").build()));
        universals(m, "Horn Toss", "Barn Slam", 125);
        return m;
    }

    // =====================================================================================================
    // DONKEY: stubborn mule. Legendary back-kicks, bray bursts, and a saddlebag full of fight items
    // =====================================================================================================

    private static Moveset donkey() {
        FighterStats stats = new FighterStats(1050, 3.3f, 2.8f, 15f, 4.6f, 0.8f, 96, 100, 70);
        Moveset m = new Moveset(FighterType.DONKEY, stats);
        Tune t = new Tune(1, 0, 1.0f, 0.75f, 30);
        normals(m, t, new String[]{"Muzzle Jab", "Bray Bite", "Neck Whip", "Hoof Tap", "Hoof Kick", "Mule Spin",
                "Low Muzzle", "Hay Butt", "Neck Rise", "Hoof Poke", "Trot Poke", "Stubborn Sweep",
                "Air Muzzle", "Air Bite", "Saddle Drop", "Air Hoof", "Air Buck", "Pack Drop"});
        m.commandNormals.add(new Moveset.CommandNormal(4, Button.HK,
                commandNormal("Donkey Kick", AnimPose.THROW_BACK, 14, 4, 24, 95, 30, 135, 10, 85, HitLevel.MID)
                        .toBuilder().push(18).launch(6).knockdown().wallSplat().callout("DONKEY KICK!").build()));
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Ear Slap", AnimPose.STOMP, 17, 3, 17, 64, 30, 100, 20, 105, HitLevel.HIGH)));

        Move biteWhip = Move.builder("Bite & Whip").kind(Kind.TARGET).frames(8, 4, 18).damage(72).box(30, 110, 25, 100)
                .stun(22, 15).push(8).cancelable().pose(AnimPose.HEADBUTT).build();
        Move kick3 = Move.builder("Buck Buck Buck").kind(Kind.TARGET).frames(8, 12, 20).damage(30).box(30, 115, 0, 70)
                .stun(18, 12).push(3).multiHit(3, 5).knockdown().launch(5).pose(AnimPose.THROW_BACK).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, biteWhip));
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MK), Button.HK, kick3));

        m.specials.add(new Moveset.Special("Chest Toss", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 15, 14, 13, 13), 2, pick(s, 25, 24, 23, 22)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.CHEST)
                        .velocity(pick(s, 6f, 7.5f, 9f, 8.5f), pick(s, 6f, 5f, 4f, 5f)).gravity(0.26f).size(40, 40).offset(40, 100)
                        .damage(pick(s, 64f, 70f, 76f, 60f)).stun(21, 14).push(7).spin().explodes().blast(110)
                        .count(s == 3 ? 2 : 1, 9).lifetime(170).build(), pick(s, 15, 14, 13, 13))
                .pose(AnimPose.SHOOT), "Chest Toss")));
        m.specials.add(new Moveset.Special("Hee-Haw!", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(pick(s, 7, 8, 9, 6), 2, pick(s, 24, 27, 30, 24)).damage(0).box(0, 0, 0, 0)
                .invuln(0, pick(s, 8, 9, 11, 14))
                .projectile(ProjectileSpec.builder(ProjectileVisual.BLAST).velocity(0f, 0f).size(30, 30).offset(10, 120)
                        .damage(pick(s, 85f, 95f, 105f, 115f)).stun(26, 16).push(8).launch(11).knockdown()
                        .explodes().blast(pick(s, 170f, 190f, 210f, 230f)).lifetime(1).build(), pick(s, 7, 8, 9, 6))
                .pose(AnimPose.SCREAM).callout(s >= 2 ? "HEE-HAW!" : null), "Hee-Haw!")));
        m.specials.add(new Moveset.Special("Pack Mule Charge", Motion.QCF, true, Role.RUSH, 2, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 14, 12, 10, 10), 22, pick(s, 24, 26, 28, 22))
                    .damage(pick(s, 78f, 86f, 94f, 104f)).box(30, 110, 10, 90).stun(24, 16).push(13).launch(6).knockdown()
                    .stopOnHit().dash(pick(s, 9f, 11f, 13f, 13f), pick(s, 14, 12, 10, 10), pick(s, 36, 34, 32, 32))
                    .pose(AnimPose.CHARGE);
            if (s == 3) b.armor(1, 28).wallSplat();
            return b;
        }, "Pack Mule Charge")));
        m.specials.add(new Moveset.Special("Rummage", Motion.QCB, true, Role.BUFF, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 26, 22, 18, 16), 1, 16).damage(0).box(0, 0, 0, 0).grantProp()
                    .pose(AnimPose.SHOOT_UP).callout("*rummage rummage*");
            if (s == 3) b.selfEffect(300, StatusEffect.SPEED);
            return b;
        }, "Rummage")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Caravan").superLevel(1)
                .frames(10, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.DONKEY).velocity(12f, 0f).size(100, 100).offset(-220, 0)
                        .damage(70).stun(26, 14).push(5).launch(6).count(3, 7).durable().lifetime(160).build(), 10)
                .pose(AnimPose.SUMMON).callout("CARAVAN!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Treasure Rain").superLevel(2)
                .frames(12, 2, 38).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.CHEST).velocity(0f, -13f).size(42, 42)
                        .damage(50).stun(22, 12).push(3).launch(4).count(7, 6).fromSky(120, 560).explodes().blast(100)
                        .spin().lifetime(100).build(), 12)
                .pose(AnimPose.SCREAM).callout("TREASURE RAIN!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Kick of the Century").superLevel(3)
                .frames(8, 66, 36).damage(44).box(30, 125, 0, 95).stun(30, 12).push(0.5f).launch(14).knockdown()
                .multiHit(9, 7).dash(8f, 8, 16).invuln(0, 14).lockOnHit().wallSplat().pose(AnimPose.THROW_BACK)
                .callout("KICK OF THE CENTURY!").build()));
        universals(m, "Saddle Toss", "Stubborn Flip", 120);
        return m;
    }

    // =====================================================================================================
    // CREEPER: walking bomb. Glass cannon whose explosions hurt itself too
    // =====================================================================================================

    private static Moveset creeper() {
        FighterStats stats = new FighterStats(870, 3.3f, 3.0f, 14f, 4.2f, 0.78f, 50, 112, 80);
        Moveset m = new Moveset(FighterType.CREEPER, stats);
        Tune t = new Tune(0, -4, 1.0f, 0.85f, 4);
        normals(m, t, new String[]{"Head Bonk", "Leaf Jab", "Creep Hook", "Toe Tap", "Creep Kick", "Sizzle Spin",
                "Low Bonk", "Gut Bonk", "Pop Up", "Root Poke", "Creep Poke", "Sneak Sweep",
                "Air Bonk", "Air Head", "Kaboom Drop", "Air Toe", "Air Kick", "Creeper Dive"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Spark Headbutt", AnimPose.HEADBUTT, 17, 3, 18, 66, 10, 85, 60, 130, HitLevel.HIGH)));
        m.commandNormals.add(new Moveset.CommandNormal(3, Button.HK,
                commandNormal("Sneak Trip", AnimPose.SWEEP, 10, 4, 22, 70, 15, 110, 0, 26, HitLevel.LOW)
                        .toBuilder().knockdown().crouching().build()));

        Move fizzle = Move.builder("Fizzle Combo").kind(Kind.TARGET).frames(8, 3, 18).damage(70).box(10, 88, 50, 115)
                .stun(21, 15).push(7).cancelable().pose(AnimPose.HEADBUTT).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, fizzle));

        m.specials.add(new Moveset.Special("Gunpowder Toss", Motion.QCF, false, Role.PROJECTILE, 5, variants(s -> Move.builder("")
                .frames(pick(s, 13, 12, 11, 11), 2, pick(s, 23, 22, 21, 20)).damage(0).box(0, 0, 0, 0)
                .projectile(ProjectileSpec.builder(ProjectileVisual.GUNPOWDER)
                        .velocity(pick(s, 6.5f, 8f, 9.5f, 9f), pick(s, 6f, 5f, 4f, 5f)).gravity(0.28f).size(28, 28).offset(25, 90)
                        .damage(pick(s, 62f, 68f, 74f, 56f)).stun(22, 14).push(7).launch(5).explodes().blast(110)
                        .spin().count(s == 3 ? 2 : 1, 10).lifetime(150).build(), pick(s, 13, 12, 11, 11))
                .pose(AnimPose.SHOOT), "Gunpowder Toss")));
        m.specials.add(new Moveset.Special("Rocket Jump", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(4, 2, pick(s, 30, 32, 34, 30)).damage(0).box(0, 0, 0, 0).invuln(0, pick(s, 6, 8, 10, 16))
                .leap(pick(s, 14f, 16f, 18f, 18f), 2.5f, 4).selfDamage(pick(s, 15f, 18f, 22f, 15f)).landingLag(14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.BLAST).velocity(0f, 0f).size(30, 30).offset(15, 60)
                        .damage(pick(s, 90f, 100f, 110f, 125f)).stun(28, 16).push(5).launch(12).knockdown().explodes()
                        .blast(pick(s, 150f, 170f, 190f, 210f)).lifetime(1).build(), 4)
                .pose(AnimPose.ANTI_AIR), "Rocket Jump")));
        m.specials.add(new Moveset.Special("Creep Up", Motion.QCF, true, Role.RUSH, 2, variants(s -> Move.builder("")
                .frames(pick(s, 9, 8, 7, 7), 14, pick(s, 18, 20, 22, 16)).damage(pick(s, 66f, 72f, 78f, 86f))
                .box(10, 80, 20, 100).stun(22, 14).push(8).stopOnHit().knockdown().launch(4)
                .dash(pick(s, 8f, 10f, 12f, 12f), pick(s, 9, 8, 7, 7), pick(s, 23, 22, 21, 21)).crouching()
                .projInvuln(0, s == 3 ? 23 : 12).pose(AnimPose.LUNGE), "Creep Up")));
        m.specials.add(new Moveset.Special("Short Fuse", Motion.QCB, false, Role.OVERHEAD, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(pick(s, 22, 26, 30, 22), 2, pick(s, 24, 26, 28, 24)).damage(0)
                    .box(0, 0, 0, 0).selfDamage(pick(s, 35f, 45f, 55f, 40f))
                    .projectile(ProjectileSpec.builder(ProjectileVisual.BLAST).velocity(0f, 0f).size(30, 30).offset(55, 60)
                            .damage(pick(s, 125f, 145f, 165f, 190f)).stun(30, 20).push(12).launch(10).knockdown().explodes()
                            .blast(pick(s, 150f, 170f, 190f, 210f)).lifetime(1).build(), pick(s, 22, 26, 30, 22))
                    .pose(AnimPose.TAUNT).callout("Ssssss...");
            if (s == 3) b.armor(2, 22);
            return b;
        }, "Short Fuse")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Chain Reaction").superLevel(1)
                .frames(12, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.TNT).velocity(7f, 6f, 7f, 8f, 9f).gravity(0.3f)
                        .size(40, 40).offset(25, 90).damage(58).stun(24, 16).push(6).launch(8).explodes().count(4, 8)
                        .lifetime(150).build(), 12)
                .pose(AnimPose.SUMMON).callout("CHAIN REACTION!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Creeper Swarm").superLevel(2)
                .frames(10, 2, 38).damage(0).box(0, 0, 0, 0).invuln(0, 12)
                .projectile(ProjectileSpec.builder(ProjectileVisual.CREEPER).velocity(7.5f, 0f).size(50, 110).offset(-200, 0)
                        .damage(66).stun(28, 14).push(5).launch(7).explodes().blast(160).count(3, 12)
                        .lifetime(170).build(), 10)
                .pose(AnimPose.SCREAM).callout("CREEPER SWARM!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Charged Creeper").superLevel(3)
                .frames(34, 2, 40).damage(0).box(0, 0, 0, 0).invuln(0, 40).selfDamage(80)
                .projectile(ProjectileSpec.builder(ProjectileVisual.BLAST).velocity(0f, 0f).size(30, 30).offset(40, 60)
                        .damage(300).stun(40, 24).push(14).launch(14).knockdown().wallSplat().explodes().blast(330)
                        .lifetime(1).build(), 34)
                .pose(AnimPose.SUMMON).callout("CHARGED CREEPER!").build()));
        universals(m, "Fuse Toss", "Back Blast", 110);
        return m;
    }

    // =====================================================================================================
    // BRAWLER: every other mob
    // =====================================================================================================

    private static Moveset brawler() {
        Moveset m = new Moveset(FighterType.BRAWLER, HUMANOID);
        Tune t = new Tune(1, 0, 1f, 1f, 0);
        normals(m, t, new String[]{"Swipe", "Claw", "Smash", "Stomp", "Kick", "Wild Kick",
                "Low Swipe", "Gut Claw", "Rising Smash", "Low Stomp", "Trip Poke", "Trip",
                "Air Swipe", "Air Claw", "Pounce", "Air Stomp", "Air Kick", "Dive"});
        m.commandNormals.add(new Moveset.CommandNormal(6, Button.HP,
                commandNormal("Skull Bash", AnimPose.OVERHEAD, 18, 3, 17, 62, 10, 85, 70, 140, HitLevel.HIGH)));
        m.commandNormals.add(new Moveset.CommandNormal(3, Button.HK,
                commandNormal("Tail Trip", AnimPose.SWEEP, 10, 4, 24, 75, 15, 115, 0, 26, HitLevel.LOW)
                        .toBuilder().knockdown().crouching().build()));
        Move maul = Move.builder("Maul").kind(Kind.TARGET).frames(8, 3, 18).damage(70).box(12, 90, 56, 118)
                .stun(21, 15).push(7).cancelable().pose(AnimPose.HOOK).build();
        m.targetCombos.add(new Moveset.TargetCombo(m.stand.get(Button.MP), Button.HP, maul));

        m.specials.add(new Moveset.Special("Lunge", Motion.QCF, false, Role.RUSH, 5, variants(s -> Move.builder("")
                .frames(pick(s, 12, 10, 9, 9), 12, pick(s, 18, 20, 22, 18)).damage(pick(s, 75f, 85f, 95f, 105f))
                .box(15, 80, 20, 110).stun(22, 14).push(10).knockdown().launch(5).stopOnHit()
                .dash(pick(s, 7f, 9f, 11f, 11f), 10, 22).armor(s == 3 ? 1 : 0, 20).pose(AnimPose.LUNGE), "Lunge")));
        m.specials.add(new Moveset.Special("Rising Claw", Motion.DP, false, Role.ANTI_AIR, 6, variants(s -> Move.builder("")
                .frames(4, 14, pick(s, 22, 26, 30, 26)).damage(pick(s, 90f, 100f, 110f, 120f)).box(0, 60, 50, 160)
                .stun(28, 16).push(3).launch(11).knockdown().leap(pick(s, 11f, 13f, 15f, 15f), 2f, 4)
                .invuln(0, pick(s, 3, 5, 6, 12)).landingLag(14).pose(AnimPose.UPPERCUT), "Rising Claw")));
        m.specials.add(new Moveset.Special("Pounce", Motion.QCF, true, Role.LOW, 2, variants(s -> Move.builder("")
                .frames(10, 12, 20).damage(pick(s, 60f, 68f, 76f, 84f)).box(15, 90, 0, 40).level(HitLevel.LOW)
                .stun(20, 14).push(6).knockdown().dash(pick(s, 6f, 8f, 10f, 10f), 10, 22).crouching()
                .pose(AnimPose.SLIDE), "Pounce")));
        m.specials.add(new Moveset.Special("Leap Smash", Motion.QCB, true, Role.OVERHEAD, 4, variants(s -> {
            Move.Builder b = Move.builder("").frames(12, 1, 18).damage(pick(s, 82f, 90f, 98f, 105f)).box(-10, 85, -10, 80)
                    .level(HitLevel.HIGH).stun(22, 14).push(7).launch(4).knockdown().untilLanding()
                    .leap(14f, pick(s, 3f, 4.5f, 6f, 6f), 4).pose(AnimPose.SLAM);
            if (s == 3) b.groundBounce();
            return b;
        }, "Leap Smash")));

        m.supers.add(new Moveset.Super(1, Motion.QCF2, false, Move.builder("Frenzy").superLevel(1)
                .frames(8, 28, 26).damage(50).box(5, 80, 20, 120).stun(18, 10).push(1.5f).launch(10).knockdown()
                .multiHit(5, 6).dash(6f, 8, 30).invuln(0, 10).pose(AnimPose.RUSH).callout("FRENZY!").build()));
        m.supers.add(new Moveset.Super(2, Motion.QCB2, false, Move.builder("Bone Storm").superLevel(2)
                .frames(12, 2, 34).damage(0).box(0, 0, 0, 0).invuln(0, 14)
                .projectile(ProjectileSpec.builder(ProjectileVisual.BONE).velocity(11f, 0f, 3f, 6f).gravity(0.12f)
                        .size(30, 24).offset(30, 90).damage(70).stun(20, 14).push(6).knockdown().count(3, 6).lifetime(120).build(), 12)
                .pose(AnimPose.THROW).callout("BONE STORM!").build()));
        m.supers.add(new Moveset.Super(3, Motion.QCF2, true, Move.builder("Rampage").superLevel(3)
                .frames(8, 66, 32).damage(40).box(5, 85, 20, 125).stun(30, 12).push(0.5f).launch(13).knockdown()
                .multiHit(9, 7).dash(9f, 8, 16).invuln(0, 12).lockOnHit().wallSplat().pose(AnimPose.RUSH)
                .callout("RAMPAGE!").build()));
        universals(m, "Toss", "Back Toss", 115);
        return m;
    }
}
