package com.nyr.evoluta.common.mixin;

import com.nyr.evoluta.common.combat.HazardBlocks;
import java.util.List;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every explosion passes over a creeper blast's hazard blocks ({@link HazardBlocks}), TNT and other mods' blasts
 * included: they break nothing and drop nothing there. Trimmed as soon as the blocks are collected, so the list the
 * server then breaks and sends to clients is the same one.
 */
@Mixin(Explosion.class)
abstract class ExplosionMixin {
	@Shadow
	@Final
	private World world;

	@Shadow
	public abstract List<BlockPos> getAffectedBlocks();

	@Inject(method = "collectBlocksAndDamageEntities", at = @At("RETURN"))
	private void evoluta$spareHazards(CallbackInfo ci) {
		if (this.world instanceof ServerWorld server) {
			HazardBlocks.spare(server, this.getAffectedBlocks());
		}
	}
}
