package com.nyr.evoluta.common.mutation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.IntPredicate;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Everything that makes a mob a mutant, in three bytes. Only mutated mobs carry it (see
 * {@link EvolutaAttachments#MUTATION}); an ordinary mob holds nothing.
 *
 * @param tier      {@link Tier} id: 1 Evolved, 2 Elite, 3 Apex
 * @param element   {@link Element} id: 0 none, 1 Ignited, 2 Permafrost, 3 Toxic, 4 Abyssal (reserved)
 * @param archetype {@link Archetype} id: 0 none, 1 Brute, 2 Stalker, 3 Alpha (reserved)
 */
public record MutationData(byte tier, byte element, byte archetype) {
	/** Saved form, e.g. {@code {tier:2b,element:1b,archetype:1b}}. Unknown ids fail to decode instead of loading. */
	public static final Codec<MutationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			id("tier", id -> Tier.byId(id) != null).fieldOf("tier").forGetter(MutationData::tier),
			id("element", id -> Element.byId(id) != null).optionalFieldOf("element", Element.NONE.id()).forGetter(MutationData::element),
			id("archetype", id -> Archetype.byId(id) != null).optionalFieldOf("archetype", Archetype.NONE.id()).forGetter(MutationData::archetype)
	).apply(instance, MutationData::new));

	/** Network form: three raw bytes. */
	public static final PacketCodec<ByteBuf, MutationData> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.BYTE, MutationData::tier,
			PacketCodecs.BYTE, MutationData::element,
			PacketCodecs.BYTE, MutationData::archetype,
			MutationData::new
	);

	public MutationData {
		if (Tier.byId(tier) == null) {
			throw new IllegalArgumentException("Unknown tier id " + tier);
		}
		if (Element.byId(element) == null) {
			throw new IllegalArgumentException("Unknown element id " + element);
		}
		if (Archetype.byId(archetype) == null) {
			throw new IllegalArgumentException("Unknown archetype id " + archetype);
		}
	}

	public static MutationData of(Tier tier, Element element, Archetype archetype) {
		return new MutationData(tier.id(), element.id(), archetype.id());
	}

	public Tier getTier() {
		return Tier.byId(this.tier);
	}

	public Element getElement() {
		return Element.byId(this.element);
	}

	public Archetype getArchetype() {
		return Archetype.byId(this.archetype);
	}

	public boolean isChampion() {
		return this.getTier().isChampion();
	}

	public MutationData withArchetype(Archetype archetype) {
		return new MutationData(this.tier, this.element, archetype.id());
	}

	private static Codec<Byte> id(String what, IntPredicate known) {
		return Codec.BYTE.validate(id -> known.test(id) ? DataResult.success(id) : DataResult.error(() -> "Unknown " + what + " id " + id));
	}
}
