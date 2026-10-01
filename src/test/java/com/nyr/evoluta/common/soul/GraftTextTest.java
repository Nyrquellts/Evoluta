package com.nyr.evoluta.common.soul;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nyr.evoluta.common.mutation.Element;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every line a graft's tooltip can show has its English text, with a slot for the number where the tooltip gives one. */
class GraftTextTest {
	private static final Element[] MEAT_ELEMENTS = {Element.IGNITED, Element.PERMAFROST, Element.TOXIC};
	/** Kinds whose mining line carries a number (a chance, a speed, a range). */
	private static final Set<SoulKind> NUMBERED_TOOL_LINES = Set.of(SoulKind.HUSK, SoulKind.ZOMBIE_VILLAGER, SoulKind.BOGGED, SoulKind.CREEPER,
			SoulKind.SPIDER, SoulKind.CAVE_SPIDER);

	private static JsonObject english() throws IOException {
		try (InputStream in = GraftTextTest.class.getResourceAsStream("/assets/evoluta/lang/en_us.json")) {
			assertTrue(in != null, "no en_us.json on the classpath");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static int slots(String text) {
		int slots = 0;
		for (int at = text.indexOf("%s"); at >= 0; at = text.indexOf("%s", at + 2)) {
			slots++;
		}
		return slots;
	}

	@Test
	void everyKindAndElementHasItsLines() throws IOException {
		JsonObject lang = english();
		for (SoulKind kind : SoulKind.values()) {
			for (String line : List.of("evoluta.soul_kind.", "evoluta.graft.weapon.", "evoluta.graft.armor.", "evoluta.graft.tool.")) {
				assertTrue(lang.has(line + kind.key()), "missing " + line + kind.key());
			}
			String tool = lang.get("evoluta.graft.tool." + kind.key()).getAsString();
			int expected = NUMBERED_TOOL_LINES.contains(kind) ? 1 : 0;
			assertTrue(slots(tool) == expected, "evoluta.graft.tool." + kind.key() + " has " + slots(tool) + " number slots, the tooltip fills " + expected);
		}
		for (Element element : MEAT_ELEMENTS) {
			for (String line : List.of("evoluta.element.", "evoluta.graft.damage.", "evoluta.graft.retaliate.", "evoluta.graft.tool_element.")) {
				assertTrue(lang.has(line + element.key()), "missing " + line + element.key());
			}
			String text = lang.get("evoluta.graft.tool_element." + element.key()).getAsString();
			int expected = element == Element.IGNITED ? 1 : 0;
			assertTrue(slots(text) == expected, "evoluta.graft.tool_element." + element.key() + " has " + slots(text) + " number slots, expected " + expected);
		}
		assertTrue(lang.has("evoluta.graft.overlap") && slots(lang.get("evoluta.graft.overlap").getAsString()) == 1, "the overlap line needs one number slot");
	}
}
