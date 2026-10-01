package com.nyr.evoluta.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.nyr.evoluta.common.champion.ChampionRewards;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.soul.GraftCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Shadow
	@Nullable
	protected PlayerEntity attackingPlayer;

	/** Toxic mutants cannot be poisoned, so their own trail and death cloud never hurt them. */
	@Inject(method = "canHaveStatusEffect", at = @At("HEAD"), cancellable = true)
	private void evoluta$toxicIgnoresPoison(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if (effect.equals(StatusEffects.POISON)) {
			MutationData data = Mutations.get((LivingEntity) (Object) this);
			if (data != null && data.getElement() == Element.TOXIC) {
				cir.setReturnValue(false);
			}
		}
	}

	/** A mutant's tier loot table rolls right after its own, in the same context. */
	@Inject(method = "dropLoot", at = @At("TAIL"))
	private void evoluta$dropMutantLoot(DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
		ChampionRewards.dropLoot((LivingEntity) (Object) this, damageSource, causedByPlayer ? this.attackingPlayer : null);
	}

	/** Mutants are worth more experience, whenever vanilla drops any. */
	@ModifyReturnValue(method = "getXpToDrop(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
	private int evoluta$addMutantExperience(int experience) {
		return experience + ChampionRewards.bonusExperience((LivingEntity) (Object) this);
	}

	/** Grafts join a weapon hit before armour and protection see it; armour grafts ward off part of it. */
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float evoluta$grafts(float amount, @Local(argsOnly = true) DamageSource source) {
		return GraftCombat.modifyDamage((LivingEntity) (Object) this, source, amount);
	}

	/** Bogged and Toxic armour grafts shorten poison before it takes hold. */
	@ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
			at = @At("HEAD"), argsOnly = true)
	private StatusEffectInstance evoluta$wardPoison(StatusEffectInstance effect) {
		return GraftCombat.wardEffect((LivingEntity) (Object) this, effect);
	}
}
