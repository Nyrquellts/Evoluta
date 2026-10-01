package com.nyr.evoluta.client.particle;

import com.nyr.evoluta.common.particle.EvolutaParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

public final class EvolutaParticleFactories {
	private EvolutaParticleFactories() {
	}

	public static void register() {
		ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
		registry.register(EvolutaParticles.FROST_MIST, sprites -> new AuraParticle.Factory(sprites, AuraParticle.FROST_MIST));
		registry.register(EvolutaParticles.FROST_BREATH, sprites -> new AuraParticle.Factory(sprites, AuraParticle.FROST_BREATH));
		registry.register(EvolutaParticles.EMBER, sprites -> new AuraParticle.Factory(sprites, AuraParticle.EMBER));
		registry.register(EvolutaParticles.ACID_DRIP, sprites -> new AuraParticle.Factory(sprites, AuraParticle.ACID_DRIP));
		registry.register(EvolutaParticles.TOXIC_HAZE, sprites -> new AuraParticle.Factory(sprites, AuraParticle.TOXIC_HAZE));
		registry.register(EvolutaParticles.GLYPH_IGNITED, sprites -> new AuraParticle.Factory(sprites, AuraParticle.GLYPH_IGNITED));
		registry.register(EvolutaParticles.GLYPH_FROST, sprites -> new AuraParticle.Factory(sprites, AuraParticle.GLYPH_FROST));
		registry.register(EvolutaParticles.GLYPH_TOXIC, sprites -> new AuraParticle.Factory(sprites, AuraParticle.GLYPH_TOXIC));
	}
}
