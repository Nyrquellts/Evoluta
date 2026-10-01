package com.nyr.evoluta.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nyr.evoluta.common.mutation.Element;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class TargetPosPayloadTest {
	@Test
	void aTargetAndNoTargetBothSurviveTheWire() {
		for (TargetPosPayload payload : new TargetPosPayload[]{
				new TargetPosPayload(Optional.of(new BlockPos(-29_999_000, -64, 29_999_000)), Element.TOXIC),
				new TargetPosPayload(Optional.of(BlockPos.ORIGIN), Element.NONE),
				new TargetPosPayload(Optional.of(BlockPos.ORIGIN), Element.ABYSSAL),
				TargetPosPayload.NOTHING}) {
			ByteBuf buf = Unpooled.buffer();
			TargetPosPayload.CODEC.encode(buf, payload);
			assertEquals(payload, TargetPosPayload.CODEC.decode(buf));
			assertEquals(0, buf.readableBytes(), "trailing bytes after " + payload);
		}
	}

	@Test
	void theWireFormIsSmall() {
		ByteBuf buf = Unpooled.buffer();
		TargetPosPayload.CODEC.encode(buf, new TargetPosPayload(Optional.of(new BlockPos(100, 64, -100)), Element.PERMAFROST));
		assertEquals(10, buf.readableBytes(), "one presence byte, a packed long and the element");
	}
}
