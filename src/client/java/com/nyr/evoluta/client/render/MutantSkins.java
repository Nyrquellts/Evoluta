package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.skin.SkinPainter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The element skins drawn over mutants: {@link SkinPainter} run on the mob's own texture the first time a mutant of
 * that look is drawn, uploaded as two small textures and kept until resources reload. Reading the texture from the
 * loaded resources means a resource pack's zombie gets frozen too, and a modded mob added to
 * {@code #evoluta:can_mutate} gets its skin with no art made for it. Render thread only.
 */
public final class MutantSkins {
	record Skin(Identifier crust, Identifier glow) {
	}

	private record Key(Identifier texture, Element element, Tier tier, @Nullable SkinPainter.Core core) {
	}

	/** Enough for every vanilla mob in every look many times over; a mod handing out endless textures stops here. */
	private static final int MAX_SKINS = 512;
	private static final Map<Key, Optional<Skin>> SKINS = new HashMap<>();

	private MutantSkins() {
	}

	/** Clears the skins whenever resources reload, so they are painted again from whatever textures are now loaded. */
	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Evoluta.id("mutant_skins");
			}

			@Override
			public void reload(ResourceManager manager) {
				clear();
			}
		});
	}

	/**
	 * The skin for a mutant of this look, painted on first use; null when the element paints nothing or the texture
	 * cannot be read. {@code core} is where the mob's chest sits in a 64-pixel-wide texture, or null.
	 */
	@Nullable
	static Skin get(Identifier texture, Element element, Tier tier, @Nullable SkinPainter.Core core) {
		if (!SkinPainter.paints(element)) {
			return null;
		}
		Key key = new Key(texture, element, tier, core);
		Optional<Skin> skin = SKINS.get(key);
		if (skin == null) {
			if (SKINS.size() >= MAX_SKINS) {
				return null;
			}
			skin = Optional.ofNullable(paint(key));
			SKINS.put(key, skin);
		}
		return skin.orElse(null);
	}

	private static void clear() {
		TextureManager textures = MinecraftClient.getInstance().getTextureManager();
		for (Optional<Skin> skin : SKINS.values()) {
			skin.ifPresent(painted -> {
				textures.destroyTexture(painted.crust());
				textures.destroyTexture(painted.glow());
			});
		}
		SKINS.clear();
	}

	@Nullable
	private static Skin paint(Key key) {
		MinecraftClient client = MinecraftClient.getInstance();
		Optional<Resource> resource = client.getResourceManager().getResource(key.texture());
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream stream = resource.get().getInputStream(); NativeImage image = NativeImage.read(stream)) {
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = new int[width * height];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					pixels[y * width + x] = swapRedBlue(image.getColor(x, y));
				}
			}
			// the texture's name picks the pattern: every zombie shares one, and it is the same every session
			long seed = key.texture().toString().hashCode() * 0x9E3779B97F4A7C15L + key.element().ordinal();
			SkinPainter.Core core = key.core() == null ? null : key.core().scaled(Math.max(1, width / 64));
			SkinPainter.Layers layers = SkinPainter.paint(pixels, width, height, key.element(), key.tier(), seed, core);
			String path = "skins/" + key.texture().getNamespace() + "/" + key.texture().getPath() + "/" + key.element().key() + "_" + key.tier().key()
					+ (core == null ? "" : "_cored");
			Skin skin = new Skin(Evoluta.id(path + "/crust"), Evoluta.id(path + "/glow"));
			upload(client.getTextureManager(), skin.crust(), layers.crust(), width, height);
			upload(client.getTextureManager(), skin.glow(), layers.glow(), width, height);
			return skin;
		} catch (IOException | RuntimeException e) {
			Evoluta.LOGGER.warn("No mutant skin for {}: could not read it ({})", key.texture(), e.toString());
			return null;
		}
	}

	private static void upload(TextureManager textures, Identifier id, int[] argb, int width, int height) {
		NativeImage image = new NativeImage(width, height, true);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setColor(x, y, swapRedBlue(argb[y * width + x]));
			}
		}
		textures.registerTexture(id, new NativeImageBackedTexture(image));
	}

	/** ARGB to NativeImage's ABGR and back: the same swap both ways. */
	private static int swapRedBlue(int color) {
		return color & 0xFF00FF00 | (color & 0xFF) << 16 | color >> 16 & 0xFF;
	}
}
