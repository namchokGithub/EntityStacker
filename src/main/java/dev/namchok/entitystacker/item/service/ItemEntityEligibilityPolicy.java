package dev.namchok.entitystacker.item.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Defines the conservative conditions required before a dropped item may be
 * considered by future stacking logic. This class only inspects entities; it
 * never changes them.
 *
 * <p>Only the direct vanilla {@link ItemEntity} implementation with the
 * vanilla {@link EntityType#ITEM} type is eligible. Unknown Forge persistent
 * data is rejected because it may represent mod-specific state. Forge does not
 * provide a general, side-effect-free way to enumerate arbitrary capabilities
 * attached by other mods, so an otherwise vanilla-looking item with unexposed
 * mod capability state cannot be detected reliably. Such integrations require
 * an explicit compatibility rule before stacking is enabled for them.</p>
 */
public final class ItemEntityEligibilityPolicy
{
    public static final String DEBUG_ENTITY_MARKER_KEY = "entitystacker.debug_logical_item_count";
    private static final int VANILLA_ITEM_LIFESPAN_TICKS = 6000;

    private ItemEntityEligibilityPolicy()
    {
    }

    /**
     * Evaluates basic safety requirements for one item entity. Age is not part
     * of this result; use {@link #checkLifecycleCompatibility(ItemEntity,
     * ItemEntity)} when evaluating a pair.
     */
    public static EligibilityResult checkEligibility(final ItemEntity itemEntity)
    {
        if (!itemEntity.isAlive())
        {
            return EligibilityResult.ineligible("entity is not alive");
        }

        Level level = itemEntity.level();
        if (level.isClientSide)
        {
            return EligibilityResult.ineligible("entity is client-side");
        }

        if (!level.isLoaded(itemEntity.blockPosition()))
        {
            return EligibilityResult.ineligible("entity position is not in a loaded chunk");
        }

        if (itemEntity.getClass() != ItemEntity.class || itemEntity.getType() != EntityType.ITEM)
        {
            return EligibilityResult.ineligible("entity is not a vanilla ItemEntity");
        }

        ItemStack itemStack = itemEntity.getItem();
        if (itemStack.isEmpty())
        {
            return EligibilityResult.ineligible("item stack is empty");
        }

        if (itemStack.getItem().hasCustomEntity(itemStack))
        {
            return EligibilityResult.ineligible("item declares a custom item entity");
        }

        CompoundTag persistentData = itemEntity.getPersistentData();
        if (persistentData.getBoolean(DEBUG_ENTITY_MARKER_KEY))
        {
            return EligibilityResult.ineligible("entity is a temporary debug item");
        }

        if (!LogicalItemCountStorage.hasValidStoredLogicalCount(itemEntity))
        {
            return EligibilityResult.ineligible("logical item count is missing, invalid, or corrupt");
        }

        if (persistentData.getAllKeys().stream()
                .anyMatch(key -> !LogicalItemCountStorage.isLogicalCountPersistentDataKey(key)))
        {
            return EligibilityResult.ineligible("entity has unsupported persistent data");
        }

        if (itemEntity.hasPickUpDelay())
        {
            return EligibilityResult.ineligible("entity has a pickup delay");
        }

        if (itemEntity.isInvulnerable() || itemEntity.isNoGravity())
        {
            return EligibilityResult.ineligible("entity has an unsafe custom state");
        }

        CompoundTag serializedEntityData = itemEntity.saveWithoutId(new CompoundTag());
        if (serializedEntityData.hasUUID("Owner"))
        {
            return EligibilityResult.ineligible("entity has an ownership restriction");
        }

        if (itemEntity.lifespan != VANILLA_ITEM_LIFESPAN_TICKS)
        {
            return EligibilityResult.ineligible("entity has a nonstandard lifespan");
        }

        return EligibilityResult.allowed();
    }

    /**
     * Checks pair-specific lifecycle constraints independently from basic
     * eligibility. Equal ages preserve the point at which either entity would
     * naturally expire, while equal lifespans prevent custom expiry behavior
     * from being combined.
     */
    public static EligibilityResult checkLifecycleCompatibility(
            final ItemEntity firstItemEntity,
            final ItemEntity secondItemEntity
    )
    {
        if (firstItemEntity.getAge() != secondItemEntity.getAge())
        {
            return EligibilityResult.ineligible("item entity ages differ");
        }

        if (firstItemEntity.lifespan != secondItemEntity.lifespan)
        {
            return EligibilityResult.ineligible("item entity lifespans differ");
        }

        return EligibilityResult.allowed();
    }

    /** Result of a non-mutating eligibility or lifecycle check. */
    public record EligibilityResult(boolean eligible, String reason)
    {
        private static final String ELIGIBLE_REASON = "eligible";

        private static EligibilityResult allowed()
        {
            return new EligibilityResult(true, ELIGIBLE_REASON);
        }

        private static EligibilityResult ineligible(final String reason)
        {
            return new EligibilityResult(false, reason);
        }
    }
}
