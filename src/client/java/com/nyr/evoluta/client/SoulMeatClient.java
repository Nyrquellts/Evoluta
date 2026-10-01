package com.nyr.evoluta.client;

import com.nyr.evoluta.client.render.GraftGlyphs;
import com.nyr.evoluta.common.forge.SoulForge;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Tier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * Soul Meat on the client: each piece's soul (model layer 1, painted in grey) glows in its element's colour and
 * throbs like a heart. The grade shows: an Evolved soul smoulders, an Apex one beats fast and flares white-hot.
 */
public final class SoulMeatClient {
	private SoulMeatClient() {
	}

	public static void register() {
		SoulTooltips.register();
		HandledScreens.register(SoulForge.SCREEN, SoulForgeScreen::new);
		ClientTickEvents.END_CLIENT_TICK.register(GraftGlyphs::tick);
		for (SoulMeatItem meat : EvolutaItems.allSoulMeat()) {
			int color = SoulMeatItem.color(meat.element());
			ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 1
					? soulTint(color, SoulMeatItem.grade(stack), Util.getMeasuringTimeMs()) : -1, meat);
		}
	}

	/**
	 * The soul's colour at {@code timeMs}: the element's colour, dimmed at rest and pushed toward white on each beat.
	 * Higher grades beat faster and flare brighter.
	 */
	static int soulTint(int rgb, Tier grade, long timeMs) {
		long period = switch (grade) {
			case EVOLVED -> 1500;
			case ELITE -> 1200;
			case APEX -> 900;
		};
		float rest = switch (grade) {
			case EVOLVED -> 0.55F;
			case ELITE -> 0.7F;
			case APEX -> 0.8F;
		};
		float flare = switch (grade) {
			case EVOLVED -> 0.1F;
			case ELITE -> 0.25F;
			case APEX -> 0.45F;
		};
		float phase = (timeMs % period) / (float) period;
		float beat = Math.max(bump(phase, 0.0F), 0.6F * bump(phase, 0.22F));
		float scale = rest + (1.0F - rest) * beat;
		int red = channel(rgb >> 16 & 0xFF, scale, flare * beat);
		int green = channel(rgb >> 8 & 0xFF, scale, flare * beat);
		int blue = channel(rgb & 0xFF, scale, flare * beat);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static int channel(int value, float scale, float white) {
		return MathHelper.clamp((int) MathHelper.lerp(white, value * scale, 255), 0, 255);
	}

	private static float bump(float phase, float at) {
		float distance = Math.abs(phase - at);
		distance = Math.min(distance, 1.0F - distance) / 0.07F;
		return (float) Math.exp(-distance * distance);
	}
}
