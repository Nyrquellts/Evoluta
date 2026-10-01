# Testing

What is tested, how to run it, and what is not covered. Last full run of the commands below: **83 JUnit tests and
101 game tests, all passing**, with the jar verified (`./gradlew build`).

## Layers

| Layer | Where | Runs on | What it proves |
|---|---|---|---|
| JUnit | `src/test` | the build JVM, Minecraft classes loaded but registries not bootstrapped | pure logic: stat and stacking maths, the roller, the config reader, the skin and glint painters, the render hooks' injection targets, hazard persistence, the tick buckets |
| Game tests | `src/gametest` | a headless 1.21.1 dedicated server with Evoluta loaded | behaviour through the real mixins, events, loot tables, recipes, commands and block breaking, with in-world mock players |
| Fuzzers | both of the above | the same | thousands of random scenes checked against an independent oracle, replayable from a seed |
| Load test | `LoadGameTest` | the game-test server | the cost of Evoluta's own work per tick against a budget |

`./gradlew build` runs them in that order and then remaps the jar and checks its contents (`verifyJar`: the mod descriptor,
both mixin configs, no dev-only classes or data, and no unremapped Yarn names).

```bash
./gradlew build                  # everything, the way a release is checked
./gradlew test                   # JUnit only
./gradlew runGametest            # game tests only; verdicts in build/gametest/junit.xml
```

The game-test run ends with `All N required tests passed` in its log; any failure exits non-zero and names the test.

## What the game tests cover

- **Spawning**: the real `MobEntity#initialize` path, off-thread (structure) spawns, the blacklist, mobs given a mutation by
  tag, zombie conversions, saving and loading a mutant, the champion cap per chunk, and every command.
- **Hits and deaths**: every element's hit and death effect, the Brute's shield lock, the Stalker's pace by gaze (sprinting,
  hesitating, neutral, past 14 blocks, past 32) and its melee-only lunge through the real scheduler, the Brute's plant, rush
  and slam, Apex ambushes and hunters, creepers surviving their blast and recharging.
- **Hazard blocks**: each block hurting what walks in and sparing its own element and the loot in it, trails, death discs and
  craters going back to the original blocks, refusing a player's break, a TNT blast and a piston, and surviving a save and load.
- **Soul Meat and grafting**: drops by kind, element and grade; the Soul Forge's graft and the rag's scrub; grafts in a fight
  (element damage and effects, full swings only, feast, an Apex Ignited kill's soul fire, the creeper burst, bow arrows, the
  skeleton volley, armour wards and bonuses); champions' grafted gear.
- **Mining grafts**: every graft through a survival player's real block break, each drop change and after-break effect,
  Blast Mining's tool wear and its stop before the tool breaks, Sandsifter on shovel blocks only, Tunnel Sense's waits.
- **Champions and the locator**: the spatial index, the locator target and its signal, loot and experience.
- **Cost**: see below.

## The fuzzers

| Fuzzer | What it throws at the mod | What it checks |
|---|---|---|
| `ToolGraftFuzzGameTest` | every block in the game in a random state, random grafted gear, and a real player breaking the middle of random scenes (veins, lava, water, waterlogged blocks, crops, ice, beehives, claimed blocks, three game modes) | with every chance forced to fire, the drops equal what the documented rules make of vanilla's own rolls, and with none firing they equal vanilla's; no block yields more of itself than vanilla gives; every block that differs from the same break without grafts is a documented effect |
| `ToolGraftAuditGameTest` | the mining grafts from the other ends | every graftable item's attribute modifiers against the documented numbers (saved, sent, equipped, scrubbed); the chances measured against the numbers the docs state; the Soul Forge, the command and mutants' gear putting grafts on every tool |
| `CombatGraftFuzzGameTest` | every living entity type as a victim, random grafted armour, wet or dry, every kind of blow (full, half and misaimed swings, mob hits, stings, arrows, trident throws, blasts, cold, thorns, sonic booms) | which hits count, the bonus damage and the wards to the number, the poison ward, effects, freezing and fire, how many bursts land and on whom, retaliation, feasts, soul fire, skeleton volleys |
| `HazardFuzzGameTest` | random terrain (floors from dirt to bedrock, chests, plants, tall plants, snow, torches, rails, wire, overhangs), one to four overlapping hazard sources of every element and tier, now and then a TNT blast, with the world clock moved on instead of waited for | hazards stand only in air, plants or plain blocks a blast could break and take nothing else with them; players cannot mine them and a blast leaves them standing; afterwards every block is exactly as it was and nothing lies on the ground |
| `MutantArenaFuzzGameTest` | four walled arenas (two lit, two dark) playing rounds of mutants of every kind, tier, element and archetype, AI on, against grafted players who strike back, with mob griefing off | every tick every creature is sound (finite position, sane speed, health within its maximum, freezing within its caps) and keeps a mutation its kind may have; champions stay in the locator index and leave it when they go; after each round the arena is exactly as built |
| `EvolutaConfigFuzzTest` (JUnit) | random, broken and hostile JSON config | it never throws, every setting lands in its documented range, and what the mod writes back reads back unchanged |

Knobs, for both the game tests and the config fuzz:

```bash
./gradlew runGametest -Pevoluta.fuzz.cycles=100000   # cases per fuzz test (default 3000)
./gradlew runGametest -Pevoluta.fuzz.seed=42         # the seed every case derives from (default 20260924)
./gradlew runGametest -Pevoluta.fuzz.only=333        # replay one case on its own
```

Every failure prints the seed and case number to replay. The default is small enough to run on every build and fixed so
a build is repeatable; the larger runs are for hunting. Beyond the build the fuzzers have been run at up to 100,000 cases
per test over seeds 1 to 8 (the arena fuzz at 50,000 over seeds 1 to 5, about 1,000 rounds, 4,600 mutants and 5,000 blows on
players), and the config fuzz at 200,000 cases. All passed.

