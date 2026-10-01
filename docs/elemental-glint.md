# Elemental glint

Grafted gear, and gear worn or held by elemental mutants, shimmers in its element's light even without enchantments. The
renderer receives a temporary `ItemStack` copy with a glint override: inventory and equipment data, real enchantments,
damage, attributes and packets are unchanged. Ordinary unenchanted items still do not shimmer.

## Where the colour comes from

In order of priority:

1. **The item's grafts.** The strongest element wins; an item with several elements shows each in turn, every 2 seconds.
   Apex grafts use a brighter variant.
2. **An infusion key.** A string under the namespaced key `evoluta:elemental_infusion` in the existing
   `minecraft:custom_data` component, with the value `ignited`, `permafrost` or `toxic`. This keeps the feature entirely
   client-side and needs no registered component:
   ```mcfunction
   /give @s minecraft:diamond_sword[minecraft:custom_data={"evoluta:elemental_infusion":"permafrost"}]
   /give @s minecraft:iron_axe[minecraft:custom_data={"evoluta:elemental_infusion":"ignited"}]
   /give @s minecraft:iron_chestplate[minecraft:custom_data={"evoluta:elemental_infusion":"toxic"}]
   ```
3. **The holder's element,** when the holder is an elemental mutant of any tier.

Anything else (no element, the reserved Abyssal element, missing or unsupported data) uses the ordinary purple glint.

## How it is drawn

Vanilla 1.21.1's glint shader takes a position and a texture and no vertex colour, so simply tinting a vertex has no
visible effect. Instead the client paints each element's glint in memory as two layers and moves each its own way:

- **Sheen** (`GlintArt`): broad, soft bands of light with a thin bright highlight at whole-number frequencies so they tile,
  warped by the element: licking heat for Ignited, crystal facets for Permafrost, a gloopy wobble for Toxic.
- **Detail** (`GlintArt`): wisps and embers for Ignited, four-pointed ice glints and needles for Permafrost, bubbles and drips
  for Toxic, box-blurred so they stay soft.

Both are 256 pixels, black between the light so the item keeps its own look, and dimmer on the armour pass. `GlintMotion`
moves each layer at its own speed and scale (sheen 6 on items, 0.75 on armour; detail 12 and 1.5; the armour pass runs at an
eighth of the item pass so a chestplate shows what a sword does) on a clock of seconds x Glint Speed x 2. Vanilla shaders, the
Glint Speed and Glint Strength settings, fog, depth, blending, armour layering and Fabulous targets are retained. No vanilla
images are shipped.

`ElementalGlintLayers` gives each pass and colour two fixed-buffer layers and returns both as one `VertexConsumers.union`
wherever vanilla asks for its glint. The item-renderer and armour/elytra mixins use `ModifyVariable`, the buffer storage uses
`ModifyArg`, and there are no `Overwrite` hooks. The provider wrapper substitutes only the five vanilla glint passes; surface
textures, dyes and trims keep their own consumers. Every colour and pass has a stable layer and a fixed buffer so that the
paired base and glint consumers cannot flush each other. A resource reload frees the generated textures and regenerates them
from the new resource pack, keeping layer identity stable. Each render call owns its provider wrapper, so there is no global
current entity or tint to leak into later items. Missing source resources fall back to the original pass, and optional
injections and unknown third-party vertex providers degrade to vanilla rendering. Custom renderers that bypass vanilla's item,
armour or elytra paths may need their own integration.

Glyphs (`GraftGlyphs`): anything wearing or holding an Elite or Apex graft draws enchanting glyphs burning in the graft's element
(`evoluta:glyph_ignited`, `_frost`, `_toxic`) flying in toward it, following the Particles setting.

## Verification

```bash
./gradlew compileJava compileClientJava test --offline --console=plain
```

The JUnit tests cover: infusion decoding and invalid-data fallback; all mutation tiers; colour priority; that the render copy
leaves the real stack untouched; `NativeImage` channel, alpha and brightness conversion; the painter (`GlintArtTest`: tiling
over several seeds, colours, the sheen's smoothness against vanilla's, gaps, highlights, that every sword-sized window catches
light, and the detail's sparseness); the motion (`GlintMotionTest`: directions, that the layers move differently, and the
armour-to-item scale ratio); and the render hooks (`GlintRenderHooksTest`).

The hook test loads every injection target through its own Fabric Knot class loader with `mixin.debug.countInjections` enabled,
which checks each injection site without starting `MinecraftClient` or opening a window. The buffer regression writes paired
glint and base consumers for every coloured pass without issuing an OpenGL draw.

**Not covered:** how the glint looks in a running game, and its compatibility with any given shader pack. Those are judged by
eye.
