package com.nyr.evoluta.common.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class MutationDataTest {
	@Test
	void everyCombinationRoundTripsThroughNbtAndNetwork() {
		for (Tier tier : Tier.values()) {
			for (Element element : Element.values()) {
				for (Archetype archetype : Archetype.values()) {
					MutationData data = MutationData.of(tier, element, archetype);

					NbtElement nbt = MutationData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
					assertEquals(data, MutationData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());

					ByteBuf buf = Unpooled.buffer();
					MutationData.PACKET_CODEC.encode(buf, data);
					assertEquals(3, buf.readableBytes(), "three bytes on the wire");
					assertEquals(data, MutationData.PACKET_CODEC.decode(buf));
				}
			}
		}
	}

	@Test
	void savedFormIsThreeNamedBytes() {
		NbtCompound nbt = (NbtCompound) MutationData.CODEC.encodeStart(NbtOps.INSTANCE,
				MutationData.of(Tier.ELITE, Element.PERMAFROST, Archetype.STALKER)).getOrThrow();
		assertEquals(NbtByte.of((byte) 2), nbt.get("tier"));
		assertEquals(NbtByte.of((byte) 2), nbt.get("element"));
		assertEquals(NbtByte.of((byte) 2), nbt.get("archetype"));
	}

	@Test
	void missingElementAndArchetypeMeanNone() {
		JsonObject json = JsonParser.parseString("{\"tier\": 1}").getAsJsonObject();
		MutationData data = MutationData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(MutationData.of(Tier.EVOLVED, Element.NONE, Archetype.NONE), data);
	}

	@Test
	void unknownIdsFailToDecodeInsteadOfLoading() {
		for (String bad : new String[]{"{\"tier\": 0}", "{\"tier\": 4}", "{\"tier\": 1, \"element\": 5}",
				"{\"tier\": 1, \"archetype\": 4}", "{\"tier\": 1, \"element\": -1}", "{}"}) {
			JsonObject json = JsonParser.parseString(bad).getAsJsonObject();
			assertTrue(MutationData.CODEC.parse(JsonOps.INSTANCE, json).isError(), bad + " must not decode");
		}
	}

	@Test
	void constructorRejectsUnknownIds() {
		assertThrows(IllegalArgumentException.class, () -> new MutationData((byte) 0, (byte) 0, (byte) 0));
		assertThrows(IllegalArgumentException.class, () -> new MutationData((byte) 1, (byte) 9, (byte) 0));
		assertThrows(IllegalArgumentException.class, () -> new MutationData((byte) 1, (byte) 0, (byte) 9));
	}

	@Test
	void idsMatchTheSaveFormat() {
		assertEquals(1, Tier.EVOLVED.id());
		assertEquals(2, Tier.ELITE.id());
		assertEquals(3, Tier.APEX.id());
		assertEquals(0, Element.NONE.id());
		assertEquals(1, Element.IGNITED.id());
		assertEquals(2, Element.PERMAFROST.id());
		assertEquals(3, Element.TOXIC.id());
		assertEquals(4, Element.ABYSSAL.id());
		assertEquals(0, Archetype.NONE.id());
		assertEquals(1, Archetype.BRUTE.id());
		assertEquals(2, Archetype.STALKER.id());
		assertEquals(3, Archetype.ALPHA.id());
	}

	@Test
	void onlyEliteAndApexAreChampions() {
		assertTrue(!Tier.EVOLVED.isChampion() && Tier.ELITE.isChampion() && Tier.APEX.isChampion());
	}
}
