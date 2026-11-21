package com.streetfighter.fight;

/** Animation clip a move plays. The client maps each one to keyframes per body type. */
public enum AnimPose {
    // punches
    JAB, STRAIGHT, HOOK, OVERHEAD, LOW_JAB, BODY_BLOW, ANTI_AIR, UPPERCUT, HEADBUTT, PALM,
    // kicks
    SHIN_KICK, FRONT_KICK, ROUNDHOUSE, LOW_KICK, POKE_KICK, SWEEP, AXE_KICK, SPIN_KICK,
    // air
    AIR_PUNCH, AIR_SLAM, AIR_KICK, FLYING_KICK, DIVE_KICK,
    // specials / misc
    THROW, SHOOT, SHOOT_UP, LUNGE, CHARGE, SLIDE, BACKFLIP, GRAB, THROW_FWD, THROW_BACK, SLAM, STOMP,
    TAUNT, RUSH, DRIVE_IMPACT, SUMMON, BELLY_FLOP,
    // round 4
    PECK, FLAP, DRINK, SCREAM;

    public boolean isKick() {
        return switch (this) {
            case SHIN_KICK, FRONT_KICK, ROUNDHOUSE, LOW_KICK, POKE_KICK, SWEEP, AXE_KICK, SPIN_KICK, AIR_KICK,
                 FLYING_KICK, DIVE_KICK, SLIDE -> true;
            default -> false;
        };
    }
}
