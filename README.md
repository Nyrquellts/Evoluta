# Evoluta

**Night-time mobs evolve.** Evoluta turns a share of hostile spawns into tiered, elemental mutants, gives the strongest
of them hunting tactics, and lets you turn what they drop into upgrades for your gear.

Fabric mod for Minecraft 1.21.1 · Java 21 · version 0.1.0

## What it is

Evoluta is a survival-difficulty mod with a payoff attached. It has four parts that feed each other:

- **Mutants.** A share of hostile spawns evolve into mutants. A mutant has a **tier** (Evolved, Elite or Apex), usually an
  **element** (Ignited, Permafrost or Toxic) and often a **fighting style** (Brute or Stalker). Elite and Apex mutants are
  *champions*: their eyes glow, and a **Blight Locator** points you to the nearest one.
- **Hunters, not punching bags.** Apex mutants stalk you, sprint at a turned back, pounce, and lie in wait in the dark.
  Mutant creepers survive their own blast. A Brute plants its feet and charges.
- **Hazards you can see.** Everything that hurts is a real block (fire, frost spikes, blight), never a particle you might
  mistake for decoration. They disappear after a few seconds and whatever stood there is put back.
- **Soul Meat and grafting.** Mutants drop Soul Meat of their own kind and element. At a **Soul Forge** you graft it onto a
  weapon, a piece of armour or a mining tool, up to three grafts per item. A graft adds the creature's ability and the
  element's damage, and gets stronger with the tier of the mob it came from.

It is built for modpacks: which mobs mutate is a datapack tag, the odds are one JSON file, grafting works on any
enchantable gear (modded gear included), and an ordinary mob carries no Evoluta data at all.

## Requirements and installation

- Minecraft 1.21.1
- Fabric Loader 0.16 or newer (developed against 0.19.5)
- Fabric API 0.116.17 or newer (the version it is tested with)
- Java 21
- Install the mod on the server **and** on every client.

There is no packaged release yet. Build the jar from source (see [Building](#building-and-testing)) and put
`build/libs/evoluta-0.1.0.jar` in the `mods` folder, next to Fabric API.

## What mutates

| Tier | Health | Damage | Notes |
|---|---|---|---|
| Evolved | +30% | +15% | |
| Elite | +80% | +35% | a champion |
| Apex | +150% | +60% | a champion; only at night by default |

Mobs that can mutate by default: zombies, husks, drowned, zombie villagers, skeletons, strays, bogged, spiders, cave
spiders and creepers. A blacklist (ender dragon, wither, warden, elder guardian and `#c:bosses`) always wins.

### Elements

Each element repaints the mob's skin from its own texture, gives it an aura that glows in the dark (both grow with the
tier: an Evolved mutant is marked on about a third of its body, an Apex almost everywhere) and leaves its own hazard
blocks behind.

- **Ignited.** Charred black skin split by glowing magma fissures; embers stream off its shoulders. Its hits set you
  alight, and it leaves real fire behind it while it hunts you and where it dies. That fire burns anything alive but
  Ignited mutants, never spreads, never burns a block and leaves drops alone.
- **Permafrost.** Flesh frozen under streaked ice and snow, with a circling frost mist. Its hits freeze you (vanilla
  freezing: frost overlay, slower movement) and numb your hands (Mining Fatigue, slower attacks), and standing within 3
  blocks chills you. It leaves jagged frost spikes while it hunts and raises a ring of them where it dies; they drag at
  you and cut and freeze you as you push through. Any piece of leather armour stops the freezing, and Permafrost
  mutants never freeze.
- **Toxic.** Rotting skin with glowing pustules, veins and dripping acid. It sneezes poison, leaves glowing blight
  behind it while it hunts and a pool of it where it dies, which drags at your feet and poisons you (Poison II from an
  Apex). Toxic mutants cannot be poisoned.

Hazard blocks cover 1.5 to 2.5 blocks around a death, by tier, and are gone after 3 to 6 seconds with the original blocks
put back. They cannot be mined, blown up, pushed or looted, and mobs walk round them. They never take half of a tall plant
or a door, and a crater's ice spikes rise only through open air, so nothing growing nearby is uprooted.

Each vanilla mutant also carries its soul in its body: a core glowing in its chest (inside the ribcage for a skeleton, on
the abdomen for a spider) with veins running into the skin. Skins are painted from each mob's own texture, so they fit
resource packs, and any modded mob you add to `#evoluta:can_mutate` gets the element's skin (without a core).

### Mutant creepers

Their blast is smaller than vanilla's (power 1.5, 1.75 and 2.0 by tier, against vanilla's 3; doubled when charged) and
breaks blocks as usual (`mobGriefing` decides), but they survive it: it costs them 30, 25 or 20% of their health, throws
them back, and they need 5, 4 or 3 seconds to recharge. Each blast leaves its element's hazard for about 6 to 10 seconds, then
every block goes back as it was. Ignited turns the crater floor to magma with fire over most of it; Toxic turns it to slime
goop crusted with blight; Permafrost raises ice pillars that hurt, freeze and throw up whoever stands by them and covers
the rest in frost spikes. Elite and Apex Permafrost blasts also fill half the crater with powder snow.

