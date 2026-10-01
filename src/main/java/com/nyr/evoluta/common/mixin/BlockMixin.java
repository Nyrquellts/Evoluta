package com.nyr.evoluta.common.mixin;

import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.ToolGrafts;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where a mined block's drops are rolled with the tool in hand: a grafted tool changes them there ({@link ToolGrafts}),
 * for any block of any mod. An ungrafted tool costs one component lookup.
 */
@Mixin(Block.class)
abstract class BlockMixin {
	@Inject(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
			at = @At("RETURN"), cancellable = true)
	private static void evoluta$toolGrafts(BlockState state, ServerWorld world, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity,
			ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
		if (!tool.isEmpty() && tool.contains(SoulComponents.GRAFTS)) {
			cir.setReturnValue(ToolGrafts.modifyDrops(state, world, pos, blockEntity, entity, tool, cir.getReturnValue(), world.getRandom()));
		}
	}
}
