package com.nyr.evoluta.common.tag;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.mutation.Archetype;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/**
 * Which mobs mutate is decided by datapack tags, never by code: add a modded mob to {@code #evoluta:can_mutate} and it
 * mutates like a vanilla one.
 */
public final class EvolutaTags {
	/** Mobs that may mutate when they spawn. */
	public static final TagKey<EntityType<?>> CAN_MUTATE = of("can_mutate");
	/** Mobs that never mutate, even when also in {@link #CAN_MUTATE}: bosses by default, {@code #c:bosses} included. */
	public static final TagKey<EntityType<?>> BLACKLIST = of("blacklist");
	/** Mobs that may roll the Brute archetype. */
	public static final TagKey<EntityType<?>> BRUTES = of("archetype/brute");
	/** Mobs that may roll the Stalker archetype. */
	public static final TagKey<EntityType<?>> STALKERS = of("archetype/stalker");
	/** Items that point at the nearest champion while held in either hand. */
	public static final TagKey<Item> LOCATORS = TagKey.of(RegistryKeys.ITEM, Evoluta.id("champion_locators"));

	private EvolutaTags() {
	}

	public static boolean canMutate(EntityType<?> type) {
		return type.isIn(CAN_MUTATE) && !type.isIn(BLACKLIST);
	}

	public static boolean canBe(EntityType<?> type, Archetype archetype) {
		return switch (archetype) {
			case NONE -> true;
			case BRUTE -> type.isIn(BRUTES);
			case STALKER -> type.isIn(STALKERS);
			case ALPHA -> false;
		};
	}

	private static TagKey<EntityType<?>> of(String path) {
		return TagKey.of(RegistryKeys.ENTITY_TYPE, Evoluta.id(path));
	}
}
