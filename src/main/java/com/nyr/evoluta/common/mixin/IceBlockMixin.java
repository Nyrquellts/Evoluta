package com.nyr.evoluta.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.nyr.evoluta.common.soul.ToolGrafts;
import net.minecraft.block.IceBlock;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ice taken whole by an Ice Cutter graft leaves no water behind, as with Silk Touch ({@link ToolGrafts#cutsIce}). */
@Mixin(IceBlock.class)
abstract class IceBlockMixin {
	@ModifyExpressionValue(method = "afterBreak", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/enchantment/EnchantmentHelper;hasAnyEnchantmentsIn(Lnet/minecraft/item/ItemStack;Lnet/minecraft/registry/tag/TagKey;)Z"))
	private boolean evoluta$iceCutter(boolean keepsIce, @Local(argsOnly = true) ItemStack tool) {
		return keepsIce || ToolGrafts.cutsIce(tool);
	}
}
