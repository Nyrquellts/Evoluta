package com.nyr.evoluta.common.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.random.Random;

/**
 * One piece of Soul Meat grafted onto a weapon or armour: the kind of creature (its ability), the element (its damage),
 * the grade it came in (the range it rolled in) and the power it rolled, 0 to 1, which scales every number it gives.
 */
public record Graft(SoulKind kind, Element element, Tier grade, float power) {
	/** Saved on the item, e.g. {@code {kind:"skeleton",element:"permafrost",grade:"apex",power:0.93f}}. */
	public static final Codec<Graft> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			SoulKind.CODEC.fieldOf("kind").forGetter(Graft::kind),
			Element.CODEC.fieldOf("element").forGetter(Graft::element),
			Tier.CODEC.fieldOf("grade").forGetter(Graft::grade),
			Codec.floatRange(0.0F, 1.0F).fieldOf("power").forGetter(Graft::power)
	).apply(instance, Graft::new));
	public static final PacketCodec<ByteBuf, Graft> PACKET_CODEC = PacketCodec.tuple(
			SoulKind.PACKET_CODEC, Graft::kind,
			Element.PACKET_CODEC, Graft::element,
			Tier.PACKET_CODEC, Graft::grade,
			PacketCodecs.FLOAT, Graft::power,
			Graft::new
	);

	private static final float[] MIN = {0.2F, 0.45F, 0.75F};
	private static final float[] MAX = {0.5F, 0.8F, 1.0F};

	/** The lowest power a graft of this grade can roll: Evolved 0.2, Elite 0.45, Apex 0.75. */
	public static float minPower(Tier grade) {
		return MIN[grade.id() - 1];
	}

	/** The highest power a graft of this grade can roll: Evolved 0.5, Elite 0.8, Apex 1. */
	public static float maxPower(Tier grade) {
		return MAX[grade.id() - 1];
	}

	/** A fresh roll within the grade's range. */
	public static float roll(Tier grade, Random random) {
		return minPower(grade) + random.nextFloat() * (maxPower(grade) - minPower(grade));
	}

	/** How good the roll was within its grade, 0 (the grade's floor) to 1 (its ceiling), for the stars and bars. */
	public float quality() {
		float span = maxPower(this.grade) - minPower(this.grade);
		return span <= 0 ? 1.0F : Math.max(0.0F, Math.min(1.0F, (this.power - minPower(this.grade)) / span));
	}
}
