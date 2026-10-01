package com.nyr.evoluta.common.soul;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.List;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Evoluta's item data components. Synced to clients, so tooltips and item looks can read them. */
public final class SoulComponents {
	/**
	 * The tier of the mutant a piece of Soul Meat came from: grafting rolls within its range, and an Apex grade looks
	 * the part. Soul Meat without it counts as Evolved.
	 */
	public static final ComponentType<Tier> SOUL_GRADE = Registry.register(Registries.DATA_COMPONENT_TYPE, Evoluta.id("soul_grade"),
			ComponentType.<Tier>builder().codec(Tier.CODEC).packetCodec(Tier.PACKET_CODEC).build());

	/** The Soul Meat grafted onto a weapon or armour, at most {@link Grafting#MAX_GRAFTS}, in the order they went on. */
	public static final ComponentType<List<Graft>> GRAFTS = Registry.register(Registries.DATA_COMPONENT_TYPE, Evoluta.id("grafts"),
			ComponentType.<List<Graft>>builder()
					.codec(Graft.CODEC.sizeLimitedListOf(Grafting.MAX_GRAFTS))
					.packetCodec(Graft.PACKET_CODEC.collect(PacketCodecs.toList(Grafting.MAX_GRAFTS)))
					.build());

	private SoulComponents() {
	}

	/** Loads this class, which registers the component types above. */
	public static void register() {
	}
}
