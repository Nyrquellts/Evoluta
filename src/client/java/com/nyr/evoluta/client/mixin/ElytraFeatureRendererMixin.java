package com.nyr.evoluta.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nyr.evoluta.client.render.ElementalGlintLayers;
import com.nyr.evoluta.client.render.GlintColors;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Elytra use the armor glint pass but bypass ArmorFeatureRenderer entirely. */
@Mixin(ElytraFeatureRenderer.class)
abstract class ElytraFeatureRendererMixin {
	@ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private VertexConsumerProvider evoluta$elytraGlint(VertexConsumerProvider provider, @Local(argsOnly = true) LivingEntity holder) {
		return ElementalGlintLayers.wrap(provider, GlintColors.getGlintColor(holder.getEquippedStack(EquipmentSlot.CHEST), holder));
	}

	@ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
			at = @At("STORE"), ordinal = 0, require = 0)
	private ItemStack evoluta$shimmerElytra(ItemStack stack, @Local(argsOnly = true) LivingEntity holder) {
		return GlintColors.forRender(stack, holder);
	}
}
