package com.nyr.evoluta.common.skin;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.jetbrains.annotations.Nullable;

/**
 * Paints an element onto a mob's own texture, so a mutant looks transformed rather than recoloured: Ignited skin
 * chars around glowing magma fissures, Permafrost skin grows rime and ice crystals, Toxic skin sickens and breaks out
 * in glowing pustules joined by veins. The result is two layers the size of the texture: a crust drawn over the body
 * in the world's light, and a glow added on top at full brightness (black where nothing glows).
 *
 * <p>Higher tiers take over more of the body and always keep the lower tier's pattern; the seed gives each kind of
 * mob its own. Because it reads the mob's texture instead of shipping one, it fits any mob, resource pack or modded
 * mob included. Pure pixel work on ARGB ints with no client classes, so the unit tests reach it; only the client
 * calls it.
 */
public final class SkinPainter {
	/** Share of a texture's opaque pixels the element takes over, by tier (Evolved, Elite, Apex). */
	static final float[] COVERAGE = {0.3F, 0.55F, 0.85F};

	private static final int CHAR_DEEP = 0x120806;
	private static final int CHAR = 0x1C100B;
	private static final int SOOT_DARK = 0x221510;
	private static final int SOOT_LIGHT = 0x4A2C1C;
	private static final int MAGMA_DULL = 0xB8330A;
	private static final int MAGMA = 0xFF7A18;
	private static final int MAGMA_HOT = 0xFFD27A;
	private static final int EMBER = 0x3A0E02;

	private static final int FROZEN = 0xCFEFFF;
	private static final int FROZEN_EDGE = 0x6FA8D8;
	private static final int SNOW = 0xFFFFFF;
	private static final int COLD_EDGE = 0x4E7FA8;
	private static final int ICE = 0xF4FDFF;
	private static final int GLINT = 0x4FA8CC;
	private static final int FROST_SHEEN = 0x081A28;
	private static final int STREAK_GLOW = 0x1A4A60;
	private static final int CRYSTAL_GLOW = 0x2F7FA0;
	private static final int CRYSTAL_TIP = 0x8FEAFF;

	private static final int ROT = 0x2E3A0C;
	private static final int SICK = 0x8FA320;
	private static final int VEIN = 0x1F2E05;
	private static final int VEIN_GLOW = 0x2E6E0A;
	private static final int DRIP = 0x7FB82A;
	private static final int DRIP_GLOW = 0x24560A;
	private static final int PUS = 0xD2F55C;
	private static final int PUS_GLOW = 0xB8FF3A;
	private static final int RIM = 0x2B3606;
	private static final int RIM_GLOW = 0x1C3A05;

	private static final int SOUL_FLESH = 0x3A0A16;
	private static final int SOUL_VEIN = 0x5A1024;
	private static final int CORE_IGNITED = 0xFFE9A0;
	private static final int CORE_PERMAFROST = 0xE8FCFF;
	private static final int CORE_TOXIC = 0xE6FF9A;

	/** The two layers, ARGB, row by row, the size of the texture they were painted from. */
	public record Layers(int[] crust, int[] glow) {
	}

	/**
	 * Where the soul sits in a texture: the face of the mob's chest (a spider's back), in the texture's own pixels. The
	 * core is painted into its upper middle, and the mutation spreads from there as the tier rises.
	 */
	public record Core(int x, int y, int width, int height) {
		public Core scaled(int factor) {
			return new Core(this.x * factor, this.y * factor, this.width * factor, this.height * factor);
		}

		boolean contains(int px, int py) {
			return px >= this.x && py >= this.y && px < this.x + this.width && py < this.y + this.height;
		}
	}

	private SkinPainter() {
	}

	/** Whether {@code element} changes the skin; the others leave the mob's look to its aura. */
	public static boolean paints(Element element) {
		return element == Element.IGNITED || element == Element.PERMAFROST || element == Element.TOXIC;
	}

