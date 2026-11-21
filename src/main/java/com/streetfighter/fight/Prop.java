package com.streetfighter.fight;

/**
 * Items the funny villager throws into fights. Anyone can pick one up by walking over it; while held, punch
 * buttons use it one way ({@link #wayA}) and kick buttons the other ({@link #wayB}). Held items last
 * {@link #HOLD_FRAMES} frames or {@link #uses} uses, and are dropped on a knockdown.
 */
public enum Prop {
    // ---------------- blocks
    TNT("TNT", true, 2,
            Move.builder("Plant TNT").kind(Move.Kind.ITEM).frames(10, 2, 16).damage(0).box(0, 0, 0, 0)
                    .projectile(ProjectileSpec.builder(ProjectileVisual.TNT).velocity(0f, 0f).size(36, 36).offset(40, 18)
                            .damage(110).stun(26, 16).push(8).launch(9).knockdown().explodes().blast(140)
                            .lifetime(75).build(), 10)
                    .pose(AnimPose.LOW_JAB).crouching().callout("TNT PLANTED!").build(),
            Move.builder("TNT Toss").kind(Move.Kind.ITEM).frames(11, 2, 18).damage(0).box(0, 0, 0, 0).consumesProp()
                    .projectile(ProjectileSpec.builder(ProjectileVisual.TNT).velocity(8f, 7f).gravity(0.3f).size(36, 36)
                            .offset(30, 110).damage(100).stun(26, 16).push(8).launch(9).knockdown().explodes().spin()
                            .lifetime(120).build(), 11)
                    .pose(AnimPose.THROW).callout("TNT TOSS!").build()),
    ANVIL("Anvil", true, 2,
            Move.builder("Anvil Smash").kind(Move.Kind.ITEM).frames(16, 4, 26).damage(140).box(10, 105, 0, 160)
                    .level(HitLevel.HIGH).stun(28, 18).push(9).launch(6).knockdown().groundBounce().armor(1, 16)
                    .pose(AnimPose.SLAM).callout("ANVIL SMASH!").build(),
            Move.builder("Anvil Drop").kind(Move.Kind.ITEM).frames(12, 2, 22).damage(0).box(0, 0, 0, 0).consumesProp()
                    .projectile(ProjectileSpec.builder(ProjectileVisual.ANVIL).velocity(0f, -16f).size(60, 60)
                            .damage(130).stun(30, 18).push(4).launch(8).knockdown().fromSky(0, 560).lifetime(90).build(), 12)
                    .pose(AnimPose.SUMMON).callout("ANVIL DROP!").build()),
    SLIME("Slime Block", true, 3,
            Move.builder("Slime Bounce").kind(Move.Kind.ITEM).frames(10, 1, 18).damage(95).box(-30, 110, -10, 90)
                    .level(HitLevel.HIGH).stun(24, 16).push(8).launch(7).knockdown().groundBounce().untilLanding()
                    .leap(21f, 4.5f, 4).pose(AnimPose.BELLY_FLOP).callout("BOING!").build(),
            Move.builder("Slime Lob").kind(Move.Kind.ITEM).frames(11, 2, 18).damage(0).box(0, 0, 0, 0).consumesProp()
                    .projectile(ProjectileSpec.builder(ProjectileVisual.SLIME).velocity(7f, 9f).gravity(0.4f).bounce(0.85f)
                            .size(40, 40).offset(30, 100).damage(60).stun(22, 14).push(7).launch(6).spin()
                            .lifetime(200).build(), 11)
                    .pose(AnimPose.THROW).callout("SLIME LOB!").build()),
    COBWEB("Cobweb", true, 2,
            Move.builder("Web Trap").kind(Move.Kind.ITEM).frames(12, 2, 18).damage(0).box(0, 0, 0, 0)
                    .projectile(ProjectileSpec.builder(ProjectileVisual.WEB).velocity(0f, 0f).size(110, 60).offset(120, 0)
                            .damage(3).multiHit(99, 15).effect(StatusEffect.SLOW, 100).aura().lifetime(260).build(), 12)
                    .pose(AnimPose.THROW).callout("WEB TRAP!").build(),
            Move.builder("Web Shot").kind(Move.Kind.ITEM).frames(10, 2, 16).damage(0).box(0, 0, 0, 0)
                    .projectile(ProjectileSpec.builder(ProjectileVisual.COBWEB).velocity(12f, 0f).size(34, 34).offset(35, 95)
                            .damage(45).stun(20, 12).push(4).effect(StatusEffect.SLOW, 160).lifetime(110).build(), 10)
                    .pose(AnimPose.SHOOT).callout("WEB SHOT!").build()),
    // ---------------- items
    DIAMOND_SWORD("Diamond Sword", false, 4,
            Move.builder("Sword Slash").kind(Move.Kind.ITEM).frames(6, 4, 14).damage(92).box(10, 140, 50, 140)
                    .stun(22, 15).push(8).pose(AnimPose.HOOK).callout(null).build(),
            Move.builder("Sweeping Edge").kind(Move.Kind.ITEM).frames(8, 10, 18).damage(55).box(-45, 135, 30, 130)
                    .stun(20, 14).push(6).multiHit(2, 6).launch(6).knockdown().pose(AnimPose.SPIN_KICK)
                    .callout("SWEEPING EDGE!").build()),
    FISHING_ROD("Fishing Rod", false, 4,
            Move.builder("Hook Pull").kind(Move.Kind.ITEM).frames(12, 4, 18).damage(30).box(60, 230, 40, 130)
                    .stun(30, 14).push(-9).pose(AnimPose.SHOOT).callout("HOOKED!").build(),
            Move.builder("Rod Whip").kind(Move.Kind.ITEM).frames(7, 4, 14).damage(55).box(10, 150, 40, 120)
                    .stun(18, 12).push(6).pose(AnimPose.HOOK).build()),
    TRIDENT("Trident", false, 3,
            Move.builder("Trident Stab").kind(Move.Kind.ITEM).frames(9, 4, 16).damage(88).box(10, 175, 60, 115)
                    .stun(22, 15).push(9).pose(AnimPose.STRAIGHT).build(),
            Move.builder("Trident Throw").kind(Move.Kind.ITEM).frames(12, 2, 20).damage(0).box(0, 0, 0, 0).consumesProp()
                    .projectile(ProjectileSpec.builder(ProjectileVisual.TRIDENT).velocity(17f, 0f).size(70, 20).offset(40, 100)
                            .damage(115).stun(28, 18).push(10).launch(8).knockdown().wallSplat().durable()
                            .lifetime(90).build(), 12)
                    .pose(AnimPose.THROW).callout("TRIDENT THROW!").build()),
    ENDER_PEARL("Ender Pearl", false, 2,
            Move.builder("Pearl Warp").kind(Move.Kind.ITEM).frames(8, 1, 14).damage(0).box(0, 0, 0, 0).teleport()
                    .invuln(0, 12).pose(AnimPose.THROW).callout("WARP!").build(),
            Move.builder("Pearl Throw").kind(Move.Kind.ITEM).frames(10, 2, 16).damage(0).box(0, 0, 0, 0).consumesProp()
                    .projectile(ProjectileSpec.builder(ProjectileVisual.PEARL).velocity(11f, 3f).gravity(0.15f).size(24, 24)
                            .offset(30, 105).damage(55).stun(20, 14).push(6).lifetime(120).build(), 10)
                    .pose(AnimPose.THROW).build()),
    // ---------------- unique
    CHAIR("Chair", false, 2,
            Move.builder("Chair Shot").kind(Move.Kind.ITEM).frames(14, 4, 22).damage(125).box(10, 115, 40, 165)
                    .stun(26, 16).push(7).crumple().pose(AnimPose.OVERHEAD).callout("CHAIR SHOT!").build(),
            Move.builder("Chair Toss").kind(Move.Kind.ITEM).frames(12, 2, 20).damage(0).box(0, 0, 0, 0).consumesProp()
                    .projectile(ProjectileSpec.builder(ProjectileVisual.CHAIR).velocity(9f, 5f).gravity(0.25f).size(50, 50)
                            .offset(30, 110).damage(100).stun(26, 16).push(9).launch(7).knockdown().spin()
                            .lifetime(130).build(), 12)
                    .pose(AnimPose.THROW).callout("CHAIR TOSS!").build()),
    BOTTLE("Mystery Bottle", false, 1,
            Move.builder("Swig").kind(Move.Kind.ITEM).frames(20, 1, 18).damage(0).box(0, 0, 0, 0).randomBrew()
                    .consumesProp().pose(AnimPose.DRINK).callout("*GULP*").build(),
            Move.builder("Bottle Smash").kind(Move.Kind.ITEM).frames(10, 2, 16).damage(0).box(0, 0, 0, 0).consumesProp()
                    .projectile(ProjectileSpec.builder(ProjectileVisual.BOTTLE).velocity(9f, 5f).gravity(0.3f).size(26, 32)
                            .offset(30, 110).damage(70).stun(22, 14).push(6).explodes().blast(90)
                            .effect(StatusEffect.SLOW, 150).spin().lifetime(130).build(), 10)
                    .pose(AnimPose.THROW).callout("BOTTLE SMASH!").build());

    /** How long a held prop lasts (8 seconds), and how long one lies on the ground (10 seconds). */
    public static final int HOLD_FRAMES = 8 * 60, GROUND_FRAMES = 10 * 60;
    public static final Prop[] ALL = values();

    public final String displayName;
    public final boolean block;
    public final int uses;
    public final Move wayA, wayB;

    Prop(String displayName, boolean block, int uses, Move wayA, Move wayB) {
        this.displayName = displayName;
        this.block = block;
        this.uses = uses;
        this.wayA = wayA;
        this.wayB = wayB;
    }

    /** The prop a move belongs to, or null. */
    public static Prop of(Move m) {
        if (m == null || m.kind != Move.Kind.ITEM) return null;
        for (Prop p : ALL) if (p.wayA == m || p.wayB == m) return p;
        return null;
    }
}
