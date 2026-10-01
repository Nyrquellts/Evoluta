package com.nyr.evoluta.common.combat;

import com.nyr.evoluta.common.Evoluta;
import net.fabricmc.fabric.api.registry.LandPathNodeTypesRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/**
 * The blocks elemental mutants leave where they hunt, die and blow up, so the danger is something you can see, not a
 * haze you might take for decoration. All of them kitbash vanilla models and textures, drop
 * nothing, cannot be pushed, and only ever stand for a few seconds through {@link HazardBlocks}. They have no items.
 * Mobs path around them.
 */
public final class HazardBlockTypes {
	public static final Block MUTANT_FIRE = register("mutant_fire", new MutantFireBlock(fire(MapColor.BRIGHT_RED, 15), false));
	/** A player's Apex Ignited graft leaves this where its kills fall: it burns monsters only. */
	public static final Block SOUL_FIRE = register("soul_fire", new MutantFireBlock(fire(MapColor.LIGHT_BLUE, 10), true));
	public static final Block FROST_SPIKES = register("frost_spikes", new FrostSpikesBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.PALE_PURPLE).noCollision().nonOpaque().strength(0.5F).luminance(state -> 2).sounds(BlockSoundGroup.GLASS)
			.pistonBehavior(PistonBehavior.DESTROY).dropsNothing()));
	public static final Block BLIGHT = register("blight", new BlightBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.LIME).noCollision().nonOpaque().strength(0.2F).luminance(state -> 6).sounds(BlockSoundGroup.SCULK)
			.pistonBehavior(PistonBehavior.DESTROY).dropsNothing()));

	private HazardBlockTypes() {
	}

	/** Loads this class, registering the blocks, and tells mob pathfinding to keep out of them. */
	public static void register() {
		LandPathNodeTypesRegistry.register(MUTANT_FIRE, PathNodeType.DAMAGE_FIRE, PathNodeType.DANGER_FIRE);
		LandPathNodeTypesRegistry.register(FROST_SPIKES, PathNodeType.DAMAGE_OTHER, PathNodeType.DANGER_OTHER);
		LandPathNodeTypesRegistry.register(BLIGHT, PathNodeType.DAMAGE_OTHER, PathNodeType.DANGER_OTHER);
	}

	/** Vanilla fire's settings, without drops. */
	private static AbstractBlock.Settings fire(MapColor color, int light) {
		return AbstractBlock.Settings.create().mapColor(color).replaceable().noCollision().breakInstantly().luminance(state -> light)
				.sounds(BlockSoundGroup.WOOL).pistonBehavior(PistonBehavior.DESTROY).dropsNothing();
	}

	private static Block register(String name, Block block) {
		return Registry.register(Registries.BLOCK, Evoluta.id(name), block);
	}
}
