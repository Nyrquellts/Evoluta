package com.nyr.evoluta.common.mixin;

import com.nyr.evoluta.common.combat.MutantCreepers;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mutant creeper's blast is its own ({@link MutantCreepers}): smaller, survived, and followed by a recharge. */
@Mixin(CreeperEntity.class)
abstract class CreeperEntityMixin {
	@Inject(method = "explode", at = @At("HEAD"), cancellable = true)
	private void evoluta$mutantBlast(CallbackInfo ci) {
		CreeperEntity creeper = (CreeperEntity) (Object) this;
		if (creeper.getWorld() instanceof ServerWorld world && MutantCreepers.blast(world, creeper)) {
			ci.cancel();
		}
	}

	/** Runs before vanilla moves the fuse this tick, so a recharging mutant's fuse only ever burns down. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void evoluta$recharge(CallbackInfo ci) {
		CreeperEntity creeper = (CreeperEntity) (Object) this;
		if (creeper.getWorld() instanceof ServerWorld world) {
			MutantCreepers.holdFuse(world, creeper);
		}
	}
}
