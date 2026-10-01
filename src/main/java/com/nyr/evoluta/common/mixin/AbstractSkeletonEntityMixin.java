package com.nyr.evoluta.common.mixin;

import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skeletons pick up their bow after MobEntity#initialize has run, so a Brute's dagger has to be handed over here. */
@Mixin(AbstractSkeletonEntity.class)
abstract class AbstractSkeletonEntityMixin {
	@Inject(method = "initialize", at = @At("RETURN"))
	private void evoluta$equipLoadout(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
		AbstractSkeletonEntity skeleton = (AbstractSkeletonEntity) (Object) this;
		MutationData data = Mutations.get(skeleton);
		if (data != null) {
			Mutations.equipLoadout(skeleton, data);
		}
	}
}
