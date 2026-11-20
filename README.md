# Street Fighter Mod (Minecraft Forge 1.21.1)

Hostile mobs stop attacking you. To settle things, **hold E on any mob** and you get pulled into a
2D street-fighting screen. Beat a mob and you learn its fighting style.

## Features

- **2D fight screen.** Side-on arena drawn with real Minecraft block textures. The fighters are the
  actual player skin and mob models, posed for punches, kicks, knockdowns and K.O.s. There are health bars,
  a 99-second timer, best-of-3 rounds, super meters, combo counters, hit sparks, screen shake and
  "ROUND 1 / FIGHT! / K.O. / PERFECT" announcements.
- **Peaceful mobs.** Mobs never target players, and mob damage to players is cancelled. This covers arrows, creepers and slimes too.
- **Hold E to challenge any mob.** A quick tap of E still opens your inventory. Holding it for 1 second
  while looking at a mob starts a challenge.
- **AI difficulty per world.** When a new world starts, you choose **Normal** or **Hard**.
  Ops can change it later with `/streetfighter difficulty normal|hard`.
- **Learn styles by winning.** Defeat a Skeleton, Pig or plain Villager and its style becomes
  selectable at the start of your next fights.

## Fighters

| Fighter | Style | Specials (L / I) | Super (O) |
|---|---|---|---|
| **Steve** (you) | Block Brawler: all-rounder | Block Toss (arcing dirt block), Diamond Uppercut (invincible anti-air) | Crafting Combo (6-hit rush ending in a TNT blast) |
| **Skeleton** | Bone Archer: zoner, fast but fragile | Arrow Shot, Triple Volley (anti-air spread) | Arrow Rain (arrows fall from the sky) |
| **Villager** (no profession / nitwit) | Hmm-Fu Master: grappler | Trade Offer (unblockable command grab), Emerald Toss (bouncing) | Iron Golem Assist (a golem drops onto the opponent) |
| **Pig** | Pork Tank: rushdown tank | Hog Charge, Belly Flop (overhead) | Stampede (three pigs charge across the screen) |
| **Any other mob** | Wild Mob: brawler, scaled from the mob's health and attack | Lunge, Leap Smash | Frenzy |

Each mob has its own AI personality. The Skeleton keeps away and zones you with arrows. The Villager walks in and grabs you when you block too much.
The Pig charges and mixes low stomps with overhead flops. On **Hard** the AI reacts faster, blocks correctly,
anti-airs, punishes your whiffs and cancels its normals into specials for combos.

## Controls (in a fight)

| Key | Action |
|---|---|
| A / D (or arrows) | Walk. Hold away from the opponent to block |
| W / Space | Jump |
| S | Crouch. Crouch and hold back to block low attacks |
| J | Light attack (S+J low poke, jump+J air attack) |
| K | Heavy attack (S+K sweep, jump+K air attack) |
| L or ↓↘→ + J/K | Special 1 |
| I or ↓↙← + J/K | Special 2 |
| O | Super (needs a full SUPER meter) |
| Esc | Pause (Q while paused forfeits) |

Rules: overheads (jump-ins, Belly Flop) must be blocked standing, and lows (sweeps, Hoof Stomp) must be blocked crouching.
Grabs can't be blocked. Specials do a little chip damage through blocks.

**Win:** hostile mobs are K.O.'d for good and drop their loot. Passive mobs are spared. You get XP, and you learn the mob's style if it has one.
**Lose:** you're bruised (Weakness and Slowness for 30 seconds). Backing out on the character-select screen costs nothing.

## Commands

- `/streetfighter difficulty [normal|hard]`: show or set (op) the AI difficulty
- `/streetfighter styles`: list your learned styles and wins
- `/streetfighter unlock <skeleton|villager|pig|all>`: op/testing
- `/streetfighter resetstyles`: op/testing

Settings are in `config/streetfighter-common.toml`: peaceful mobs, challenge range, hold time, what happens to defeated mobs,
and the loser penalty.

## Building

Requires Java 21.

```bash
./gradlew build        # mod jar -> build/libs/streetfighter-0.1.0.jar
./gradlew runClient    # launch a dev client with the mod
```

Put the jar in the `mods` folder of a Forge **1.21.1** (52.x) install.

## Code layout

- `fight/`: the fighting game itself (pure simulation at 60 fps: frame data, hitboxes, blocking, combos, rounds)
- `fight/ai/`: one AI per mob, sharing reactions scaled by difficulty
- `client/`: fight screen, stage art, entity rendering, hold-E challenge and HUD prompt, difficulty prompt
- `server/`: fight sessions, peaceful mobs, saved difficulty, learned styles, commands
- `network/`: packets between client and server
