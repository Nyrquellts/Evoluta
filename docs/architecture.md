# Architecture

How Evoluta is put together. Gameplay is described in the [README](../README.md) and the numbers in
[soul-meat.md](soul-meat.md); this page is for people reading or changing the code.

## Layout

- `src/main` (`com.nyr.evoluta.common`): server-safe code. Loom's split source sets make referencing a client class from
  here a compile error.
- `src/client` (`com.nyr.evoluta.client`): the locator needle, clicks and gem, glowing eyes, Soul Meat's pulsing soul, auras,
  skins, the glint, screen shake, the Soul Forge screen and the client mixins. `common/skin/SkinPainter` is used only by the
  client but is pure pixel math over int arrays with no client class, so it sits in `src/main` beside its tests.
- `src/test`: JUnit with `fabric-loader-junit` (Minecraft classes load, registries are not bootstrapped).
- `src/gametest`: a dev-only mod (`evoluta-gametest`) with the game tests, the fuzzers and some test datapack tags. Never shipped.
- `src/showcase`: a data-only dev mod with the demo scene function. Never shipped.
- `art/`: scripts that paint the shipped art. `python art/soul_meat.py [preview_dir]` repaints every Soul Meat texture and
  model, `silk_rag.py` the rag, `blight_locator.py` the locator's frames and gem, and `hazard_blocks.py` the hazard blocks'
  models and states. They need Pillow and are not in the jar.

## Data model

- `mutation/MutationData` (3 bytes: tier, element, archetype) lives in Fabric's data attachment `evoluta:mutation`: saved with
  the entity, synced to tracking clients, absent on ordinary mobs. The ids are part of the save format and never change;
  unknown ids fail to decode. The Alpha archetype and the Abyssal element are reserved ids that nothing rolls or grants.
- Stats are persistent attribute modifiers with `evoluta:` ids (`Mutations`), re-checked on every load.
- Grafts live in the data component `evoluta:grafts` (kind, element, grade, rolled power 0-1). Soul Meat's grade is the
  component `evoluta:soul_grade`.

## Spawning

- `mixin/MobEntityMixin` rolls at `MobEntity#initialize` RETURN (`SpawnMutations`, `MutationRoller`). Tags in
  `data/evoluta/tags/entity_type` decide eligibility and `config/evoluta.json` the odds.
- Structure and chunk-generation spawns run `initialize` on world-generation threads. There the mob only gets the command tag
  `evoluta.pending_roll.<reason>` and rolls on its first load, on the server thread.
