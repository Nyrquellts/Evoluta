package com.nyr.evoluta.common.mutation;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;
import org.jetbrains.annotations.Nullable;

/** The elemental affix of a mutant. Stored as its byte id; the ids are part of the save format and never change. */
public enum Element implements StringIdentifiable {
	NONE((byte) 0, "none"),
	IGNITED((byte) 1, "ignited"),
	PERMAFROST((byte) 2, "permafrost"),
	TOXIC((byte) 3, "toxic"),
	/** Reserved by the save format for the void affix. It has no behaviour yet, so nothing rolls or grants it. */
	ABYSSAL((byte) 4, "abyssal");

	/** Saved by key, e.g. in a graft: {@code element:"permafrost"}. */
	public static final Codec<Element> CODEC = StringIdentifiable.createCodec(Element::values);
	private static final IntFunction<Element> BY_ORDINAL = ValueLists.createIdToValueFunction(Element::ordinal, values(),
			ValueLists.OutOfBoundsHandling.ZERO);
	public static final PacketCodec<ByteBuf, Element> PACKET_CODEC = PacketCodecs.indexed(BY_ORDINAL, Element::ordinal);

	/** The affixes a spawn can roll and a command can grant: every one that has behaviour. */
	public static final List<Element> IMPLEMENTED = List.of(IGNITED, PERMAFROST, TOXIC);

	private static final Element[] BY_ID = {NONE, IGNITED, PERMAFROST, TOXIC, ABYSSAL};

	private final byte id;
	private final String key;

	Element(byte id, String key) {
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

	@Nullable
	public static Element byId(int id) {
		return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
	}

	@Nullable
	public static Element byKey(String key) {
		for (Element element : values()) {
			if (element.key.equals(key)) {
				return element;
			}
		}
		return null;
	}
}
