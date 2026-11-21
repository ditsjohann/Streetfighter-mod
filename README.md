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
  - Villagers nose-bonk and slap, and pigs and goats ram and stomp.
  - Chickens have their own rig: pecks, wing slaps, talon kicks, flapping double jumps and glides.
- **Real effects.** Crits, sweep slashes, enchanted-hit sparks on block, block dust from the ground under you, and
  explosions. Supers trigger totem bursts and an end-rod aura. Sounds are positional (they come from where the hit lands).
- **Real projectiles.** Splash potions in their real colours, eggs, snowballs, flocks of chickens and herds of goats.
  Arrows are actual arrow models with crit trails. Thrown dirt blocks and emeralds spin. An Iron Golem drops from the
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
- **Learn styles by winning.** Defeat a Skeleton, Pig, plain Villager, Chicken, Cleric villager (the Alchemist) or Goat
  and its style becomes selectable.

## Fighting system

Every style has a Street Fighter–sized kit (~44 moves each):

- **18 normals:** light, medium and heavy punch and kick, standing, crouching and jumping.
- **Command normals:** 2 per style, including overheads and long-range lows.
- **Target combos:** button sequences such as MP → HP.
- **4 specials:** each in **L / M / H** strength (by button), plus an **EX** version (two buttons, half a bar of meter).
- **3 Super Arts:** Lv1 (1 bar), Lv2 (2 bars) and Lv3 cinematic Critical Arts (3 bars). The super meter holds 3 bars.
- **Universal moves:** forward throw, back throw (switches sides, wall-splats), **Drive Parry** (perfect parry if timed), and
  **Drive Impact** (an armored strike that crumples or wall-splats).
- **Combo rules:**
  - Lights chain into each other and into heavier normals.
  - Most normals cancel into specials, and specials cancel into Super Arts.
  - SF4 damage scaling applies.
  - Counter hits and punish counters do bonus damage.
  - Reversals, juggles, throw techs, and "FIRST ATTACK" are tracked.
- **Input:** a 6-frame buffer that keeps working through hitstop. Motions are lenient: 236, 214, 623, 63214, 236236 and 214214.

| Style | Specials (motion + P/K) | Super Arts |
|---|---|---|
| **Steve** | Block Toss 236P, Diamond Uppercut 623P, Pickaxe Whirlwind 214K, Shield Bash 214P | Crafting Combo, TNT Barrage, Netherite Finale |
| **Skeleton** | Arrow Shot 236P, Triple Volley 214P, Bone Rattle 236K (low), Rattle Step 214K (evasive backflip) | Arrow Rain, Sniper Shot, Wither Storm |
| **Villager** | Emerald Toss 236P, Bell Ringer 623P, Price Hike 214K (overhead), Trade Offer 63214P (command grab) | Iron Golem Assist, Raid Alarm, Hero of the Village |
| **Pig** | Hog Charge 236P, Snout Uppercut 623P, Mud Slide 236K (low), Belly Flop 214K (overhead) | Stampede, Carrot Frenzy, Pigstep Apocalypse |
| **Chicken** | Egg Toss 236P, Rooster Rise 623P, Fowl Play 236K (pecking rush), Feather Dive 214K (overhead dive) | Egg Barrage, Flock Call, Phoenix Fury |
| **Alchemist** (Cleric villager) | Splash Potion 236P (L poison / M slowness / H harming), Lingering Cloud 214P, Brew Up 214K (L speed / M strength / H regeneration / EX all three), Fizz Rocket 623P | Potion Barrage, Witch's Brew, Elixir of the Ancients |
| **Goat** | Ram Charge 236P (wall-splats on H/EX, armored EX), Horn Toss 623P, Screaming Bleat 214P (low shockwave that slows), Goat Leap 214K (overhead) | Avalanche, Herd Charge, Peak Breaker |
| **Any other mob** | Lunge, Rising Claw, Pounce, Leap Smash | Frenzy, Bone Storm, Rampage |

The pause menu (ESC) shows the full move list for your style.

### Style mechanics

- **Chicken: Feather Fury.** The smallest, fastest fighter.
  - **Double jump:** press up again in the air.
  - **Glide:** hold up while falling to float down slowly.
  - Pecks are multi-hit, and its punches are pecks and wing slaps.
- **Alchemist: Potion Master.** A status-effect zoner.
  - **Splash potions** explode on contact. Poison and Slowness land at half strength even through a block.
  - **Lingering clouds** drift forward, poisoning anyone inside. They can't be blocked and don't cause hitstun.
  - **Brew Up** drinks a buff:
    - Swiftness: walk and jump faster
    - Strength: +25% damage
    - Regeneration: heals over time
- **Goat: Mountain Rammer.** Jumps far higher than anyone else.
  - Charges hit with huge knockback.
  - Its scream shockwave hits low and slows you down.
  - Herd Charge stampedes goats across the screen, and Avalanche buries you in snowballs.

**Status effects** show as coloured tags above the super meter (e.g. `POISON 4s`) and as swirling potion particles around
the fighter:
- **Poison** ticks damage but never KOs.
- **Slowness** cuts walk and jump speed by 40%.

## Environment combat

The arena is the real terrain, and the blocks matter:
- **Walls** come from cliffs, trees and buildings:
  - Knock someone into a wall at speed and they bounce off it.
  - Wall-splat moves (back throws, Drive Impact, heavy Shield Bash, some supers) pin them to the wall for a juggle.
  - You can **wall-jump** by jumping into a wall and pressing up again.
- **Cactus and berry-bush walls** deal spike damage on impact.
- **Leaves, glass, flowers and hay bales** break when a fighter is smashed through them. The blocks are really destroyed on
  the server. This respects protection/claim mods and can be turned off with `breakableWalls`.
- **Floors:**
  - **ice** is slippery (momentum)
  - **soul sand, mud, honey and cobwebs** are slow
  - **shallow water** slows you down and splashes
  - **magma, fire and campfires** burn while you stand there, and a knockdown onto them burns more
  - **berry bushes and dripstone** stab

## Animation

Fighters are animated with keyframed clips:
- **Clips:** about 40 per body type, covering humanoids, villagers and four-legged mobs.
- **Timing:** each clip has a wind-up, a strike and a follow-through, timed to the move's actual startup, active and recovery frames.
- **Blending:** every joint is smoothly blended.
- **Springs:** hit recoil and landing squash.
- **Impacts:** a hitstop shudder.
- **Movement:** a walk cycle driven by real distance moved, somersault jumps, wall-splat and crumple poses, and belly-up
  knockdowns for four-legged mobs.

Positions are interpolated between simulation steps, so motion stays smooth at any frame rate.
Strikes leave additive swoosh trails. Hits throw spark bursts and shockwave rings, and super-ready fighters glow.

## Commands

- `/streetfighter difficulty [normal|hard]`: show or set (op) the AI difficulty
- `/streetfighter styles`: list your learned styles and wins
- `/streetfighter training on|off`: training mode (the dummy stands still and refills its health, your meter is full, no rewards or penalties)
- `/streetfighter unlock <skeleton|villager|pig|chicken|alchemist|goat|all>`: op/testing
- `/streetfighter resetstyles`: op/testing

Settings are in `config/streetfighter-common.toml`: peaceful mobs, challenge range, hold time, what happens to defeated mobs,
and the loser penalty.

## Building

Requires Java 21.

```bash
./gradlew build        # mod jar -> build/libs/streetfighter-0.3.0.jar
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