- A chunk holding a champion caps new spawns there at Evolved (`maxChampionsPerChunk`, default 1).
- `soul/MutantGear`: a champion's graftable equipment gets one graft of its kind and element at its tier on its first load
  (after the mob's own `initialize` has handed out gear), marked by the saved attachment `evoluta:gear_grafted` so reloads never
  regraft. Drop chances stay vanilla's.

## Tactics

- `tactics/Tactics` + `MutantBuckets` + `world/EvolutaWorlds` run the staggered tactical tick: a mutant is visited when
  `(worldTime + id % 10) % 10 == 0`, and does nothing while no player is within 32 blocks.
- `tactics/Stalking`: on each visit a Stalker reads its target's gaze and sets one temporary speed modifier,
  `evoluta:stalker_pace` (+0.5 back turned; while seen +0.25 for an Apex, 0 otherwise; 0 beyond 14 blocks). It lunges (melee
  only, 4.5 blocks, 60-tick cooldown in the unsaved attachment `evoluta:stalker_last_lunge`) or, watched within 60 degrees,
  sidesteps as it comes. Nothing that hunts holds back: a lesser Stalker lunges only at a turned back, an Apex of any
  archetype whichever way the target faces. Movement speed is a tracked attribute, so clients see the pace too; the shadow
  trail reads it.
- `tactics/BruteCharge` (Elite and Apex Brutes): with the target 4.5-10 blocks off, in sight, within 2 blocks of height, the
  Brute on the ground, off its 100-tick cooldown and not holding a bow, crossbow or trident, it plants (temporary speed modifier
  `evoluta:brute_planted` at -100%, a growl, ground dust). The next visit launches a straight rush at the target's position
  then. `WorldState.charging` holds the Brutes mid-rush and `EvolutaWorlds` pushes them every tick (0.7 blocks a tick, 15 ticks
  at most): touching the target is the slam (`tryAttack` plus a 1.4 shove), a wall is a stagger (Slowness IV, 30 ticks). The
  unsaved attachment `evoluta:brute_charge` holds the phase. While planted or rushing, `Stalking` leaves the Brute alone.
- `tactics/Ambush`, `Tactics.hunts`: every Apex runs `Stalking`. An Apex monster with no target in light 7 or less lies in wait
  (temporary speed modifier `evoluta:ambush_still` at -100%, so never saved), and `MobEntityMixin` refuses it targets and
  ambient sounds while it waits. It wakes (a roar, `CombatEvents.callTheHunt`) on a player within 6 blocks, a turned back within
  16 in its sight, or a hit.

## Combat and hazards

- `combat/CombatEvents` hooks Fabric's `ALLOW_DAMAGE`, `AFTER_DAMAGE` and `AFTER_DEATH`.
- `combat/HazardBlockTypes` registers the blocks that hurt: `evoluta:mutant_fire` (a non-spreading `AbstractFireBlock` on
  vanilla's fire models, in `#minecraft:fire`; burns the living but Ignited mutants, never items), `evoluta:soul_fire` (the
  graft's, on vanilla's soul fire models; burns `Monster`s only), `evoluta:frost_spikes` (packed and blue ice spikes; slow, and
  cut and freeze whatever moves through, sweet-berry-bush style) and `evoluta:blight` (tinted sculk crust; slows and poisons).
  Each has a `tier` property where it matters, no item, no drops and a pathfinding penalty (`LandPathNodeTypesRegistry`).
- `combat/Hazards` puts them down through `HazardBlocks`: a trail block every 20 ticks while a mutant chases (3 s), a disc
  where one dies (1.5, 2 or 2.5 blocks by tier, 5 s; spikes on about half of it), and the creeper craters.
- `combat/HazardBlocks` is a world `PersistentState` (`evoluta_hazard_blocks.dat`). It replaces only air, plants or plain
  blocks no harder than stone (never block entities, never half of a `DOUBLE_BLOCK_HALF` block whose other half would fall,
  and ice pillars rise only through open air) and restores each when its time is up: 60 ticks for a trail block, 100 for a
  death disc and 120, 160 or 200 by tier for a crater (death discs and craters add up to 20 random ticks). A hazard due while
  its chunk is unloaded waits in a per-chunk list, unpolled, and goes back when `ServerChunkEvents.CHUNK_LOAD` brings the
  chunk. It lets nothing take them: it refuses player breaks (`PlayerBlockBreakEvents.BEFORE`), `mixin/ExplosionMixin` removes
  them from every explosion's block list (at `collectBlocksAndDamageEntities` RETURN, so what the server breaks and what it
  sends clients agree) and `mixin/PistonBlockMixin` makes them immovable.
- `combat/MutantCreepers`: `mixin/CreeperEntityMixin` takes over `explode` at HEAD for mutants: a smaller blast (1.5, 1.75 or
  2.0, doubled when charged, `ExplosionSourceType.MOB` so `mobGriefing` decides) that the creeper survives (30, 25 or 20%
  self-damage, recoil, fuse out through `CreeperEntityAccessor`), then a 100, 80 or 60 tick recharge enforced at the head of
  `tick` (unsaved attachment `evoluta:creeper_last_blast`). `Hazards.blastHazards` leaves the element's hazard.
- `combat/Juice`: hit bursts of the element with a sound (from `CombatEvents.afterDamage` and `GraftCombat.onHit`, at most 12 per
  world per tick, sent only to players within 16 blocks), a champion's soul burst on death, the charge lane in red dust, a
  whoosh on an Apex's lunge (never a Stalker's), and camera shake: `ShakePayload` (one float) goes only to clients that
  registered its channel, `client/render/ScreenShake` decays it and `client/mixin/CameraMixin` turns the view at `Camera#update`
  TAIL (never the aim), scaled by the Distortion Effects setting.

## Soul Meat and grafting

- `soul/SoulKind` maps a mob to its kind through `#evoluta:soul_kind/<kind>` tags. `item/SoulMeatItem` is one item per element
  and kind, `evoluta:<element>_<kind>_soul_meat`, graded by `evoluta:soul_grade`; the loot function `evoluta:soul_meat` turns
  a mutant-table entry into the mob's own meat. The locator recipe takes any `#evoluta:soul_meat`.
- `soul/Graft`, `Grafting`, `GraftCombat` and `forge/SoulForge*`: at most 3 grafts per item in `#evoluta:graftable/weapon`,
  `/armor` or `/tool` (vanilla enchantable tags). `Grafting.weighted` orders them strongest first and weights overlaps
  1, 0.6 and 0.35. Lasting bonuses are rewritten into the stack's vanilla attribute modifiers, ids
  `evoluta:graft/<slot>/<n>/<name>`; armour's own points live on the item, so they are copied in first and the item's defaults
  come back when the last graft goes.
