package com.nyr.evoluta.common.mutation;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;
import org.jetbrains.annotations.Nullable;

/** How far a mob has evolved. Stored as its byte id; the ids are part of the save format and never change. */
public enum Tier implements StringIdentifiable {
	EVOLVED((byte) 1, "evolved"),
	ELITE((byte) 2, "elite"),
	APEX((byte) 3, "apex");

	/** Saved by key, e.g. on a piece of Soul Meat: {@code evoluta:soul_grade="apex"}. */
	public static final Codec<Tier> CODEC = StringIdentifiable.createCodec(Tier::values);
	private static final IntFunction<Tier> BY_ORDINAL = ValueLists.createIdToValueFunction(Tier::ordinal, values(),
			ValueLists.OutOfBoundsHandling.ZERO);
	public static final PacketCodec<ByteBuf, Tier> PACKET_CODEC = PacketCodecs.indexed(BY_ORDINAL, Tier::ordinal);

	private static final Tier[] BY_ID = {null, EVOLVED, ELITE, APEX};

	private final byte id;
	private final String key;

	Tier(byte id, String key) {
		this.id = id;
		this.key = key;
	}

	public byte id() {
		return this.id;
	}

	public String key() {
		return this.key;
	}

	@Override
	public String asString() {
		return this.key;
	}

	/** Elite and Apex mutants are champions: their eyes glow and the Blight Locator finds them. */
	public boolean isChampion() {
		return this != EVOLVED;
	}

	@Nullable
	public static Tier byId(int id) {
		return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
	}

	@Nullable
	public static Tier byKey(String key) {
		for (Tier tier : values()) {
			if (tier.key.equals(key)) {
				return tier;
			}
		}
		return null;
	}
}
