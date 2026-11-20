package com.streetfighter.fight;

/** Body stats of a fighter (units: arena units per frame at 60 fps; 1 block ~ 66 units). */
public record FighterStats(float maxHp, float walkSpeed, float backSpeed, float jumpVy, float jumpVx,
                           float gravity, float hurtWidth, float hurtHeight, float crouchHeight) {
}