- `GraftCombat`: bonus damage at the head of `LivingEntity#damage` (`LivingEntityMixin`), effects on `AFTER_DAMAGE`, feast and an
  Apex Ignited graft's soul fire on `AFTER_DEATH` (a counted hit's kills only), the skeleton volley on `UseItemCallback`, and
  the poison ward at the head of `addStatusEffect`. `PlayerEntityMixin` records each swing's target and charge (attachment
  `evoluta:swing`) because vanilla resets the cooldown before the hit lands.
- The Soul Forge is a plain block whose screen handler grafts and scrubs through vanilla button clicks; the roll happens on the
  server at the click. `client/SoulForgeScreen` draws its panel in code and `SoulTooltips` lists grafts on gear.
- Mining grafts (`soul/ToolGrafts`): lasting bonuses are attribute modifiers from `Grafting.toolBonuses` (Corrosion: mining
  efficiency; Aqua Lung: submerged mining speed; Frenzy: block break speed; Long Reach: block interaction range). Drops change at
  the RETURN of the static `Block#getDroppedStacks(..., Entity, ItemStack tool)` (`BlockMixin`), which every block's tool drops
  pass through; Silk Thread rolls the drops again with a graftless Silk Touch copy of the tool, so it never comes back to the
  hook. `IceBlockMixin` (MixinExtras on `hasAnyEnchantmentsIn`) stops cut ice melting, as Silk Touch does. After-break effects run
  on Fabric's `PlayerBlockBreakEvents.AFTER`, which fires inside `tryBreakBlock`, before the tool wears and the drops fall. Blast
  Mining breaks the vein through nested `tryBreakBlock` (claims, spawn protection, other mods' break hooks and the tool's wear all
  apply), with a static guard so blasted ore does not blast again, and stops with 2 durability left. Sandsifter multiplies
  `PlayerEntity#getBlockBreakingSpeed` on both sides (the grafts component syncs, so the client's prediction agrees). Tunnel
  Sense reads a cube of side 2r+1 (r 3-5), then waits 10 ticks after finding nothing and 120 after a chime (attachment
  `evoluta:tunnel_sense`, not saved).

## Client rendering

- Auras: `common/particle/EvolutaParticles` registers five simple particle types (registries need both sides) and only the
  client spawns them. They draw vanilla sprites listed in `assets/evoluta/particles/*.json`; colour, glow (a minimum block
  light), size and motion are set in code.
- Skins: the first time a mutant of a given texture, element and tier is drawn, `MutantSkins` reads the mob's own texture from
  the loaded resources, `SkinPainter` paints two layers the texture's size (Ignited: char and Voronoi magma fissures;
  Permafrost: translucent ice, diagonal streaks, snow flecks, crystals; Toxic: rot blotches, pustules with veins and drips) and
  they are uploaded as textures under `evoluta:skins/...`, cleared on every resource reload. `MutantFeatureRenderer` draws the
  crust (`getEntityTranslucent`, the world's light, a Stalker's shade, the hurt flash) and the glow (`getEyes`, additive,
  full-bright, strength by tier, flickering per element). Higher tiers cover 30, 55 and 85% of the opaque pixels and always
  contain the lower tier's pattern; the seed is the texture id's hash, so every zombie shares one look and a resource pack or a
  modded mob gets its own with no art made for it. `NativeImage` colours are ABGR in 1.21.1, so `MutantSkins` swaps red and blue
  both ways.
- Soul cores: `client/render/SoulCores` maps each vanilla mutant to its chest face in a 64-wide texture. `SkinPainter` pulls the
  zone toward the core, paints a core and a ring of soul flesh sized by tier, and 2-4 veins each on its own random stream. Only
  the chest face may be painted over clear pixels, which is how a skeleton's core hangs inside its see-through ribcage.
- Brute bulk and Stalker shade come from `render/MutantLooks` through the one render mixin
  (`client/mixin/LivingEntityRendererMixin`); its injectors are `require = 0`, so a mod that rewrites that method costs the look,
  not a crash.
- The glint is described in [elemental-glint.md](elemental-glint.md).

## Champions and the locator

- `champion/ChunkSpatialIndex` (chunk-keyed, ring search) tracks champions; `LocatorSync` sends `TargetPosPayload` every 30
  ticks with the nearest champion's position and element; `ChampionRewards` handles the loot tables `evoluta:mutant/<tier>` and
  bonus experience.
- The client's needle predicate, clicks (`LocatorClicks`) and gem (`LocatorGem`) read that signal.

## Cost

An ordinary mob carries no Evoluta data. Mutants are visited on the staggered 10-tick schedule; combat effects, grafts and mining
grafts run from events that already happen. `world/TickMeter` times Evoluta's own server work per tick and `/evoluta stats`
prints it; `LoadGameTest` reads the same meter against a budget.
