package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.mutation.Element;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * The elemental glint textures: each element's two layers ({@link GlintArt}), painted the first time they are drawn
 * and kept until resources reload, the armour pass a little dimmer since armour fills more of the view. At most
 * twenty-four textures (two passes, three elements, normal and Apex, two layers), render-thread only. Vanilla 1.21.1
 * glint is POSITION_TEXTURE: a vertex colour alone would be silently ignored by its shader, hence textures.
 */
public final class GlintTextures {
	private record Key(Identifier source, int color, GlintArt.Layer layer) {
	}

	/** Armour glint strength against the item glint's. */
	static final float ARMOR_STRENGTH = 0.75F;

	private static final Map<Key, Boolean> READY = new HashMap<>();

	private GlintTextures() {
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Evoluta.id("elemental_glint");
			}

			@Override
			public void reload(ResourceManager manager) {
				var textures = MinecraftClient.getInstance().getTextureManager();
				READY.forEach((key, ready) -> {
					if (ready) {
						textures.destroyTexture(id(key.source(), key.color(), key.layer()));
					}
				});
				READY.clear();
			}
		});
	}

	static Identifier id(Identifier source, int color, GlintArt.Layer layer) {
		return Evoluta.id("glint/" + Integer.toHexString(color) + "/" + layer.name().toLowerCase(java.util.Locale.ROOT) + "/" + source.getPath());
	}

	static boolean ready(Identifier source, int color, GlintArt.Layer layer) {
		return READY.computeIfAbsent(new Key(source, color, layer), GlintTextures::load);
	}

	private static boolean load(Key key) {
		Element element = GlintColors.element(key.color());
		if (element == null) {
			return false;
		}
		var client = MinecraftClient.getInstance();
		int size = GlintArt.SIZE;
		try {
			int[] argb = GlintArt.paint(element, key.layer(), GlintColors.isApex(key.color()), size, element.ordinal() * 7919L + key.layer().ordinal());
			if (argb == null) {
				return false;
			}
			float strength = key.source().equals(ItemRenderer.ENTITY_ENCHANTMENT_GLINT) ? ARMOR_STRENGTH : 1.0F;
			NativeImage painted = new NativeImage(size, size, false);
			try {
				for (int y = 0; y < size; y++) {
					for (int x = 0; x < size; x++) {
						painted.setColor(x, y, abgr(argb[y * size + x], strength));
					}
				}
				client.getTextureManager().registerTexture(id(key.source(), key.color(), key.layer()), new NativeImageBackedTexture(painted));
			} catch (RuntimeException failure) {
				painted.close();
				throw failure;
			}
			return true;
		} catch (RuntimeException failure) {
			Evoluta.LOGGER.warn("Using vanilla glint for {}: {}", key.source(), failure.toString());
			return false; // Cache a failed paint until reload instead of retrying/logging every frame.
		}
	}

	/** ARGB scaled by {@code strength}, as NativeImage's ABGR. */
	private static int abgr(int argb, float strength) {
		int red = Math.round((argb >> 16 & 0xFF) * strength);
		int green = Math.round((argb >> 8 & 0xFF) * strength);
		int blue = Math.round((argb & 0xFF) * strength);
		return 0xFF000000 | blue << 16 | green << 8 | red;
	}
}
