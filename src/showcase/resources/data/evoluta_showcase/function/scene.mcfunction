# Evoluta showcase scene. Run it as yourself: /function evoluta_showcase:scene
# Night that stays night and nothing else spawning. Ahead: an Elite Ignited Brute zombie in grafted gear, an Apex
# Permafrost Stalker skeleton with a grafted bow, an Elite Toxic baby zombie and an Elite Toxic creeper; a Soul Forge
# to your right. It replaces your main hand (a sword with an Apex Ignited and an Elite Toxic graft), off hand (a
# Blight Locator) and chest slot (a chestplate with an Apex Permafrost graft), and gives you an Apex Soul Meat of
# each element and a Diamond Silk Rag to try the forge with. Run it again to reset the scene.
difficulty normal
time set midnight
weather clear
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false
# earlier takes: clear the mutation first so their death effects do not fire, then remove them and their drops
evoluta clear @e[type=minecraft:zombie,distance=..64]
evoluta clear @e[type=minecraft:skeleton,distance=..64]
evoluta clear @e[type=minecraft:creeper,distance=..64]
kill @e[type=minecraft:zombie,distance=..64]
kill @e[type=minecraft:skeleton,distance=..64]
kill @e[type=minecraft:creeper,distance=..64]
kill @e[type=minecraft:item,distance=..64]
kill @e[type=minecraft:experience_orb,distance=..64]
kill @e[type=minecraft:area_effect_cloud,distance=..64]
# summoning with data skips vanilla's random gear (and baby zombies), so each mob is handed its gear here; mutating a
# champion grafts its own gear with its kind and element, as when one spawns
summon minecraft:zombie ^ ^ ^10 {PersistenceRequired:1b,Tags:["evoluta_showcase_brute"],HandItems:[{id:"minecraft:iron_sword",count:1},{}],ArmorItems:[{},{},{id:"minecraft:iron_chestplate",count:1},{}]}
evoluta mutate @e[type=minecraft:zombie,tag=evoluta_showcase_brute,distance=..64] elite ignited brute
summon minecraft:skeleton ^5 ^ ^14 {PersistenceRequired:1b,HandItems:[{id:"minecraft:bow",count:1},{}]}
evoluta mutate @e[type=minecraft:skeleton,distance=..64] apex permafrost stalker
summon minecraft:zombie ^-4 ^ ^8 {IsBaby:1b,PersistenceRequired:1b,Tags:["evoluta_showcase_baby"]}
evoluta mutate @e[type=minecraft:zombie,tag=evoluta_showcase_baby,distance=..64] elite toxic
summon minecraft:creeper ^-7 ^ ^14 {PersistenceRequired:1b}
evoluta mutate @e[type=minecraft:creeper,distance=..64] elite toxic
# grafted gear: graft what the main hand holds, then copy it where it goes
item replace entity @s weapon.mainhand with minecraft:diamond_chestplate
evoluta graft @s zombie permafrost apex 1
item replace entity @s armor.chest from entity @s weapon.mainhand
item replace entity @s weapon.mainhand with minecraft:diamond_sword
evoluta graft @s skeleton ignited apex 1
evoluta graft @s creeper toxic elite 0.8
item replace entity @s weapon.offhand with evoluta:blight_locator
# the forge and something to graft with, given after the gear so nothing lands in the hand it replaces
setblock ^-3 ^ ^2 evoluta:soul_forge
give @s evoluta:ignited_skeleton_soul_meat[evoluta:soul_grade="apex"]
give @s evoluta:permafrost_stray_soul_meat[evoluta:soul_grade="apex"]
give @s evoluta:toxic_spider_soul_meat[evoluta:soul_grade="apex"]
give @s evoluta:diamond_silk_rag
