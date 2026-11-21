package com.streetfighter.fight;

/**
 * What the real terrain under and around the arena is like. Implemented on the client from the actual blocks;
 * the engine uses it for floor physics, hazards and wall interactions.
 */
public interface FightEnvironment {
    enum Floor { NORMAL, ICE, SLOW, WATER, SNOW }

    enum Hazard { NONE, FIRE, SPIKES }

    enum Wall { NONE, SOLID, SPIKES, BREAKABLE }

    Floor floor(float x);

    Hazard hazard(float x);

    /** The wall at the left (false) or right (true) arena edge. */
    Wall wall(boolean right);

    FightEnvironment FLAT = new FightEnvironment() {
        public Floor floor(float x) {
            return Floor.NORMAL;
        }

        public Hazard hazard(float x) {
            return Hazard.NONE;
        }

        public Wall wall(boolean right) {
            return Wall.SOLID;
        }
    };
}
