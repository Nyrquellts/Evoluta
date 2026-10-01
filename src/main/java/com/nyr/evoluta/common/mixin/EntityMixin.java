package com.nyr.evoluta.common.mixin;

import com.nyr.evoluta.common.tactics.Tactics;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Stalkers step without footstep sounds and without the step vibrations sculk listens for. */
@Mixin(Entity.class)
abstract class EntityMixin {
	@ModifyVariable(method = "stepOnBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private boolean evoluta$muteStalkerSteps(boolean playSound) {
		return playSound && !Tactics.movesSilently((Entity) (Object) this);
	}

	@ModifyVariable(method = "stepOnBlock", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private boolean evoluta$hideStalkerStepVibrations(boolean emitEvent) {
		return emitEvent && !Tactics.movesSilently((Entity) (Object) this);
	}
}
