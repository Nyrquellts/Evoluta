package com.nyr.evoluta.common.mixin;

import com.nyr.evoluta.common.soul.GraftCombat;
import com.nyr.evoluta.common.soul.ToolGrafts;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Remembers how charged a swing is and whom it was aimed at, for as long as the swing lands: vanilla resets the
 * cooldown before the target takes damage, and grafts count full swings on the aimed-at target only. Also lets a husk
 * graft's Sandsifter speed up digging, on both sides, so the client's break progress matches the server's.
 */
@Mixin(PlayerEntity.class)
abstract class PlayerEntityMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void evoluta$beginSwing(Entity target, CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (!player.getWorld().isClient) {
			GraftCombat.beginSwing(player, target);
		}
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void evoluta$endSwing(Entity target, CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (!player.getWorld().isClient) {
			GraftCombat.endSwing(player);
		}
	}

	@Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
	private void evoluta$sandsifter(BlockState state, CallbackInfoReturnable<Float> cir) {
		float speed = cir.getReturnValueF();
		float sifted = ToolGrafts.breakSpeed((PlayerEntity) (Object) this, state, speed);
		if (sifted != speed) {
			cir.setReturnValue(sifted);
		}
	}
}
