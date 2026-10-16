package dev.namchok.entitystacker.item;

import com.mojang.logging.LogUtils;
import dev.namchok.entitystacker.config.ItemStackingConfig;
import dev.namchok.entitystacker.item.service.ItemCompatibilityPolicy;
import dev.namchok.entitystacker.item.service.ItemEntityEligibilityPolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds dropped item entities near active players without loading chunks.
 */
public final class ItemEntityDetector
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DETECTION_RADIUS = 32.0D;
    private static final int MAX_ITEM_ENTITIES_PER_SCAN = 128;
    private static final int MAX_DETECTED_ITEM_LOG_MESSAGES_PER_SCAN = 10;
    private static final int MAX_INELIGIBLE_ITEM_LOG_MESSAGES_PER_SCAN = 10;
    private static final int MAX_PAIR_LOG_MESSAGES_PER_SCAN = 10;
    private static final long DIAGNOSTIC_LOG_INTERVAL_TICKS = 200L;
    private static final long INELIGIBLE_LOG_REPEAT_TICKS = 1200L;
    private static final long INELIGIBLE_LOG_STATE_RETENTION_TICKS = 2400L;
    private static final Map<UUID, IneligibleLogState> INELIGIBLE_LOG_STATES = new HashMap<>();
    private static long lastDiagnosticLogGameTime = Long.MIN_VALUE;

    private ItemEntityDetector()
    {
    }

    /**
     * Logs detected dropped items around online players. Entity lookups only
     * read currently loaded entity sections and do not force chunk loading.
     */
    public static void logNearbyItems(final MinecraftServer server)
    {
        Set<UUID> inspectedItemIds = new HashSet<>();
        List<ItemEntity> detectedItems = new ArrayList<>();
        long gameTime = server.overworld().getGameTime();
        int loggedItemCount = 0;
        int loggedIneligibleItemCount = 0;

        pruneIneligibleLogStates(gameTime);

        playerLoop:
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            AABB searchArea = player.getBoundingBox().inflate(DETECTION_RADIUS);
            List<ItemEntity> itemEntities = player.serverLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    searchArea,
                    ItemEntity::isAlive
            );

            for (ItemEntity itemEntity : itemEntities)
            {
                if (!inspectedItemIds.add(itemEntity.getUUID()))
                {
                    continue;
                }

                ItemStack itemStack = itemEntity.getItem();
                ItemEntityEligibilityPolicy.EligibilityResult eligibility =
                        ItemEntityEligibilityPolicy.checkEligibility(itemEntity);
                if (!eligibility.eligible())
                {
                    if (shouldLogIneligibleItem(itemEntity.getUUID(), eligibility.reason(), gameTime)
                            && loggedIneligibleItemCount < MAX_INELIGIBLE_ITEM_LOG_MESSAGES_PER_SCAN)
                    {
                        LOGGER.info(
                                "Skipped dropped item: reason={}, id={}, position=({}, {}, {})",
                                eligibility.reason(),
                                ForgeRegistries.ITEMS.getKey(itemStack.getItem()),
                                itemEntity.getX(),
                                itemEntity.getY(),
                                itemEntity.getZ()
                        );
                        loggedIneligibleItemCount++;
                        markIneligibleItemLogged(itemEntity.getUUID(), eligibility.reason(), gameTime);
                    }
                    continue;
                }

                INELIGIBLE_LOG_STATES.remove(itemEntity.getUUID());

                detectedItems.add(itemEntity);

                if (detectedItems.size() >= MAX_ITEM_ENTITIES_PER_SCAN)
                {
                    break playerLoop;
                }

                if (loggedItemCount < MAX_DETECTED_ITEM_LOG_MESSAGES_PER_SCAN)
                {
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
                    LOGGER.info(
                            "Detected dropped item: id={}, count={}, position=({}, {}, {})",
                            itemId,
                            itemStack.getCount(),
                            itemEntity.getX(),
                            itemEntity.getY(),
                            itemEntity.getZ()
                    );
                    loggedItemCount++;
                }
            }
        }

        PairScanCounts pairCounts = logNearbyItemPairs(detectedItems);
        if (!inspectedItemIds.isEmpty()
                && (lastDiagnosticLogGameTime == Long.MIN_VALUE
                || gameTime < lastDiagnosticLogGameTime
                || gameTime - lastDiagnosticLogGameTime >= DIAGNOSTIC_LOG_INTERVAL_TICKS))
        {
            LOGGER.info(
                    "Item scan diagnostic: scanned={}, eligible={}, nearbyPairs={}, compatibilityChecks={}",
                    inspectedItemIds.size(),
                    detectedItems.size(),
                    pairCounts.nearbyPairs(),
                    pairCounts.compatibilityChecks()
            );
            lastDiagnosticLogGameTime = gameTime;
        }
    }

    private static boolean shouldLogIneligibleItem(
            final UUID itemId,
            final String reason,
            final long gameTime
    )
    {
        IneligibleLogState logState = INELIGIBLE_LOG_STATES.computeIfAbsent(
                itemId,
                ignored -> new IneligibleLogState(gameTime)
        );
        logState.lastSeenGameTime = gameTime;

        return logState.reason == null
                || !logState.reason.equals(reason)
                || gameTime - logState.lastLoggedGameTime >= INELIGIBLE_LOG_REPEAT_TICKS;
    }

    private static void markIneligibleItemLogged(
            final UUID itemId,
            final String reason,
            final long gameTime
    )
    {
        IneligibleLogState logState = INELIGIBLE_LOG_STATES.get(itemId);
        if (logState != null)
        {
            logState.reason = reason;
            logState.lastLoggedGameTime = gameTime;
        }
    }

    private static void pruneIneligibleLogStates(final long gameTime)
    {
        Iterator<Map.Entry<UUID, IneligibleLogState>> iterator = INELIGIBLE_LOG_STATES.entrySet().iterator();
        while (iterator.hasNext())
        {
            IneligibleLogState logState = iterator.next().getValue();
            if (gameTime - logState.lastSeenGameTime >= INELIGIBLE_LOG_STATE_RETENTION_TICKS)
            {
                iterator.remove();
            }
        }
    }

    private static PairScanCounts logNearbyItemPairs(final List<ItemEntity> detectedItems)
    {
        double itemStackRadius = ItemStackingConfig.ITEM_STACK_RADIUS.get();
        double itemStackRadiusSquared = itemStackRadius * itemStackRadius;
        int loggedPairCount = 0;
        int nearbyPairCount = 0;
        int compatibilityCheckCount = 0;

        for (int firstIndex = 0; firstIndex < detectedItems.size() - 1; firstIndex++)
        {
            ItemEntity firstItemEntity = detectedItems.get(firstIndex);

            for (int secondIndex = firstIndex + 1; secondIndex < detectedItems.size(); secondIndex++)
            {
                ItemEntity secondItemEntity = detectedItems.get(secondIndex);
                if (firstItemEntity.position().distanceToSqr(secondItemEntity.position()) > itemStackRadiusSquared)
                {
                    continue;
                }
                nearbyPairCount++;

                ItemStack firstStack = firstItemEntity.getItem();
                ItemStack secondStack = secondItemEntity.getItem();
                ItemEntityEligibilityPolicy.EligibilityResult lifecycleCompatibility =
                        ItemEntityEligibilityPolicy.checkLifecycleCompatibility(firstItemEntity, secondItemEntity);
                boolean compatible = false;
                if (lifecycleCompatibility.eligible())
                {
                    compatibilityCheckCount++;
                    compatible = ItemCompatibilityPolicy.canStack(firstStack, secondStack);
                }
                String incompatibilityReason = lifecycleCompatibility.eligible()
                        ? "item stacks differ"
                        : lifecycleCompatibility.reason();

                if (loggedPairCount < MAX_PAIR_LOG_MESSAGES_PER_SCAN)
                {
                    LOGGER.info(
                            "Nearby dropped item pair is {}: reason={}, first=(id={}, count={}, position=({}, {}, {})), second=(id={}, count={}, position=({}, {}, {}))",
                            compatible ? "compatible" : "incompatible",
                            compatible ? "eligible" : incompatibilityReason,
                            ForgeRegistries.ITEMS.getKey(firstStack.getItem()),
                            firstStack.getCount(),
                            firstItemEntity.getX(),
                            firstItemEntity.getY(),
                            firstItemEntity.getZ(),
                            ForgeRegistries.ITEMS.getKey(secondStack.getItem()),
                            secondStack.getCount(),
                            secondItemEntity.getX(),
                            secondItemEntity.getY(),
                            secondItemEntity.getZ()
                    );
                    loggedPairCount++;
                }
            }
        }

        return new PairScanCounts(nearbyPairCount, compatibilityCheckCount);
    }

    private record PairScanCounts(int nearbyPairs, int compatibilityChecks)
    {
    }

    private static final class IneligibleLogState
    {
        private String reason;
        private long lastLoggedGameTime;
        private long lastSeenGameTime;

        private IneligibleLogState(final long gameTime)
        {
            this.lastLoggedGameTime = Long.MIN_VALUE;
            this.lastSeenGameTime = gameTime;
        }
    }
}
