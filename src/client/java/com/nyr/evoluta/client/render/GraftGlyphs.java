package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.particle.EvolutaParticles;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.SoulComponents;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Strong grafts announce themselves: enchanting glyphs burning in the graft's element fly in toward whoever holds or
 * wears one, the way they fly from bookshelves to an enchanting table. Client-only: for each Apex piece two glyphs
 * every ten ticks, for each Elite piece one every twenty (half as many on Decreased particles, none on Minimal), for
 * anything within {@link #RANGE} blocks of the camera, staggered by entity so a crowd does not pulse together.
 */
public final class GraftGlyphs {
	static final double RANGE = 24;

	private GraftGlyphs() {
	}

	public static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.isPaused()) {
			return;
		}
		ParticlesMode mode = client.options.getParticles().getValue();
		if (mode == ParticlesMode.MINIMAL) {
			return;
		}
		int every = mode == ParticlesMode.DECREASED ? 20 : 10;
		long time = world.getTime();
		Vec3d camera = client.gameRenderer.getCamera().getPos();
		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || (time + living.getId()) % every != 0
					|| living.squaredDistanceTo(camera) > RANGE * RANGE) {
				continue;
			}
			boolean eliteTurn = (time + living.getId()) / every % 2 == 0;
			Random random = living.getRandom();
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				Graft graft = glyphGraft(living.getEquippedStack(slot));
				SimpleParticleType glyph = graft == null ? null : glyph(graft.element());
				if (glyph == null) {
					continue;
				}
				int count = graft.grade() == Tier.APEX ? 2 : eliteTurn ? 1 : 0;
				for (int i = 0; i < count; i++) {
					// the glyph starts out at the offset and flies in to the body
					world.addParticle(glyph, living.getX(), living.getBodyY(0.6), living.getZ(),
							(random.nextFloat() - 0.5F) * 3.0F, random.nextFloat() * 1.5F - 0.3F, (random.nextFloat() - 0.5F) * 3.0F);
				}
			}
		}
	}

	/** The graft a piece's glyphs burn with: its best of Elite grade or better, the stronger roll on a tie; or null. */
	@Nullable
	static Graft glyphGraft(ItemStack stack) {
		List<Graft> grafts = stack.getOrDefault(SoulComponents.GRAFTS, List.of());
		Graft best = null;
		for (Graft graft : grafts) {
			if (graft.grade().id() < Tier.ELITE.id()) {
				continue;
			}
			if (best == null || graft.grade().id() > best.grade().id() || graft.grade() == best.grade() && graft.power() > best.power()) {
				best = graft;
			}
		}
		return best;
	}

	@Nullable
	static SimpleParticleType glyph(Element element) {
		return switch (element) {
			case IGNITED -> EvolutaParticles.GLYPH_IGNITED;
			case PERMAFROST -> EvolutaParticles.GLYPH_FROST;
			case TOXIC -> EvolutaParticles.GLYPH_TOXIC;
			default -> null;
		};
	}
}
