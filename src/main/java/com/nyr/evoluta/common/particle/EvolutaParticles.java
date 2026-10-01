package com.nyr.evoluta.common.particle;

import com.nyr.evoluta.common.Evoluta;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * The element auras' and grafts' own particles. Registered on both sides as the registry requires, but only ever spawned by the
 * client, around mutants it can see: the server sends no particle packets for auras. They draw vanilla particle
 * sprites (no textures of their own); their colour, glow and motion come from the client's particle class.
 */
public final class EvolutaParticles {
	/** Permafrost: glowing frost mist circling the feet and torso. Spawn at the orbit's centre; velocity = (radius, rise, turn). */
	public static final SimpleParticleType FROST_MIST = register("frost_mist");
	/** Permafrost: a cold breath puff drifting out of the mouth. Velocity is its drift. */
	public static final SimpleParticleType FROST_BREATH = register("frost_breath");
	/** Ignited: a glowing ember rising off the shoulders. Velocity is its initial rise. */
	public static final SimpleParticleType EMBER = register("ember");
	/** Toxic: a glowing acid drop falling off the body. Velocity is its initial fall. */
	public static final SimpleParticleType ACID_DRIP = register("acid_drip");
	/** Toxic: sickly haze circling the feet. Spawn at the orbit's centre; velocity = (radius, rise, turn). */
	public static final SimpleParticleType TOXIC_HAZE = register("toxic_haze");
	/** Grafts: an enchanting glyph in the graft's element, flying in to its bearer. Velocity is where it starts from. */
	public static final SimpleParticleType GLYPH_IGNITED = register("glyph_ignited");
	public static final SimpleParticleType GLYPH_FROST = register("glyph_frost");
	public static final SimpleParticleType GLYPH_TOXIC = register("glyph_toxic");

	private EvolutaParticles() {
	}

	/** Loads this class, which registers the particle types above. */
	public static void register() {
	}

	private static SimpleParticleType register(String path) {
		return Registry.register(Registries.PARTICLE_TYPE, Evoluta.id(path), FabricParticleTypes.simple());
	}
}
