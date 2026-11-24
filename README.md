# Street Fighter Mod (Minecraft Forge 1.21.1)

Hostile mobs stop attacking you. To settle things, **hold E on any mob** and the camera swings out of your
eyes into a cinematic side view. You and the mob then fight a real Street Fighter-style 2D battle, **right there in
the world**, on the actual terrain. Beat a mob and you learn its fighting style.

## New player? Start here

1. **Look at any mob and hold E.** A quick tap of E still opens your inventory.
2. **Pick a style** with A/D and press **Enter**. If you're new, pick **Steve**.
3. **Fight!** Your first fight teaches you step by step at the bottom of the screen: move, punch, kick, block and a
   special move. Each step ticks off when you do it.

| Key | What it does |
|---|---|
| **A / D** | walk (hold the key pointing **away** from your opponent to **block**) |
| **W / S** | jump / crouch |
| **U I O** | light / medium / heavy punch |
| **J K L** | light / medium / heavy kick |
| **H + a direction** | special moves (hold **TAB** in a fight to see yours) |
| **H + O** | super move, once your SUPER bar is full |
| **ESC** | pause and full move list (**Q** gives up) |
| **G** | the **Fight Guide**, outside fights (or from the pause menu) |

- **The Fight Guide (G)** has 10 short chapters: getting started, moving and blocking, attacking, special moves, the
  super meter, defense, loot, **where to find every fighter**, Ki Clash, and settings.
- **Help where you need it:**
  - The first time your super bar fills, a reminder tells you how to use it.
  - After a loss, the results screen shows a beginner tip.
  - New players get a "pick Steve" hint on the select screen.
  - `/streetfighter help` prints the basics in chat.
- **Training tips can be turned off** from the pause menu with **T**.

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
  | Iron Sword | an enchanted iron sword (Sharpness + Sweeping Edge) plus a plain one, iron ingots, sticks |
  | Furnace | **everything it was smelting**, the furnace itself, coal, charcoal, iron ingots, cooked beef, maybe gold |
  | Coal Ore | **2 coal per ore in the vein** + 4–8 extra, cobblestone, torches, maybe a coal block or (rarely) a diamond |
  | Zombie | a pile of rotten flesh, iron ingots, carrots, potatoes, a rare zombie head |
  | Cow | 8–12 beef, 6–10 leather, a bucket of milk |
  | Donkey | leather, chests, apples, golden carrots, maybe a saddle |
  | Creeper | 8–14 gunpowder, TNT, a rare music disc or creeper head |
  | Slime | 10–18 slimeballs, maybe a slime block, a lead or a sticky piston |
  | Spider | 10–18 string, spider eyes, cobwebs, maybe a fermented spider eye |
  | Leatherworker | 10–16 leather, emeralds, rabbit hide, a leather armor piece, maybe a saddle or horse armor |
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
- **Coal ore and furnaces fight too.** Hold E on coal ore and its vein tears out as a smouldering coal golem. Hold E
  on a furnace, smoker or blast furnace and it hops off its spot with whatever it was smelting. Lose or back out, and
  it climbs back in **with its contents untouched**.
- **Iron swords fight back.** Like the stone pickaxe, taking an iron sword out of a crafting table makes it draw itself
  and fight you (config: `ironSwordChallenge`).
