package com.nyr.evoluta.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GlintRenderHooksTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	@Test
	void allNewMixinsTransformTheirActualMinecraft1211Targets() throws Exception {
        assertTrue(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("evoluta"), "Evoluta must be discovered by the test launcher");
		assertHooks("net.minecraft.client.render.item.ItemRenderer",
				"evoluta$shimmerHeld", "evoluta$heldGlint", "evoluta$shimmerItem", "evoluta$itemGlint");
		assertHooks("net.minecraft.client.render.entity.feature.ArmorFeatureRenderer", "evoluta$armorGlint", "evoluta$shimmerArmor");
		assertHooks("net.minecraft.client.render.entity.feature.ElytraFeatureRenderer", "evoluta$elytraGlint", "evoluta$shimmerElytra");
		assertHooks("net.minecraft.client.render.BufferBuilderStorage", "evoluta$glintBuffers");
	}

	private static void assertHooks(String target, String... hooks) throws Exception {
		Class<?> transformed = Class.forName(target, false, GlintRenderHooksTest.class.getClassLoader());
		List<String> methods = Arrays.stream(transformed.getDeclaredMethods()).map(method -> method.getName()).toList();
		for (String hook : hooks) {
			assertTrue(methods.stream().anyMatch(name -> name.contains(hook)), target + " did not receive " + hook);
		}
	}

	@Test
	void eachElementAndPassHasAStableFixedBufferAndKeepsThePairedConsumerAlive() {
		SequencedMap<RenderLayer, BufferAllocator> original = new LinkedHashMap<>();
		for (RenderLayer layer : List.of(RenderLayer.getArmorEntityGlint(), RenderLayer.getGlint(),
				RenderLayer.getGlintTranslucent(), RenderLayer.getEntityGlint(), RenderLayer.getDirectEntityGlint())) {
			original.put(layer, new BufferAllocator(layer.getExpectedBufferSize()));
		}
		RenderLayer surface = RenderLayer.getEntitySolid(Identifier.ofVanilla("textures/entity/zombie/zombie.png"));
		original.put(surface, new BufferAllocator(1536));
		SequencedMap<RenderLayer, BufferAllocator> fixed = ElementalGlintLayers.addBuffers(original);
		SequencedMap<RenderLayer, BufferAllocator> again = ElementalGlintLayers.addBuffers(original);
		try (BufferAllocator fallback = new BufferAllocator(1536)) {
			assertEquals(6, original.size(), "do not mutate the caller's map");
			assertEquals(66, fixed.size(), "five vanilla passes x six glint colours (three elements, plain and Apex) x two layers (sheen and detail), plus originals");
			assertEquals(List.copyOf(fixed.keySet()), List.copyOf(again.keySet()), "layer identity must survive texture reloads");
			original.forEach((layer, buffer) -> assertSame(buffer, fixed.get(layer)));
			VertexConsumerProvider.Immediate provider = VertexConsumerProvider.immediate(fixed, fallback);
			for (RenderLayer layer : fixed.keySet()) {
				if (original.containsKey(layer)) continue;
				VertexConsumer glint = provider.getBuffer(layer);
				provider.getBuffer(surface);
				// If glint had used the shared fallback allocator, requesting surface would have ended it.
				assertDoesNotThrow(() -> {
					for (int vertex = 0; vertex < 4; vertex++) {
						glint.vertex(vertex & 1, vertex >> 1, 0).texture(vertex & 1, vertex >> 1);
					}
				});
				assertEquals(RenderLayer.getGlint().getVertexFormat(), layer.getVertexFormat());
			}
		} finally {
			// No draw call/GL context: this test exercises buffering and transformed classes without a game window.
			fixed.values().forEach(BufferAllocator::close);
			again.forEach((layer, buffer) -> {
				if (!original.containsKey(layer)) buffer.close();
			});
		}
	}
}
