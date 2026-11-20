package com.streetfighter.fight;

import java.util.EnumMap;
import java.util.Map;

/**
 * All fighting styles. Frame data is at 60 fps, damage is out of ~1000 HP.
 */
public final class Movesets {
    private static final Map<FighterType, Moveset> SETS = new EnumMap<>(FighterType.class);

    public static final FighterStats HUMANOID = new FighterStats(1000, 3.4f, 2.8f, 15f, 4.2f, 0.75f, 44, 128, 84);

    static {
        SETS.put(FighterType.STEVE, steve());
        SETS.put(FighterType.SKELETON, skeleton());
        SETS.put(FighterType.VILLAGER, villager());
        SETS.put(FighterType.PIG, pig());
        SETS.put(FighterType.BRAWLER, brawler());
    }

    private Movesets() {
    }

    public static Moveset get(FighterType type) {
        return SETS.get(type);
    }

    // ---------------------------------------------------------------- shared normals

    private static Move crouchJab(String name) {
        return Move.builder(name).frames(4, 3, 8).damage(25).box(10, 62, 10, 50).level(HitLevel.LOW)
                .stun(12, 8).push(3).cancelable().chainable().pose(AnimPose.PUNCH).crouching().build();
    }

    private static Move sweep(String name) {
        return Move.builder(name).frames(8, 4, 20).damage(60).box(10, 100, 0, 30).level(HitLevel.LOW)
                .stun(20, 14).push(6).knockdown().pose(AnimPose.KICK).crouching().build();
    }

    private static Move airPunch(String name) {
        return Move.builder(name).frames(5, 8, 4).damage(40).box(5, 60, 40, 100).level(HitLevel.HIGH)
                .stun(16, 10).push(3).pose(AnimPose.PUNCH).air().build();
    }

    private static Move airKick(String name) {
        return Move.builder(name).frames(7, 9, 4).damage(65).box(5, 80, 10, 70).level(HitLevel.HIGH)
                .stun(19, 12).push(5).pose(AnimPose.KICK).air().build();
    }

    // ---------------------------------------------------------------- STEVE

    private static Moveset steve() {
        Move light = Move.builder("Jab").frames(4, 3, 7).damage(35).box(10, 66, 60, 112)
                .stun(14, 9).push(4).cancelable().chainable().pose(AnimPose.PUNCH).build();
        Move heavy = Move.builder("Pickaxe Swing").frames(9, 4, 16).damage(75).box(15, 96, 45, 125)
                .stun(20, 14).push(8).cancelable().pose(AnimPose.SLAM).build();
        Move blockToss = Move.builder("Block Toss").frames(12, 2, 24).damage(0).box(0, 0, 0, 0).special()
                .projectile(ProjectileSpec.builder(ProjectileVisual.DIRT).velocity(8.5f, 7.5f).gravity(0.32f)
                        .size(34, 34).offset(30, 100).damage(85).stun(20, 14).push(7).spin().lifetime(160).build(), 12)
                .pose(AnimPose.THROW).callout("Block Toss!").build();
        Move uppercut = Move.builder("Diamond Uppercut").frames(3, 14, 22).damage(120).box(0, 58, 50, 160)
                .stun(30, 16).push(3).launch(11).knockdown().special()
                .leap(13.5f, 2.2f, 3).invuln(0, 8).landingLag(14).pose(AnimPose.UPPERCUT).callout("Diamond Uppercut!").build();
        Move craftingCombo = Move.builder("Crafting Combo").frames(6, 32, 28).damage(52).box(5, 78, 30, 125)
                .stun(18, 10).push(1.5f).launch(12).knockdown().superMove().multiHit(6, 6)
                .dash(6.5f, 6, 26).invuln(0, 10).pose(AnimPose.LUNGE).callout("CRAFTING COMBO!").build();
        return new Moveset(FighterType.STEVE, HUMANOID, light, heavy,
                crouchJab("Low Jab"), sweep("Shovel Sweep"), airPunch("Jump Punch"), airKick("Creeper Kick"),
                blockToss, uppercut, craftingCombo);
    }

    // ---------------------------------------------------------------- SKELETON (zoner)

