package com.nyr.evoluta.client;

import com.nyr.evoluta.common.forge.SoulForgeScreenHandler;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * The Soul Forge, drawn in code rather than from a texture: a dark glass panel lit in the element of whatever is on
 * the bench, the grafts listed with their stars and a bar for how well each rolled, the next graft's range, and a
 * Graft button that breathes when there is something to graft. A rag in its slot puts a scrub mark on every graft.
 */
public final class SoulForgeScreen extends HandledScreen<SoulForgeScreenHandler> {
	private static final int PANEL = 0xF0101217;
	private static final int PANEL_EDGE = 0xFF262B35;
	private static final int WELL = 0xFF0A0C10;
	private static final int TEXT = 0xFFE6E8EE;
	private static final int MUTED = 0xFF8A90A0;
	private static final int SOUL = 0xFF5FE3D0;
	private static final int LIST_X = 46;
	private static final int ROW_Y = 24;
	private static final int ROW_HEIGHT = 22;
	private static final int BUTTON_X = 164;
	private static final int BUTTON_Y = 96;
	private static final int BUTTON_W = 56;
	private static final int BUTTON_H = 18;

	public SoulForgeScreen(SoulForgeScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 230;
		this.backgroundHeight = 218;
		this.playerInventoryTitleY = SoulForgeScreenHandler.INVENTORY_Y - 11;
		this.playerInventoryTitleX = 35;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		int accent = this.accent();
		// the glass panel, a hairline edge, and the accent along the top
		context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL);
		outline(context, x, y, this.backgroundWidth, this.backgroundHeight, PANEL_EDGE);
		context.fillGradient(x + 1, y + 1, x + this.backgroundWidth - 1, y + 20, withAlpha(accent, 0x40), withAlpha(accent, 0x00));
		context.fill(x + 1, y + 1, x + this.backgroundWidth - 1, y + 2, withAlpha(accent, 0xC0));
		// the bench's three wells, the list's well, and the inventory's
		for (int[] slot : SoulForgeScreenHandler.SLOT_XY) {
			this.well(context, x + slot[0] - 1, y + slot[1] - 1, 18, 18, accent);
		}
		context.fill(x + LIST_X, y + 20, x + this.backgroundWidth - 8, y + 122, WELL);
		outline(context, x + LIST_X, y + 20, this.backgroundWidth - 8 - LIST_X, 102, PANEL_EDGE);
		for (int row = 0; row < 4; row++) {
			for (int column = 0; column < 9; column++) {
				int slotY = row < 3 ? SoulForgeScreenHandler.INVENTORY_Y + row * 18 : SoulForgeScreenHandler.INVENTORY_Y + 58;
				this.well(context, x + 34 + column * 18, y + slotY - 1, 18, 18, PANEL_EDGE);
			}
		}
		this.drawGrafts(context, x, y, mouseX, mouseY);
		this.drawNext(context, x, y, mouseX, mouseY, accent);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, 10, 7, TEXT, false);
		context.drawText(this.textRenderer, Text.translatable("container.evoluta.soul_forge.gear"), 6, 20, MUTED, false);
		context.drawText(this.textRenderer, Text.translatable("container.evoluta.soul_forge.meat"), 6, 48, MUTED, false);
		context.drawText(this.textRenderer, Text.translatable("container.evoluta.soul_forge.rag"), 6, 84, MUTED, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, MUTED, false);
	}

	private void drawGrafts(DrawContext context, int x, int y, int mouseX, int mouseY) {
		List<Graft> grafts = Grafting.grafts(this.handler.gear());
		if (grafts.isEmpty()) {
			Text hint = this.handler.gear().isEmpty()
					? Text.translatable("container.evoluta.soul_forge.empty")
					: Text.translatable("container.evoluta.soul_forge.no_grafts");
			context.drawText(this.textRenderer, hint, x + LIST_X + 6, y + ROW_Y + 6, MUTED, false);
			return;
		}
		for (int i = 0; i < grafts.size(); i++) {
			Graft graft = grafts.get(i);
			int rowY = y + ROW_Y + i * ROW_HEIGHT;
			int color = 0xFF000000 | SoulMeatItem.color(graft.element());
			// a diamond in the element's colour, the graft's name, its stars, and the roll bar
			context.fill(x + LIST_X + 6, rowY + 4, x + LIST_X + 9, rowY + 7, color);
			context.fill(x + LIST_X + 5, rowY + 5, x + LIST_X + 10, rowY + 6, color);
			Text name = Text.translatable("evoluta.element." + graft.element().key()).append(" ")
					.append(Text.translatable("evoluta.soul_kind." + graft.kind().key()));
			context.drawText(this.textRenderer, name, x + LIST_X + 14, rowY + 1, color, false);
			context.drawText(this.textRenderer, stars(graft.grade()), x + LIST_X + 14, rowY + 11, 0xFFE0B040, false);
			this.bar(context, x + LIST_X + 44, rowY + 13, 84, graft.quality(), color);
			if (this.handler.canScrub(i)) {
				boolean hover = inside(mouseX, mouseY, this.scrubX(x), rowY + 4, 10, 10);
				context.fill(this.scrubX(x), rowY + 4, this.scrubX(x) + 10, rowY + 14, hover ? 0xFF5A2430 : 0xFF2A1418);
				context.drawText(this.textRenderer, "×", this.scrubX(x) + 2, rowY + 5, hover ? 0xFFFF8090 : 0xFFC06070, false);
			}
		}
	}

	/** The next graft's range, and the Graft button, breathing in the meat's colour when it can be pressed. */
	private void drawNext(DrawContext context, int x, int y, int mouseX, int mouseY, int accent) {
		ItemStack meat = this.handler.meat();
		boolean ready = this.handler.canGraft();
		if (meat.getItem() instanceof SoulMeatItem soulMeat && !this.handler.gear().isEmpty()) {
			Tier grade = SoulMeatItem.grade(meat);
			Text range = Text.translatable("container.evoluta.soul_forge.range", Math.round(Graft.minPower(grade) * 100),
					Math.round(Graft.maxPower(grade) * 100));
			Text next = Grafting.canGraft(this.handler.gear()) ? range : Text.translatable("container.evoluta.soul_forge.full");
			context.drawText(this.textRenderer, next, x + LIST_X + 6, y + BUTTON_Y + 5, ready ? withAlpha(accentOf(soulMeat.element()), 0xFF) : MUTED, false);
		}
		float breath = ready ? 0.5F + 0.5F * MathHelper.sin(Util.getMeasuringTimeMs() / 280.0F) : 0.0F;
		boolean hover = ready && inside(mouseX, mouseY, x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H);
		int fill = ready ? withAlpha(accent, (int) (0x50 + 0x50 * breath) + (hover ? 0x30 : 0)) : 0xFF1A1D24;
		context.fill(x + BUTTON_X, y + BUTTON_Y, x + BUTTON_X + BUTTON_W, y + BUTTON_Y + BUTTON_H, fill);
		outline(context, x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H, ready ? withAlpha(accent, 0xFF) : PANEL_EDGE);
		Text label = Text.translatable("container.evoluta.soul_forge.graft");
		context.drawText(this.textRenderer, label, x + BUTTON_X + (BUTTON_W - this.textRenderer.getWidth(label)) / 2, y + BUTTON_Y + 5,
				ready ? TEXT : MUTED, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && this.client != null && this.client.interactionManager != null) {
			if (this.handler.canGraft() && inside(mouseX, mouseY, this.x + BUTTON_X, this.y + BUTTON_Y, BUTTON_W, BUTTON_H)) {
				return this.press(SoulForgeScreenHandler.GRAFT);
			}
			int grafts = Grafting.grafts(this.handler.gear()).size();
			for (int i = 0; i < grafts; i++) {
				if (this.handler.canScrub(i) && inside(mouseX, mouseY, this.scrubX(this.x), this.y + ROW_Y + i * ROW_HEIGHT + 4, 10, 10)) {
					return this.press(SoulForgeScreenHandler.SCRUB + i);
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean press(int id) {
		this.client.interactionManager.clickButton(this.handler.syncId, id);
		this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		return true;
	}

	/** The accent: the meat on the bench, else the gear's strongest graft, else soul cyan. */
	private int accent() {
		if (this.handler.meat().getItem() instanceof SoulMeatItem meat) {
			return accentOf(meat.element());
		}
		List<Grafting.Weighted> grafts = Grafting.weighted(Grafting.grafts(this.handler.gear()));
		return grafts.isEmpty() ? SOUL : accentOf(grafts.get(0).graft().element());
	}

	private static int accentOf(Element element) {
		return 0xFF000000 | SoulMeatItem.color(element);
	}

	private int scrubX(int x) {
		return x + this.backgroundWidth - 22;
	}

	private void well(DrawContext context, int x, int y, int width, int height, int edge) {
		context.fill(x, y, x + width, y + height, WELL);
		outline(context, x, y, width, height, withAlpha(edge, 0x90));
	}

	private void bar(DrawContext context, int x, int y, int width, float fill, int color) {
		context.fill(x, y, x + width, y + 3, 0xFF1C2028);
		int filled = Math.max(1, Math.round(width * MathHelper.clamp(fill, 0.0F, 1.0F)));
		context.fillGradient(x, y, x + filled, y + 3, withAlpha(color, 0xFF), withAlpha(color, 0x90));
	}

	private static void outline(DrawContext context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y, x + 1, y + height, color);
		context.fill(x + width - 1, y, x + width, y + height, color);
	}

	private static int withAlpha(int color, int alpha) {
		return MathHelper.clamp(alpha, 0, 255) << 24 | color & 0xFFFFFF;
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private static String stars(Tier grade) {
		return switch (grade) {
			case EVOLVED -> "★☆☆";
			case ELITE -> "★★☆";
			case APEX -> "★★★";
		};
	}
}