- **Learn styles by winning.** Defeat a Skeleton, Pig, plain Villager, Chicken, Cleric villager (the Alchemist), Goat, Oak Tree, Iron Ore,
  Stone Pickaxe, Zombie, Cow, Donkey, Creeper, Slime or Spider and its style becomes selectable. Beat a Leatherworker
  villager (in Ki Clash) to learn his Ki Clash style.

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
| **Cow** | Milk Splash 236P, Moo-percut 623P, Cattle Charge 236K (armored), Milk Break 214K (cleanses your debuffs + regen); Back Buck 4HK wall-splats | Cattle Drive, Milk Flood, Mooonshot |
| **Donkey** | Chest Toss 236P, Hee-Haw! 623P (sonic bray burst), Pack Mule Charge 236K, Rummage 214K (pulls a random fight item from its saddlebag); Donkey Kick 4HK | Caravan, Treasure Rain, Kick of the Century |
| **Creeper** | Gunpowder Toss 236P, Rocket Jump 623P, Creep Up 236K (silent low dash), Short Fuse 214P (huge blast); explosions also hurt the creeper | Chain Reaction, Creeper Swarm, Charged Creeper |
| **Iron Sword** | Sword Toss 236P, Rising Slash 623P, Lunge Thrust 236K (wall-splats on H/EX), Sweeping Edge 214K (low) | Sword Storm, Thousand Cuts, Smite (a giant sword from the sky) |
| **Furnace** | Fire Charge 236P, Flame Geyser 623P, Smelt Down 63214P (command grab), Hot Coals 214K (a burning patch on the ground) | Blast Furnace, Smoker, Meltdown |
| **Coal Ore** | Coal Toss 236P, Ember Uppercut 623P, Soot Cloud 214P (slows), Stoke the Fire 214K (damage buff) | Coal Rain, Firedamp, Diamond Pressure |
| **Slime** | Slimeball 236P (bouncing, slows), Boing Uppercut 623P, Goo Slide 236K (low, slows), Belly Flop 214P (overhead, armored) | Slime Rain, Mitosis, King Slime Crush |
| **Spider** | Web Shot 236P (slows), Pounce 623P, Venom Lunge 236K (poisons), Web Trap 214P (a sticky web on the ground) | Web Prison, Brood Swarm, Eight-Leg Frenzy |
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

- **Cow: Mad Cow.** Milk Break drinks milk like vanilla, clearing Poison, Slowness and Hunger, then regenerates.
- **Donkey: Stubborn Mule.** Rummage pulls a random fight item (the same ones the funny villager throws) out of its
  saddlebag. Its Donkey Kick is the strongest wall-splat back-kick in the game.
- **Creeper: Walking Bomb.** A glass cannon: its explosions are huge but **also hurt the creeper**. An enemy
  creeper really swells and flashes white while its fuse burns.

- **Slime: Bouncing Blob.** Hold **W** when you land from a jump to **BOING** straight into a higher jump, twice
  in a row. Sticky slimeballs, slides and traps slow you down; Mitosis splits off mini slimes that hop at you.
- **Iron Sword: Blade Dancer.** The longest reach in the game, but fragile. Falling jump attacks are
  **CRITICAL HITS** (+40%), like Minecraft's own crits.
- **Furnace: Smelter.** A slow, heavy fire grappler. Its fire sets you **BURNING** (quick damage over time that never
  K.O.s on its own), Hot Coals leaves a burning patch, and Smelt Down grabs you.
- **Coal Ore: Black Rock.** Burning coal, soot clouds that slow, and Stoke the Fire for extra damage. Its level 3
  super **Diamond Pressure** crushes it into diamond: its ore and fists turn to diamond while it's powered up.
- **Spider: Wall Crawler.** Low, fast and fragile. **Web Zip:** press W again in mid-air to shoot a silk line and
  yank yourself along. Webs slow you, fangs and lunges poison you, and Web Trap leaves a sticky web on the ground.
  Spiders and cave spiders both fight in this style.

**Status effects** show as coloured tags above the super meter (e.g. `POISON 4s`) and as swirling potion particles around
the fighter:
- **Poison** ticks damage but never KOs.
- **Slowness** cuts walk and jump speed by 40%.

## Controls (keys, not arrows)

Motions are shown as the actual keys, and they **flip when you switch sides**. When you face right, a "quarter circle
forward" is written **S, S+D, D + U/I/O**: press S, then S and D together, then D, then a punch. When you face left it
becomes **S, S+A, A**.
- **During a fight:** hold **TAB** to see your specials. The pause menu (ESC) shows the full move list for the side you're on.
- **Simple inputs:** every special also has a one-button version, **H + direction**.
- **Dash:** **tap forward twice** (D, D).
- **Backdash:** **tap back twice** (A, A). It's briefly invincible.

## Finishers: X-ray and unique deaths

The match-winning K.O. plays an 8 to 9 second Mortal Kombat style cinematic, **staged in the world itself**
(not as a flat overlay). Time stops. **SPACE** skips to the death, and pressing it again skips to the end.
1. **FINISH!** The camera cuts behind the winner's shoulder and pushes in. The world sinks into dark fog, a heartbeat
   thumps and the victim staggers.