	/**
	 * @param base the mob's texture, ARGB, row by row; pixels under half opacity count as empty and stay unpainted
	 * @param seed picks the pattern: the same seed gives the same pattern at every tier, each tier covering more
	 */
	public static Layers paint(int[] base, int width, int height, Element element, Tier tier, long seed) {
		return paint(base, width, height, element, tier, seed, null);
	}

	/**
	 * @param core where the mob's chest is, for a mob whose layout is known: the soul is painted into it, glowing,
	 *             with veins running out into the skin, and the element spreads outward from it by tier. Only there may
	 *             paint land on clear pixels (a skeleton's ribcage is see-through, so its core hangs inside it).
	 */
	public static Layers paint(int[] base, int width, int height, Element element, Tier tier, long seed, @Nullable Core core) {
		if (base.length != width * height) {
			throw new IllegalArgumentException("texture is " + base.length + " pixels, not " + width + "x" + height);
		}
		Canvas canvas = new Canvas(base, width, height, COVERAGE[tier.id() - 1], seed, core);
		if (canvas.skinCount > 0) {
			switch (element) {
				case IGNITED -> canvas.magma();
				case PERMAFROST -> canvas.frost();
				case TOXIC -> canvas.blight();
				default -> {
				}
			}
			if (core != null && paints(element)) {
				canvas.soul(element, tier, core);
			}
		}
		return new Layers(canvas.crust, canvas.glow);
	}

	/** One painting: the texture's opaque pixels, the zone this tier covers, and the two layers being painted. */
	private static final class Canvas {
		final int width;
		final int height;
		/** Texture pixels per 1/64th of the width: 1 for vanilla, more for a high-resolution pack. Scales every feature. */
		final int unit;
		final long seed;
		final boolean[] skin;
		/** Indices of the opaque pixels, in order. */
		final int[] skinIndex;
		final int skinCount;
		/** Smooth noise over the texture; the zone is the opaque pixels whose value lies at or under the threshold. */
		final float[] field;
		final float threshold;
		final float floor;
		final int[] crust;
		final int[] glow;

