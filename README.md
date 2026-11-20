# Street Fighter Mod (Minecraft Forge 1.21.1)

Hostile mobs stop attacking you. To settle things, **hold E on any mob** and the camera swings out of your
eyes into a cinematic side view. You and the mob then fight a real Street Fighter-style 2D battle, **right there in
the world**, on the actual terrain. Beat a mob and you learn its fighting style.

## Features

- **In-world fights.** The fight runs on a 2D line through the real terrain between you and the mob.
  - Real cliffs, trees and buildings become the arena walls.
  - Fighters walk over real bumps and kick up dust from the actual ground blocks.
  - The world keeps its own day/night, lighting and weather.
- **Cinematic camera.** It flies out of your head into a side view, then follows, zooms and shakes with the action.
  It punches in and tilts on supers, focuses the K.O., and flies back into your eyes when the fight ends.
  Letterbox bars appear for intros, supers and K.O.s.
- **Animations.** Real Minecraft models are posed limb by limb through model mixins:
  - Fighting guard, punches, kicks, overhead slams, uppercuts, crossed-arm blocks and grabs.
  - Hit reactions, knockdowns (bipeds fall flat, four-legged mobs flip belly-up with legs kicking) and victory pumps.
  - Villagers nose-bonk and slap, and pigs ram and stomp.
- **Real effects.** Crits, sweep slashes, enchanted-hit sparks on block, block dust from the ground under you, and
  explosions. Supers trigger totem bursts and an end-rod aura. Sounds are positional (they come from where the hit lands).
- **Real projectiles.** Arrows are actual arrow models with crit trails. Thrown dirt blocks and emeralds spin. An Iron Golem drops from the
  sky, and a pig stampede charges across the field.
- **Street Fighter HUD.** Slanted gold health bars with damage trails, live 3D portraits, a timer, round "V" badges,
  segmented super meters, combo counters, COUNTER / PUNISH COUNTER callouts, an outlined announcer, super cut-ins,
  and a low-health vignette.
- **Real fighting-game mechanics** (see [RESEARCH.md](RESEARCH.md)):
  - A 6-frame input buffer and lenient motion inputs (236 / 214 / 623 / 236236).
  - SF4 damage scaling, counter hits, punish counters, throws with throw-tech, and chain and special cancels.
- **Peaceful mobs.** Mobs never target players, and mob damage to players is cancelled. This covers arrows, creepers and slimes too.
- **Hold E to challenge any mob.** A quick tap of E still opens your inventory. Short mobs like pigs are easy to target.
- **AI difficulty per world.** When a new world starts, you choose **Normal** or **Hard**.
  Ops can change it later with `/streetfighter difficulty normal|hard`.
- **Learn styles by winning.** Defeat a Skeleton, Pig or plain Villager and its style becomes selectable.

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
| J + K together (or U) | Throw. Press J+K right as you're grabbed to tech it |
| L or ↓↘→ + J/K | Special 1 |
| I or ↓↙← / →↓↘ + J/K | Special 2 |
| O or ↓↘→↓↘→ + J/K | Super (needs a full SUPER meter) |
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
- `client/`: fight session (runs the fight in the world), HUD, select overlay, hold-E challenge, difficulty prompt
- `client/world/`: arena-on-terrain mapping, cinematic camera, animator (limb poses and body transforms), world particles and sounds
- `mixin/`: camera override and model pose hooks
- `server/`: fight sessions, peaceful mobs, saved difficulty, learned styles, commands
- `network/`: packets between client and server