### Archetypes

- **Brute.** 1.35x size, hitbox included (it will not fit through a 2-high doorway). It kicks up the ground as it walks,
  takes half knockback, and any hit it lands on a raised shield locks the shield for 3 seconds. Brute skeletons trade the
  bow for a bone dagger. Elite and Apex Brutes **charge**: from 4.5 to 10 blocks off they plant their feet, growl for half a
  second, then rush you in a straight line and slam you (a full hit and a heavy shove, even through a shield). Step aside
  and they thunder past; one that runs into a wall staggers. Once every 5 seconds.
- **Stalker.** 0.88x size, drawn in shadow, no footsteps (nor step vibrations for sculk). Within 14 blocks it sprints 50%
  faster while your back is turned, and a melee Stalker leaps at you from 4.5 blocks without a sound (once every 3
  seconds). Look at it and it keeps coming, weaving side to side across your view. Stalker arrows make their target glow
  and send idle mutants nearby after them.

### Apex hunters

Every Apex mutant hunts, whatever its archetype, and none hangs back: it sprints at a turned back, comes on faster while
you watch it, and pounces from 4.5 blocks whichever way you face. With no target, somewhere dark, an Apex **lies in
wait**: it stands still and silent, eyes glowing. A player within 6 blocks, a back turned to it within 16 blocks in its
sight, or a hit wakes it with a roar, and idle mutants within 16 blocks join the hunt.

## Rewards: Soul Meat

Mutants with an element drop Soul Meat of their own kind and element (an Ignited Skeleton's, a Toxic Baby Zombie's, ...),
graded by their tier: Evolved 10% of the time, Elite half the time, Apex one or two, when a player gets the kill, and
Looting helps. The soul inside glows in the element's colour and beats faster at higher grades. Mutants also give extra
experience (+5, +15 or +40 by tier). Which kind a modded mob counts as is a datapack tag, `#evoluta:soul_kind/<kind>`.

## Upgrading gear: the Soul Forge

Craft a Soul Forge (any Soul Meat above a smithing table, with polished blackstone on both sides and underneath) and graft
Soul Meat onto any enchantable weapon, armour piece or mining tool, modded gear included. An item takes up to three grafts
on top of its enchantments.

A graft rolls its power when you press **Graft**, higher for higher grades (Evolved 20-50%, Elite 45-80%, Apex 75-100%),
and gives two things:

- **The element's effect.** On weapons: fire damage and burning (an Apex Ignited graft's kills leave blue soul fire that
  burns monsters for 3 seconds, never players), frost damage and freezing, or blight damage and poison. On armour:
  retaliation, so whoever strikes the wearer in melee burns, freezes or is poisoned.
- **The creature's ability.** For example, on weapons: a zombie graft heals on kills, a husk starves, a drowned drags
  targets in, a skeleton fires an arrow on use, a creeper bursts every third hit. On armour: more health, shorter burns,
  longer breath, an arrow ward, a blast ward, safer falls, and so on. The full table is in
  [docs/soul-meat.md](docs/soul-meat.md).

A second or third graft sharing a kind or an element works at 60% and 35%, so stacking pays but cannot run away. A Diamond
Silk Rag (two diamonds, four string; three uses) wipes a graft off in the forge. Armour bonuses are vanilla attribute
modifiers on the item, so they cost nothing to run and other mods can see them. Weapon grafts fire only on fully charged
swings at the entity you aimed at; arrows carry the grafts of the bow that fired them.

### Mining gear

