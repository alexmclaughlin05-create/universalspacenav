package com.universalspacenav;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(UniversalSpaceNavigation.MODID)
public final class UniversalSpaceNavigation {
    public static final String MODID = "universalspacenav";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UniversalSpaceNavigation(IEventBus modBus, ModContainer container) {
        UsnAttachments.TYPES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, UsnConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, UsnConfig.CLIENT_SPEC);
    }
}
