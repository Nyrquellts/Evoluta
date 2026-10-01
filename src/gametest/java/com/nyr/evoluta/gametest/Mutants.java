package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/** Small builders shared by the game tests. */
final class Mutants {
	private Mutants() {
	}

	/**
	 * A mob spawned at {@code (x, 2, z)} in the test area and made into the given mutant, AI switched off. Note that
	 * {@code TestContext#spawnMob} also strips the mob's goals; use {@code spawnEntity} for a mob that must act.
	 */
	static <T extends MobEntity> T still(TestContext context, EntityType<T> type, int x, int z, Tier tier, Element element, Archetype archetype) {
		T mob = context.spawnMob(type, x, 2, z);
		mob.setAiDisabled(true);
		Mutations.apply(mob, MutationData.of(tier, element, archetype), EvolutaConfig.get());
		return mob;
	}

	/** A survival player that is not in the world: enough to receive hits, not to be seen by world queries. */
	static PlayerEntity target(TestContext context, int x, int z, float yaw) {
		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		player.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(x, 2, z)), yaw, 0.0F);
		player.setHeadYaw(yaw);
		return player;
	}

	/** Removes every mutant loaded in {@code world}, for batches that need a world without leftovers. */
	static void discardAll(ServerWorld world) {
		List<MobEntity> mutants = new ArrayList<>();
		for (Entity entity : world.iterateEntities()) {
			if (entity instanceof MobEntity mob && Mutations.get(mob) != null) {
				mutants.add(mob);
			}
		}
		mutants.forEach(MobEntity::discard);
	}

	/** Stone at y = 1 under the whole 8x8 test area, so mobs standing at y = 2 do not fall. */
	static void floor(TestContext context) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				context.setBlockState(x, 1, z, Blocks.STONE);
			}
		}
	}

	/** Raises a held shield as if it had been held up for {@code ticks} ticks (blocking starts after 5). */
	static void holdUp(LivingEntity entity, int ticks) {
		try {
			Field left = LivingEntity.class.getDeclaredField("itemUseTimeLeft");
			left.setAccessible(true);
			left.setInt(entity, entity.getActiveItem().getMaxUseTime(entity) - ticks);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("dev runtime without Yarn names?", e);
		}
	}
}
