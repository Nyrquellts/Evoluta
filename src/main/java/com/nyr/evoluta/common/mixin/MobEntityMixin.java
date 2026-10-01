package com.nyr.evoluta.common.mixin;

import com.nyr.evoluta.common.mutation.SpawnMutations;
import com.nyr.evoluta.common.tactics.Ambush;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
abstract class MobEntityMixin {
	@Inject(method = "initialize", at = @At("RETURN"))
	private void evoluta$mutateOnSpawn(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
		SpawnMutations.onInitialize((MobEntity) (Object) this, world.toServerWorld(), difficulty, spawnReason);
	}

	/** An Apex lying in wait takes no target but the player who wakes it (Ambush#wake lifts the wait first). */
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void evoluta$holdAmbush(@Nullable LivingEntity target, CallbackInfo ci) {
		if (target != null && Ambush.isWaiting((MobEntity) (Object) this)) {
			ci.cancel();
		}
	}

	/** ...and makes no sound while it waits. */
	@Inject(method = "playAmbientSound", at = @At("HEAD"), cancellable = true)
	private void evoluta$silentAmbush(CallbackInfo ci) {
		if (Ambush.isWaiting((MobEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
