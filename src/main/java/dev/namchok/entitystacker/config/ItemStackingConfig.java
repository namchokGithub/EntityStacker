package dev.namchok.entitystacker.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-side settings for item entity stacking.
 *
 * <p>This class only declares configuration values. Item stacking behavior is
 * implemented separately.</p>
 */
public final class ItemStackingConfig
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_ITEM_STACKING = BUILDER
            .comment("Enables merging compatible nearby dropped item entities into logical stacks.")
            .define("enableItemStacking", true);

    public static final ForgeConfigSpec.DoubleValue ITEM_STACK_RADIUS = BUILDER
            .comment("Maximum distance in blocks for compatible dropped item entities to be considered for stacking.")
            .defineInRange("itemStackRadius", 2.0D, 0.5D, 16.0D);

    public static final ForgeConfigSpec.IntValue MAX_ITEM_STACK_SIZE = BUILDER
            .comment("Maximum number of items represented by one logical item stack.")
            .defineInRange("maxItemStackSize", 1024, 64, 100000);

    public static final ForgeConfigSpec.IntValue STACKING_INTERVAL_TICKS = BUILDER
            .comment("Number of server ticks between item stacking checks.")
            .defineInRange("stackingIntervalTicks", 40, 1, 1200);

    public static final ForgeConfigSpec.BooleanValue SHOW_STACK_COUNT = BUILDER
            .comment("Shows the logical item stack count when a stacked item is displayed.")
            .define("showStackCount", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ItemStackingConfig()
    {
    }
}
