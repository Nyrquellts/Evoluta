package com.nyr.evoluta.common.champion;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * What a mutant gives back. Loot is data: each tier rolls the loot table {@code evoluta:mutant/<tier>} on top of the
 * mob's own, in the same context vanilla uses, so {@code killed_by_player} and Looting behave as usual and a
 * datapack can replace the tables. Both hooks sit inside vanilla's own drop code, so {@code doMobLoot} and the
 * player-kill window apply unchanged.
 */
public final class ChampionRewards {
	private ChampionRewards() {
	}

	public static RegistryKey<LootTable> lootTable(Tier tier) {
		return RegistryKey.of(RegistryKeys.LOOT_TABLE, Evoluta.id("mutant/" + tier.key()));
	}

	/**
	 * Called at the end of {@code LivingEntity#dropLoot}.
	 *
	 * @param killer the player credited with the kill (vanilla's attacking player), or null
	 */
	public static void dropLoot(LivingEntity entity, DamageSource source, @Nullable PlayerEntity killer) {
		MutationData data = Mutations.get(entity);
		if (data == null || !(entity.getWorld() instanceof ServerWorld world)) {
			return;
		}
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(lootTable(data.getTier()));
		LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.THIS_ENTITY, entity)
				.add(LootContextParameters.ORIGIN, entity.getPos())
				.add(LootContextParameters.DAMAGE_SOURCE, source)
				.addOptional(LootContextParameters.ATTACKING_ENTITY, source.getAttacker())
				.addOptional(LootContextParameters.DIRECT_ATTACKING_ENTITY, source.getSource());
		if (killer != null) {
			builder = builder.add(LootContextParameters.LAST_DAMAGE_PLAYER, killer).luck(killer.getLuck());
		}
		// seed 0 (every mob unless its NBT says otherwise) means the world's random, as for the mob's own loot
		table.generateLoot(builder.build(LootContextTypes.ENTITY), entity.getLootTableSeed(), entity::dropStack);
	}

	/** Experience added to what vanilla drops for a mutant, by tier. Only asked when vanilla drops experience at all. */
	public static int bonusExperience(LivingEntity entity) {
		MutationData data = Mutations.get(entity);
		return data == null ? 0 : (int) Math.round(EvolutaConfig.get().xpBonus().get(data.getTier()));
	}
}