2. **X-RAY.** The victim's body is swapped, in place, for an x-ray body: glass flesh around a skeleton built from
   blocks. The fog closes into a deep blue void.
   - Every blow gets its own camera cut, close on the target bone.
   - The winner's weapon (a pickaxe, a bow, a TNT block...) swings in as a real 3D item.
   - Impacts flash, throw bone shards, shake the camera and spread the block-breaking cracks.
   - The last blow **snaps the bone in two**, and the halves drift apart.
   - The finisher is set by the **winner's style**: Steve's *Pickaxe to the Dome*, Skeleton's *Rib Collector*,
     Goat's *Ram Rupture*, Zombie's *Bone Appetit*, Creeper's *Total Detonation*, and so on.
   - The skeleton is set by the **victim's body**: biped, four-legged, bird, creeper or tool. Trees have wooden bones,
     the ore golem iron ones and the pickaxe a wooden handle.
3. **The death.** A wide, slowly orbiting shot, and **every character dies its own way**, with physics debris:

| Character | Death | What happens |
|---|---|---|
| Steve / Brawler | *Knocked out cold* | Wobbles, knees buckle, falls flat, dust puff, dizzy stars |
| Villager | *Bankrupt* | Collapses and spills a shower of emeralds |
| Alchemist | *Shattered* | Collapses as its potions burst into coloured clouds and empty bottles |
| Skeleton | *Bone pile* | Falls apart into a clattering heap of bones, its skull and its bow |
| Zombie | *Back to the grave* | Sways, then sinks into the ground reaching upward |
| Pig | *Belly up* | Rolls onto its back, legs twitching, carrots spill |
| Cow | *Spilt milk* | Rolls over, the bucket goes flying, milk splashes |
| Chicken | *Feather explosion* | Pops into a cloud of drifting feathers and one last egg |
| Goat | *Dehorned* | Both horns snap off and fly, then it topples |
| Donkey | *Last kick* | Rears up and topples, losing its chest, saddle and apples |
| Creeper | *Fizzled out* | Swells... and fizzles with a sad "pfft", leaving gunpowder |
| Oak tree | *TIMBER!* | Creaks, then falls flat with a crash of leaves, logs and apples |
| Iron ore | *Crumbled* | Breaks into tumbling stone, ore and raw iron blocks |
| Stone pickaxe | *Snapped* | The handle snaps; sticks, cobblestone and the pickaxe fly |
| Slime | *Split up* | Jiggles, flattens, then splits into bouncing slime cubes and slimeballs (it has no bones, only a gooey core) |
| Spider | *Curled up* | Flips onto its back, legs flailing, then curls up; string and spider eyes fly |
| Leatherworker | *Tanned hide* | Collapses while his leather armor, leather and saddle go flying |
| Iron Sword | *Broken blade* | Shatters into iron nuggets; the sword spins away |
| Furnace | *Burnt out* | Sputters smoke, then crumbles into cobblestone, magma and charcoal (x-ray: a glowing magma core) |
| Coal Ore | *Crumbled* | Breaks into stone, coal ore and coal blocks in a cloud of smoke |

- **Config:** turn it off with `xrayFinishers`.

## Ki Clash (Dragon Ball style), villagers only

Against **any villager** the select screen offers a second rule set. Press **M** to switch between **Street Fighter** and
**Ki Clash**. Ki Clash is playable as Steve, or as Villager or Leatherworker once you've learned those styles.

| Action | Keys |
|---|---|
| Fly (take off / rise / descend) | **W** / **W** / **S**. Hold S near the ground to land. Fly sideways with A/D |
| Charge ki (aura) | hold **H + S** |
| Ki blast (aimed at the opponent) | tap **H** |
| Dragon Dash: homes in through the air | **D, D** |
| Vanish: teleport behind and strike (1 ki bar) | **I + K** |
| Rush auto-combo / Smash (sends them flying) | **U U U U U** / **O** |
| Guard (also in the air) | hold back |

- **Recovery:** launched fighters catch themselves in mid-air.
- **Beams:** specials cost ki. When two beams collide there's a **beam struggle**: mash any button to push yours through
  and hit for 1.6× damage.
- **Supers:** a giant spirit ball, a huge beam, and the **Super Villager / Super Steve** transformation (golden aura, +30%
  damage, +20% speed, ki regeneration).
- **The AI** charges, blasts, flies after you, rushes, vanishes and transforms.

### The Leatherworker (Ki Clash only)

