# Fighting-game research notes

What I studied in open-source fighting-game code and Street Fighter docs, and how each idea ended up in this mod.
No code was copied. Everything below was re-implemented in Java for Minecraft.

## Sources

| Source | What it is | What I took from it |
|---|---|---|
| [Ikemen GO](https://github.com/ikemen-engine/Ikemen-GO) (`src/input.go`, MIT, ~1.7k★) | Open-source MUGEN-compatible engine in Go | Input model: a signed per-key counter, SOCD resolution, lenient "$" directions, step-by-step command matching |
| [fight-engine](https://github.com/Lennon-Knight/fight-engine) (C++17/SFML) | Small deterministic 2D fighter | Frame-data conventions (LP 4/3/8, HP 9/4/18, HK launcher 10/5/20), hitstun only ticking on the ground for juggles, both hits checked against the pre-hit state so trades are symmetric |
| [Sakuga-Engine](https://github.com/NoisyChain/Sakuga-Engine) (C#/Godot 4) and [Castagne](https://github.com/panthavma/castagne) (Godot, MPL-2.0) | Fighting-game engines/toolkits | General architecture: fixed 60 Hz simulation separated from rendering, data-driven moves. Nothing copied (and Castagne's license would require attribution). |
| [ChainShiftGames/fighting-game PR #30](https://github.com/ChainShiftGames/fighting-game/pull/30), [UE4InputBuffer](https://github.com/Isatin/UE4InputBuffer) | Motion parser and input buffer implementations | Motions as data (236, 214, 623) instead of hard-coded checks |
| [SuperCombo wiki: SF6 game data](https://srk.shib.live/w/Street_Fighter_6/Game_Data), [SFV game data](https://srk.shib.live/w/Street_Fighter_V/Game_Data) | Community frame-data wikis | ~4-frame input buffer for links (7 in SF6's assisted modes), counter-hit bonus damage, punish counters |
| [SF4 damage scaling discussion](https://boards.ie/discussion/2055569754/sf-iv-damage-scaling-system), [Superjump: damage scaling](https://superjumpmagazine.com/how-damage-scaling-holds-fighting-games-together) | Articles | The 100/100/80/70/60/50/40/30/20/10% scaling table, and supers keeping a minimum damage |

## What the mod now does (and where)

### Input buffer & motion parser: `fight/InputBuffer.java`
- **Per-key counters (Ikemen GO style):** `+n` means held for n frames and `-n` means released for n frames. A "press" is exactly `1`.
- **SOCD:** left+right gives neutral, and up+down gives up (Ikemen's "absolute priority" mode).
- **Button buffer:** a press stays usable for 6 frames, so buttons pressed during hitstop or recovery come out on the
  first possible frame, and links and reversals feel good on a keyboard.
- **Motions** are data: `QCF 236`, `QCB 214`, `DP 623` (the `323` shortcut also works), and `QCF2 236236` for supers.
  Matching runs newest to oldest. Each step must happen strictly before the step after it, so one frame can't satisfy
  two steps. Diagonals are optional, so `2,6` works on a keyboard. Direction sets are lenient like Ikemen's `$`:
  "down" accepts 1, 2 or 3.
- **Priority:** super > special 2 (623/214) > special 1 (236) > throw > heavy > light, the same order fight-engine
  and SF resolve same-frame presses.
- A small test harness (not shipped) checks that 236, 2-6, 214, 623 and 3-2-3 are recognised, while holding forward or walking
  then pressing a button is not.

### Combo system: `fight/FightEngine.java`
- **SF4 damage scaling table** 100/100/80/70/60/50/40/30/20/10%. Supers never scale below 50%.
- **Counter hit** (interrupting the opponent's startup or active frames): +20% damage, +4 hitstun, extra hitstop and
  screen shake, and a "COUNTER!" callout.
- **Punish counter** (hitting the opponent's recovery): +20% damage, +8 hitstun, bonus super meter, and a "PUNISH COUNTER!" callout.
- **Throws:** light+heavy together at close range. Throws can't be blocked, but can be **teched** by pressing light+heavy within 7 frames
  of being grabbed. The AI uses throws against turtling players and techs on Hard.
- **Trades:** both fighters' hitboxes are checked against the same snapshot before either hit applies (as in fight-engine).
- Juggle limit, chip damage on specials, corner pushback onto the attacker, cancels (normal → special,
  light → light/heavy chains), and hitstop that scales with damage.

### HUD conventions: `client/FightHud.java`
- Health bars drain toward the portrait, with a red damage trail that waits until the combo ends. It flashes orange under 25%.
- Combo counter with a "pop" scale, timer flashing red under 10s, round badges, and super meter with "MAX".
- Announcer flow: ROUND n → FIGHT! → K.O./TIME → YOU WIN / PERFECT. Each round is scored the moment it ends, so the
  K.O. screen can name the winner (the same flow as fight-engine's simulate.cpp).
- Super cut-in band with the attacker's portrait, plus letterboxing during intros, supers and K.O.s.

### Round 3 additions
- **Inputs keep buffering during hitstop and super freeze, and presses don't age while frozen**, as in SF. Before this
  fix, cancels pressed during a hit freeze were silently dropped.
- Classic 6-button layout (LP/MP/HP/LK/MK/HK) plus an SF6 "Modern"-style special button (SP + direction).
- L/M/H/EX special variants, 3-level Super Arts on a 3-bar meter, and target combos.
- Drive Parry (perfect parry on the first 3 frames), Drive Impact with 2-hit armor, crumple, and SF6-style wall splat,
  including Drive Impact splatting even when blocked in the corner.
- Stricter double-motion parsing: a held ↓↙ diagonal can no longer satisfy two separate steps of ↓↙←↓↙←.
- Rendering: the 60 Hz simulation is interpolated (prev → current by accumulator alpha) every frame. Animation is
  keyframed with exponential (frame-rate independent) blending and spring layers.

### Round 4 additions (Chicken, Alchemist, Goat)
- **Status effects:** `fight/StatusEffect.java`, ticked in `FightEngine.tickStatus`.
  - They work like SF6's Drive-gauge burnout or Guilty Gear's poison (Venom/Zato-style): timed per-fighter states, not extra hitstun.
  - Poison and Regeneration tick on fixed intervals like vanilla potions. Poison leaves at least 1 HP.
  - Slowness and Swiftness multiply walk and jump speed. Strength multiplies damage after scaling.
- **Air mobility:** `Moveset.airJumps` (double jump on a fresh up press, as in Marvel/Guilty Gear) and `Moveset.glideFrames`
  (slow fall while holding up, capped per jump so it can't be stalled forever).
- **Lingering projectiles (`ProjectileSpec.aura`):** stationary or drifting hitboxes.
  - They tick damage and status without hitstun or blockstun, like Dhalsim's or Menat's traps.
  - They're excluded from projectile clashes and from the AI's "incoming projectile" checks, so the AI doesn't try to block a cloud.
- **Self-buff specials (`Move.selfEffects`):** applied on the move's first active frame. The AI uses `Role.BUFF` from full
  screen and only while no buff is running.

## Not adopted (yet)
- Rollback netcode (GGPO-style), which several engines plan for. In Minecraft the simulation runs on the challenger's
  client, so it isn't needed for 1-player fights.
- Charge motions (`[4]6`), negative edge, and Drive-style systems. These are easy to add on top of `InputBuffer` later.
