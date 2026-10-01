package com.nyr.evoluta.common.combat;

import com.nyr.evoluta.common.mixin.CreeperEntityAccessor;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Mutant creepers are a fight, not a jump scare. A mutant's blast is smaller than a vanilla creeper's (which is 3.0),
 * breaks blocks the same way (mobGriefing decides), and leaves its element's hazard behind for a few seconds
 * ({@link Hazards#blastHazards}). The creeper survives it: it pays part of its health, recoils, puts its fuse out and
 * needs a recharge before it can swell again. A fight is three to five blasts to dodge, with openings in between.
 */
public final class MutantCreepers {
	/** Blast power by tier (Evolved, Elite, Apex); a charged mutant doubles it, as vanilla does. */
	static final float[] POWER = {1.5F, 1.75F, 2.0F};
	/** Share of its max health a blast costs the creeper. */
	static final float[] SELF_DAMAGE = {0.3F, 0.25F, 0.2F};
	/** Ticks before a mutant creeper's fuse can run again. */
	public static final int[] RECHARGE = {100, 80, 60};

	private MutantCreepers() {
	}

	/** Replaces vanilla's explosion for a mutant. False for an ordinary creeper, which explodes and dies as usual. */
	public static boolean blast(ServerWorld world, CreeperEntity creeper) {
		MutationData data = Mutations.get(creeper);
		if (data == null) {
			return false;
		}
		int tier = data.getTier().id() - 1;
		float power = POWER[tier] * (creeper.shouldRenderOverlay() ? 2.0F : 1.0F);
		// the hazard blocks of earlier blasts stand: explosions pass over them (ExplosionMixin)
		world.createExplosion(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), power, World.ExplosionSourceType.MOB);
		Juice.shakeAround(world, creeper.getPos(), Juice.BLAST_RANGE, Juice.BLAST_TRAUMA);
		Hazards.blastHazards(world, creeper, data, power, HazardBlocks.of(world));

		CreeperEntityAccessor fuse = (CreeperEntityAccessor) creeper;
		fuse.evoluta$setCurrentFuseTime(0);
		creeper.getDataTracker().set(CreeperEntityAccessor.evoluta$ignited(), false);
		creeper.setFuseSpeed(-1);
		creeper.setAttached(EvolutaAttachments.CREEPER_LAST_BLAST, world.getTime());
		// thrown back by its own blast
		Vec3d back = creeper.getRotationVec(1.0F).multiply(-0.45);
		creeper.addVelocity(back.x, 0.42, back.z);
		creeper.velocityModified = true;
		creeper.damage(world.getDamageSources().explosion(creeper, creeper), creeper.getMaxHealth() * SELF_DAMAGE[tier]);
		return true;
	}

	/** Called at the start of every creeper tick: a recharging mutant's fuse burns down, whatever lit it. */
	public static void holdFuse(ServerWorld world, CreeperEntity creeper) {
		Long last = creeper.getAttached(EvolutaAttachments.CREEPER_LAST_BLAST);
		if (last == null) {
			return;
		}
		MutationData data = Mutations.get(creeper);
		if (data == null || world.getTime() - last >= RECHARGE[data.getTier().id() - 1]) {
			return;
		}
		creeper.setFuseSpeed(-1);
		if (creeper.isIgnited()) {
			creeper.getDataTracker().set(CreeperEntityAccessor.evoluta$ignited(), false);
		}
	}
}
