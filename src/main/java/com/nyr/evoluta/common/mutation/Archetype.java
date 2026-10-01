package com.nyr.evoluta.common.mutation;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** The tactical archetype of a mutant. Stored as its byte id; the ids are part of the save format and never change. */
public enum Archetype {
	NONE((byte) 0, "none"),
	BRUTE((byte) 1, "brute"),
	STALKER((byte) 2, "stalker"),
	/** Reserved by the save format for pack leaders. It has no behaviour yet, so nothing rolls or grants it. */
	ALPHA((byte) 3, "alpha");

	/** The archetypes a spawn can roll and a command can grant: every one that has behaviour. */
	public static final List<Archetype> IMPLEMENTED = List.of(BRUTE, STALKER);

	private static final Archetype[] BY_ID = {NONE, BRUTE, STALKER, ALPHA};

	private final byte id;
	private final String key;

	Archetype(byte id, String key) {
		this.id = id;
		this.key = key;
	}

	public byte id() {
		return this.id;
	}

	public String key() {
		return this.key;
	}

	@Nullable
	public static Archetype byId(int id) {
		return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
	}

	@Nullable
	public static Archetype byKey(String key) {
		for (Archetype archetype : values()) {
			if (archetype.key.equals(key)) {
				return archetype;
			}
		}
		return null;
	}
}
