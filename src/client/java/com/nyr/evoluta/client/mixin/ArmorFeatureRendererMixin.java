package com.nyr.evoluta.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nyr.evoluta.client.render.ElementalGlintLayers;
import com.nyr.evoluta.client.render.GlintColors;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Wraps only this slot's provider; armor material colors and trims pass through unchanged. */
@Mixin(ArmorFeatureRenderer.class)
abstract class ArmorFeatureRendererMixin {
	@ModifyVariable(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private VertexConsumerProvider evoluta$armorGlint(VertexConsumerProvider provider,
			@Local(argsOnly = true) LivingEntity holder, @Local(argsOnly = true) EquipmentSlot slot) {
		return ElementalGlintLayers.wrap(provider, GlintColors.getGlintColor(holder.getEquippedStack(slot), holder));
	}

	@ModifyVariable(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;)V",
			at = @At("STORE"), ordinal = 0, require = 0)
	private ItemStack evoluta$shimmerArmor(ItemStack stack, @Local(argsOnly = true) LivingEntity holder) {
		return GlintColors.forRender(stack, holder);
	}
}
