# Soul Meat and grafting

Mutants drop Soul Meat of their own kind and element. Grafted onto a weapon, a piece of armour or a mining tool it gives
the kind's ability and the element's damage, stronger by tier. Everything reuses vanilla systems (attributes, status
effects, damage types, tags, glint, cooldowns, particles), so it costs almost nothing at run time, works on weak
machines and busy servers, and reaches other mods' gear. All numbers below are defaults; `p` is the graft's rolled power
(0 to 1).

## Soul kinds

Which kind a mob is comes from entity-type tags `#evoluta:soul_kind/<kind>`, so a modpack maps its own mobs by datapack. A
baby of the zombie family counts as `baby_zombie`. A mutant with no kind drops nothing extra.

| Kind | Default mobs | Weapon ability | Armour bonus (per graft) |
|---|---|---|---|
| zombie | zombie | Feast: heal 1+2p HP on a kill | +1+1p max health |
| husk | husk | Parch: Hunger II for 3+3p s | burn time -10-20p% |
| drowned | drowned | Undertow: full hits pull the target in; +1+2p damage to wet targets | +1+2p oxygen, +10+20p% water speed |
| zombie_villager | zombie villager | Plague: Weakness I for 2+3p s | +0.5+1p luck |
| baby_zombie | baby zombies | Frenzy: +5+15p% attack speed while held | +2+4p% movement speed |
| skeleton | skeleton | Bone volley: use (right-click; sneak-use when the other hand holds a shield) fires an arrow carrying the element, cooldown 4-2p s, from a weapon with no use of its own (a trident keeps its throw); on bows every arrow carries it | projectile damage -5-10p% |
| stray | stray | Frostshot: Slowness II for 1+2p s (hits and arrows) | freezing -25-50p% |
| bogged | bogged | Spore shot: Poison I for 2+2p s (hits and arrows) | poison time -25-50p% |
| creeper | creeper | Blast: every third full hit bursts at the target (2+4p damage around it, no blocks, never the wielder), cooldown 5 s; several creeper grafts count each hit once and add their damage | +0.2+0.4p explosion knockback resistance, blast damage -10-20p% |
| spider | spider | Ensnare: 20+30p% chance of Slowness III for 1.5 s | +1+2p safe fall; Apex grade on boots +0.5 step height |
| cave_spider | cave spider | Venom: 50% chance of Poison II for 1+2p s | +10+20p% sneaking speed |

## Elements

Element damage uses Evoluta damage types in vanilla's tags, so vanilla balance applies by itself: fire does nothing to
fire-immune mobs, blazes and magma cubes take extra from ice (`#minecraft:freeze_hurts_extra_types`), undead shrug off
poison. Weapon effects fire once per full-strength hit (attack cooldown at 90% or more), never on spam clicks.

| Element | Weapon (per graft) | Armour (per graft) |
|---|---|---|
| Ignited | +0.5+1.5p fire damage, target burns 2+4p s; Apex grade: a kill leaves soul fire blocks, radius 1.5+p, 3 s, that burn monsters and never players | burn time -15-25p%; melee attackers burn 1+2p s |
| Permafrost | +0.5+1.5p freeze damage, +40+80p freeze ticks (slows; frost overlay on players) | freezing -25-50p%; melee attackers gain 40+60p freeze ticks |
| Toxic | +0.5+1.5p blight damage, Poison I for 2+3p s (Poison II at p 0.9+) | poison time -25-50p% (an infinite poison stays infinite); melee attackers get Poison I for 1+2p s |

Retaliation answers melee only (a player's or mob's own blow, a sting): not a burst, thorns, a sonic boom or a shot.

## Grades and rolls

Soul Meat carries the tier of the mob that dropped it. Grafting rolls a power `p` in the grade's range; Apex has the best
rolls and the best looks.

| Grade | p range | Drop (killed by a player) |
|---|---|---|
| Evolved | 0.20-0.50 | 10%, +5% per Looting level |
| Elite | 0.45-0.80 | 50%, +10% per Looting level |
| Apex | 0.75-1.00 | 1-2, +1 per Looting level |

## Grafting, stacking and the Diamond Silk Rag

- Any item in `#evoluta:graftable/weapon` (vanilla's `#minecraft:enchantable/weapon`, `/bow`, `/crossbow` and `/trident`:
  swords, axes, maces, bows, crossbows, tridents), `/armor` (`#minecraft:enchantable/armor`) or `/tool`
  (`#minecraft:enchantable/mining_loot`: pickaxes, shovels, axes, hoes) takes grafts, so modded gear that is enchantable
  works too. Up to 3 grafts per item; they stack with enchantments.
- A second or third graft sharing a kind or an element works at 60% and 35%, so stacking pays but cannot run away.
- Grafts live in one data component, `evoluta:grafts` (kind, element, grade, p). Lasting bonuses are written into the
  stack's vanilla attribute modifiers, so the game applies them with zero Evoluta work per tick; weapon and retaliation
  effects run from damage events only. Cooldowns use the vanilla item cooldown (the hotbar shows it).