    private static Moveset skeleton() {
        FighterStats stats = new FighterStats(900, 3.7f, 3.5f, 15.5f, 4.6f, 0.75f, 40, 128, 84);
        Move light = Move.builder("Bone Jab").frames(4, 3, 7).damage(30).box(10, 64, 62, 112)
                .stun(13, 9).push(4).cancelable().chainable().pose(AnimPose.PUNCH).build();
        Move heavy = Move.builder("Bow Bash").frames(10, 4, 16).damage(70).box(15, 108, 55, 120)
                .stun(20, 14).push(9).cancelable().pose(AnimPose.SLAM).build();
        Move arrow = Move.builder("Arrow Shot").frames(11, 2, 18).damage(0).box(0, 0, 0, 0).special()
                .projectile(ProjectileSpec.builder(ProjectileVisual.ARROW).velocity(13f, 0f).size(40, 14)
                        .offset(35, 92).damage(62).stun(18, 12).push(6).lifetime(120).build(), 11)
                .pose(AnimPose.SHOOT).build();
        Move volley = Move.builder("Triple Volley").frames(14, 2, 26).damage(0).box(0, 0, 0, 0).special()
                .projectile(ProjectileSpec.builder(ProjectileVisual.ARROW).velocity(10.5f, 0f, 3.6f, 7.2f)
                        .gravity(0.08f).size(36, 14).offset(35, 96).damage(42).stun(16, 10).push(5)
                        .count(3, 0).lifetime(110).build(), 14)
                .pose(AnimPose.SHOOT).callout("Triple Volley!").build();
        Move rain = Move.builder("Arrow Rain").frames(14, 2, 40).damage(0).box(0, 0, 0, 0).superMove()
                .projectile(ProjectileSpec.builder(ProjectileVisual.ARROW).velocity(0f, -15f).size(18, 40)
                        .damage(48).stun(18, 12).push(3).count(7, 6).fromSky(170, 520).lifetime(90).build(), 14)
                .invuln(0, 14).pose(AnimPose.SHOOT).callout("ARROW RAIN!").build();
        return new Moveset(FighterType.SKELETON, stats, light, heavy,
                crouchJab("Shin Rattle"), sweep("Femur Sweep"), airPunch("Rib Punch"), airKick("Skull Dive"),
                arrow, volley, rain);
    }

    // ---------------------------------------------------------------- VILLAGER (grappler / trickster)

    private static Moveset villager() {
        FighterStats stats = new FighterStats(1050, 2.9f, 2.6f, 14f, 3.8f, 0.75f, 46, 128, 86);
        Move light = Move.builder("Nose Bonk").frames(5, 3, 8).damage(36).box(10, 64, 85, 125)
                .stun(14, 9).push(4).cancelable().chainable().pose(AnimPose.LUNGE).build();
        Move heavy = Move.builder("Emerald Slap").frames(9, 4, 17).damage(78).box(15, 92, 50, 118)
                .stun(21, 14).push(9).cancelable().pose(AnimPose.SLAM).build();
        Move trade = Move.builder("Trade Offer").frames(6, 3, 32).damage(145).box(5, 70, 20, 120)
                .level(HitLevel.GRAB).stun(30, 0).push(10).launch(9).knockdown().special()
                .pose(AnimPose.GRAB).callout("Hmm? DEAL!").build();
        Move emerald = Move.builder("Emerald Toss").frames(12, 2, 22).damage(0).box(0, 0, 0, 0).special()
                .projectile(ProjectileSpec.builder(ProjectileVisual.EMERALD).velocity(6.8f, 7f).gravity(0.38f)
                        .bounce(0.72f).size(28, 28).offset(30, 95).damage(58).stun(18, 12).push(6).spin()
                        .lifetime(150).build(), 12)
                .pose(AnimPose.THROW).build();
        Move golem = Move.builder("Iron Golem Assist").frames(10, 2, 40).damage(0).box(0, 0, 0, 0).superMove()
                .projectile(ProjectileSpec.builder(ProjectileVisual.GOLEM).velocity(0f, -13f).size(120, 190)
                        .damage(260).stun(40, 24).push(10).launch(10).knockdown().count(1, 0).fromSky(0, 480)
                        .durable().lifetime(80).build(), 10)
                .invuln(0, 12).pose(AnimPose.TAUNT).callout("IRON GOLEM ASSIST!").build();
        return new Moveset(FighterType.VILLAGER, stats, light, heavy,
                crouchJab("Robe Poke"), sweep("Robe Sweep"), airPunch("Flying Nose"), airKick("Robe Drop"),
                trade, emerald, golem);
    }

    // ---------------------------------------------------------------- PIG (rushdown tank)