### What the fuzzers found

Each of these was a defect the ordinary tests had missed. Each was fixed and is held by the fuzzers and tests since.

Mining grafts:

- Silk Touch together with Prospector gave two ore blocks for one: there is no extra on an ore that drops itself.
- Silk Thread on a beehive gave its bees twice: it now skips block entities, melting ice and infested blocks.
- Cold Snap hardened lava inside a claimed block: it now asks the break event first.
- Sandsifter worked from a held chestplate: it is mining gear only.
- A Blast Mining blast reached ore past a claimed block: it spreads only from blocks it broke.
- Empty stacks in loot lists were not dropped first, and smelted output did not come in whole stacks.

Combat grafts:

- The poison ward cut an infinite poison to one tick: it stays infinite.
- Each creeper graft counted the same hit, so three grafts burst on every hit: one count per hit, the grafts' burst damage
  summed.
- A burst (or thorns, or a sonic boom) drew retaliation from everything it hit, the victim twice: retaliation answers melee only.
- A skeleton graft took a trident's throw for its volley: volleys come only from weapons with no use of their own.

Hazard blocks:

- Death bursts, trails and soul fire took the lower half of tall grass and tall flowers, so the top fell and the plant was
  uprooted: hazards never take half of a two-block thing.
- A Permafrost crater's ice pillars rose through the ground and ceilings, uprooting what grew on top: they rise through open
  air only.
- A hazard due while its chunk was unloaded was re-queued and polled every 200 ticks for ever, found through the load test: a
  test world's save had gathered about 3,300 of them and every 200th tick spiked to 9 ms. Such hazards now wait in a
  per-chunk list, unpolled, and go back when the chunk loads.

## Cost

`world/TickMeter` times Evoluta's own server work each tick; `/evoluta stats` prints it and `LoadGameTest` reads the same
meter. The test puts 200 mutants of every kind on 50 survival players and asserts an average under 80 microseconds of
Evoluta work per tick. Across many runs on one development PC the median was about 12 to 45 microseconds and the average
about 17 to 65 (single-tick spikes of a few milliseconds, from rushes and block placements arriving in bursts, drive the
average and not the median). Two consecutive builds of the same code on a PC with other game servers running gave a median
of 20.0 and 44.9 and an average of 33.3 and 65.3, so treat a single run as an estimate. These are timings from one
machine, not a guarantee, and they say nothing about vanilla's own cost for the same mobs. The test is sensitive to other
work on the machine: a result taken while a game client runs beside it is not meaningful.

Per mined block, the fuzz times the same scene broken with and without grafts, taking turns at going first: over 33,333
scenes a grafted break took a median of 11.7 microseconds against 7.1 (mean 28.4 against 17.7, blasts and Tunnel Sense
included). Short runs time cold code and read several times higher.

## Tips for writing tests here

These cost real time to find. They are listed so the next test does not pay for them again.

- `TestContext#spawnMob` strips a mob's AI goals; use `spawnEntity` for a mob that must act. A mob with AI disabled never
  moves, even from a velocity set by code. Test players never move either (their movement comes from packets) and keep
  vanilla's 60-tick join immunity; `TestPlayers.vulnerable` ends it for a test that must hurt one.
- Game tests in one batch share the world and run side by side. Anything that needs "no other player near" or a forced
  config gets its own `batchId` with `@BeforeBatch` / `@AfterBatch`, and a test cleans up only its own entities.
- A game test counts its ticks by the world's clock: moving the clock on (to expire hazards) fires every running test's timed
  tasks early. Move it, act and set it back in the same breath.
- Only a game test's own chunks are force-loaded: keep moves within two chunks.
- Building a fuzz scene: clear it from the top down, place decorations only where `canPlaceAt` allows, settle every block
  with `Block.postProcessState`, and sweep up items. `FORCE_STATE` placement still runs `onBlockAdded`.
- `ZombieEntity#initialize` makes one zombie in twenty a baby, which changes its Soul Meat kind: pass
  `new ZombieEntity.ZombieData(false, false)` for a grown one. A test that reseeds the world's random changes later tests'
  luck; reseed from `RandomSeed.getSeed()` when done.
- A loot table rolls from its own random sequence, not the world's: reset the sequence to roll the same drops twice.
- Fabric's `PlayerBlockBreakEvents.AFTER` fires before the block's drops and before the tool wears, and only when vanilla's
  `removeBlock` removed something.
- `NativeImage#getColor` and `setColor` are ABGR in 1.21.1, not ARGB.
- Tests of exactly side-on angles are fragile (`MathHelper`'s sine table leaves a remainder); use angles clear of 90 degrees.
- `/summon` with data skips `initialize`, so no random gear: put a skeleton's bow in the data.

## Not covered

Stated plainly, so a green build is not read as more than it is:

- **How anything looks or sounds in a running game.** Skins, auras, the glint and glyphs, particles, screen shake, the locator's
  needle, clicks and gem, the Soul Forge screen, tooltips, Tunnel Sense's chime and sparks. The client code compiles, its mixins
  resolve against the 1.21.1 bytecode and the painters have unit tests, but whether it looks right is judged by eye, and the
  `runProductionClient` task exists for that.
- **Shader packs.** The glint and skins use vanilla render layers; compatibility with a given shader pack is untested.
- **Real modpacks and live servers**, including claim and protection mods that bring mixins of their own.
- **A real server restart in the middle of a crater.** Hazards waiting to go back are saved and loaded through the world's
  persistent state and that is unit-tested, but a crash or restart with a crater standing has not been run.
- **Balance.** Every number is a first-pass default, not a tuned one.
