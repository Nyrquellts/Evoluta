package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.skin.SkinPainter;
import java.util.Map;
import net.minecraft.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Where each vanilla mutant's soul sits in its texture (64 pixels wide; a larger pack scales it): the front of the
 * chest for zombies, skeletons, their variants and creepers, the top of the abdomen for spiders, read off the 1.21.1
 * models' UV layouts. A mob not listed, modded ones included, gets its element's skin without a core.
 */
final class SoulCores {
	/** Body box at UV (16, 16), 8x12x4: its front face. */
	private static final SkinPainter.Core CHEST = new SkinPainter.Core(20, 20, 8, 12);
	/** The zombie villager's body box at UV (16, 20), 8x12x6. */
	private static final SkinPainter.Core VILLAGER_CHEST = new SkinPainter.Core(22, 26, 8, 12);
	/** The spider's abdomen at UV (0, 12), 10x8x12: its top face. */
	private static final SkinPainter.Core SPIDER_BACK = new SkinPainter.Core(12, 12, 10, 12);

	private static final Map<EntityType<?>, SkinPainter.Core> BY_TYPE = Map.ofEntries(
			Map.entry(EntityType.ZOMBIE, CHEST),
			Map.entry(EntityType.HUSK, CHEST),
			Map.entry(EntityType.DROWNED, CHEST),
			Map.entry(EntityType.SKELETON, CHEST),
			Map.entry(EntityType.STRAY, CHEST),
			Map.entry(EntityType.BOGGED, CHEST),
			Map.entry(EntityType.WITHER_SKELETON, CHEST),
			Map.entry(EntityType.CREEPER, CHEST),
			Map.entry(EntityType.ZOMBIE_VILLAGER, VILLAGER_CHEST),
			Map.entry(EntityType.SPIDER, SPIDER_BACK),
			Map.entry(EntityType.CAVE_SPIDER, SPIDER_BACK)
	);

	private SoulCores() {
	}

	@Nullable
	static SkinPainter.Core forType(EntityType<?> type) {
		return BY_TYPE.get(type);
	}
}
