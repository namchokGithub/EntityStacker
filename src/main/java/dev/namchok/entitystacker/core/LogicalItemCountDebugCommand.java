package dev.namchok.entitystacker.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.namchok.entitystacker.EntityStacker;
import dev.namchok.entitystacker.config.ItemStackingConfig;
import dev.namchok.entitystacker.item.service.LogicalItemCountStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Temporary operator command for manually verifying logical item count storage.
 * Remove this class after the verification is complete.
 */
@Mod.EventBusSubscriber(modid = EntityStacker.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LogicalItemCountDebugCommand
{
    private static final String DEBUG_MARKER_KEY = "entitystacker.debug_logical_item_count";
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
                )
        );
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

        itemEntity.getPersistentData().putBoolean(DEBUG_MARKER_KEY, true);
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
                itemEntity -> itemEntity.getPersistentData().getBoolean(DEBUG_MARKER_KEY)
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
                itemEntity -> itemEntity.getPersistentData().getBoolean(DEBUG_MARKER_KEY)
        ).stream().findFirst().orElse(null);
    }

    private static ItemStack createDebugStack()
    {
        ItemStack itemStack = new ItemStack(Items.DIAMOND, DEBUG_VISIBLE_COUNT);
        itemStack.setHoverName(Component.literal("EntityStacker logical-count debug"));
        return itemStack;
    }
}
