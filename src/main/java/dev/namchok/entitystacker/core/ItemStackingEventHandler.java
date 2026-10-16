package dev.namchok.entitystacker.core;

import dev.namchok.entitystacker.EntityStacker;
import dev.namchok.entitystacker.config.ItemStackingConfig;
import dev.namchok.entitystacker.item.ItemEntityDetector;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Schedules server-side item entity detection.
 */
@Mod.EventBusSubscriber(modid = EntityStacker.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemStackingEventHandler
{
    private ItemStackingEventHandler()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.side.isClient() || event.phase != TickEvent.Phase.END)
        {
            return;
        }

        if (!ItemStackingConfig.ENABLE_ITEM_STACKING.get())
        {
            return;
        }

        int intervalTicks = ItemStackingConfig.STACKING_INTERVAL_TICKS.get();
        if (event.getServer().getTickCount() % intervalTicks != 0)
        {
            return;
        }

        ItemEntityDetector.logNearbyItems(event.getServer());
    }
}
