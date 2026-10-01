package com.nyr.evoluta.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nyr.evoluta.common.mutation.Element;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Exact 1.21.1 glint render states with element-specific textures and motion, in stable, separately batched layers.
 * Each element's glint is two layers drawn together, a sheen and the detail over it ({@link GlintArt}), each moving its
 * own way ({@link GlintMotion}): where vanilla asks for its glint buffer, it gets both at once.
 */
public final class ElementalGlintLayers extends RenderLayer {
	private enum Pass {
		ARMOR, ITEM, TRANSLUCENT, ENTITY, DIRECT_ENTITY;

		Identifier texture() {
			return this == ITEM || this == TRANSLUCENT ? ItemRenderer.ITEM_ENCHANTMENT_GLINT : ItemRenderer.ENTITY_ENCHANTMENT_GLINT;
		}

		/** Armour and entity models: UVs span their own texture rather than a sliver of the item atlas. */
		boolean modelUvs() {
			return this != ITEM && this != TRANSLUCENT;
		}
	}

	private record Kind(Pass pass, int color, GlintArt.Layer layer) {
	}

	private static final int[] COLORS = {GlintColors.PERMAFROST_GLINT, GlintColors.TOXIC_GLINT, GlintColors.IGNITED_GLINT,
			GlintColors.PERMAFROST_APEX_GLINT, GlintColors.TOXIC_APEX_GLINT, GlintColors.IGNITED_APEX_GLINT};
	private static final Map<Pass, Map<Kind, RenderLayer>> LAYERS = new EnumMap<>(Pass.class);
	private static final Map<String, Texturing> MOTIONS = new HashMap<>();
	private static boolean fixedBuffersInstalled;

	private ElementalGlintLayers() {
		super("evoluta_glint_factory", VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS, 1536,
				false, false, () -> {}, () -> {});
	}

	/**
	 * Glint and base consumers are used together. A fallback buffer would flush the glint builder as soon as the base
	 * is requested, so each of the sixty glint layers (five passes, six colours, two layers) must have a fixed buffer,
	 * like vanilla. Keep the same order relative to vanilla's other passes; layer identities survive resource reloads.
	 */
	public static SequencedMap<RenderLayer, BufferAllocator> addBuffers(SequencedMap<RenderLayer, BufferAllocator> original) {
		SequencedMap<RenderLayer, BufferAllocator> result = new LinkedHashMap<>();
		original.forEach((layer, allocator) -> {
			result.put(layer, allocator);
			Pass pass = pass(layer);
			if (pass != null) {
				for (int color : COLORS) {
					for (GlintArt.Layer part : GlintArt.Layer.values()) {
						RenderLayer colored = layer(new Kind(pass, color, part));
						result.put(colored, new BufferAllocator(colored.getExpectedBufferSize()));
					}
				}
			}
		});
		fixedBuffersInstalled = true;
		return result;
	}

	/** Scoped to this render call, with no mutable global current-holder/current-color state. */
	public static VertexConsumerProvider wrap(VertexConsumerProvider provider, int color) {
		if (!GlintColors.isElemental(color)) {
			return provider;
		}
		if (provider instanceof ColoredProvider colored) {
			return colored.color == color ? provider : new ColoredProvider(colored.delegate, color);
		}
		if (!fixedBuffersInstalled) {
			return provider;
		}
		var storage = MinecraftClient.getInstance().getBufferBuilders();
		// Unknown third-party providers may not have fixed buffers; preserve vanilla rather than invalidate them.
		if (provider != storage.getEntityVertexConsumers() && provider != storage.getOutlineVertexConsumers()) {
			return provider;
		}
		return new ColoredProvider(provider, color);
	}

	private record ColoredProvider(VertexConsumerProvider delegate, int color) implements VertexConsumerProvider {
		@Override
		public VertexConsumer getBuffer(RenderLayer original) {
			Pass pass = pass(original);
			if (pass == null || !GlintTextures.ready(pass.texture(), this.color, GlintArt.Layer.SHEEN)
					|| !GlintTextures.ready(pass.texture(), this.color, GlintArt.Layer.DETAIL)) {
				return this.delegate.getBuffer(original);
			}
			// both layers take every glint vertex: the sheen, and the detail sliding over it
			return VertexConsumers.union(this.delegate.getBuffer(layer(new Kind(pass, this.color, GlintArt.Layer.SHEEN))),
					this.delegate.getBuffer(layer(new Kind(pass, this.color, GlintArt.Layer.DETAIL))));
		}
	}

	@Nullable
	private static Pass pass(RenderLayer layer) {
		if (layer == getArmorEntityGlint()) return Pass.ARMOR;
		if (layer == getGlint()) return Pass.ITEM;
		if (layer == getGlintTranslucent()) return Pass.TRANSLUCENT;
		if (layer == getEntityGlint()) return Pass.ENTITY;
		if (layer == getDirectEntityGlint()) return Pass.DIRECT_ENTITY;
		return null;
	}

	/**
	 * The layer's own motion ({@link GlintMotion}) in place of vanilla's texturing. Vanilla's glint clock is
	 * milliseconds x Glint Speed x 8; ours is seconds x Glint Speed x 2, so the default speed (0.5) runs in real time.
	 */
	private static Texturing motion(@Nullable Element element, GlintArt.Layer layer, boolean modelUvs) {
		if (element == null) {
			return modelUvs ? ENTITY_GLINT_TEXTURING : GLINT_TEXTURING;
		}
		String name = element.key() + "_" + layer.name().toLowerCase(Locale.ROOT) + (modelUvs ? "_model" : "_item");
		return MOTIONS.computeIfAbsent(name, ignored -> new Texturing("evoluta_glint_motion_" + name, () -> {
			double speed = MinecraftClient.getInstance().options.getGlintSpeed().getValue();
			RenderSystem.setTextureMatrix(GlintMotion.matrix(element, layer, modelUvs, Util.getMeasuringTimeMs() / 1000.0 * speed * 2.0));
		}, RenderSystem::resetTextureMatrix));
	}

	private static RenderLayer layer(Kind kind) {
		return LAYERS.computeIfAbsent(kind.pass(), ignored -> new LinkedHashMap<>()).computeIfAbsent(kind, ignored -> {
			Pass pass = kind.pass();
			Element element = GlintColors.element(kind.color());
			Texturing texturing = motion(element, kind.layer(), pass.modelUvs());
			var state = MultiPhaseParameters.builder()
					.texture(new Texture(GlintTextures.id(pass.texture(), kind.color(), kind.layer()), true, false))
					.writeMaskState(COLOR_MASK)
					.cull(DISABLE_CULLING)
					.depthTest(EQUAL_DEPTH_TEST)
					.transparency(GLINT_TRANSPARENCY)
					.texturing(texturing);
			switch (pass) {
				case ARMOR -> state.program(ARMOR_ENTITY_GLINT_PROGRAM).layering(VIEW_OFFSET_Z_LAYERING);
				case ITEM -> state.program(GLINT_PROGRAM);
				case TRANSLUCENT -> state.program(TRANSLUCENT_GLINT_PROGRAM).target(ITEM_ENTITY_TARGET);
				case ENTITY -> state.program(ENTITY_GLINT_PROGRAM).target(ITEM_ENTITY_TARGET);
				case DIRECT_ENTITY -> state.program(DIRECT_ENTITY_GLINT_PROGRAM);
			}
			return of("evoluta_glint_" + pass.name().toLowerCase(Locale.ROOT) + "_" + Integer.toHexString(kind.color()) + "_"
					+ kind.layer().name().toLowerCase(Locale.ROOT), VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS, 1536,
					state.build(false));
		});
	}
}
