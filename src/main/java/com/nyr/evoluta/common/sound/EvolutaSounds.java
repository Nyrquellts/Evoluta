package com.nyr.evoluta.common.sound;

import com.nyr.evoluta.common.Evoluta;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class EvolutaSounds {
	/** The Blight Locator's tick. Its own event (with a subtitle) so resource packs can replace it. */
	public static final SoundEvent BLIGHT_LOCATOR_CLICK = register("item.blight_locator.click");

	private EvolutaSounds() {
	}

	/** Loads this class, which registers the sound events above. */
	public static void register() {
	}

	private static SoundEvent register(String path) {
		Identifier id = Evoluta.id(path);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}
}