On pickaxes, shovels, axes and hoes both halves of a graft turn to digging. Ignited smelts some drops as they fall (with
the furnace's experience), Permafrost hardens lava and freezes still water around the broken block, and Toxic mines
faster. The creature adds one more: Gnaw (food from ore), Sandsifter (faster sand, gravel and dirt), Aqua Lung (underwater
mining), Prospector (an occasional extra ore), Frenzy (faster digging), Long Reach (further reach), Ice Cutter (ice drops
whole), Bountiful (an occasional extra crop), Blast Mining (a few more blocks of an ore vein), Silk Thread (an occasional
Silk Touch) or Tunnel Sense (a chime and sparks toward nearby ore). Nothing makes something from nothing, Blast Mining goes
through the game's own block breaking so claims and spawn protection hold, and Cold Snap asks claims before it touches a
block. Numbers and rules are in [docs/soul-meat.md](docs/soul-meat.md).

### Mutated gear

Elite and Apex mutants fight with mutated gear: every weapon, tool and armour piece they spawn with carries a graft of
their own kind and element at their tier. It shimmers on them, and it drops at vanilla's equipment chances (8.5%, more
with Looting), so grafted gear is rare loot.

### Glint

Grafted gear carries its element on any weapon, tool, bow, crossbow, trident, mace or armour piece, modded ones included:
a sheen of the element's light sweeps over it while soft detail drifts the other way, painted in code and drawn by
vanilla's glint shaders, so the Glint Speed and Glint Strength settings apply. Gear with several elements shows each in
turn, and Apex grafts are brighter. See [docs/elemental-glint.md](docs/elemental-glint.md).

## Fights and the Blight Locator

Every blow a mutant lands bursts with its element and sounds like it. Heavy moments shake your view: a mutant's blow on
you, a Brute's slam, a mutant creeper's blast, an Apex's roar, a champion's death. The shake follows the Distortion
Effects setting (0 turns it off) and only turns the view, never where you aim. A planted Brute marks the lane it is about
to charge in red dust.

A compass and any Soul Meat craft the **Blight Locator**: held in either hand, its needle turns toward the nearest champion
within 128 blocks and it clicks faster within 20. The soul gem in its rim glows the colour of that champion's eyes.

## Configuration

- **Which mobs:** datapack tags, no config. `#evoluta:can_mutate`, `#evoluta:blacklist` (always wins: dragon, wither,
  warden, elder guardian and `#c:bosses`), `#evoluta:archetype/brute` and `#evoluta:archetype/stalker`, and
  `#evoluta:soul_kind/<kind>`. Add a modded mob to `can_mutate` and it mutates like a vanilla one.
- **Loot:** loot tables `evoluta:mutant/evolved`, `elite` and `apex`, rolled on top of the mob's own loot.
- **Odds and numbers:** `config/evoluta.json`, written with every key on first start and read again by `/evoluta reload`.
  Chance per spawn (5%, +10% with local difficulty, x1.5 at night), tier weights, element and archetype chances per
  tier, health, damage and experience bonuses, element weights (0 turns an element off), spawn reasons (spawners, spawn
  eggs and commands are left out by default), dimensions to skip, champions per chunk and the locator's range. A bad value
  is clamped to the nearest valid one with a warning; a file that cannot be read at all never crashes the server (startup
  uses the defaults, and `/evoluta reload` keeps the settings already in use).

## Commands (permission level 2)

| Command | What it does |
|---|---|
| `/evoluta mutate <targets> <tier> [element] [archetype]` | Make mobs mutants. A champion made this way gets its gear grafted, as one that spawns does. |
| `/evoluta clear <targets>` | Remove the mutation. |
| `/evoluta inspect <target>` | Show a mob's mutation and health. |
| `/evoluta graft <targets> <kind> <element> <grade> [power]` | Graft Soul Meat onto the gear in each target's main hand, as the Soul Forge does (power 0 to 1, or rolled in the grade's range). |
| `/evoluta reload` | Re-read `config/evoluta.json`. |
| `/evoluta stats` | Per world: loaded mutants and champions, and what Evoluta's own server work cost per tick over the last 200 ticks (median, p99, max, average). |

Values: tier `evolved`, `elite` or `apex`; element `ignited`, `permafrost`, `toxic` or `none`; archetype `brute`,
`stalker` or `none`.

## Performance

Nothing mutated costs nothing: an ordinary mob carries no Evoluta data. Mutants with work to do are visited once every 10
ticks, staggered, and do nothing while no player is within 32 blocks; combat effects run from damage events, and mining
grafts run only when a block breaks. Auras cost the server nothing: each client draws them from the mutation data it
already has, and no particle packets are sent. On the client they follow the Particles setting (Decreased halves them,
Minimal keeps a fifth) and stop beyond 40 blocks.

