package dev.namchok.entitystacker.item.service;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Calculates a possible logical item merge without changing either entity.
 * The logical counts and maximum are input snapshots; this planner does not
 * read or write logical-count storage or the server configuration.
 */
public final class ItemMergePlanner
{
    private ItemMergePlanner()
    {
    }

    /**
     * Plans a transfer from {@code source} into {@code destination}.
     * Eligibility, lifecycle, and ItemStack compatibility remain separate
     * checks. Counts must represent at least each visible ItemStack count and
     * must not exceed {@code maximumLogicalCount}.
     */
    public static MergePlan plan(
            final ItemEntity destination,
            final ItemEntity source,
            final int destinationLogicalCount,
            final int sourceLogicalCount,
            final int maximumLogicalCount
    )
    {
        if (destination == null || source == null)
        {
            return MergePlan.rejected("item entity is missing");
        }

        if (maximumLogicalCount <= 0)
        {
            return MergePlan.rejected("maximum logical count must be positive");
        }

        if (destination == source || destination.getUUID().equals(source.getUUID()))
        {
            return MergePlan.rejected("source and destination are the same entity");
        }

        if (destination.level() != source.level())
        {
            return MergePlan.rejected("item entities are in different levels");
        }

        ItemEntityEligibilityPolicy.EligibilityResult destinationEligibility =
                ItemEntityEligibilityPolicy.checkEligibility(destination);
        if (!destinationEligibility.eligible())
        {
            return MergePlan.rejected("destination: " + destinationEligibility.reason());
        }

        ItemEntityEligibilityPolicy.EligibilityResult sourceEligibility =
                ItemEntityEligibilityPolicy.checkEligibility(source);
        if (!sourceEligibility.eligible())
        {
            return MergePlan.rejected("source: " + sourceEligibility.reason());
        }

        ItemStack destinationStack = destination.getItem();
        ItemStack sourceStack = source.getItem();
        if (!isValidCount(destinationLogicalCount, destinationStack, maximumLogicalCount)
                || !isValidCount(sourceLogicalCount, sourceStack, maximumLogicalCount))
        {
            return MergePlan.rejected("logical count is invalid or exceeds the maximum");
        }

        ItemEntityEligibilityPolicy.EligibilityResult lifecycleCompatibility =
                ItemEntityEligibilityPolicy.checkLifecycleCompatibility(destination, source);
        if (!lifecycleCompatibility.eligible())
        {
            return MergePlan.rejected(lifecycleCompatibility.reason());
        }

        if (!ItemCompatibilityPolicy.canStack(destinationStack, sourceStack))
        {
            return MergePlan.rejected("item stacks are incompatible");
        }

        int capacity = maximumLogicalCount - destinationLogicalCount;
        int transferAmount = Math.min(capacity, sourceLogicalCount);
        return new MergePlan(
                true,
                transferAmount == 0 ? "destination is full" : "ready",
                capacity,
                transferAmount,
                destinationLogicalCount + transferAmount,
                sourceLogicalCount - transferAmount
        );
    }

    private static boolean isValidCount(final int logicalCount, final ItemStack stack, final int maximumLogicalCount)
    {
        int visibleCount = stack.getCount();
        return visibleCount > 0
                && visibleCount <= stack.getMaxStackSize()
                && logicalCount >= visibleCount
                && logicalCount <= maximumLogicalCount;
    }

    /**
     * An immutable calculation result. When {@code valid} is false, the count
     * fields are zero and only {@code reason} describes the rejection. A valid
     * plan with zero transfer means the destination is already full.
     */
    public record MergePlan(
            boolean valid,
            String reason,
            int destinationCapacity,
            int transferAmount,
            int resultingDestinationCount,
            int remainingSourceCount
    )
    {
        private static MergePlan rejected(final String reason)
        {
            return new MergePlan(false, reason, 0, 0, 0, 0);
        }
    }
}
