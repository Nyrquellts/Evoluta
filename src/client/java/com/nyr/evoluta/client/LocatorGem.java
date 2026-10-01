package com.nyr.evoluta.client;

import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Element;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The Blight Locator's soul gem (model layer 1, painted in grey, set in the rim): a dim violet ember breathing while
 * the needle searches; once a champion is found, the colour its eyes glow, flaring white with every click, so the gem
 * beats faster as the clicks do on the way in.
 */
final class LocatorGem {
	/** Soul violet, while no champion is in range. */
	static final int SEARCHING = 0xC07CFF;
	/** An element-less champion's eyes glow red; so does the gem. */
	static final int NO_ELEMENT = 0xFF3B3B;
	/** How fast a click's flare fades, in milliseconds. */
	private static final float FLARE_MS = 160.0F;

	private LocatorGem() {
	}

	static void register() {
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 1 ? current() : -1, EvolutaItems.BLIGHT_LOCATOR);
	}

	private static int current() {
		MinecraftClient client = MinecraftClient.getInstance();
		Element element = client.world == null ? null : ClientLocatorState.element(client.world);
		long now = Util.getMeasuringTimeMs();
		return tint(element, now - LocatorClicks.lastClickMs(), now);
	}

	/**
	 * The gem's colour, opaque: {@code element} is the target's, or null while searching; {@code sinceClickMs} is the
	 * time since the last click.
	 */
	static int tint(@Nullable Element element, long sinceClickMs, long timeMs) {
		if (element == null) {
			float breath = 0.35F + 0.1F * MathHelper.sin(timeMs / 3000.0F * MathHelper.TAU);
			return shade(SEARCHING, breath, 0.0F);
		}
		int rgb = element == Element.NONE ? NO_ELEMENT : SoulMeatItem.color(element);
		float flare = (float) Math.exp(-Math.max(0L, sinceClickMs) / FLARE_MS);
		return shade(rgb, 0.65F + 0.35F * flare, 0.4F * flare);
	}

	/** {@code rgb} scaled by {@code scale}, then pushed {@code white} of the way to white. */
	private static int shade(int rgb, float scale, float white) {
		int red = channel(rgb >> 16 & 0xFF, scale, white);
		int green = channel(rgb >> 8 & 0xFF, scale, white);
		int blue = channel(rgb & 0xFF, scale, white);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static int channel(int value, float scale, float white) {
		return MathHelper.clamp((int) MathHelper.lerp(white, value * scale, 255), 0, 255);
	}
}