- The power is rolled on the server when Graft is pressed. Before that the forge shows only the grade's range, so moving
  items in and out cannot reroll anything.
- Diamond Silk Rag: 2 diamonds and 4 string (shaped), 3 uses. In the Soul Forge it wipes the graft you pick.
- Attribute modifier ids carry the slot (`evoluta:graft/<slot>/<n>`), so pieces never overwrite each other.
- Weapon grafts fire only on fully charged swings at the entity you aimed at. An axe is a weapon and a mining tool, and gets
  both sets; a pickaxe, shovel or hoe fights as a plain one.

## Looks

- Grafted gear carries its element as a painted two-layer glint that keeps the item's own look (see
  [elemental-glint.md](elemental-glint.md)), so any weapon, tool or armour from any mod gets it. Several elements cycle every
  2 s. Apex grade: a brighter, busier pattern and two glyphs every 10 ticks burning in the element's colour, flying in to the
  holder; Elite, one every 20. Both follow the Particles setting.
- Tooltip: one line per graft in the element's colour, with kind, grade stars and roll, then the ability.
- Soul Meat items: one shape per family (zombie flesh strip, skeleton rib chunk, creeper pod cluster, spider abdomen, cave
  spider egg sac, baby curled knot); variants recolour their family (husk sand, drowned teal, stray frost-rag, bogged moss).
  The soul wisp inside pulses in the element's colour.
- On the mob: the core is painted into the chest, with a cavity edge, veins running from the core into the skin, and the core
  on the glow layer with a heartbeat. The painter spreads the mutation outward from it by tier.
- Blight Locator: a soul-bound compass (iron and copper rim, bone needle, a soul gem in the rim that glows the colour of the
  target champion's eyes and flares with each click; a dim violet ember while searching).

## Mining gear

On pickaxes, shovels, axes and hoes both halves of a graft turn to digging. Numbers run from the weakest Evolved roll
(`p` 0.2) to the best Apex one (`p` 1), at full weight; stacked grafts add at 60% and 35%, and no chance ever passes 75%.

| Graft | Name | Effect |
|---|---|---|
| Ignited | Smelting Touch | 22-50% of drops come out smelted, with the furnace's experience (logs to charcoal, sand to glass) |
| Permafrost | Cold Snap | lava touching the broken block hardens (source to obsidian, flow to cobblestone); still water freezes to Frost Walker's ice; claimed blocks are left alone |
| Toxic | Corrosion | +1.6 to +4 mining efficiency (Efficiency I is 2, II is 5) |
| zombie | Gnaw | each ore gives 1 food and 0.7-1.5 saturation |
| husk | Sandsifter | shovel blocks (sand, gravel, dirt, clay, snow) break 40-80% faster |
| drowned | Aqua Lung | +0.48 to +0.8 submerged mining speed (vanilla 0.2; Aqua Affinity sets 1.0) |
| zombie villager | Prospector | 14-30% chance an ore drops one more of its main drop, never when the ore drops itself |
| baby zombie | Frenzy | +8-20% block break speed |
| skeleton | Long Reach | +0.7 to +1.5 blocks of block reach |
| stray | Ice Cutter | ice, packed ice and blue ice drop themselves and leave no water |
| bogged | Bountiful | 15-35% chance a ripe crop (wheat, carrots, potatoes, beetroot, nether wart, cocoa, modded crops built on vanilla's) drops one more |
| creeper | Blast Mining | 10-20% chance an ore blasts out 2-4 more blocks of its vein, nearest first and only through blocks it broke, each wearing the tool |
| spider | Silk Thread | 11-25% chance a block drops as with Silk Touch, except blocks holding contents, melting ice and infested stone |
| cave spider | Tunnel Sense | mining stone, a chime and a spark trail toward the nearest ore within 3-5 blocks; the way to dig, not the spot |

Balance rules:

- Nothing makes items from nothing: no extra on an ore that drops itself, none on an unripe crop, and no Silk Thread where
  Silk Touch does more than change the drops (a beehive would give its bees twice).
- Blast Mining breaks only ore that is there, through vanilla's own block breaking (claims, spawn protection and the tool's
  wear all apply), spreads only from blocks it broke, and stops before the tool would break.
- Cold Snap asks claims first, through Fabric's `PlayerBlockBreakEvents.BEFORE`.
- Everything runs on events that happen anyway (drops rolled, a block broken); nothing runs per tick.

## Mutant gear

Elite and Apex mutants that spawn with weapons, tools (a zombie's shovel) or armour get one graft per piece, of their own
kind and element at their tier; only a weapon's graft joins their hits. Vanilla drop chances (8.5%, +1% per Looting)
make grafted gear rare loot, and it shimmers on the mob.