A game test puts 200 mutants of every kind in reach of 50 survival players and meters Evoluta's own work per tick
(two recent runs on a PC with other work running: median 20.0 and 44.9, average 33.3 and 65.3 microseconds per tick; the test fails above an 80 microsecond average). In a fuzz run that timed 33,333 block breaks, a grafted break
took a median of 12 microseconds against 7 for the same break without grafts. Those figures measure Evoluta's work on one
desktop PC, not the vanilla AI cost of the mobs; check your own server with `/evoluta stats`.

## Status

Version 0.1.0. What has and has not been checked, by running it:

| Area | State |
|---|---|
| Spawning, tiers, elements, archetypes, Apex hunters and ambushes, mutant creepers, hazard blocks, Soul Meat drops, the Soul Forge, grafting, mining grafts, champion index and locator target, commands | Covered by game tests that run on a headless 1.21.1 server (`101` tests) and by JUnit tests (`83` tests). |
| Mining grafts, combat grafts, hazard blocks, live mutant fights, the config file | Also held to written rules by seeded fuzzers. A build runs 3,000 cases of each; they were also run at 50,000 to 100,000 cases over several seeds. See [docs/testing.md](docs/testing.md). |
| Server performance | Within its budget in a load test (see Performance). |
| Client | Compiles into the jar, and the client mixins are checked to resolve against 1.21.1. The mod has been launched in a production-style client (Fabric Loader, Fabric API, Sodium, Iris). |
| **Rendering** (skins, auras, glint, particles, screen shake, locator needle and gem, Soul Forge screen, tooltips, Tunnel Sense's chime and sparks, Blast Mining's burst) | **Not covered by automated tests.** The code compiles, the client mixins are checked to load, and the painters are unit-tested, but how it looks and how it behaves with a given shader pack is judged by eye. |
| Real modpacks and live multiplayer servers | **Not tested.** |
| Claim and protection mods | Cold Snap asks Fabric's block-break event, so a claim mod that protects land through its own mixins instead will not see it. |

Known gaps:

- The Alpha archetype and the Abyssal element are reserved in the save format, but nothing rolls or grants them.
- A cartographer zombie villager's monocle covers one of its glowing eyes.
- A server restart in the middle of a creeper crater is not tested; hazards waiting to go back do survive a save and load
  (unit-tested).
- Balance numbers (tier odds, Soul Meat drop rates, graft strengths) are first-pass defaults. Tune them in
  `config/evoluta.json`.

## Building and testing

Gradle runs on a recent JDK; the project compiles with a Java 21 toolchain, which Gradle provisions when it is missing.

```bash
./gradlew build              # compile, JUnit tests, game tests on a headless server, remapped jar, jar check
./gradlew test               # JUnit tests only
./gradlew runGametest        # game tests only; results in build/gametest/junit.xml
./gradlew runGametest -Pevoluta.fuzz.cycles=100000 -Pevoluta.fuzz.seed=9   # a bigger fuzz run
./gradlew runProductionClient   # a client as a player has it (+ Sodium, Iris and a demo scene)
```

The jar to install is `build/libs/evoluta-0.1.0.jar`. `runProductionClient` opens a game window; in a world,
`/function evoluta_showcase:scene` builds a demo scene (an Elite Ignited Brute, an Apex Permafrost Stalker, a baby zombie
and a creeper, a Soul Forge, and grafted gear and Soul Meat for you). Run it again to reset.

## Documentation

- [docs/soul-meat.md](docs/soul-meat.md): every graft, element, grade and number.
- [docs/architecture.md](docs/architecture.md): how the mod is put together.
- [docs/elemental-glint.md](docs/elemental-glint.md): how the glint is painted and drawn.
- [docs/testing.md](docs/testing.md): the test layers, the fuzzers and their knobs, and what is not covered.

## Project layout

```text
src/main       server-safe code shared by both sides: mutation data, spawning, tactics, combat, hazards, grafting, commands, config
src/client     client-only code: skins, auras, glint, locator needle, Soul Forge screen, screen shake
src/test       JUnit tests
src/gametest   a dev-only mod holding the game tests and fuzzers (never shipped)
src/showcase   a dev-only data mod holding the demo scene (never shipped)
art/           scripts that paint the Soul Meat, Blight Locator and Silk Rag textures
docs/          design and architecture notes
```

The remapped jar is checked at build time to hold the mod and none of the dev-only files. Mutant skins are painted at
runtime from each mob's own texture and the hazard blocks reuse vanilla models and textures, so no vanilla texture is
copied into the jar.

## License

Copyright (c) 2026 NYR. All rights reserved. See [LICENSE](LICENSE).