    private static Moveset pig() {
        FighterStats stats = new FighterStats(1150, 3.1f, 2.5f, 13f, 4.4f, 0.8f, 92, 92, 66);
        Move light = Move.builder("Snout Poke").frames(4, 3, 8).damage(32).box(30, 82, 25, 75)
                .stun(13, 9).push(4).cancelable().chainable().pose(AnimPose.LUNGE).build();
        Move heavy = Move.builder("Hoof Stomp").frames(10, 4, 17).damage(72).box(20, 92, 0, 40)
                .level(HitLevel.LOW).stun(20, 14).push(7).cancelable().pose(AnimPose.SLAM).build();
        Move cLight = Move.builder("Truffle Dig").frames(4, 3, 8).damage(26).box(25, 80, 0, 35)
                .level(HitLevel.LOW).stun(12, 8).push(3).cancelable().chainable().pose(AnimPose.LUNGE).crouching().build();
        Move slide = Move.builder("Mud Slide").frames(8, 12, 18).damage(65).box(20, 90, 0, 35)
                .level(HitLevel.LOW).stun(20, 14).push(6).knockdown().dash(6f, 8, 20).pose(AnimPose.LUNGE)
                .crouching().build();
        Move charge = Move.builder("Hog Charge").frames(10, 24, 18).damage(98).box(20, 85, 10, 80)
                .stun(24, 16).push(14).launch(6).knockdown().special().stopOnHit().dash(10.5f, 10, 34)
                .pose(AnimPose.LUNGE).callout("Hog Charge!").build();
        Move flop = Move.builder("Belly Flop").frames(14, 1, 18).damage(105).box(-20, 90, -10, 70)
                .level(HitLevel.HIGH).stun(24, 16).push(8).launch(5).knockdown().special().untilLanding()
                .leap(14.5f, 4.5f, 4).pose(AnimPose.SLAM).callout("Belly Flop!").build();
        Move stampede = Move.builder("Stampede").frames(10, 2, 40).damage(0).box(0, 0, 0, 0).superMove()
                .projectile(ProjectileSpec.builder(ProjectileVisual.PIG).velocity(11.5f, 0f).size(90, 80)
                        .offset(-220, 0).damage(95).stun(22, 14).push(8).launch(7).knockdown()
                        .count(3, 16).durable().lifetime(160).build(), 10)
                .invuln(0, 12).pose(AnimPose.TAUNT).callout("STAMPEDE!").build();
        Move airLight = Move.builder("Air Oink").frames(5, 8, 4).damage(38).box(20, 80, 20, 80)
                .level(HitLevel.HIGH).stun(15, 10).push(3).pose(AnimPose.LUNGE).air().build();
        Move airHeavy = Move.builder("Ham Drop").frames(7, 9, 4).damage(68).box(0, 90, -10, 50)
                .level(HitLevel.HIGH).stun(19, 12).push(5).pose(AnimPose.SLAM).air().build();
        return new Moveset(FighterType.PIG, stats, light, heavy, cLight, slide, airLight, airHeavy,
                charge, flop, stampede);
    }

    // ---------------------------------------------------------------- BRAWLER (every other mob)

    private static Moveset brawler() {
        Move light = Move.builder("Swipe").frames(5, 3, 9).damage(35).box(10, 66, 50, 110)
                .stun(14, 9).push(4).cancelable().chainable().pose(AnimPose.PUNCH).build();
        Move heavy = Move.builder("Smash").frames(10, 4, 18).damage(80).box(15, 96, 30, 120)
                .stun(20, 14).push(8).cancelable().pose(AnimPose.SLAM).build();
        Move lunge = Move.builder("Lunge").frames(10, 12, 18).damage(85).box(15, 80, 20, 110)
                .stun(22, 14).push(10).knockdown().launch(5).special().stopOnHit().dash(9f, 10, 22)
                .pose(AnimPose.LUNGE).build();
        Move leap = Move.builder("Leap Smash").frames(12, 1, 18).damage(90).box(-10, 85, -10, 80)
                .level(HitLevel.HIGH).stun(22, 14).push(7).launch(4).knockdown().special().untilLanding()
                .leap(14f, 4.5f, 4).pose(AnimPose.SLAM).build();
        Move frenzy = Move.builder("Frenzy").frames(8, 28, 26).damage(55).box(5, 80, 20, 120)
                .stun(18, 10).push(1.5f).launch(10).knockdown().superMove().multiHit(5, 6)
                .dash(6f, 8, 30).invuln(0, 10).pose(AnimPose.LUNGE).callout("FRENZY!").build();
        return new Moveset(FighterType.BRAWLER, HUMANOID, light, heavy,
                crouchJab("Low Swipe"), sweep("Trip"), airPunch("Pounce"), airKick("Dive"),
                lunge, leap, frenzy);
    }
}