		Canvas(int[] base, int width, int height, float coverage, long seed, @Nullable Core core) {
			this.width = width;
			this.height = height;
			this.unit = Math.max(1, width / 64);
			this.seed = seed;
			int size = width * height;
			this.skin = new boolean[size];
			this.skinIndex = new int[size];
			this.field = new float[size];
			this.crust = new int[size];
			this.glow = new int[size];
			float[] values = new float[size];
			int count = 0;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int i = y * width + x;
					if (base[i] >>> 24 >= 128) {
						this.skin[i] = true;
						this.skinIndex[count] = i;
						float noise = 0.65F * noise(x / (8.0F * this.unit), y / (8.0F * this.unit), seed)
								+ 0.35F * noise(x / (3.5F * this.unit), y / (3.5F * this.unit), seed + 1);
						if (core != null) {
							// the mutation grows out of the soul: nearer the core, sooner in the zone
							float dx = x + 0.5F - (core.x() + core.width() * 0.5F);
							float dy = y + 0.5F - (core.y() + core.height() * 0.35F);
							float reach = Math.min(1.0F, (float) Math.sqrt(dx * dx + dy * dy) / (0.5F * width));
							noise = 0.4F * noise + 0.6F * reach;
						}
						this.field[i] = noise;
						values[count++] = this.field[i];
					}
				}
			}
			this.skinCount = count;
			if (count == 0) {
				this.threshold = -1;
				this.floor = 0;
				return;
			}
			Arrays.sort(values, 0, count);
			// an exact share of the opaque pixels, whatever the noise happened to produce
			this.threshold = values[Math.min(count - 1, (int) (coverage * count))];
			this.floor = values[0];
		}

		boolean inZone(int i) {
			return this.skin[i] && this.field[i] <= this.threshold;
		}

		/** How deep inside the zone a pixel lies: 0 on its border, 1 at its heart. */
		float depth(int i) {
			return (this.threshold - this.field[i]) / Math.max(1.0E-6F, this.threshold - this.floor);
		}

		/** Softens the zone's border so a patch fades out instead of ending in a hard line. */
		float fade(int i) {
			return Math.min(1.0F, 0.35F + this.depth(i) * 2.5F);
		}

		float mottle(int i) {
			return noise(i % this.width / (2.0F * this.unit), i / this.width / (2.0F * this.unit), this.seed + 7);
		}

		/** Charred, basalt-dark skin split by glowing magma fissures along the edges of Voronoi cells. */
		void magma() {
			int cell = 5 * this.unit;
			int cellsX = this.width / cell + 1;
			int cellsY = this.height / cell + 1;
			float[] seedX = new float[cellsX * cellsY];
			float[] seedY = new float[cellsX * cellsY];
			for (int cy = 0; cy < cellsY; cy++) {
				for (int cx = 0; cx < cellsX; cx++) {
					seedX[cy * cellsX + cx] = (cx + hash01(cx, cy, this.seed + 11)) * cell;
					seedY[cy * cellsX + cx] = (cy + hash01(cx, cy, this.seed + 12)) * cell;
				}
			}
			for (int k = 0; k < this.skinCount; k++) {
				int i = this.skinIndex[k];
				if (!this.inZone(i)) {
					continue;
				}
				int x = i % this.width;
				int y = i / this.width;
				float px = x + 0.5F;
				float py = y + 0.5F;
				int nearest = -1;
				float nearestSquared = Float.MAX_VALUE;
				for (int cy = y / cell - 1; cy <= y / cell + 1; cy++) {
					for (int cx = x / cell - 1; cx <= x / cell + 1; cx++) {
						if (cx < 0 || cy < 0 || cx >= cellsX || cy >= cellsY) {
							continue;
						}
						float dx = seedX[cy * cellsX + cx] - px;
						float dy = seedY[cy * cellsX + cx] - py;
						if (dx * dx + dy * dy < nearestSquared) {
							nearestSquared = dx * dx + dy * dy;
							nearest = cy * cellsX + cx;
						}
					}
				}
				// the exact distance to the nearest cell border: to the bisector between this cell's seed and each
				// neighbour's, which keeps a fissure one pixel wide and unbroken at any angle
				float border = Float.MAX_VALUE;
				for (int cy = nearest / cellsX - 2; cy <= nearest / cellsX + 2; cy++) {
					for (int cx = nearest % cellsX - 2; cx <= nearest % cellsX + 2; cx++) {
						int other = cy * cellsX + cx;
						if (cx < 0 || cy < 0 || cx >= cellsX || cy >= cellsY || other == nearest) {
							continue;
						}
						float nx = seedX[other] - seedX[nearest];
						float ny = seedY[other] - seedY[nearest];
						float length = (float) Math.sqrt(nx * nx + ny * ny);
						float mx = (seedX[other] + seedX[nearest]) / 2 - px;
						float my = (seedY[other] + seedY[nearest]) / 2 - py;
						border = Math.min(border, (mx * nx + my * ny) / length);
					}
				}
				float edge = border / this.unit;
				float depth = this.depth(i);
				if (edge < 0.5F && depth > 0.04F) {
					this.crust(i, CHAR_DEEP, 0.8F);
					// fissures run dull red at the edge of the burn and white-hot toward its heart
					boolean hot = depth > 0.5F && noise(x / (3.0F * this.unit), y / (3.0F * this.unit), this.seed + 13) > 0.45F;
					this.glow(i, depth < 0.25F ? MAGMA_DULL : hot ? MAGMA_HOT : MAGMA);
				} else if (edge < 1.4F) {
					this.crust(i, CHAR, 0.72F * this.fade(i));
					if (depth > 0.15F) {
						this.glow(i, EMBER);
					}
				} else {
					float mottle = this.mottle(i);
					this.crust(i, lerpRgb(SOOT_DARK, SOOT_LIGHT, mottle), (0.5F + 0.2F * mottle) * this.fade(i));
				}
			}
		}

		/**
		 * Flesh frozen under translucent ice: a cold blue film that whitens toward the heart of each patch, diagonal
		 * highlight streaks like vanilla ice, snow flecks and ice crystals that catch the light.
		 */
		void frost() {
			for (int k = 0; k < this.skinCount; k++) {
				int i = this.skinIndex[k];
				int x = i % this.width;
				int y = i / this.width;
				if (this.inZone(i)) {
					float thick = Math.min(1.0F, this.depth(i) * 2.2F);
					if (hash01(x, y, this.seed + 21) < 0.03F + 0.05F * thick) {
						this.crust(i, SNOW, 0.95F);
						this.glow(i, GLINT);
					} else if (Math.floorMod(x + y, 5 * this.unit) < this.unit
							&& noise(x / (2.0F * this.unit), y / (2.0F * this.unit), this.seed + 51) > 0.42F) {
						this.crust(i, ICE, 0.55F + 0.3F * thick);
						this.glow(i, STREAK_GLOW);
					} else {
						this.crust(i, lerpRgb(FROZEN_EDGE, FROZEN, thick), 0.26F + 0.3F * thick);
						// a faint cold sheen, so frost still reads in the dark
						this.glow(i, FROST_SHEEN);
					}
				} else if (this.touchesZone(x, y)) {
					this.crust(i, COLD_EDGE, 0.25F);
				}
			}
			SplittableRandom random = new SplittableRandom(this.seed + 31);
			int candidates = Math.max(1, this.skinCount / (45 * this.unit * this.unit));
			for (int c = 0; c < candidates; c++) {
				// drawn over the whole skin whatever the tier, so each tier keeps the crystals of the tiers below it
				int start = this.skinIndex[random.nextInt(this.skinCount)];
				int dx = random.nextBoolean() ? 1 : -1;
				int dy = random.nextInt(4) == 0 ? 0 : random.nextBoolean() ? 1 : -1;
				int length = (2 + random.nextInt(3)) * this.unit;
				if (!this.inZone(start) || this.depth(start) < 0.15F) {
					continue;
				}
				int x = start % this.width;
				int y = start / this.width;
				for (int step = 0; step < length; step++) {
					int px = x + dx * step;
					int py = y + dy * step;
					if (px < 0 || py < 0 || px >= this.width || py >= this.height || !this.inZone(py * this.width + px)) {
						break;
					}
					int i = py * this.width + px;
					this.crust(i, ICE, 0.95F);
					this.glow(i, step == length - 1 ? CRYSTAL_TIP : CRYSTAL_GLOW);
				}
			}
		}

		/** Rotting, sickly blotched skin broken out in glowing pustules, each trailing a vein and a drip of acid. */
		void blight() {
			for (int k = 0; k < this.skinCount; k++) {
				int i = this.skinIndex[k];
				if (this.inZone(i)) {
					float mottle = this.mottle(i);
					this.crust(i, lerpRgb(ROT, SICK, mottle), (0.25F + 0.7F * Math.abs(mottle - 0.5F)) * this.fade(i));
				}
			}
			SplittableRandom random = new SplittableRandom(this.seed + 41);
			int candidates = Math.max(1, this.skinCount / (16 * this.unit * this.unit));
			int[] placed = new int[candidates];
			int placedCount = 0;
			for (int c = 0; c < candidates; c++) {
				int center = this.skinIndex[random.nextInt(this.skinCount)];
				long own = random.nextLong();
				boolean big = random.nextInt(5) == 0;
				// spacing is decided over the whole skin, not the zone, so each tier keeps the pustules below it
				if (this.near(placed, placedCount, center, 3 * this.unit)) {
					continue;
				}
				placed[placedCount++] = center;
				if (!this.inZone(center) || this.depth(center) < 0.1F) {
					continue;
				}
				// separate streams: a vein walks further at a higher tier, and must not change the drip after it
				this.vein(center, new SplittableRandom(own));
				this.drip(center, big, new SplittableRandom(own + 1));
				this.pustule(center, big);
			}
		}

		/** A boil with a glowing core and a dark rim: one pixel across, or a round two-by-two for a big one. */
		private void pustule(int center, boolean big) {
			// a big boil is centred on a pixel corner, so its core is two by two and its rim rounds off the corners
			float offset = big ? 0.5F * this.unit : 0.0F;
			float core = 0.75F;
			float rim = big ? 1.6F : 1.5F;
			int radius = (int) Math.ceil(rim * this.unit + offset);
			int cx = center % this.width;
			int cy = center / this.width;
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dx = -radius; dx <= radius; dx++) {
					int px = cx + dx;
					int py = cy + dy;
					if (px < 0 || py < 0 || px >= this.width || py >= this.height || !this.skin[py * this.width + px]) {
						continue;
					}
					int i = py * this.width + px;
					float ox = dx - offset;
					float oy = dy - offset;
					float distance = (float) Math.sqrt(ox * ox + oy * oy) / this.unit;
					if (distance <= core) {
						this.crust(i, PUS, 1.0F);
						this.glow(i, PUS_GLOW);
					} else if (distance <= rim) {
						this.crust(i, RIM, 0.85F);
						this.glow(i, RIM_GLOW);
					}
				}
			}
		}

		/** Acid running down from under a pustule's rim (down the texture is down the body on most faces). */
		private void drip(int from, boolean big, SplittableRandom random) {
			int x = from % this.width;
			int start = from / this.width + (big ? 3 : 2) * this.unit;
			int length = (1 + random.nextInt(3)) * this.unit;
			for (int y = start; y < start + length; y++) {
				if (y >= this.height || !this.skin[y * this.width + x]) {
					return;
				}
				int i = y * this.width + x;
				this.crust(i, DRIP, 0.8F);
				this.glow(i, DRIP_GLOW);
			}
		}

		/**
		 * The soul in the chest: a glowing core in a dark ring of soul flesh, bigger by tier, with veins reaching out
		 * from it into the skin (two, three or four, longer by tier). Each vein walks its own random stream, so a
		 * higher tier only lengthens the veins of the tier below and adds more.
		 */
		void soul(Element element, Tier tier, Core core) {
			int litColor = switch (element) {
				case IGNITED -> CORE_IGNITED;
				case PERMAFROST -> CORE_PERMAFROST;
				default -> CORE_TOXIC;
			};
			int glowColor = switch (element) {
				case IGNITED -> MAGMA;
				case PERMAFROST -> CRYSTAL_TIP;
				default -> PUS_GLOW;
			};
			float cx = core.x() + core.width() * 0.5F;
			float cy = core.y() + core.height() * 0.35F;
			float coreRadius = (0.35F + 0.35F * tier.id()) * this.unit;
			float ringRadius = coreRadius + 1.0F * this.unit;
			int reach = (int) Math.ceil(ringRadius);
			for (int y = (int) cy - reach; y <= (int) cy + reach; y++) {
				for (int x = (int) cx - reach; x <= (int) cx + reach; x++) {
					if (!core.contains(x, y)) {
						continue;
					}
					int i = y * this.width + x;
					float distance = (float) Math.hypot(x + 0.5F - cx, y + 0.5F - cy);
					if (distance <= coreRadius) {
						this.crust(i, litColor, 1.0F);
						this.glow(i, glowColor);
					} else if (distance <= ringRadius) {
						this.crust(i, SOUL_FLESH, 0.95F);
						this.glow(i, dim(glowColor, 0.3F));
					}
				}
			}
			for (int v = 0; v < tier.id() + 1; v++) {
				SplittableRandom random = new SplittableRandom(this.seed + 900 + v);
				double angle = random.nextDouble() * Math.PI * 2;
				int length = (3 + 2 * tier.id()) * this.unit;
				double x = cx;
				double y = cy;
				for (int step = 0; step < length + (int) ringRadius; step++) {
					angle += (random.nextDouble() - 0.5) * 0.9;
					x += Math.cos(angle);
					y += Math.sin(angle);
					int px = (int) Math.floor(x);
					int py = (int) Math.floor(y);
					if (px < 0 || py < 0 || px >= this.width || py >= this.height) {
						break;
					}
					int i = py * this.width + px;
					if (Math.hypot(px + 0.5 - cx, py + 0.5 - cy) <= ringRadius || !this.skin[i] && !core.contains(px, py)) {
						continue;
					}
					this.crust(i, SOUL_VEIN, 0.85F);
					this.glow(i, dim(glowColor, 0.45F));
				}
			}
		}

		private void vein(int from, SplittableRandom random) {
			int x = from % this.width;
			int y = from / this.width;
			int dx = random.nextInt(3) - 1;
			int dy = dx == 0 ? (random.nextBoolean() ? 1 : -1) : random.nextInt(3) - 1;
			int length = (4 + random.nextInt(5)) * this.unit;
			for (int step = 0; step < length; step++) {
				if (random.nextInt(3) == 0) {
					// a vein wanders
					if (dx == 0) {
						x += random.nextBoolean() ? 1 : -1;
					} else {
						y += random.nextBoolean() ? 1 : -1;
					}
				}
				x += dx;
				y += dy;
				if (x < 0 || y < 0 || x >= this.width || y >= this.height || !this.inZone(y * this.width + x)) {
					return;
				}
				int i = y * this.width + x;
				this.crust(i, VEIN, 0.75F);
				this.glow(i, VEIN_GLOW);
			}
		}

		private boolean near(int[] placed, int count, int index, int spacing) {
			int x = index % this.width;
			int y = index / this.width;
			for (int p = 0; p < count; p++) {
				int dx = placed[p] % this.width - x;
				int dy = placed[p] / this.width - y;
				if (dx * dx + dy * dy < spacing * spacing) {
					return true;
				}
			}
			return false;
		}

		private boolean touchesZone(int x, int y) {
			return x > 0 && this.inZone(y * this.width + x - 1)
					|| x < this.width - 1 && this.inZone(y * this.width + x + 1)
					|| y > 0 && this.inZone((y - 1) * this.width + x)
					|| y < this.height - 1 && this.inZone((y + 1) * this.width + x);
		}

		private void crust(int i, int rgb, float alpha) {
			int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255);
			this.crust[i] = a << 24 | rgb & 0xFFFFFF;
		}

		private void glow(int i, int rgb) {
			this.glow[i] = 0xFF000000 | rgb;
		}
	}

	private static int dim(int rgb, float factor) {
		int r = Math.round((rgb >> 16 & 0xFF) * factor);
		int g = Math.round((rgb >> 8 & 0xFF) * factor);
		int b = Math.round((rgb & 0xFF) * factor);
		return r << 16 | g << 8 | b;
	}

	/** Smooth value noise in [0, 1). */
	static float noise(float x, float y, long seed) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		float fx = smooth(x - x0);
		float fy = smooth(y - y0);
		float top = lerp(fx, hash01(x0, y0, seed), hash01(x0 + 1, y0, seed));
		float bottom = lerp(fx, hash01(x0, y0 + 1, seed), hash01(x0 + 1, y0 + 1, seed));
		return lerp(fy, top, bottom);
	}

	/** A well-mixed value in [0, 1) for a lattice point. */
	static float hash01(int x, int y, long seed) {
		long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ h >>> 30) * 0xBF58476D1CE4E5B9L;
		h = (h ^ h >>> 27) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 40) / (float) (1L << 24);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	private static float lerp(float t, float from, float to) {
		return from + (to - from) * t;
	}

	private static int lerpRgb(int fromRgb, int toRgb, float t) {
		int r = Math.round(lerp(t, fromRgb >> 16 & 0xFF, toRgb >> 16 & 0xFF));
		int g = Math.round(lerp(t, fromRgb >> 8 & 0xFF, toRgb >> 8 & 0xFF));
		int b = Math.round(lerp(t, fromRgb & 0xFF, toRgb & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
