package com.nyr.evoluta.common.mixin;

import com.nyr.evoluta.common.combat.HazardBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pistons treat a creeper blast's hazard blocks ({@link HazardBlocks}) like obsidian: a push that meets one fails,
 * and slime that touches one leaves it behind. Otherwise a piston could carry magma, slime or packed ice off for good.
 */
@Mixin(PistonBlock.class)
abstract class PistonBlockMixin {
	@Inject(method = "isMovable", at = @At("HEAD"), cancellable = true)
	private static void evoluta$pinHazards(BlockState state, World world, BlockPos pos, Direction direction, boolean canBreak, Direction pistonDir,
			CallbackInfoReturnable<Boolean> cir) {
		if (world instanceof ServerWorld server && HazardBlocks.holds(server, pos)) {
			cir.setReturnValue(false);
		}
	}
}
