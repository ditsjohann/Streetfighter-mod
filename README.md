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
- **Street Fighter character select.**
  - **Portrait grid:**
    - Live 3D faces of Steve, Pig, Skeleton, Villager, Alchemist, Chicken and Goat, each with its icon.
    - A "?" random slot (with a roulette) and black slots reserved for future fighters.
    - Locked styles show as black silhouettes.
  - **Showcases:** the highlighted style on a blue slab and the opponent on a red one. Each gets a huge italic name, its title,
    its home turf, an archetype tag and POW/SPD/DEF/RNG bars.
  - **Cursors and timer:** a blinking 1P cursor, a CPU marker, and a 30-second select timer.
  - **Extras:** a TAB move list, and an SF2-style VS splash before the fight.
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
- **Fight for loot.** Street fighting is how you get stuff now. A beaten mob is knocked out for good and drops far more
  than killing it normally would (on top of its usual drops):

  | Opponent | Drops |
  |---|---|
  | Pig | ~10 porkchops, carrots, sometimes a saddle |
  | Skeleton | a stack of bones and arrows, bone meal, maybe a bow or skull |
  | Villager | 6–12 emeralds, bread, wheat |
  | Alchemist | emeralds, redstone, glowstone, nether wart, bottles o' enchanting, random potions |
  | Chicken | chicken, a pile of feathers and eggs |
  | Goat | a goat horn, leather, snowballs, maybe milk |
  | Oak Tree | **twice the tree's logs** plus extra, saplings, apples, sticks, a rare golden apple |
  | Iron Ore | **2 raw iron per ore in the vein** + 4–8 extra, cobblestone, iron ingots, maybe a raw iron block |
  | Stone Pickaxe | an enchanted stone pickaxe (Efficiency + Unbreaking) plus a plain one, cobblestone, sticks |
  | Zombie | a pile of rotten flesh, iron ingots, carrots, potatoes, a rare zombie head |
  | Any other mob | its own loot rolled 2–4 extra times |

  - **Bonuses:** ×1.5 on Hard AI and ×1.25 for a perfect win, plus XP orbs.
  - **Chat summary:** a "LOOT" line lists what dropped.
  - **Config:** `rewardMultiplier` scales all rewards; turning off `defeatedMobsDie` lets beaten mobs walk away (no loot).
- **Challenge oak trees.** Hold E on an oak's trunk or its leaves:
  - **Uproot:** the whole tree (logs and natural leaves) rips itself out of the ground and fights you as a walking
    treant built from real oak blocks.
  - **Win:** the tree is felled for a pile of logs.
  - **Lose or back out:** it plants itself back exactly as it was.
  - **Limits:** it respects protection/claim mods, and player-built log structures (no natural leaves) can't be challenged.
- **Challenge iron ore.** Hold E on iron ore (or deepslate iron ore):
  - **The golem:** the whole vein tears out of the rock as a stone golem with an ore torso and raw-iron fists.
  - **Win:** the vein is "mined" for far more raw iron than a pickaxe would get.
  - **Lose or back out:** the ore sinks back into the wall.
- **Stone pickaxes fight back.** Try to take a stone pickaxe out of a crafting table and it refuses to be crafted:
  - **The fight:** the table closes, your cobblestone and sticks go back to your inventory, and a giant living pickaxe
    hops out to fight you.
  - **Win:** you get the pickaxe (enchanted!) plus a spare.
  - **Exempt:** creative mode.
  - **Config:** turn it off with `stonePickaxeChallenge`.
- **Learn styles by winning.** Defeat a Skeleton, Pig, plain Villager, Chicken, Cleric villager (the Alchemist), Goat, Oak Tree, Iron Ore,
  Stone Pickaxe or Zombie and its style becomes selectable.

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
| **Oak Tree** | Apple Toss 236P, Root Eruption 623P (roots burst out of the ground at a distance), Timber Rush 236K (armored charge), Canopy Crash 214K (overhead) | Orchard Rain, Root Wave, Ancient Oak |
| **Iron Ore** | Ore Chunk 236P, Stalagmite 623P (a stone spike bursts from the ground), Boulder Roll 236K (armored), Smelter Grab 63214P (command grab) | Cave-In, Minecart Rush, Molten Core |
| **Stone Pickaxe** | Rubble Toss 236P, Uppick 623P, Spin Mine 236K, Drill Dive 214K (overhead); 4-hit "mining" target combo | Strip Mine, Cave Collapse, Diamond Strike |
| **Zombie** | Flesh Toss 236P, Grave Rise 623P, Lunge Bite 236K, Grave Grab 63214P (command grab); bites inflict HUNGER | The Horde, Baby Zombie Blitz, Apocalypse Feast |
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

- **Zombie: Undead Brawler.** Bites, flesh tosses and grabs inflict **HUNGER**, which drains your super meter.
  Zombies, husks, drowned and zombie villagers all fight in this style.

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
- `/streetfighter unlock <skeleton|villager|pig|chicken|alchemist|goat|oak|iron_ore|stone_pickaxe|zombie|all>`: op/testing
- `/streetfighter resetstyles`: op/testing

Settings are in `config/streetfighter-common.toml`: peaceful mobs, challenge range, hold time, whether defeated mobs die
(`defeatedMobsDie`), the loot multiplier (`rewardMultiplier`) and the loser penalty.

## Building

Requires Java 21.

```bash
./gradlew build        # mod jar -> build/libs/streetfighter-0.5.0.jar
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