Challenge a **leatherworker villager** and the fight is always Ki Clash (the select screen locks the mode). His kit:
- **Tough Hide:** his Smash plows through a hit early in its wind-up.
- **Leather Lasso** (S, S+A, A + kick): a thrown lead that **yanks you toward him** and leaves you open. "GET OVER HERE!"
- **Hide Barrage:** a volley of tanned leather instead of ki blasts.
- **Tanning Cannon** (beam), **Saddle Rising** (uppercut), and the supers **Cauldron Bomb**, **Final Whip** and the
  **Super Leatherworker** transformation. His aura burns tan-orange.
- Win to learn his style. It can only be picked in Ki Clash, so it shows as "KI" on the Street Fighter grid.
- **Clash:** when both fighters' strikes meet head-on, nobody gets hit. The fists collide in a shockwave and both
  fighters are knocked back (with a bit of ki for each).

### Its own camera and vibe

Ki Clash doesn't use the flat Street Fighter side view. It has its own camera director, inspired by Sparking! ZERO /
Budokai Tenkaichi and the anime:
- **Chase camera:** an angled shot from behind your shoulder that swings around when you switch sides.
- **Sky battles:** when the fight goes airborne, the camera drops below and looks up at the fighters.
- **Power-up:** while someone charges ki, the camera pushes in low and rumbles.
  - Chunks of the real ground float up around them.
  - The screen edges glow in their ki colour.
  - A **POWER** readout climbs.
- **Transformation:** slow motion while the camera circles the fighter. The ground bursts up, the sky turns gold and
  lightning flickers.
- **Beam struggles:** a slow orbit around the point where the beams meet.
- **Anime impact frames:** a split-second black-and-white burst on smashes, clashes, beam collisions and counters.
- **Speed lines** during dragon dashes, vanishes, fast flight and smashes. The camera whips after a smashed fighter.
- **Craters:** slamming into the ground throws up chunks of the real ground block, with a dust ring and a shockwave.
- **Haze:** a warm battle haze on the horizon. It turns gold while powering up or transformed, and white-blue in a beam
  struggle.

## The funny villager (fight items)

Every 8 seconds of fighting there's a **20% chance** a nitwit villager pops up at the edge of the fight:
- He yells something ("Hrmm! CATCH!", "No refunds!", "Free sample!") and lobs a random item at the **left, middle
  or right** of the screen.
- **Anyone can grab it, you or the AI.** Just walk over it.
- While you hold it, **punch buttons use it one way and kick buttons the other.**
- It lasts **8 seconds** or a few uses. The HUD shows the item, its timer and its two moves.
- **Get knocked down and you drop it**, so the other fighter can steal it.

| Item | Punch | Kick |
|---|---|---|
| **TNT** (block) | Plant TNT: a lit trap on the ground | TNT Toss: explodes on impact |
| **Anvil** (block) | Anvil Smash: armored overhead, ground bounce | Anvil Drop: falls from the sky onto them |
| **Slime Block** (block) | Slime Bounce: a huge jump into a body slam | Slime Lob: a bouncing slime |
| **Cobweb** (block) | Web Trap: a sticky patch that slows | Web Shot: slows on hit |
| **Diamond Sword** | Sword Slash: fast and long | Sweeping Edge: a spinning slash that hits behind you too |
| **Fishing Rod** | Hook Pull: yanks them to you from far away | Rod Whip |
| **Trident** | Trident Stab: very long reach | Trident Throw: wall-splats |
| **Ender Pearl** | Pearl Warp: teleport behind them | Pearl Throw |
| **Chair** | Chair Shot: crumples them, wrestling style | Chair Toss |
| **Mystery Bottle** | Swig: random Strength/Swiftness/Regeneration... or Poison | Bottle Smash: shatters and slows |

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
- `/streetfighter unlock <skeleton|villager|pig|chicken|alchemist|goat|oak|iron_ore|stone_pickaxe|zombie|cow|donkey|creeper|all>`: op/testing
- `/streetfighter resetstyles`: op/testing

Settings are in `config/streetfighter-common.toml`: peaceful mobs, challenge range, hold time, whether defeated mobs die
(`defeatedMobsDie`), the loot multiplier (`rewardMultiplier`) and the loser penalty.

## Building

Requires Java 21.

```bash
./gradlew build        # mod jar -> build/libs/streetfighter-0.12.0.jar
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
