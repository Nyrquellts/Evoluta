package com.nyr.evoluta.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nyr.evoluta.client.render.MutantLooks;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Brute's bulk and the Stalker's shade (see {@link MutantLooks}). Both are looks only, so neither is required:
 * if another mod rewrites this method, the mutants lose the effect instead of the game crashing.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", shift = At.Shift.AFTER,
					target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;scale(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V"),
			require = 0)
	private void evoluta$shapeMutant(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, CallbackInfo ci) {
		MutantLooks.shape(entity, matrices);
	}

	@ModifyArg(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"),
			index = 4, require = 0)
	private int evoluta$shadeMutant(int color, @Local(argsOnly = true) LivingEntity entity) {
		return MutantLooks.bodyColor(entity, color);
	}
}
