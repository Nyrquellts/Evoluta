package com.nyr.evoluta.client.mixin;

import com.nyr.evoluta.client.render.ElementalGlintLayers;
import java.util.SequencedMap;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.BufferAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BufferBuilderStorage.class)
abstract class BufferBuilderStorageMixin {
	@ModifyArg(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/VertexConsumerProvider;immediate(Ljava/util/SequencedMap;Lnet/minecraft/client/util/BufferAllocator;)Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;"),
			index = 0, require = 0)
	private SequencedMap<RenderLayer, BufferAllocator> evoluta$glintBuffers(SequencedMap<RenderLayer, BufferAllocator> buffers) {
		return ElementalGlintLayers.addBuffers(buffers);
	}
}
