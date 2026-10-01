package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.combat.BlightBlock;
import com.nyr.evoluta.common.combat.HazardBlockTypes;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.render.RenderLayer;

/** How the hazard blocks draw: both fires cut out like vanilla fire, and blight is sculk tinted poison green by tier. */
public final class HazardBlockLooks {
	/** Blight's tint over sculk, by tier: brighter and sicker as the mutant gets stronger. */
	static final int[] BLIGHT_TINT = {0x78D038, 0x9CFF45, 0xC8FF6E};

	private HazardBlockLooks() {
	}

	public static void register() {
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), HazardBlockTypes.MUTANT_FIRE, HazardBlockTypes.SOUL_FIRE);
		ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> BLIGHT_TINT[state.get(BlightBlock.TIER) - 1], HazardBlockTypes.BLIGHT);
	}
}
