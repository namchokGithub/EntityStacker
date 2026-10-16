package dev.namchok.entitystacker.item.service;

import dev.namchok.entitystacker.config.ItemStackingConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Stores item counts above vanilla stack limits in an {@link ItemEntity}'s
 * persistent data without changing its visible {@code ItemStack}.
 *
 * <p>Invariant: the visible ItemStack count remains the vanilla count. A
 * stored logical count is authoritative only when it is greater than that
 * count and no greater than the configured maximum. Otherwise, the vanilla
 * count is the sole source of truth.</p>
 */
public final class LogicalItemCountStorage
{
    private static final String LOGICAL_ITEM_COUNT_KEY = "entitystacker.logical_item_count";

    private LogicalItemCountStorage()
    {
    }

    /**
     * Returns the validated logical count, or the visible ItemStack count when
     * no valid logical count is stored.
     */
    public static int getLogicalCount(final ItemEntity itemEntity)
    {
        int vanillaCount = itemEntity.getItem().getCount();
        CompoundTag persistentData = itemEntity.getPersistentData();

        if (!persistentData.contains(LOGICAL_ITEM_COUNT_KEY, Tag.TAG_INT))
        {
            return vanillaCount;
        }

        int logicalCount = persistentData.getInt(LOGICAL_ITEM_COUNT_KEY);
        return isValidLogicalCount(logicalCount, vanillaCount) ? logicalCount : vanillaCount;
    }

    /**
     * Returns whether this entity either has no stored logical count or has a
     * stored count that satisfies the logical-count invariant.
     *
     * <p>This is deliberately read-only so eligibility checks can reject
     * corrupt data without changing the entity while it is being inspected.</p>
     */
    public static boolean hasValidStoredLogicalCount(final ItemEntity itemEntity)
    {
        CompoundTag persistentData = itemEntity.getPersistentData();
        if (!persistentData.contains(LOGICAL_ITEM_COUNT_KEY))
        {
            return true;
        }

        if (!persistentData.contains(LOGICAL_ITEM_COUNT_KEY, Tag.TAG_INT))
        {
            return false;
        }

        return isValidLogicalCount(
                persistentData.getInt(LOGICAL_ITEM_COUNT_KEY),
                itemEntity.getItem().getCount()
        );
    }

    static boolean isLogicalCountPersistentDataKey(final String key)
    {
        return LOGICAL_ITEM_COUNT_KEY.equals(key);
    }

    /**
     * Stores a logical count without changing the ItemStack count or its NBT.
     * A count equal to the vanilla count clears the redundant stored value.
     *
     * @return {@code true} when the count was stored or cleared; {@code false}
     *         when it violates the count invariant or configured maximum
     */
    public static boolean setLogicalCount(final ItemEntity itemEntity, final int logicalCount)
    {
        int vanillaCount = itemEntity.getItem().getCount();

        if (logicalCount == vanillaCount)
        {
            clearLogicalCount(itemEntity);
            return true;
        }

        if (!isValidLogicalCount(logicalCount, vanillaCount))
        {
            return false;
        }

        itemEntity.getPersistentData().putInt(LOGICAL_ITEM_COUNT_KEY, logicalCount);
        return true;
    }

    /**
     * Removes the stored logical count so the visible ItemStack count is the
     * only item count source.
     */
    public static void clearLogicalCount(final ItemEntity itemEntity)
    {
        itemEntity.getPersistentData().remove(LOGICAL_ITEM_COUNT_KEY);
    }

    private static boolean isValidLogicalCount(final int logicalCount, final int vanillaCount)
    {
        return logicalCount > vanillaCount
                && logicalCount <= ItemStackingConfig.MAX_ITEM_STACK_SIZE.get();
    }
}
