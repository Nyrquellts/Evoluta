package com.nyr.evoluta.common.champion;

import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.network.TargetPosPayload;
import com.nyr.evoluta.common.tag.EvolutaTags;
import com.nyr.evoluta.common.world.WorldState;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Tells players holding a champion locator (any item in {@code #evoluta:champion_locators}) where the nearest living
 * champion is. Each player is served every {@link #INTERVAL} ticks, staggered by entity id, and the lookup is the
 * chunk index's bounded ring search: no entity scan, whatever the world holds.
 */
public final class LocatorSync {
	/** 1.5 seconds; the client animates the needle between updates. */
	public static final int INTERVAL = 30;

	private LocatorSync() {
	}

	public static void tick(ServerWorld world, long worldTime, @Nullable WorldState state) {
		List<ServerPlayerEntity> players = world.getPlayers();
		for (int i = 0; i < players.size(); i++) {
			ServerPlayerEntity player = players.get(i);
			if ((worldTime + player.getId()) % INTERVAL != 0 || player.isSpectator() || !holdsLocator(player)) {
				continue;
			}
			if (ServerPlayNetworking.canSend(player, TargetPosPayload.ID)) {
				ServerPlayNetworking.send(player, payload(nearest(state, player)));
			}
		}
	}

	public static boolean holdsLocator(PlayerEntity player) {
		return player.getMainHandStack().isIn(EvolutaTags.LOCATORS) || player.getOffHandStack().isIn(EvolutaTags.LOCATORS);
	}

	/** Where the nearest living champion within the configured range stands, if any. */
	public static Optional<BlockPos> target(@Nullable WorldState state, PlayerEntity player) {
		MobEntity champion = nearest(state, player);
		return champion == null ? Optional.empty() : Optional.of(champion.getBlockPos());
	}

	/** The nearest living champion within the configured range, or null. */
	@Nullable
	public static MobEntity nearest(@Nullable WorldState state, PlayerEntity player) {
		if (state == null) {
			return null;
		}
		return state.champions.nearest(player.getX(), player.getZ(), EvolutaConfig.get().locatorRange(),
				Entity::getX, Entity::getZ, LivingEntity::isAlive);
	}

	/** What the holder's locator is told: the champion's position and element, or nothing. */
	public static TargetPosPayload payload(@Nullable MobEntity champion) {
		if (champion == null) {
			return TargetPosPayload.NOTHING;
		}
		MutationData data = Mutations.get(champion);
		return new TargetPosPayload(Optional.of(champion.getBlockPos()), data == null ? Element.NONE : data.getElement());
	}
}
