package com.nyr.evoluta.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nyr.evoluta.client.render.ElementalGlintLayers;
import com.nyr.evoluta.client.render.GlintColors;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Holder-aware items, plus the direct baked-model path used by inventory/world rendering. */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
	@ModifyVariable(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack evoluta$shimmerHeld(ItemStack stack, @Local(argsOnly = true) @Nullable LivingEntity holder) {
		return GlintColors.forRender(stack, holder);
	}

	@ModifyVariable(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private VertexConsumerProvider evoluta$heldGlint(VertexConsumerProvider provider,
			@Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) @Nullable LivingEntity holder) {
		return ElementalGlintLayers.wrap(provider, GlintColors.getGlintColor(stack, holder));
	}

	@ModifyVariable(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack evoluta$shimmerItem(ItemStack stack) {
		return GlintColors.forRender(stack, null);
	}

	@ModifyVariable(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private VertexConsumerProvider evoluta$itemGlint(VertexConsumerProvider provider, @Local(argsOnly = true) ItemStack stack) {
		return ElementalGlintLayers.wrap(provider, GlintColors.getGlintColor(stack, null));
	}
}
