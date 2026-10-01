package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.Evoluta;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Textures that are clear everywhere except a mob's eye pixels, drawn over the mob's own model with the additive
 * eyes layer so only the eyes glow. They are built in memory from pixel coordinates read off the vanilla textures
 * (all in the head's front face, u 8-15): no image files ship, and a resource pack that keeps the vanilla face
 * layout keeps working. Mobs without a mask here still get their aura.
 */
final class EyeMasks {
	private record Mask(Identifier id, int width, int height, int[][] pixels) {
	}

	/** Zombie, husk, drowned (64x64) and skeleton, stray, bogged (64x32): two-pixel eyes on row 12. */
	private static final int[][] BIPED_EYES = {{9, 12}, {10, 12}, {13, 12}, {14, 12}};
	private static final Mask BIPED_64X64 = new Mask(Evoluta.id("eyes/biped_64x64"), 64, 64, BIPED_EYES);
	private static final Mask BIPED_64X32 = new Mask(Evoluta.id("eyes/biped_64x32"), 64, 32, BIPED_EYES);
	/** The zombie villager's taller head puts its eyes on row 14. */
	private static final Mask ZOMBIE_VILLAGER = new Mask(Evoluta.id("eyes/zombie_villager"), 64, 64,
			new int[][]{{9, 14}, {10, 14}, {13, 14}, {14, 14}});
	/** The creeper's two-by-two eyes, rows 10 and 11. */
	private static final Mask CREEPER = new Mask(Evoluta.id("eyes/creeper"), 64, 32,
			new int[][]{{9, 10}, {10, 10}, {9, 11}, {10, 11}, {13, 10}, {14, 10}, {13, 11}, {14, 11}});

	private static final List<Mask> ALL = List.of(BIPED_64X64, BIPED_64X32, ZOMBIE_VILLAGER, CREEPER);
	private static final Map<EntityType<?>, Mask> BY_TYPE = Map.of(
			EntityType.ZOMBIE, BIPED_64X64,
			EntityType.HUSK, BIPED_64X64,
			EntityType.DROWNED, BIPED_64X64,
			EntityType.SKELETON, BIPED_64X32,
			EntityType.STRAY, BIPED_64X32,
			EntityType.BOGGED, BIPED_64X32,
			EntityType.WITHER_SKELETON, BIPED_64X32,
			EntityType.ZOMBIE_VILLAGER, ZOMBIE_VILLAGER,
			EntityType.CREEPER, CREEPER
	);

	private static boolean uploaded;

	private EyeMasks() {
	}

	/** The eye mask for {@code type}, or null when Evoluta has none for it. Render thread only. */
	@Nullable
	static Identifier forType(EntityType<?> type) {
		Mask mask = BY_TYPE.get(type);
		if (mask == null) {
			return null;
		}
		if (!uploaded) {
			upload();
		}
		return mask.id();
	}

	private static void upload() {
		TextureManager textures = MinecraftClient.getInstance().getTextureManager();
		for (Mask mask : ALL) {
			// the STB-backed image starts zeroed: fully transparent
			NativeImage image = new NativeImage(mask.width(), mask.height(), true);
			for (int[] pixel : mask.pixels()) {
				image.setColor(pixel[0], pixel[1], 0xFFFFFFFF);
			}
			textures.registerTexture(mask.id(), new NativeImageBackedTexture(image));
		}
		uploaded = true;
	}
}
