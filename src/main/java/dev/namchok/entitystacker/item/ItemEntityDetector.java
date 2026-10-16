package dev.namchok.entitystacker.item;

import com.mojang.logging.LogUtils;
import dev.namchok.entitystacker.config.ItemStackingConfig;
import dev.namchok.entitystacker.item.service.ItemCompatibilityPolicy;
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
import java.util.List;
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
    private static final int MAX_PAIR_LOG_MESSAGES_PER_SCAN = 10;

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
        int loggedItemCount = 0;

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

                detectedItems.add(itemEntity);

                if (detectedItems.size() >= MAX_ITEM_ENTITIES_PER_SCAN)
                {
                    break playerLoop;
                }

                ItemStack itemStack = itemEntity.getItem();
                if (itemStack.isEmpty())
                {
                    continue;
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

        logNearbyItemPairs(detectedItems);
    }

    private static void logNearbyItemPairs(final List<ItemEntity> detectedItems)
    {
        double itemStackRadius = ItemStackingConfig.ITEM_STACK_RADIUS.get();
        double itemStackRadiusSquared = itemStackRadius * itemStackRadius;
        int loggedPairCount = 0;

        for (int firstIndex = 0; firstIndex < detectedItems.size() - 1; firstIndex++)
        {
            ItemEntity firstItemEntity = detectedItems.get(firstIndex);

            for (int secondIndex = firstIndex + 1; secondIndex < detectedItems.size(); secondIndex++)
            {
                if (loggedPairCount >= MAX_PAIR_LOG_MESSAGES_PER_SCAN)
                {
                    return;
                }

                ItemEntity secondItemEntity = detectedItems.get(secondIndex);
                if (firstItemEntity.position().distanceToSqr(secondItemEntity.position()) > itemStackRadiusSquared)
                {
                    continue;
                }

                ItemStack firstStack = firstItemEntity.getItem();
                ItemStack secondStack = secondItemEntity.getItem();
                boolean compatible = ItemCompatibilityPolicy.canStack(firstStack, secondStack);

                LOGGER.info(
                        "Nearby dropped item pair is {}: first=(id={}, count={}, position=({}, {}, {})), second=(id={}, count={}, position=({}, {}, {}))",
                        compatible ? "compatible" : "incompatible",
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
}
