package io.github.foa.stackaware;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class StackHandlers {
	public static int getMaxDamage(Item item, ItemStack stack) {
		return item.getMaxDamage();
	}

	public static int getMaxCount(Item item, ItemStack stack) {
		return item.getMaxCount();
	}

	public static boolean isNbtSynced(Item item, ItemStack stack) {
		return item.isNbtSynced();
	}

	public static Item getRecipeRemainder(Item item, ItemStack stack) {
		return item.getRecipeRemainder();
	}
}
