package com.nyr.evoluta.common.forge;

import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * The Soul Forge's workings: a gear slot, a Soul Meat slot and a rag slot, like a smithing table's, with two actions
 * sent as vanilla button clicks. Graft (button {@link #GRAFT}) rolls the meat's power on the server there and then,
 * so no preview can be rerolled by taking items in and out. Scrub (button {@link #SCRUB} + index) uses one of the
 * rag's three uses to take that graft off. Nothing is stored in the block: closing hands the items back.
 */
public final class SoulForgeScreenHandler extends ScreenHandler {
	public static final int GEAR = 0;
	public static final int MEAT = 1;
	public static final int RAG = 2;
	public static final int GRAFT = 0;
	/** Scrub buttons are {@code SCRUB + graft index}. */
	public static final int SCRUB = 10;
	/** Where the three forge slots sit, for the screen that draws round them. */
	public static final int[][] SLOT_XY = {{20, 30}, {20, 58}, {20, 94}};
	public static final int INVENTORY_Y = 134;

	private final Inventory forge = new SimpleInventory(3) {
		@Override
		public void markDirty() {
			super.markDirty();
			SoulForgeScreenHandler.this.onContentChanged(this);
		}
	};
	private final ScreenHandlerContext context;

	/** Client side: the server holds the real forge. */
	public SoulForgeScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
	}

	public SoulForgeScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
		super(SoulForge.SCREEN, syncId);
		this.context = context;
		this.addSlot(new Slot(this.forge, GEAR, SLOT_XY[GEAR][0], SLOT_XY[GEAR][1]) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return Grafting.isWeapon(stack) || Grafting.isArmor(stack) || Grafting.isTool(stack);
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});
		this.addSlot(new Slot(this.forge, MEAT, SLOT_XY[MEAT][0], SLOT_XY[MEAT][1]) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof SoulMeatItem;
			}
		});
		this.addSlot(new Slot(this.forge, RAG, SLOT_XY[RAG][0], SLOT_XY[RAG][1]) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.isOf(EvolutaItems.DIAMOND_SILK_RAG);
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 35 + column * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(playerInventory, column, 35 + column * 18, INVENTORY_Y + 58));
		}
	}

	public ItemStack gear() {
		return this.forge.getStack(GEAR);
	}

	public ItemStack meat() {
		return this.forge.getStack(MEAT);
	}

	public ItemStack rag() {
		return this.forge.getStack(RAG);
	}

	/** Whether the Graft button does anything right now. */
	public boolean canGraft() {
		return Grafting.canGraft(this.gear()) && this.meat().getItem() instanceof SoulMeatItem;
	}

	/** Whether graft {@code index} can be scrubbed right now. */
	public boolean canScrub(int index) {
		return this.rag().isOf(EvolutaItems.DIAMOND_SILK_RAG) && index >= 0 && index < Grafting.grafts(this.gear()).size();
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id == GRAFT && this.canGraft()) {
			SoulMeatItem meat = (SoulMeatItem) this.meat().getItem();
			var grade = SoulMeatItem.grade(this.meat());
			float power = Graft.roll(grade, player.getRandom());
			this.forge.setStack(GEAR, Grafting.graft(this.gear(), meat, grade, power));
			this.meat().decrement(1);
			this.forge.markDirty();
			this.context.run((world, pos) -> world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.8F, 0.8F + power * 0.4F));
			return true;
		}
		if (id >= SCRUB && this.canScrub(id - SCRUB)) {
			this.forge.setStack(GEAR, Grafting.scrub(this.gear(), id - SCRUB));
			ItemStack rag = this.rag();
			rag.setDamage(rag.getDamage() + 1);
			if (rag.getDamage() >= rag.getMaxDamage()) {
				this.forge.setStack(RAG, ItemStack.EMPTY);
				this.context.run((world, pos) -> world.playSound(null, pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 0.8F, 1.0F));
			}
			this.forge.markDirty();
			this.context.run((world, pos) -> world.playSound(null, pos, SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.BLOCKS, 1.0F, 0.9F));
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		if (index < 3) {
			if (!this.insertItem(stack, 3, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof SoulMeatItem) {
			if (!this.insertItem(stack, MEAT, MEAT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.isOf(EvolutaItems.DIAMOND_SILK_RAG)) {
			if (!this.insertItem(stack, RAG, RAG + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (this.slots.get(GEAR).canInsert(stack) && !this.slots.get(GEAR).hasStack()) {
			if (!this.insertItem(stack, GEAR, GEAR + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return original;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(this.context, player, SoulForge.BLOCK);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		this.context.run((world, pos) -> this.dropInventory(player, this.forge));
	}
}
