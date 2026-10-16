package dev.namchok.entitystacker.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.namchok.entitystacker.EntityStacker;
import dev.namchok.entitystacker.config.ItemStackingConfig;
import dev.namchok.entitystacker.item.service.ItemEntityEligibilityPolicy;
import dev.namchok.entitystacker.item.service.ItemMergePlanner;
import dev.namchok.entitystacker.item.service.LogicalItemCountStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Temporary operator commands for manually verifying logical count storage
 * and item merge planning. Remove this class after verification is complete.
 */
@Mod.EventBusSubscriber(modid = EntityStacker.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LogicalItemCountDebugCommand
{
    private static final int DEBUG_LOGICAL_COUNT = 102;
    private static final int DEBUG_VISIBLE_COUNT = 64;
    private static final double DEBUG_SEARCH_RADIUS = 16.0D;

    private LogicalItemCountDebugCommand()
    {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event)
    {
        register(event.getDispatcher());
    }

    private static void register(final CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("entitystacker")
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("logical-count")
                                .then(Commands.literal("create").executes(LogicalItemCountDebugCommand::create))
                                .then(Commands.literal("inspect").executes(LogicalItemCountDebugCommand::inspect))
                                .then(Commands.literal("cleanup").executes(LogicalItemCountDebugCommand::cleanup))
                        )
                        .then(Commands.literal("merge-plan").executes(LogicalItemCountDebugCommand::verifyMergePlan))
                )
        );
    }

    /** Runs isolated planner scenarios; none of these entities enters the world. */
    private static int verifyMergePlan(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (ItemStackingConfig.MAX_ITEM_STACK_SIZE.get() != 1024)
        {
            context.getSource().sendFailure(Component.literal(
                    "Set server maxItemStackSize=1024 before running /entitystacker debug merge-plan."
            ));
            return 0;
        }

        int passed = 0;
        passed += verifyMergePlanScenario(context.getSource(), player, "full transfer",
                900, 100, 100, Items.DIAMOND, false,
                new ItemMergePlanner.MergePlan(true, "ready", 124, 100, 1000, 0));
        passed += verifyMergePlanScenario(context.getSource(), player, "partial transfer",
                900, 200, 200, Items.DIAMOND, false,
                new ItemMergePlanner.MergePlan(true, "ready", 124, 124, 1024, 76));
        passed += verifyMergePlanScenario(context.getSource(), player, "full destination",
                1024, 50, 50, Items.DIAMOND, false,
                new ItemMergePlanner.MergePlan(true, "destination is full", 0, 0, 1024, 50));
        passed += verifyMergePlanScenario(context.getSource(), player, "zero count",
                900, 100, 0, Items.DIAMOND, false,
                new ItemMergePlanner.MergePlan(false, "logical count is invalid or exceeds the maximum", 0, 0, 0, 0));
        passed += verifyMergePlanScenario(context.getSource(), player, "negative count",
                900, 100, -1, Items.DIAMOND, false,
                new ItemMergePlanner.MergePlan(false, "logical count is invalid or exceeds the maximum", 0, 0, 0, 0));
        passed += verifyMergePlanScenario(context.getSource(), player, "count above maximum",
                900, 100, 1025, Items.DIAMOND, false,
                new ItemMergePlanner.MergePlan(false, "logical count is invalid or exceeds the maximum", 0, 0, 0, 0));
        passed += verifyMergePlanScenario(context.getSource(), player, "incompatible items",
                900, 100, 100, Items.EMERALD, false,
                new ItemMergePlanner.MergePlan(false, "item stacks are incompatible", 0, 0, 0, 0));
        passed += verifyMergePlanScenario(context.getSource(), player, "different ages",
                900, 100, 100, Items.DIAMOND, true,
                new ItemMergePlanner.MergePlan(false, "item entity ages differ", 0, 0, 0, 0));

        int passedCount = passed;
        context.getSource().sendSuccess(
                () -> Component.literal("EntityStacker merge-plan verification: "
                        + (passedCount == 8 ? "PASS" : "FAIL") + " (" + passedCount + "/8 scenarios)"),
                false
        );
        return passed == 8 ? 1 : 0;
    }

    private static int verifyMergePlanScenario(
            final CommandSourceStack commandSource,
            final ServerPlayer player,
            final String name,
            final int destinationLogicalCount,
            final int sourceStoredCount,
            final int sourceInputCount,
            final Item sourceItem,
            final boolean differentAges,
            final ItemMergePlanner.MergePlan expected
    )
    {
        ServerLevel level = player.serverLevel();
        ItemEntity destination = new ItemEntity(level, player.getX(), player.getY(), player.getZ(),
                new ItemStack(Items.DIAMOND, 64));
        ItemEntity source = new ItemEntity(level, player.getX(), player.getY(), player.getZ(),
                new ItemStack(sourceItem, Math.min(sourceStoredCount, 64)));

        try
        {
            boolean fixturesReady = LogicalItemCountStorage.setLogicalCount(destination, destinationLogicalCount)
                    && LogicalItemCountStorage.setLogicalCount(source, sourceStoredCount);
            if (differentAges)
            {
                source.setExtendedLifetime();
            }

            ItemStack destinationBefore = destination.getItem().copy();
            ItemStack sourceBefore = source.getItem().copy();
            CompoundTag destinationDataBefore = destination.getPersistentData().copy();
            CompoundTag sourceDataBefore = source.getPersistentData().copy();
            int destinationCountBefore = LogicalItemCountStorage.getLogicalCount(destination);
            int sourceCountBefore = LogicalItemCountStorage.getLogicalCount(source);

            ItemMergePlanner.MergePlan actual = ItemMergePlanner.plan(
                    destination, source, destinationLogicalCount, sourceInputCount, 1024
            );
            boolean unchanged = sameStack(destinationBefore, destination.getItem())
                    && sameStack(sourceBefore, source.getItem())
                    && destinationDataBefore.equals(destination.getPersistentData())
                    && sourceDataBefore.equals(source.getPersistentData())
                    && destinationCountBefore == LogicalItemCountStorage.getLogicalCount(destination)
                    && sourceCountBefore == LogicalItemCountStorage.getLogicalCount(source);
            boolean passed = fixturesReady && expected.equals(actual) && unchanged;

            Component result = Component.literal("merge-plan " + name + ": " + (passed ? "PASS" : "FAIL")
                    + ", fixturesReady=" + fixturesReady
                    + ", expected=" + expected
                    + ", actual=" + actual
                    + ", unchanged=" + unchanged);
            if (passed)
            {
                commandSource.sendSuccess(() -> result, false);
            }
            else
            {
                commandSource.sendFailure(result);
            }
            return passed ? 1 : 0;
        }
        finally
        {
            destination.discard();
            source.discard();
        }
    }

    private static boolean sameStack(final ItemStack expected, final ItemStack actual)
    {
        return expected.getCount() == actual.getCount()
                && ItemStack.isSameItemSameTags(expected, actual);
    }

    private static int create(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ItemStack originalStack = createDebugStack();
        ItemEntity itemEntity = new ItemEntity(level, player.getX(), player.getY() + 1.0D, player.getZ(), originalStack.copy());

        boolean normalCountIsVanilla = LogicalItemCountStorage.getLogicalCount(itemEntity) == DEBUG_VISIBLE_COUNT;
        boolean storedCountAccepted = LogicalItemCountStorage.setLogicalCount(itemEntity, DEBUG_LOGICAL_COUNT);
        boolean storedCountIsReadable = LogicalItemCountStorage.getLogicalCount(itemEntity) == DEBUG_LOGICAL_COUNT;
        boolean invalidCountsRejected = !LogicalItemCountStorage.setLogicalCount(itemEntity, 0)
                && !LogicalItemCountStorage.setLogicalCount(itemEntity, -1)
                && !LogicalItemCountStorage.setLogicalCount(itemEntity, ItemStackingConfig.MAX_ITEM_STACK_SIZE.get() + 1);
        boolean storedCountRemainsReadable = LogicalItemCountStorage.getLogicalCount(itemEntity) == DEBUG_LOGICAL_COUNT;
        boolean itemStackUnchanged = ItemStack.isSameItemSameTags(originalStack, itemEntity.getItem())
                && originalStack.getCount() == itemEntity.getItem().getCount();

        itemEntity.getPersistentData().putBoolean(ItemEntityEligibilityPolicy.DEBUG_ENTITY_MARKER_KEY, true);
        itemEntity.setNoGravity(true);
        itemEntity.setPickUpDelay(32767);
        boolean spawned = level.addFreshEntity(itemEntity);

        boolean passed = normalCountIsVanilla
                && storedCountAccepted
                && storedCountIsReadable
                && invalidCountsRejected
                && storedCountRemainsReadable
                && itemStackUnchanged
                && spawned;

        context.getSource().sendSuccess(
                () -> Component.literal("EntityStacker logical-count debug item created. "
                        + "normal=" + normalCountIsVanilla
                        + ", store/read=" + (storedCountAccepted && storedCountIsReadable)
                        + ", invalid=" + invalidCountsRejected
                        + ", unchanged=" + itemStackUnchanged
                        + ", spawned=" + spawned
                        + ", result=" + (passed ? "PASS" : "FAIL")),
                true
        );
        return passed ? 1 : 0;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemEntity itemEntity = findDebugItem(player);

        if (itemEntity == null)
        {
            context.getSource().sendFailure(Component.literal("No logical-count debug item was found nearby."));
            return 0;
        }

        ItemStack expectedStack = createDebugStack();
        boolean logicalCountPersisted = LogicalItemCountStorage.getLogicalCount(itemEntity) == DEBUG_LOGICAL_COUNT;
        boolean itemStackUnchanged = ItemStack.isSameItemSameTags(expectedStack, itemEntity.getItem())
                && expectedStack.getCount() == itemEntity.getItem().getCount();
        boolean passed = logicalCountPersisted && itemStackUnchanged;

        context.getSource().sendSuccess(
                () -> Component.literal("EntityStacker logical-count debug inspection: "
                        + "logicalCount=" + LogicalItemCountStorage.getLogicalCount(itemEntity)
                        + ", visibleCount=" + itemEntity.getItem().getCount()
                        + ", itemStackUnchanged=" + itemStackUnchanged
                        + ", result=" + (passed ? "PASS" : "FAIL")),
                false
        );
        return passed ? 1 : 0;
    }

    private static int cleanup(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<ItemEntity> debugItems = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(DEBUG_SEARCH_RADIUS),
                itemEntity -> itemEntity.getPersistentData().getBoolean(ItemEntityEligibilityPolicy.DEBUG_ENTITY_MARKER_KEY)
        );

        debugItems.forEach(ItemEntity::discard);
        context.getSource().sendSuccess(
                () -> Component.literal("Removed " + debugItems.size() + " logical-count debug item(s)."),
                true
        );
        return debugItems.size();
    }

    private static ItemEntity findDebugItem(final ServerPlayer player)
    {
        return player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(DEBUG_SEARCH_RADIUS),
                itemEntity -> itemEntity.getPersistentData().getBoolean(ItemEntityEligibilityPolicy.DEBUG_ENTITY_MARKER_KEY)
        ).stream().findFirst().orElse(null);
    }

    private static ItemStack createDebugStack()
    {
        ItemStack itemStack = new ItemStack(Items.DIAMOND, DEBUG_VISIBLE_COUNT);
        itemStack.setHoverName(Component.literal("EntityStacker logical-count debug"));
        return itemStack;
    }
}
