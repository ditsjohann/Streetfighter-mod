package com.streetfighter.fight;

/** The full kit of one fighting style. */
public record Moveset(FighterType type, FighterStats stats,
                      Move light, Move heavy, Move crouchLight, Move crouchHeavy,
                      Move airLight, Move airHeavy,
                      Move special1, Move special2, Move superMove) {
}
