package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.mutation.Element;
import java.util.SplittableRandom;

/**
 * Paints each element's enchantment in two layers that move differently over the gear:
 * <ul>
 * <li>{@link Layer#SHEEN}: broad, soft bands of light sweeping over the item, bright at their heart, in the element's
 * colours; the element shapes the bands: licking heat along fire's edges, crystal facets through frost's, a gloopy
 * wobble in toxic's;</li>
 * <li>{@link Layer#DETAIL}: soft glowing detail drifting its own way over the sheen: rising wisps and embers (Ignited),
 * twinkling glints and needles of ice (Permafrost), bubbles and drips (Toxic).</li>
 * </ul>
 * Between the light the texture is black, which the additive glint shader leaves untouched, so the item keeps its own
 * look. Apex patterns are brighter and busier. Pure pixel maths on a square texture that tiles both ways (every wave
 * has a whole number of cycles across it, every shape wraps); a pixel is the light the glint adds (the shader squares
 * it). {@link GlintMotion} moves the layers.
 */
public final class GlintArt {
	/** Twice vanilla's 128, for smooth gradients however far the shader magnifies them. */
	public static final int SIZE = 256;

	public enum Layer {
		SHEEN,
		DETAIL
	}

	private GlintArt() {
	}

	/** ARGB, opaque, {@code size} x {@code size}, tiling seamlessly; null for an element with no glint of its own. */
	public static int[] paint(Element element, Layer layer, boolean apex, int size, long seed) {
		int[] palette = palette(element);
		if (palette == null) {
			return null;
		}
		float[] light = layer == Layer.SHEEN ? sheen(element, apex, size, seed) : detail(element, apex, size, seed);
		int[] pixels = new int[size * size];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = color(Math.min(1.0F, light[i]), palette);
		}
		return pixels;
	}

	/**
	 * Two waves of light across the texture, a broad soft one and a thin bright one at another angle, warped by the
	 * element and made uneven by slow noise. Whole numbers of cycles across the texture keep it tiling.
	 */
	private static float[] sheen(Element element, boolean apex, int size, long seed) {
		float[] light = new float[size * size];
		float[][] facets = element == Element.PERMAFROST ? facets(size, apex ? 9 : 7, seed) : null;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float fx = x / (float) size;
				float fy = y / (float) size;
				float warp = switch (element) {
					// heat: long vertical licks along the band's edges
					case IGNITED -> 0.09F * (fbm(fx * 5, fy * 2, 5, 2, 3, seed) - 0.5F) + 0.02F * (fbm(fx * 8, fy * 4, 8, 4, 2, seed + 3) - 0.5F);
					// ice: nearly straight, broken at the crystals' edges
					case PERMAFROST -> 0.025F * (fbm(fx * 6, fy * 6, 6, 6, 2, seed) - 0.5F);
					// ooze: slow, round, gloopy wobble
					default -> 0.11F * (fbm(fx * 3, fy * 3, 3, 3, 3, seed) - 0.5F);
				};
				float broad = wave(4 * fx + 6 * fy + warp, 2.2F);
				// a faint second band across the first, so light is always passing somewhere over the item
				float cross = wave(-5 * fx + 4 * fy + warp * 0.8F, 3.0F);
				float bright = wave(8 * fx - 3 * fy + warp * 1.6F, apex ? 10.0F : 12.0F);
				float uneven = 0.6F + 0.4F * fbm(fx * 4, fy * 4, 4, 4, 2, seed + 17);
				float value = (0.45F * broad + 0.22F * cross + 0.6F * bright) * uneven;
				switch (element) {
					case IGNITED -> value += 0.16F * Math.max(0.0F, fbm(fx * 6, fy * 3, 6, 3, 2, seed + 29) - 0.55F) * broad * 4.0F;
					case PERMAFROST -> value *= 0.78F + 0.44F * facets[y][x];
					default -> value *= 0.75F + 0.5F * fbm(fx * 12, fy * 12, 12, 12, 2, seed + 29);
				}
				// held below white where bands cross, so the brightest moment keeps its colour
				light[y * size + x] = value * (apex ? 0.95F : 0.82F);
			}
		}
		return light;
	}

	/** Soft glowing detail over the sheen: wisps and embers, glints and needles of ice, bubbles and drips. */
	private static float[] detail(Element element, boolean apex, int size, long seed) {
		float[] light = new float[size * size];
		SplittableRandom random = new SplittableRandom(seed);
		float unit = size / 128.0F;
		switch (element) {
			case IGNITED -> {
				int wisps = apex ? 70 : 48;
				for (int i = 0; i < wisps; i++) {
					float x = random.nextFloat() * size;
					float root = random.nextFloat() * size;
					float height = (8.0F + random.nextFloat() * 12.0F) * unit;
					float width = (1.2F + random.nextFloat() * 1.6F) * unit;
					float phase = random.nextFloat() * 6.28F;
					for (int up = 0; up < height; up++) {
						float t = up / height;
						float half = width * (1.0F - t);
						float center = x + 1.2F * unit * (float) Math.sin(t * 4.0F + phase) * t;
						for (int px = (int) Math.floor(center - half - 1); px <= Math.ceil(center + half + 1); px++) {
							float across = Math.abs(px + 0.5F - center) / Math.max(half, 0.6F);
							if (across < 1.0F) {
								brighten(light, size, px, Math.round(root - up), (1.0F - across) * (1.0F - t) * 0.85F);
							}
						}
					}
				}
				int embers = apex ? 90 : 60;
				for (int i = 0; i < embers; i++) {
					glow(light, size, random.nextFloat() * size, random.nextFloat() * size, (0.8F + random.nextFloat()) * unit, 1.0F);
				}
				blur(light, size, Math.round(unit));
			}
			case PERMAFROST -> {
				int glints = apex ? 46 : 30;
				for (int i = 0; i < glints; i++) {
					float x = random.nextFloat() * size;
					float y = random.nextFloat() * size;
					float ray = (2.5F + random.nextFloat() * 4.0F) * unit;
					// a four-pointed glint, the long rays level and upright, the short ones on the diagonals
					stroke(light, size, x - ray, y, x + ray, y, 0.35F * unit, 1.0F);
					stroke(light, size, x, y - ray, x, y + ray, 0.35F * unit, 1.0F);
					stroke(light, size, x - ray * 0.45F, y - ray * 0.45F, x + ray * 0.45F, y + ray * 0.45F, 0.3F * unit, 0.7F);
					stroke(light, size, x - ray * 0.45F, y + ray * 0.45F, x + ray * 0.45F, y - ray * 0.45F, 0.3F * unit, 0.7F);
					glow(light, size, x, y, 1.2F * unit, 1.0F);
				}
				int needles = apex ? 50 : 32;
				for (int i = 0; i < needles; i++) {
					float x = random.nextFloat() * size;
					float y = random.nextFloat() * size;
					float length = (4.0F + random.nextFloat() * 7.0F) * unit;
					double angle = random.nextInt(3) * Math.PI / 3;
					stroke(light, size, x, y, x + length * (float) Math.cos(angle), y + length * (float) Math.sin(angle), 0.3F * unit, 0.55F);
				}
				blur(light, size, 1);
			}
			default -> {
				int bubbles = apex ? 55 : 36;
				for (int i = 0; i < bubbles; i++) {
					float cx = random.nextFloat() * size;
					float cy = random.nextFloat() * size;
					float radius = (1.4F + random.nextFloat() * 2.4F) * unit;
					int reach = (int) Math.ceil(radius + 2);
					for (int oy = -reach; oy <= reach; oy++) {
						for (int ox = -reach; ox <= reach; ox++) {
							float d = (float) Math.sqrt(ox * ox + oy * oy);
							float rim = 1.0F - Math.abs(d - radius) / (0.9F * unit);
							float value = Math.max(rim * 0.85F, d < radius ? 0.18F : 0.0F);
							if (value > 0) {
								brighten(light, size, Math.round(cx) + ox, Math.round(cy) + oy, value);
							}
						}
					}
					glow(light, size, cx - radius * 0.45F, cy - radius * 0.45F, 0.7F * unit, 1.0F);
				}
				int drips = apex ? 40 : 26;
				for (int i = 0; i < drips; i++) {
					float x = random.nextFloat() * size;
					float top = random.nextFloat() * size;
					float length = (5.0F + random.nextFloat() * 10.0F) * unit;
					float width = (0.6F + random.nextFloat() * 0.4F) * unit;
					stroke(light, size, x, top, x, top + length, width, 0.55F);
					glow(light, size, x, top + length + unit, width + 1.1F * unit, 0.95F);
				}
				blur(light, size, Math.round(unit));
			}
		}
		for (int i = 0; i < light.length; i++) {
			light[i] = Math.min(1.0F, light[i] * (apex ? 1.1F : 0.95F));
		}
		return light;
	}

	/** A soft wave: 1 on its crest, falling off as sharply as {@code sharpness} says; a whole cycle per unit of phase. */
	private static float wave(float phase, float sharpness) {
		return (float) Math.pow(0.5 + 0.5 * Math.cos(Math.PI * 2 * phase), sharpness);
	}

	/**
	 * Crystal facets for the frost sheen: each crystal (one per grid cell, anywhere in it) lit from one side, 0 to 1,
	 * the shade jumping at the crystal edges.
	 */
	private static float[][] facets(int size, int cells, long seed) {
		SplittableRandom random = new SplittableRandom(seed + 41);
		float[][] points = new float[cells * cells][2];
		for (int i = 0; i < points.length; i++) {
			points[i][0] = ((i % cells) + random.nextFloat()) / cells;
			points[i][1] = ((i / cells) + random.nextFloat()) / cells;
		}
		float[][] shade = new float[size][size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float px = x / (float) size;
				float py = y / (float) size;
				float nearest = Float.MAX_VALUE;
				float dx = 0;
				float dy = 0;
				for (float[] point : points) {
					float ox = wrap(point[0] - px);
					float oy = wrap(point[1] - py);
					float d = ox * ox + oy * oy;
					if (d < nearest) {
						nearest = d;
						dx = ox;
						dy = oy;
					}
				}
				shade[y][x] = Math.max(0.0F, Math.min(1.0F, 0.5F + (dx - dy) * cells * 0.9F));
			}
		}
		return shade;
	}

	/** A line from (x0, y0) to (x1, y1), soft-edged, {@code width} pixels either side of its middle. */
	private static void stroke(float[] light, int size, float x0, float y0, float x1, float y1, float width, float value) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length2 = Math.max(1.0E-4F, dx * dx + dy * dy);
		int minX = (int) Math.floor(Math.min(x0, x1) - width - 2);
		int maxX = (int) Math.ceil(Math.max(x0, x1) + width + 2);
		int minY = (int) Math.floor(Math.min(y0, y1) - width - 2);
		int maxY = (int) Math.ceil(Math.max(y0, y1) + width + 2);
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float t = Math.max(0.0F, Math.min(1.0F, ((x + 0.5F - x0) * dx + (y + 0.5F - y0) * dy) / length2));
				float ox = x + 0.5F - (x0 + dx * t);
				float oy = y + 0.5F - (y0 + dy * t);
				float edge = 1.0F - (float) Math.sqrt(ox * ox + oy * oy) / (width + 0.8F);
				// brightest at the middle of the line, fading toward its ends
				float taper = 1.0F - Math.abs(t - 0.5F) * 1.2F;
				if (edge > 0) {
					brighten(light, size, x, y, value * Math.min(1.0F, edge * 1.3F) * taper);
				}
			}
		}
	}

	/** A soft round point of light. */
	private static void glow(float[] light, int size, float x, float y, float radius, float value) {
		int reach = (int) Math.ceil(radius) + 2;
		for (int oy = -reach; oy <= reach; oy++) {
			for (int ox = -reach; ox <= reach; ox++) {
				float d = (float) Math.sqrt(ox * ox + oy * oy);
				float falloff = 1.0F - d / (radius + 1.0F);
				if (falloff > 0) {
					brighten(light, size, (int) Math.floor(x) + ox, (int) Math.floor(y) + oy, value * falloff * falloff);
				}
			}
		}
	}

	/** Two box blurs of {@code radius} each way, wrapping: close to a gaussian, and no pixel edge survives it. */
	private static void blur(float[] light, int size, int radius) {
		if (radius <= 0) {
			return;
		}
		float[] scratch = new float[light.length];
		for (int pass = 0; pass < 2; pass++) {
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float sum = 0;
					for (int k = -radius; k <= radius; k++) {
						sum += light[y * size + Math.floorMod(x + k, size)];
					}
					scratch[y * size + x] = sum / (2 * radius + 1);
				}
			}
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float sum = 0;
					for (int k = -radius; k <= radius; k++) {
						sum += scratch[Math.floorMod(y + k, size) * size + x];
					}
					light[y * size + x] = sum / (2 * radius + 1);
				}
			}
		}
		// a blur dims thin shapes: bring their cores back up
		for (int i = 0; i < light.length; i++) {
			light[i] = Math.min(1.0F, light[i] * 1.8F);
		}
	}

	/** Lights a pixel to at least {@code value}, wrapping round the edges. */
	private static void brighten(float[] light, int size, int x, int y, float value) {
		int index = Math.floorMod(y, size) * size + Math.floorMod(x, size);
		light[index] = Math.max(light[index], value);
	}

	/** Palettes: light 0 to 1 through these stops, black first (black adds nothing), white at the heart. */
	private static final int[] FIRE = {0x000000, 0x3A0600, 0xA01800, 0xFF5A00, 0xFFA530, 0xFFE9B0, 0xFFFFFF};
	private static final int[] FROST = {0x000000, 0x031A40, 0x0A5AB0, 0x2AB8FF, 0x9AEBFF, 0xE8FFFF, 0xFFFFFF};
	private static final int[] OOZE = {0x000000, 0x0B2800, 0x2E7D00, 0x6FE000, 0xB8FF4A, 0xEEFFB0, 0xFFFFFF};

	private static int[] palette(Element element) {
		return switch (element) {
			case IGNITED -> FIRE;
			case PERMAFROST -> FROST;
			case TOXIC -> OOZE;
			default -> null;
		};
	}

	private static int color(float light, int[] palette) {
		float at = light * (palette.length - 1);
		int stop = Math.min(palette.length - 2, (int) at);
		float t = at - stop;
		int from = palette[stop];
		int to = palette[stop + 1];
		int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	/** Fractal value noise on a torus: {@code periodX} by {@code periodY} cells across the whole texture, so it tiles. */
	static float fbm(float x, float y, int periodX, int periodY, int octaves, long seed) {
		float sum = 0;
		float amplitude = 0.5F;
		float total = 0;
		for (int octave = 0; octave < octaves; octave++) {
			sum += amplitude * noise(x, y, periodX, periodY, seed + octave * 101L);
			total += amplitude;
			x *= 2;
			y *= 2;
			periodX *= 2;
			periodY *= 2;
			amplitude *= 0.5F;
		}
		return sum / total;
	}

	private static float noise(float x, float y, int periodX, int periodY, long seed) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		float fx = x - x0;
		float fy = y - y0;
		float u = fx * fx * (3 - 2 * fx);
		float v = fy * fy * (3 - 2 * fy);
		float a = hash(x0, y0, periodX, periodY, seed);
		float b = hash(x0 + 1, y0, periodX, periodY, seed);
		float c = hash(x0, y0 + 1, periodX, periodY, seed);
		float d = hash(x0 + 1, y0 + 1, periodX, periodY, seed);
		float top = a + (b - a) * u;
		float bottom = c + (d - c) * u;
		return top + (bottom - top) * v;
	}

	private static float hash(int x, int y, int periodX, int periodY, long seed) {
		long h = seed * 0x9E3779B97F4A7C15L + Math.floorMod(x, periodX) * 0xC2B2AE3D27D4EB4FL + Math.floorMod(y, periodY) * 0x165667B19E3779F9L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h >>> 40) / (float) (1L << 24);
	}

	/** The shortest offset on a torus of side 1. */
	private static float wrap(float d) {
		return d - Math.round(d);
	}
}
