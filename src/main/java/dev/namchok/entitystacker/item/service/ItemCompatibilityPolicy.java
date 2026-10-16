package dev.namchok.entitystacker.item.service;

import net.minecraft.world.item.ItemStack;

/**
 * Defines whether two item stacks can be represented by the same logical stack.
 */
public final class ItemCompatibilityPolicy {

    private ItemCompatibilityPolicy() {
    }

    public static boolean canStack(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }

        // Includes the item type and all item tags, including damage,
        // enchantments, and custom names.
        return ItemStack.isSameItemSameTags(a, b);
    }
}
